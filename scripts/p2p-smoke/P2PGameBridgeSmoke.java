package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.infra.network.*;

import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Java21 전용: 실제 Game 보드를 localhost P2P로 전달하는 연동 스모크 테스트. */
public final class P2PGameBridgeSmoke {
    private static void check(boolean yes, String reason) {
        if (!yes) throw new AssertionError(reason);
    }
    private static void until(String label, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && !condition.getAsBoolean()) Thread.sleep(10);
        check(condition.getAsBoolean(), "Timeout: " + label);
    }

    public static void main(String[] args) throws Exception {
        Game game = new Game(Game.NORMAL, false);
        GameStateSnapshot original = GameSnapshotAdapter.capture(game);
        check(original.board().size() == 20 && original.board().get(0).length() == 10, "Game dimensions");
        long filled = original.board().stream().flatMapToInt(String::chars).filter(c -> c != '.').count();
        check(filled == 4, "Initial tetromino should have 4 blocks");
        Cell[][] roundtrip = GameSnapshotAdapter.decodeBoard(original);
        check(roundtrip.length == 20 && roundtrip[0].length == 10, "Cell restore");
        AtomicReference<Throwable> error = new AtomicReference<>();
        Path dir = Files.createTempDirectory("p2p-game-bridge-");
        try (P2PGameBridge host = new P2PGameBridge(new NetworkListener() {}, error::set,
                new RecentIpRepository(dir.resolve("host")));
             P2PGameBridge client = new P2PGameBridge(new NetworkListener() {}, error::set,
                new RecentIpRepository(dir.resolve("client")))) {
            host.startAutoPublishing();
            int port = host.network().host(0);
            client.network().join("127.0.0.1", port).get(3, TimeUnit.SECONDS);
            until("connect", () -> host.network().isConnected() && client.network().isConnected());
            host.network().selectMode(VersusMode.NORMAL);
            until("mode", () -> client.network().getLobbyState().mode() == VersusMode.NORMAL);
            host.network().setReady(true);
            client.network().setReady(true);
            until("start", () -> host.network().getLobbyState().phase() == LobbyPhase.PLAYING
                    && client.network().getLobbyState().phase() == LobbyPhase.PLAYING);
            host.captureLocalGame(game);
            until("first real board", () -> original.equals(client.getRemoteSnapshot()));
            game.moveLeft();
            GameStateSnapshot changed = GameSnapshotAdapter.capture(game);
            check(!original.board().equals(changed.board()), "Moving must change board");
            host.captureLocalGame(game);
            until("moved real board", () -> changed.equals(client.getRemoteSnapshot()));
            check(error.get() == null, "Bridge error: " + error.get());
            host.network().finishMatch("HOST_WON");
            until("lobby return", () -> client.network().getLobbyState().phase() == LobbyPhase.WAITING);
        }
        System.out.println("PASS: real Game -> Cell adapter -> TCP -> remote snapshot -> Cell restore");
    }
}
