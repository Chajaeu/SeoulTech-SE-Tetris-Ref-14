package seoultech.se.tetris.infra.network;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Core의 읽기 전용 화면 스냅샷을 일정 주기로 전송한다.
 * Core는 Supplier<GameStateSnapshot>만 연결하면 된다. 변화 없을 때는 전송하지 않는다.
 * Game 객체/JavaFX에 직접 의존하지 않으며 백그라운드에서 Supplier를 호출한다.
 */
public final class P2PStatePublisher implements AutoCloseable {
    public static final int DEFAULT_UPDATES_PER_SECOND = 25;
    private final P2PNetworkService network;
    private final Supplier<GameStateSnapshot> snapshotSupplier;
    private final Consumer<Throwable> errorHandler;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "tetris-p2p-state-publisher");
        thread.setDaemon(true);
        return thread;
    });
    private GameStateSnapshot lastSent;
    private boolean running;
    private boolean closed;

    public P2PStatePublisher(P2PNetworkService network, Supplier<GameStateSnapshot> snapshotSupplier,
                             Consumer<Throwable> errorHandler) {
        this.network = Objects.requireNonNull(network);
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier);
        this.errorHandler = Objects.requireNonNull(errorHandler);
    }

    /** 호출은 1번만. 10~60Hz 범위에서 전송 주기 설정. */
    public synchronized void start(int updatesPerSecond) {
        if (closed || running) throw new IllegalStateException("Publisher already started or closed");
        if (updatesPerSecond < 10 || updatesPerSecond > 60) throw new IllegalArgumentException("Rate must be 10-60Hz");
        running = true;
        executor.scheduleAtFixedRate(this::publishOnce, 0, 1_000_000_000L / updatesPerSecond,
                TimeUnit.NANOSECONDS);
    }

    /** 수동 호출도 지원. 반환값은 소켓 큐 등록 여부(실제 원격 표시 완료 여부 아님). */
    public synchronized boolean publishOnce() {
        if (closed || !running) return false;
        if (network.getLobbyState().phase() != LobbyPhase.PLAYING) {
            lastSent = null;  // 재대전 시 첫 화면을 반드시 다시 보내도록 초기화
            return false;
        }
        try {
            GameStateSnapshot current = snapshotSupplier.get();
            if (current == null || current.equals(lastSent)) return false;
            if (!network.sendGameState(current)) return false;
            lastSent = current;
            return true;
        } catch (RuntimeException e) {
            try { errorHandler.accept(e); } catch (RuntimeException ignored) { /* publisher 유지 */ }
            return false;
        }
    }

    @Override public synchronized void close() {
        closed = true;
        executor.shutdownNow();
        lastSent = null;
    }
}
