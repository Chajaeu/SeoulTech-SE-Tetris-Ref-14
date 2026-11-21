# Team14 Req3 Infra: GameSnapshotAdapter + P2PGameBridge (v3)

적용 기준: 기존 `feature/infra-req3-p2p-foundation` 브랜치의 **454bfad** 커밋(후속 v2 패치) 위에 추가.

## 이 패치가 하는 일

1. `GameSnapshotAdapter` (`infra.integration`): 기존 Core의 `Game.getDisplayBoard()` / `Game.getNextMino()` / 점수·줄·레벨·종료 getter로부터 **불변 `GameStateSnapshot`**을 만든다. 역변환 `decodeBoard`/`decodeNext`도 제공한다.
2. 아이템 셀의 코드 보존: 일반 `L`과 아이템 `l`, 일반 블록 `I/J/L/O/S/T/Z`, `w/c/b/r` 등 구분. 회색 공격 블록은 `#` (임시 Core `G` Cell)로 약속. **회색 표시 방법은 UI와 최종 합의 필요.**
3. `P2PGameBridge` (`infra.integration`): `P2PNetworkService`와 `P2PStatePublisher`를 감싸고, Core 게임 스레드에서 전달한 로컬 스냅샷을 25Hz(변경 시) 자동 전송한다. 상대 게임 스냅샷을 `AtomicReference`에 저장하므로 UI가 읽을 수 있다. 기존 네트워크 이벤트는 `NetworkListener`에 전달한다.
4. 매치 재시작 시 이전 스냅샷 초기화. 기존 v1/v2의 바이너리 전송 포맷은 **변경하지 않았다**.
5. 신규 JUnit 테스트 2개 클래스와 실제 `Game` 객체를 대상으로 하는 Java21 `test-p2p-game-bridge.sh` 스모크 테스트.
6. 어댑터와 브리지는 `infra.integration`에 분리하여 기존 `infra.network` 패키지 및 종전 통신 스모크 스크립트를 수정하지 않는다.

## 사용 코드 예시 (Core/UI 연동을 붙이는 측)

```java
import seoultech.se.tetris.infra.integration.*;
import seoultech.se.tetris.infra.network.*;
import seoultech.se.tetris.game.Cell;

// uiEvents: UI 담당이 구현한 NetworkListener (백그라운드 콜백이므로 Platform.runLater 필요)
try (P2PGameBridge bridge = new P2PGameBridge(uiEvents,
        error -> System.err.println("P2P state publish error: " + error))) {
    P2PNetworkService net = bridge.network();
    bridge.startAutoPublishing(); // 프로그램 동작 중 딱 1회
    int port = net.host(7777); // 또는 net.join(hostIp, port)
    // ... 양쪽 Ready -> START ...
    // Core에서 Game을 업데이트하는 스레드(현재는 주로 JavaFX UI 스레드)에서 호출한다.
    bridge.captureLocalGame(myGame); // 프레임마다 호출 가능; 값이 같으면 송신 생략
    GameStateSnapshot remote = bridge.getRemoteSnapshot();
    if (remote != null) {
        Cell[][] remoteCells = GameSnapshotAdapter.decodeBoard(remote);
        // UI가 상대 보드를 그릴 때 사용 (그리는 코드는 UI 담당)
    }
}
```

### 꼭 지켜야 할 부분

- **절대** `P2PStatePublisher`의 백그라운드 Supplier에서 `Game.getDisplayBoard()`를 직접 호출하지 않는다. `Game`은 변경 가능한 객체이므로 `bridge.captureLocalGame(myGame)`은 UI/Core의 **게임 업데이트 스레드**에서 호출한다. 브리지에서는 불변 스냅샷만 송신한다.
- 경기 시작 시 브리지는 이전 스냅샷을 초기화한다. **`onMatchStarted`에서 새 Game을 만들고 새 스냅샷을 캡처하거나, 다음 UI 게임 업데이트 주기에 `captureLocalGame()`을 호출해야 한다.**
- 로컬 공격 이벤트는 `net.sendAttack(attackDTO)`로 전송하고, 원격 공격 수신은 `uiEvents.onRemoteAttack(...)`를 통해 **Core 대기열**에 전달한다. 현재 어댑터는 공격 줄을 생성하거나 보드에 적용하지 않는다.
- 수신한 `Cell` 객체를 게임에 적용하거나 상대 게임을 시뮬레이션하는 코드가 아니다. UI **표시 전용** 데이터다.
- `Cell`의 임시 회색 코드는 `type='G'`로 복원된다. **현재 `BlockRenderer`가 이 색상을 처리한다는 보장은 없다.** 공격 줄의 실제 생성·적용은 Core 담당 기능이다.
- 코드 작성 시 참고했던 `Game`은 전달받은 마지막 소스 ZIP 기준이다. GitHub `main`의 최신 변경으로 getter나 Cell 의미가 달라졌다면 Mac의 최신 `main`에 적용한 뒤 테스트하면서 맞춰야 한다.
- 종전 `main`에 있는 25개 Core/UI 테스트 실패는 이 패치에서 수정하지 않는다.

## 검증 명령어

```bash
bash scripts/test-p2p-standalone.sh
bash scripts/test-p2p-advanced.sh
bash scripts/test-p2p-game-bridge.sh
./gradlew test --tests 'seoultech.se.tetris.infra.network.*' --tests 'seoultech.se.tetris.infra.integration.*' --rerun-tasks
```

## 아직 실제 게임 통합에서 남은 작업

- `MultiPlayUI`/P2P 로비에서 `P2PGameBridge`를 실제로 생성하고 Core/UI 이벤트 콜백 연결
- Core의 공격 패턴 발생 이벤트 및 공격 줄 대기열 적용
- 게임 종료/승패 결과 이벤트 연동, 서버-클라이언트의 시간제한 종료 시점 조율
- 두 PC에서 입력부터 원격 화면 렌더 완료까지 **200ms 이내인지 실측**
- 최신 `main`에 신규 테스트 2개 포함하여 Gradle 전용 검증
