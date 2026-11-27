# Team14 3차 요구사항: 최신 Core API 연동 (Infra v4)

대상: `Team14-main-latest.zip` + `Team14-infra-current.zip` (2026-11-26 전달본).
최신 main/Infra의 `game/Game.java`는 동일한 것으로 확인함. **Core 및 UI 소스는 수정하지 않음.**

## 1. Core 문서와 실제 코드의 차이 (중요)

Core에서 전달한 `대전 API 및 연동 흐름.md`에는
`boolean[][] attack()` / `void attacked(boolean[][])`로 안내되어 있으나,
**업로드된 실제 `Game.java`에는 `Board attack()` / `void attacked(Board)`가 존재한다.**
따라서 Infra는 실제 코드에 맞춰 `CoreAttackAdapter`를 사용한다.

- `CoreAttackAdapter.fromBoard(game.attack())`: `Board` (0~N행, 10열) → `AttackPatternDTO` (1~10행, 10열). `0`행이면 송신하지 않음.
- `CoreAttackAdapter.toBoard(attackDto)`: `AttackPatternDTO` → `Board` → `game.attacked(board)`.
- 참조 셀 타입은 `Game.ATTACK_CELL == 'A'`, UI 표시는 회색. 패킷 공격 패턴은 `#`=공격칸, `.`=빈칸.
- 행 순서는 위→아래, 열은 왼쪽→오른쪽. **Core가 관리하는 최대 10줄/고정 후 적용 규칙은 수정하지 않음.**

### 주의: 공격 생성 시점

Core는 `placeMino()`에서 공격 배열을 준비하며, UI가 줄 삭제 애니메이션을 완료하고 `game.deleteCells()`를 호출한 **직후**
`bridge.afterDeleteCells(game)`를 호출해야 한다. `attack()`은 Core 버퍼를 비우므로 다른 곳에서 중복 호출하지 않는다.
네트워크 전송 큐가 당장 받지 못하는 경우 Infra 내부 큐에 보존하여 `flushOutgoingAttacks()`로 재시도한다.
단, 이는 **송신 큐 접수 확인**일 뿐 상대 PC가 실제로 적용했다는 ACK는 아니다.

## 2. 확장된 게임 상태 (#80)

`GameSnapshotAdapter.capture(game)`가 다음 값을 전부 복사한다.

| 값 | 실제 Core API | 네트워크/원격 UI에서의 표현 |
| --- | --- | --- |
| 보드+현재 블록 | `getDisplayBoard()` | 20×10 `board()` |
| NEXT | `getNextMino()` | 최대 4×4 `next()` |
| 점수·레벨·누적 줄 | `getScore()/getLevel()/getLines()` | 숫자 필드 |
| 공격 대기 화면 | `getAttackedLines()` | 10×10 `pendingAttacks()` (빈칸 `.`, 공격 `#`) |
| 게임 종료 | `getIsGameover()` | `gameOver()` |
| 일시정지 | `getIsPaused()` | `paused()` |
| 삭제 애니메이션 | `getIsWaitingDelete()` / `getDeleteWaitingCells()` | `waitingDelete()`, 20×10 `deleteWaitingCells()` 마스크 |

`getDeleteWaitingCells()`가 `null`이면 mask는 빈 리스트로 전송하고, 진행 중이면 20행으로 전송한다.
`GameSnapshotAdapter.decodeBoard`, `decodeNext`, `decodePendingAttacks`, `decodeDeleteWaitingCells`를 UI 표시용으로 사용할 수 있다.
이전 v3의 6인자 `GameStateSnapshot` 생성자도 남겨 기존 호출부가 컴파일되도록 했다.

### 프로토콜 버전

확장된 상태 패킷을 오래된 클라이언트가 잘못 해석하지 않도록 `MessageCodec.VERSION = 2`로 변경했다.
`HELLO` 역시 해당 버전 값을 사용한다. **양쪽 PC 모두 v4 패치를 적용한 동일한 게임 빌드를 사용해야 한다.**
새 디코더는 기존 6필드 상태 payload를 읽을 수 있지만, 구버전 클라이언트와의 네트워크 호환을 보장하지 않는다.

## 3. 실제 UI에서 호출하는 순서

```java
// P2P 연결 이후, Game은 반드시 대전용 생성자로 준비:
Game game = new Game(Game.NORMAL, false, true);

// 게임 시작 직후, 호출 1회:
bridge.startAutoPublishing();

// UI/게임 프레임마다, Core를 조작하는 동일 스레드에서:
bridge.applyReceivedAttacks(game);    // Core attacked(Board) 호출
bridge.flushOutgoingAttacks();       // 이전 프레임에 밀린 송신 재시도
// ... 키보드 입력 / game.tick() / 보드 조작 ...
bridge.captureLocalGame(game);       // 현재 Game 스냅샷 갱신

// 삭제 연출이 필요한 경우:
bridge.captureLocalGame(game);       // 삭제되기 전 상태 및 삭제 마스크 게시
// ... UI가 삭제 애니메이션 표시 ...
game.deleteCells();                  // UI에서 애니메이션 후 딱 1회 호출
bridge.afterDeleteCells(game);       // Core attack() 딱 1회 소비, 송신
bridge.captureLocalGame(game);       // 삭제 후 상태 즉시 캡처
```

`P2PStatePublisher`가 현재 스냅샷을 기본 25Hz로 전송하지만 **실제 Game 객체를 백그라운드에서 조회하지는 않는다**.
반드시 UI의 게임 처리 루프에서 `captureLocalGame(game)`를 반복적으로 호출해야 한다.
수신된 상대 보드는 `bridge.getRemoteSnapshot()` 또는 `NetworkListener.onRemoteGameState()`로 조회한다.
`onRemoteAttack()` 이벤트는 알림용이며 이 콜백에서 `game.attacked()`를 직접 호출하면 **중복 적용**된다.

주의: `afterDeleteCells`는 `game.deleteCells()` 뒤에 호출되어야 함. 삭제 애니메이션과 게임 시작/종료 타이밍은 UI 담당과 최종 연동 필요.

## 4. 현재 완료 범위 및 남은 부분

### Infra 단독 구현 / 스모크 테스트 확인

- TCP 전송, READY/START, 최근 접속 IP, PING/PONG, 종료·재대전 네트워크 흐름
- 실제 Core 상태 조회 및 공격 대기·정지·삭제 마스크 변환
- `Board` 형태의 Core 공격 데이터 ↔ 네트워크 DTO 변환
- 소켓 스레드의 공격을 큐로 보관하고 게임 스레드에서 `attacked(Board)` 호출
- 소켓 전송 실패 시 즉시 소멸시키지 않고 송신 대기 큐에 보관
- 재대전/종료/연결 끊김 시 공격 대기 이벤트 초기화

### 아직 완료 아님

- `MultiPlayUI`, `P2PLobby` 화면에서 실제 `bridge` 호출 연결
- Core 종료/시간제한 UI 조정, 승패·무승부 최종 일치 확인
- 두 PC LAN 지연 200ms 이내의 **UI 렌더링 완료 시간** 측정
- Windows/macOS 배포본 동작, 최종 JaCoCo 70% 이상
- 기존 main의 Core/UI 테스트 실패 25개는 별도 담당 확인 필요 (2026-11-21 시점 기록)

## 5. 실행 및 검증

```bash
bash scripts/test-p2p-standalone.sh
bash scripts/test-p2p-advanced.sh
bash scripts/test-p2p-game-bridge.sh
bash scripts/test-p2p-core-api.sh
./gradlew test --tests 'seoultech.se.tetris.infra.network.*' --tests 'seoultech.se.tetris.infra.integration.*' --rerun-tasks
```

독립 테스트는 Java 21로 컴파일한 뒤 localhost 서버/클라이언트 간 실제 DTO·ATTACK 메시지 왕복을 검사한다.
JUnit 테스트는 사용자의 macOS Gradle 환경에서 별도로 실행해야 한다.
