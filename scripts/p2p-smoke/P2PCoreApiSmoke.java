package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.game.Board;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.network.*;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class P2PCoreApiSmoke {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && !condition.getAsBoolean()) Thread.sleep(10);
        check(condition.getAsBoolean(), "timeout");
    }
    private static class TestGame extends Game {
        private Board outgoing = new Board(0, Game.COLS);
        TestGame() { super(Game.NORMAL, false, true); }
        @Override public Board attack() {
            Board result = outgoing;
            outgoing = new Board(0, Game.COLS);
            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        var attack = new AttackPatternDTO(List.of(".#########", "###.######"));
        Board coreAttack = CoreAttackAdapter.toBoard(attack);
        check(attack.equals(CoreAttackAdapter.fromBoard(coreAttack).orElseThrow()), "Core Board conversion");
        Game realGame = new Game(Game.NORMAL, false, true);
        realGame.attacked(coreAttack);
        realGame.pause();
        GameStateSnapshot state = GameSnapshotAdapter.capture(realGame);
        check(state.paused() && state.pendingAttacks().size() == 10, "Core state fields");
        check(state.equals(GamePayloadCodec.decodeState(GamePayloadCodec.encodeState(state))), "extended wire state");
        check(GameSnapshotAdapter.decodePendingAttacks(state)[9][3].isEmpty(), "attack hole");
        Game deletingGame = new Game(Game.NORMAL, false, true) {
            @Override public boolean getIsWaitingDelete() { return true; }
            @Override public boolean[][] getDeleteWaitingCells() {
                boolean[][] mask = new boolean[ROWS][COLS];
                mask[19][4] = true;
                return mask;
            }
        };
        GameStateSnapshot deleting = GameSnapshotAdapter.capture(deletingGame);
        check(deleting.waitingDelete() && deleting.deleteWaitingCells().size() == 20,
                "actual Core deletion snapshot");
        check(GameSnapshotAdapter.decodeDeleteWaitingCells(deleting)[19][4], "deletion mask location");
        check(deleting.equals(GamePayloadCodec.decodeState(GamePayloadCodec.encodeState(deleting))),
                "deletion mask wire roundtrip");
        AtomicReference<Throwable> errors = new AtomicReference<>();
        var dir = Files.createTempDirectory("core-api-smoke");
        try (var host = new P2PGameBridge(new NetworkListener() {}, errors::set,
                    new RecentIpRepository(dir.resolve("host")));
             var client = new P2PGameBridge(new NetworkListener() {}, errors::set,
                    new RecentIpRepository(dir.resolve("client")))) {
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
            client.captureLocalGame(realGame);
            until(() -> state.equals(host.getRemoteSnapshot()));
            TestGame mockCore = new TestGame();
            mockCore.outgoing = coreAttack;
            check(host.afterDeleteCells(mockCore) == 1, "capturing Core outgoing event");
            until(() -> client.queuedRemoteAttacks() == 1);
            Game otherGame = new Game(Game.NORMAL, false, true);
            check(client.applyReceivedAttacks(otherGame) == 1, "UI-thread attack application");
            check(attack.rows().equals(GameSnapshotAdapter.capture(otherGame).pendingAttacks().subList(8, 10)), "Core remote queue");
            host.network().finishMatch("HOST_WON");
            until(() -> client.network().getLobbyState().phase() == LobbyPhase.WAITING);
            check(errors.get() == null, "unexpected exception " + errors.get());
        }
        System.out.println("PASS: Core Board attack adapter, extended snapshot, TCP state, queued attacks");
    }
}
