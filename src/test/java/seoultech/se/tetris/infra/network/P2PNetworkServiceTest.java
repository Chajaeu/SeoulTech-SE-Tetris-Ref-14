package seoultech.se.tetris.infra.network;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class P2PNetworkServiceTest {
    @TempDir Path tempDir;
    static class Events implements NetworkListener {
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch stateReceived = new CountDownLatch(1);
        final CountDownLatch attackReceived = new CountDownLatch(1);
        final AtomicReference<GameStateSnapshot> state = new AtomicReference<>();
        final AtomicReference<AttackPatternDTO> attack = new AtomicReference<>();
        @Override public void onConnected() { connected.countDown(); }
        @Override public void onRemoteGameState(GameStateSnapshot snapshot) { state.set(snapshot); stateReceived.countDown(); }
        @Override public void onRemoteAttack(AttackPatternDTO message) { attack.set(message); attackReceived.countDown(); }
    }

    private static void until(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < end && !condition.getAsBoolean()) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for P2P condition");
    }

    @Test void localhostConnectReadyStateAttackAndRematch() throws Exception {
        Events hostEvents = new Events();
        Events clientEvents = new Events();
        try (P2PNetworkService host = new P2PNetworkService(hostEvents, new RecentIpRepository(tempDir.resolve("h")));
             P2PNetworkService client = new P2PNetworkService(clientEvents, new RecentIpRepository(tempDir.resolve("c")))) {
            int port = host.host(0);
            client.join("127.0.0.1", port).get(3, TimeUnit.SECONDS);
            assertTrue(hostEvents.connected.await(3, TimeUnit.SECONDS));
            assertTrue(clientEvents.connected.await(3, TimeUnit.SECONDS));
            until(() -> client.getRecentIps().equals(List.of("127.0.0.1")));
            host.selectMode(VersusMode.TIMED, 240);
            until(() -> client.getLobbyState().mode() == VersusMode.TIMED && client.getLobbyState().durationSeconds() == 240);
            host.setReady(true);
            client.setReady(true);
            until(() -> host.getLobbyState().phase() == LobbyPhase.PLAYING
                    && client.getLobbyState().phase() == LobbyPhase.PLAYING);

            GameStateSnapshot state = new GameStateSnapshot(Collections.nCopies(20, ".........."),
                    List.of("IIII"), 123, 4, 1, false);
            AttackPatternDTO pattern = new AttackPatternDTO(List.of("###..#####", "####..####"));
            assertTrue(client.sendGameState(state));
            assertTrue(host.sendAttack(pattern));
            assertTrue(hostEvents.stateReceived.await(3, TimeUnit.SECONDS));
            assertTrue(clientEvents.attackReceived.await(3, TimeUnit.SECONDS));
            assertEquals(state, hostEvents.state.get());
            assertEquals(pattern, clientEvents.attack.get());

            host.finishMatch("HOST_WON");
            until(() -> host.getLobbyState().phase() == LobbyPhase.WAITING
                    && client.getLobbyState().phase() == LobbyPhase.WAITING);
            host.setReady(true);
            client.setReady(true);
            until(() -> host.getLobbyState().phase() == LobbyPhase.PLAYING
                    && client.getLobbyState().phase() == LobbyPhase.PLAYING);
        }
    }

    @Test void silentPeerTriggersLagAndTimeout() throws Exception {
        try (P2PNetworkService host = new P2PNetworkService(new Events(), new RecentIpRepository(tempDir.resolve("slow")))) {
            int port = host.host(0);
            try (java.net.Socket silent = new java.net.Socket("127.0.0.1", port)) {
                until(() -> host.getLobbyState().phase() == LobbyPhase.WAITING);
                until(() -> host.getLobbyState().lagging());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline && host.getLobbyState().phase() != LobbyPhase.DISCONNECTED) {
                    Thread.sleep(30);
                }
                assertEquals(LobbyPhase.DISCONNECTED, host.getLobbyState().phase());
            }
        }
    }

    @Test void failedConnectionDoesNotLeaveSessionStuck() throws Exception {
        try (P2PNetworkService client = new P2PNetworkService(new Events(), new RecentIpRepository(tempDir.resolve("fail")));
             java.net.ServerSocket reserved = new java.net.ServerSocket(0)) {
            int port = reserved.getLocalPort();
            reserved.close();
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> client.join("127.0.0.1", port).get(3, TimeUnit.SECONDS));
            until(() -> client.getLobbyState().phase() == LobbyPhase.DISCONNECTED);
        }
    }
}
