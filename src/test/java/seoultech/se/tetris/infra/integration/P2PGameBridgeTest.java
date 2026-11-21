package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.infra.network.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class P2PGameBridgeTest {
    @TempDir Path tempDir;

    private static void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && !condition.getAsBoolean()) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Network event timeout");
    }

    @Test void sendsRealGameStateToRemoteBridgeAndResetsOnRematch() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Throwable> serverError = new AtomicReference<>();
        try (P2PGameBridge host = new P2PGameBridge(new NetworkListener() {}, error::set,
                    new RecentIpRepository(tempDir.resolve("host")));
             P2PGameBridge client = new P2PGameBridge(new NetworkListener() {}, serverError::set,
                    new RecentIpRepository(tempDir.resolve("client")))) {
            host.startAutoPublishing();
            Game game = new Game(Game.NORMAL, false);
            GameStateSnapshot initial = GameSnapshotAdapter.capture(game);
            host.captureLocalGame(game);
            int port = host.network().host(0);
            client.network().join("127.0.0.1", port).get(3, TimeUnit.SECONDS);
            until(() -> host.network().isConnected() && client.network().isConnected());
            host.network().selectMode(VersusMode.NORMAL);
            until(() -> client.network().getLobbyState().mode() == VersusMode.NORMAL);
            host.network().setReady(true);
            client.network().setReady(true);
            until(() -> host.network().getLobbyState().phase() == LobbyPhase.PLAYING
                    && client.network().getLobbyState().phase() == LobbyPhase.PLAYING);
            // 시작 직후에 실제 게임 최신 상태를 캡처한다.
            host.captureLocalGame(game);
            until(() -> initial.equals(client.getRemoteSnapshot()));
            game.moveLeft();
            GameStateSnapshot moved = GameSnapshotAdapter.capture(game);
            host.captureLocalGame(game);
            until(() -> moved.equals(client.getRemoteSnapshot()));
            assertNotEquals(initial.board(), moved.board());
            host.network().finishMatch("HOST_WON");
            until(() -> client.network().getLobbyState().phase() == LobbyPhase.WAITING);
            host.network().setReady(true);
            client.network().setReady(true);
            until(() -> host.network().getLobbyState().phase() == LobbyPhase.PLAYING
                    && client.network().getLobbyState().phase() == LobbyPhase.PLAYING);
            until(() -> client.getRemoteSnapshot() == null); // START 콜백과 PLAYING 전환의 비동기 순서 고려
            assertNull(error.get());
            assertNull(serverError.get());
        }
    }
}
