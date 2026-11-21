# Team14 / Req3 P2P 통신 명세 (Infra 초안 v1)

기준: 2026-11-07 3차 요구사항 회의 / TeamProject_Req3.pdf. **Core·UI 팀원의 API 합의 전 초안**.

## 설계 원칙

- 기존 `Game`/`PlayUI` 구현은 변경하지 않는다. 로컬 대전은 Core에서 `Game` 2개를 사용한다.
- P2P 대전에서는 각 컴퓨터가 자기 `Game`을 실행하고, 상대방은 네트워크로 전달받은 **읽기 전용 화면 스냅샷**을 표시한다.
- 로컬 대전의 공격 줄 생성·적용·승패·시간제한 규칙은 Core, 화면·타이머 표시는 UI, 통신·지연·IP 저장은 Infra가 담당한다.
- 소켓 입출력은 백그라운드 스레드에서 실행. `NetworkListener` 콜백도 백그라운드일 수 있으므로 JavaFX에서 UI 변경 시 `Platform.runLater()`를 사용한다.
- Java 객체 직렬화(ObjectInputStream)를 사용하지 않고, 버전이 있는 길이 기반 바이너리 프레임을 사용한다.

## 전송 포맷

TCP 프레임: `int32 frameLength` 뒤에 `[int32 version=1, UTF messageType, int64 sequence, int32 payloadLength, UTF-8 payloadBytes]`.

- 한 프레임 최대 512 KiB. 잘못된 길이와 버전은 거부.
- `sequence`는 송신측 연결 내 단조 증가. 수신측은 중복·이전 순번 메시지를 무시.
- `GAME_STATE`/`ATTACK` payload는 `GamePayloadCodec`에 정의된 Base64 바이너리 값(게임 DTO 내부값 기반).
- 연결 중 송신 큐 최대 256개. `sendGameState()/sendAttack()`의 `false`는 현재 연결/게임 상태에서 전송 실패 또는 큐 포화를 의미. UI에서 재시도/오류 처리 필요.

| 메시지 | 송신 | payload | 의미 |
| --- | --- | --- | --- |
| `HELLO` | 양측 | `1` | 프로토콜 버전 확인 |
| `MODE_SELECT` | 서버 | `NORMAL|0`, `ITEM|0`, `TIMED|180` 등 | 경기 유형 및 제한시간(초) 전달 |
| `READY` | 양측 | `true` / `false` | 준비 여부 |
| `START` | 서버 | 빈 문자열 | 양측 준비 완료, 경기 시작 |
| `GAME_STATE` | 양측 | GamePayloadCodec.encodeState 결과 | 원격 보드 표시 |
| `ATTACK` | 양측 | GamePayloadCodec.encodeAttack 결과 | Core에 공격 전달 |
| `GAME_OVER` | 양측 | 결과 문자열 | 결과 화면 및 로비 복귀 |
| `REMATCH` | 예약 | - | 재대전은 현재 READY 재요청으로 구현 |
| `PING`, `PONG` | 양측 | 송신 측 nanoTime의 십진수 문자열 | 왕복 지연 / 연결 확인 |
| `DISCONNECT` | 양측 | - | 정상 종료를 알리는 선택 메시지 |
| `ERROR` | 양측 | 오류 설명 | 오류 안내 |

## 자료형 (Core·UI가 최종 확인해야 할 규약)

### `GameStateSnapshot`

- `board`: **20개 문자열**, 각 문자열 길이 **10**. 위에서 아래 순서. **현재 블록을 포함한 화면 표시 상태**.
- `next`: 미리보기 행 문자열 0~4개, 각 행 길이 1~4.
- `score`, `lines`, `level`, `gameOver`: 원격 화면에 필요한 최소 정보.
- 각 셀은 ASCII 1문자: `.`은 빈칸, 나머지는 블록/색상/아이템을 식별할 코드(추후 팀 합의).
- `Game.getDisplayBoard()`/`getNextMino()`의 `Cell[][]`를 위 코드표에 따라 변환하는 **Core/UI 어댑터는 아직 없음**.
- 보내는 쪽은 `GamePayloadCodec.encodeState(state)`를 직접 호출할 필요 없이 `P2PNetworkService.sendGameState(state)`를 사용.

### `AttackPatternDTO`

- 1~10개 행, 각 행은 10문자. `#`은 공격 블록, `.`은 공격을 발생시킨 블록 위치의 빈칸.
- **공격 줄 판정, 마지막 블록의 빈칸 위치 계산, 최대 10줄 대기/초과 처리, 적용 시점은 Core 책임**.
- 전송만 `P2PNetworkService.sendAttack(pattern)`으로 수행.

## P2P 로비 흐름

1. Host: `service.host(port)` → LAN IP(`NetworkAddresses.lanIpv4Addresses()`), 포트 표시.
2. Client: `service.join(hostIp, port)` → 성공하면 최근 접속 IP 자동 저장.
3. TCP 연결 → 양쪽 `onConnected()` / `onLobbyChanged(WAITING)`.
4. Host: `selectMode(VersusMode.NORMAL|ITEM|TIMED)` 또는 `selectMode(VersusMode.TIMED, 240)` → Client에게 모드 및 시간제한(초) 동기화.
5. Host·Client: 각자 게임 시작 버튼으로 `setReady(true)`.
6. Host가 양쪽 Ready 확인 후 `START` → 양쪽 `PLAYING`.
7. Core가 게임 진행하는 동안 `sendGameState(snapshot)` 및 공격 발생 시 `sendAttack(pattern)`.
8. Core가 승패 판정 후 `finishMatch(result)` 호출 → 양쪽 `WAITING`, Ready 초기화. 다시 5번부터 재대전 가능.
9. 통신 단절 / heartbeat 타임아웃 → `DISCONNECTED`; P2P 초기 화면으로 돌아가도록 UI 연동.

## 지연 및 네트워크 이상 (팀이 정한 임시 기준)

- 500ms 이상 수신 메시지가 없으면 `LobbyState.lagging`을 통해 UI에 랙 표시.
- 3,000ms 이상 수신 메시지가 없으면 연결 종료 처리.
- 500ms 간격 PING/PONG, `service.getLastRttMillis()`로 최근 왕복 지연 확인.
- **요구사항의 두 PC에서 200ms 이하 화면 반응 지연은 Ping RTT와 다른 지표**. 실제 Core 입력 시간 → 원격 UI 렌더링 완료까지 측정해야 하고, 이 구현만으로 충족을 주장할 수 없음.
- 네트워크 불안정, 방화벽, Wi-Fi, 패킷 큐 등이 실제 성능에 영향을 준다.

## 공개 API

```java
P2PNetworkService service = new P2PNetworkService(new NetworkListener() {
  @Override public void onConnected() { /* 접속 완료 UI */ }
  @Override public void onLobbyChanged(LobbyState state) { /* JavaFX: Platform.runLater(...) */ }
  @Override public void onRemoteGameState(GameStateSnapshot state) { /* 원격 보드 그리기 */ }
  @Override public void onRemoteAttack(AttackPatternDTO attack) { /* Core 공격 대기에 전달 */ }
  @Override public void onMessage(NetworkMessage message) { /* GAME_OVER 등의 일반 이벤트 */ }
  @Override public void onDisconnected(String reason) { /* 초기 화면 복귀 */ }
});
// Host
int port = service.host(7777);
service.selectMode(VersusMode.NORMAL);  // 시간제한은 service.selectMode(VersusMode.TIMED, 240);
// Client
service.join("192.168.0.10", 7777); // CompletableFuture<Void>
service.getRecentIps();
// 양쪽
service.setReady(true);
// PLAYING 중
service.sendGameState(snapshot);
service.sendAttack(pattern);
// 경기 종료
service.finishMatch("HOST_WON");
// 연결 종료
service.close();
```

## Core/UI 협의 필요 사항

1. `Cell[][]` → ASCII 셀 코드 매핑과 NEXT·특수 블록 종류 표현(색약 모드 포함).
2. 공격 줄의 빈칸 위치 계산 및 Core 대기 큐 전달 API.
3. 3차 게임 승패 이벤트, 스코어보드 미사용, UI 결과 화면 호출 규약.
4. **시간제한 경기의 시작·종료 동기화**: `durationSeconds`는 서버에서 양측으로 전달함. 단, 서버 기준 정확한 시작/종료 시각, 동점 처리는 Core·UI 합의 및 실제 게임 적용이 필요함.
5. `GAME_OVER` 결과 문자열의 정식 enum/자료형, 중복 종료 및 동시 게임 오버 처리.
6. 네트워크 재연결 정책과 서버·클라이언트가 각자 복귀할 화면.
7. P2P 연결 실패 화면 및 Ready 버튼 연결, UI 랙 표시 기준.

## 완료 범위와 남은 부분

- Infra 단독: 프레임·DTO·TCP·Ready/START·상태/공격 메시지 전송·Ping/Pong·최근 IP 저장·로비 복귀와 재대전 메시지 흐름.
- 미완성/타팀 필요: Core 대전 판정·공격 패턴 생성·실제 `Cell` 매핑, UI 2인 보드/메뉴/로비/오류 화면, 시간제한 통합, 실제 2대 PC 200ms 검증, 최종 기능 통합 및 배포.

## 검증

- 프로젝트에서 `./gradlew test jacocoTestReport`로 전체 JUnit/JaCoCo 실행.
- Gradle 실행이 어려운 환경에서도 `bash scripts/test-p2p-standalone.sh`로 Java21 TCP 흐름 검증.
- 두 PC에서 `bash scripts/run-p2p-console.sh host 7777`, `bash scripts/run-p2p-console.sh join <호스트IP> 7777` 실행 가능. 단, 방화벽에서 포트 7777 허용 필요. 두 PC 콘솔에서 `mode NORMAL`, `ready`, `state`, `attack`, `finish`, `status` 명령으로 메시지 확인.
