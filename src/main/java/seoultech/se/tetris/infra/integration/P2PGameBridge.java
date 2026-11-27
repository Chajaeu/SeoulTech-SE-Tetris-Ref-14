package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.network.*;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Core/UI integration boundary. Core Game is only read/mutated by the caller on
 * its game-update thread. Socket callbacks enqueue immutable DTOs, never touch Game.
 */
public final class P2PGameBridge implements NetworkListener, AutoCloseable {
    private final NetworkListener uiEvents;
    private final AtomicReference<GameStateSnapshot> localState = new AtomicReference<>();
    private final AtomicReference<GameStateSnapshot> remoteState = new AtomicReference<>();
    private final Queue<AttackPatternDTO> pendingRemoteAttacks = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<AttackPatternDTO> unsentLocalAttacks = new ArrayDeque<>();
    private final P2PNetworkService network;
    private final P2PStatePublisher publisher;
    private volatile boolean closed;
    private boolean publishing;

    public P2PGameBridge(NetworkListener uiEvents, Consumer<Throwable> publisherErrors) {
        this(uiEvents, publisherErrors, new RecentIpRepository());
    }

    public P2PGameBridge(NetworkListener uiEvents, Consumer<Throwable> publisherErrors,
                         RecentIpRepository recentIps) {
        this.uiEvents = Objects.requireNonNull(uiEvents, "uiEvents");
        this.network = new P2PNetworkService(this, Objects.requireNonNull(recentIps, "recentIps"));
        this.publisher = new P2PStatePublisher(network, localState::get,
                Objects.requireNonNull(publisherErrors, "publisherErrors"));
    }

    public P2PNetworkService network() { return network; }

    /** Call after a game-frame update (and before/after deletion animation as required). */
    public void captureLocalGame(Game game) {
        publishLocalSnapshot(GameSnapshotAdapter.capture(game));
    }

    public void publishLocalSnapshot(GameStateSnapshot snapshot) {
        if (closed) throw new IllegalStateException("Bridge closed");
        localState.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public GameStateSnapshot getLocalSnapshot() { return localState.get(); }
    public GameStateSnapshot getRemoteSnapshot() { return remoteState.get(); }

    /**
     * Call on the game thread immediately AFTER game.deleteCells(), never inside
     * a socket callback. Game.attack() consumes the Core attack buffer, so retain
     * a DTO when the socket queue rejects a send. Retries happen via flushOutgoingAttacks().
     * @return number of Core attack patterns newly captured (0 or 1)
     */
    public synchronized int afterDeleteCells(Game game) {
        Objects.requireNonNull(game, "game");
        if (closed || network.getLobbyState().phase() != LobbyPhase.PLAYING) return 0;
        var captured = CoreAttackAdapter.fromBoard(game.attack());
        captured.ifPresent(unsentLocalAttacks::addLast);
        flushOutgoingAttacks();
        return captured.isPresent() ? 1 : 0;
    }

    /** Retry Core attack events if an earlier send could not enter the socket queue. */
    public synchronized int flushOutgoingAttacks() {
        if (closed || network.getLobbyState().phase() != LobbyPhase.PLAYING) return 0;
        int sent = 0;
        while (!unsentLocalAttacks.isEmpty()) {
            if (!network.sendAttack(unsentLocalAttacks.peekFirst())) break;
            unsentLocalAttacks.removeFirst();
            sent++;
        }
        return sent;
    }

    public synchronized int queuedOutgoingAttacks() { return unsentLocalAttacks.size(); }
    public int queuedRemoteAttacks() { return pendingRemoteAttacks.size(); }

    /**
     * Call once per game frame, BEFORE Game's tick/input. Only this method may
     * call game.attacked(...); socket callbacks simply enqueue. Core handles the
     * 10-row cap and applies the attack at the correct next-spawn boundary.
     */
    public int applyReceivedAttacks(Game game) {
        Objects.requireNonNull(game, "game");
        if (closed || network.getLobbyState().phase() != LobbyPhase.PLAYING) return 0;
        int applied = 0;
        AttackPatternDTO attack;
        while ((attack = pendingRemoteAttacks.poll()) != null) {
            game.attacked(CoreAttackAdapter.toBoard(attack));
            applied++;
        }
        return applied;
    }

    public synchronized void startAutoPublishing(int updatesPerSecond) {
        if (closed || publishing) throw new IllegalStateException("Already started or closed");
        publisher.start(updatesPerSecond);
        publishing = true;
    }

    public synchronized void startAutoPublishing() {
        startAutoPublishing(P2PStatePublisher.DEFAULT_UPDATES_PER_SECOND);
    }

    private synchronized void clearMatchState() {
        localState.set(null);
        remoteState.set(null);
        pendingRemoteAttacks.clear();
        unsentLocalAttacks.clear();
    }

    @Override public void onConnected() { uiEvents.onConnected(); }
    @Override public void onDisconnected(String reason) {
        clearMatchState();
        uiEvents.onDisconnected(reason);
    }
    @Override public void onMessage(NetworkMessage message) { uiEvents.onMessage(message); }
    @Override public void onLobbyChanged(LobbyState state) { uiEvents.onLobbyChanged(state); }
    @Override public void onMatchStarted(VersusMode mode, int durationSeconds) {
        clearMatchState();
        uiEvents.onMatchStarted(mode, durationSeconds);
    }
    @Override public void onMatchFinished(String result) {
        clearMatchState();
        uiEvents.onMatchFinished(result);
    }
    @Override public void onRemoteGameState(GameStateSnapshot state) {
        remoteState.set(state);
        uiEvents.onRemoteGameState(state);
    }
    @Override public void onRemoteAttack(AttackPatternDTO attack) {
        pendingRemoteAttacks.add(attack);
        // Notification only; UI must not call game.attacked() again from callback.
        uiEvents.onRemoteAttack(attack);
    }
    @Override public void onError(String message, Throwable cause) { uiEvents.onError(message, cause); }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        publisher.close();
        network.close();
        clearMatchState();
    }
}
