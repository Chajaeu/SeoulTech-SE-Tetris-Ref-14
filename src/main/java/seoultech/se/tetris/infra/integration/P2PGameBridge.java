package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.infra.network.*;

import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Core/UI 연동 지점. 소켓은 기존 P2PNetworkService에 위임하고,
 * 실제 Game -> 불변 스냅샷 -> 자동 게시를 연결한다.
 * Game을 읽는 captureLocalGame()은 게임 업데이트 스레드에서 호출해야 한다.
 * 수신 콜백은 소켓 스레드에서 실행될 수 있다(JavaFX: Platform.runLater 사용).
 */
public final class P2PGameBridge implements NetworkListener, AutoCloseable {
    private final NetworkListener uiEvents;
    private final AtomicReference<GameStateSnapshot> localState = new AtomicReference<>();
    private final AtomicReference<GameStateSnapshot> remoteState = new AtomicReference<>();
    private final P2PNetworkService network;
    private final P2PStatePublisher publisher;
    private boolean closed;
    private boolean publishing;

    public P2PGameBridge(NetworkListener uiEvents, Consumer<Throwable> publisherErrors) {
        this(uiEvents, publisherErrors, new RecentIpRepository());
    }

    /** 테스트 시 저장 경로를 분리하기 위한 생성자. */
    public P2PGameBridge(NetworkListener uiEvents, Consumer<Throwable> publisherErrors,
                         RecentIpRepository recentIps) {
        this.uiEvents = Objects.requireNonNull(uiEvents, "uiEvents");
        this.network = new P2PNetworkService(this, Objects.requireNonNull(recentIps, "recentIps"));
        this.publisher = new P2PStatePublisher(network, localState::get,
                Objects.requireNonNull(publisherErrors, "publisherErrors"));
    }

    public P2PNetworkService network() { return network; }

    /** 게임 업데이트 스레드에서 게임 상태를 읽고 무변경 불변 스냅샷을 게시한다. */
    public void captureLocalGame(Game game) {
        publishLocalSnapshot(GameSnapshotAdapter.capture(game));
    }

    /** Core에서 직접 만들었거나 테스트용으로 생성한 스냅샷을 게시한다. */
    public void publishLocalSnapshot(GameStateSnapshot snapshot) {
        if (closed) throw new IllegalStateException("Bridge closed");
        localState.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public GameStateSnapshot getLocalSnapshot() { return localState.get(); }
    public GameStateSnapshot getRemoteSnapshot() { return remoteState.get(); }

    /** 1회 시작. 실제 Game 조회는 여기서 하지 않고 이미 캡처된 불변 DTO만 읽는다. */
    public synchronized void startAutoPublishing(int updatesPerSecond) {
        if (closed || publishing) throw new IllegalStateException("Already started or closed");
        publisher.start(updatesPerSecond);
        publishing = true;
    }

    public synchronized void startAutoPublishing() {
        startAutoPublishing(P2PStatePublisher.DEFAULT_UPDATES_PER_SECOND);
    }

    @Override public void onConnected() { uiEvents.onConnected(); }
    @Override public void onDisconnected(String reason) {
        localState.set(null);
        remoteState.set(null);
        uiEvents.onDisconnected(reason);
    }
    @Override public void onMessage(NetworkMessage message) { uiEvents.onMessage(message); }
    @Override public void onLobbyChanged(LobbyState state) { uiEvents.onLobbyChanged(state); }
    @Override public void onMatchStarted(VersusMode mode, int durationSeconds) {
        localState.set(null);
        remoteState.set(null);  // 새 경기에는 이전 경기 프레임을 사용하지 않는다.
        uiEvents.onMatchStarted(mode, durationSeconds);
    }
    @Override public void onMatchFinished(String result) { uiEvents.onMatchFinished(result); }
    @Override public void onRemoteGameState(GameStateSnapshot state) {
        remoteState.set(state);
        uiEvents.onRemoteGameState(state);
    }
    @Override public void onRemoteAttack(AttackPatternDTO attack) {
        // Core의 공격 대기열에 적용하는 작업은 uiEvents를 통해 게임 스레드에 맡긴다.
        uiEvents.onRemoteAttack(attack);
    }
    @Override public void onError(String message, Throwable cause) { uiEvents.onError(message, cause); }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        publisher.close();
        network.close();
        localState.set(null);
        remoteState.set(null);
    }
}
