package seoultech.se.tetris.infra.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.game.Board;
import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.network.*;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class P2PCoreApiIntegrationTest {
    @TempDir Path tempDir;

    private static void until(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < until && !condition.getAsBoolean()) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Timeout waiting for P2P event");
    }

    private static class TestGame extends Game {
        Board outgoing = new Board(0, Game.COLS);
        TestGame() { super(Game.NORMAL, false, true); }
        @Override public Board attack() {
            Board captured = outgoing;
            outgoing = new Board(0, Game.COLS);
            return captured;
        }
    }

    @Test void transfersRealCoreAttackAndExtendedGameStateWithoutSocketThreadMutation() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (P2PGameBridge host = new P2PGameBridge(new NetworkListener() {}, error::set,
                    new RecentIpRepository(tempDir.resolve("h")));
             P2PGameBridge client = new P2PGameBridge(new NetworkListener() {}, error::set,
                    new RecentIpRepository(tempDir.resolve("c")))) {
            host.startAutoPublishing();
            client.startAutoPublishing();
            int port = host.network().host(0);
            client.network().join("127.0.0.1", port).get(3, TimeUnit.SECONDS);
            until(() -> host.network().isConnected() && client.network().isConnected());
            host.network().selectMode(VersusMode.NORMAL);
            until(() -> client.network().getLobbyState().mode() == VersusMode.NORMAL);
            host.network().setReady(true);
            client.network().setReady(true);
            until(() -> host.network().getLobbyState().phase() == LobbyPhase.PLAYING
                    && client.network().getLobbyState().phase() == LobbyPhase.PLAYING);

            TestGame hostGame = new TestGame();
            Game clientGame = new Game(Game.NORMAL, false, true);
            Board attack = CoreAttackAdapter.toBoard(new AttackPatternDTO(List.of(".#########", "###.######")));
            hostGame.outgoing = attack;
            assertEquals(1, host.afterDeleteCells(hostGame));
            assertEquals(0, host.queuedOutgoingAttacks());
            until(() -> client.queuedRemoteAttacks() == 1);
            assertEquals(1, client.applyReceivedAttacks(clientGame));
            assertEquals(0, client.queuedRemoteAttacks());
            assertEquals(List.of(".#########", "###.######"),
                    GameSnapshotAdapter.capture(clientGame).pendingAttacks().subList(8, 10));

            clientGame.pause();
            client.captureLocalGame(clientGame);
            assertTrue(client.getLocalSnapshot().paused());
            until(() -> host.getRemoteSnapshot() != null && host.getRemoteSnapshot().paused());
            assertEquals(10, host.getRemoteSnapshot().pendingAttacks().size());
            assertEquals(0, host.afterDeleteCells(hostGame));
            assertNull(error.get());

            host.network().finishMatch("HOST_WON");
            until(() -> client.network().getLobbyState().phase() == LobbyPhase.WAITING);
            assertEquals(0, client.queuedRemoteAttacks());
        }
    }
}
