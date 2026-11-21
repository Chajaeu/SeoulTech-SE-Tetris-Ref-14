# Team14 / Req3 Infra 후속 구현 v2 (2026-11-21)

기준 브랜치: `feature/infra-req3-p2p-foundation`, 기존 커밋 `9ccf167`.
Core·UI 개발자의 소스는 변경하지 않으며 기존 네트워크 API는 유지한다.

## 이번에 추가/수정한 것

1. **P2P 상태 자동 게시기** — `P2PStatePublisher` 추가. 공급받은 불변 `GameStateSnapshot`을 기본 25Hz로 점검하고 **변경된 경우에만 전송**. `WAITING`일 때는 게시하지 않고 화면 캐시를 초기화한다. Core/UI 어댑터가 실제 스냅샷을 제공해야 한다.
2. **송신 큐 지연 개선** — `TcpLink`는 아직 전송하지 않은 오래된 `GAME_STATE`를 최신 것으로 교체한다. `ATTACK`, `READY`, `GAME_OVER`와 같은 제어 이벤트는 오래된 화면 상태 때문에 큐가 포화된 경우 그 화면 상태를 제거해 보존한다. 모두 제어 이벤트로 가득 차면 기존처럼 `send(...)`가 실패할 수 있다. TCP에 적재되었더라도 원격 적용 성공을 보증하지는 않는다.
3. **경기 시작/종료 전용 콜백** — 기존 `NetworkListener`에 `onMatchStarted(mode, durationSeconds)`, `onMatchFinished(result)` 기본 메서드를 추가. 기존 구현체는 수정 없이 컴파일된다. 콜백은 **JavaFX 스레드가 아닐 수 있음**에 주의.
4. **재접속 세션 격리** — 이전 연결의 지연된 네트워크 콜백이 새로운 연결에 적용되지 않도록 서비스 내부 세션 ID를 도입. 연결 종료 후 재호스팅도 확인한다.
5. **포트 점유 예외 복원** — `host()`에서 먼저 소켓을 생성한 뒤 서비스 상태를 변경한다. 포트 충돌로 실패해도 새 포트로 즉시 재시도가 가능하다.
6. **추가 자동화 테스트** — JUnit `P2PAdvancedIntegrationTest` 4개와 Java21 독립 `P2PAdvancedSmoke`를 추가해 테스트한다.

## 사용 예시

```java
P2PNetworkService network = new P2PNetworkService(new NetworkListener() {
    @Override public void onMatchStarted(VersusMode mode, int seconds) {
        // 게임 시작 이벤트를 UI/Core에 전달한다. JavaFX 화면은 Platform.runLater 사용.
    }
    @Override public void onMatchFinished(String result) {
        // 결과 표시 및 로비 복귀. result는 아직 Core와 합의할 문자열.
    }
});

// Core가 업데이트해 주는 불변 snapshot만 읽어야 한다. Game 객체를 여기서 직접 수정하지 않는다.
AtomicReference<GameStateSnapshot> current = new AtomicReference<>();
P2PStatePublisher publisher = new P2PStatePublisher(network, current::get,
        error -> System.err.println("P2P state error: " + error));
publisher.start(P2PStatePublisher.DEFAULT_UPDATES_PER_SECOND);
// Core 갱신 시 current.set(snapshot) 호출. 변경된 화면만 자동 전송.
// 게임 종료 또는 UI 종료 시 publisher.close(), network.close().
```

> 멀티스레드 주의: `P2PStatePublisher`는 백그라운드 스레드에서 Supplier를 호출한다. Core의 변경 가능한 `Game` 객체를 직접 읽는 Supplier를 넣지 말고, 게임 스레드가 만들어 둔 **불변 스냅샷을 `AtomicReference` 등으로 게시**하도록 연결해야 한다.

## 적용 방법

이 패치는 기존 Infra ZIP을 **이미 적용하고 커밋/Push한 상태**에서 추가한다.

```bash
cd ~/Desktop/SeoulTech-SE-Tetris-Ref-14
git status -sb
git switch feature/infra-req3-p2p-foundation
# 기존 수정사항을 덮어쓸 수 있으니 확인 후 적용; zip에 필요한 파일만 수록됨.
unzip -o ~/Desktop/Team14_Req3_Infra_P2P_Followup_v2.zip -d .
bash scripts/test-p2p-standalone.sh
bash scripts/test-p2p-advanced.sh
./gradlew test --tests 'seoultech.se.tetris.infra.network.*' --rerun-tasks
# 다른 팀원의 main에 기존 25개 실패가 있는 것은 별도 문제
git status -sb
git diff --stat
```

## 아직 남은 부분

- Core의 `Game` -> `GameStateSnapshot`/`AttackPatternDTO` 변환 및 공격 큐 실제 적용
- UI의 `P2PNetworkService` 연결, JavaFX 화면 갱신 및 로비/최근 IP 표시
- TIME_LIMIT 서버 권위 기반 종료·동점 정책 합의 및 게임별 결과 충돌 방지
- 실제 LAN 2-PC 테스트 및 **입력→상대 화면 렌더 완료** 지연 200ms 검증 (Ping RTT만으로 대체 불가)
- 두 PC 상호 운용 시 양쪽 모두 같은 버전 적용; 메시지 와이어 포맷(v1)은 그대로
