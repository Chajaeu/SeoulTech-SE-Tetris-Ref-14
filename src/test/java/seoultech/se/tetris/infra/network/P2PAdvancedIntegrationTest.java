package seoultech.se.tetris.infra.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class P2PAdvancedIntegrationTest {
    @TempDir Path tempDir;

    private static GameStateSnapshot state(int score) {
        return new GameStateSnapshot(Collections.nCopies(20, ".........."), List.of("IIII"), score, 0, 1, false);
    }
    private static void until(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && !condition.getAsBoolean()) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for network condition");
    }

    @Test void busyPortDoesNotLeaveHostInConnectingState() throws Exception {
        try (ServerSocket reserved = new ServerSocket(0);
             P2PNetworkService host = new P2PNetworkService(new NetworkListener() {},
                     new RecentIpRepository(tempDir.resolve("busy")))) {
            assertThrows(IOException.class, () -> host.host(reserved.getLocalPort()));
            assertEquals(LobbyPhase.DISCONNECTED, host.getLobbyState().phase());
            assertTrue(host.host(0) > 0);
        }
    }

    @Test void queuedStatesCoalesceWithoutDroppingAttack() throws Exception {
        try (ServerSocket receiverSocket = new ServerSocket(0);
             Socket sender = new Socket("127.0.0.1", receiverSocket.getLocalPort());
             Socket receiver = receiverSocket.accept()) {
            receiver.setSoTimeout(3_000);
            try (TcpLink link = new TcpLink(sender, new TcpLink.Events() {
                public void onMessage(NetworkMessage message) {}
                public void onClosed(String reason, Throwable cause) {}
            })) {
                for (long seq = 1; seq <= 500; seq++) {
                    assertTrue(link.send(new NetworkMessage(MessageType.GAME_STATE, seq, "frame" + seq)));
                }
                assertTrue(link.send(new NetworkMessage(MessageType.ATTACK, 501, "important-attack")));
                link.start();
                DataInputStream input = new DataInputStream(receiver.getInputStream());
                NetworkMessage latest = MessageCodec.read(input);
                NetworkMessage attack = MessageCodec.read(input);
                assertEquals(MessageType.GAME_STATE, latest.type());
                assertEquals(500, latest.sequence());
                assertEquals(MessageType.ATTACK, attack.type());
                assertEquals(501, attack.sequence());
            }
        }
    }

    @Test void restartedHostIgnoresEventsFromOldConnection() throws Exception {
        try (P2PNetworkService host = new P2PNetworkService(new NetworkListener() {},
                     new RecentIpRepository(tempDir.resolve("host-restart")));
             P2PNetworkService firstClient = new P2PNetworkService(new NetworkListener() {},
                     new RecentIpRepository(tempDir.resolve("first-client")))) {
            int port = host.host(0);
            firstClient.join("127.0.0.1", port).get(3, TimeUnit.SECONDS);
            until(() -> host.isConnected() && firstClient.isConnected());
            firstClient.stop();
            until(() -> host.getLobbyState().phase() == LobbyPhase.DISCONNECTED);
            int newPort = host.host(0);
            try (P2PNetworkService secondClient = new P2PNetworkService(new NetworkListener() {},
                    new RecentIpRepository(tempDir.resolve("second-client")))) {
                secondClient.join("127.0.0.1", newPort).get(3, TimeUnit.SECONDS);
                until(() -> host.isConnected() && secondClient.isConnected());
                Thread.sleep(100);
                assertEquals(LobbyPhase.WAITING, host.getLobbyState().phase());
            }
        }
    }

    private static class Events implements NetworkListener {
        final AtomicInteger startCount = new AtomicInteger();
        final AtomicInteger finishCount = new AtomicInteger();
        final AtomicReference<GameStateSnapshot> remote = new AtomicReference<>();
        final AtomicReference<String> result = new AtomicReference<>();
        @Override public void onMatchStarted(VersusMode mode, int durationSeconds) { startCount.incrementAndGet(); }
        @Override public void onMatchFinished(String result) { finishCount.incrementAndGet(); this.result.set(result); }
        @Override public void onRemoteGameState(GameStateSnapshot snapshot) { remote.set(snapshot); }
    }

    @Test void publisherSendsUpdatedSnapshotsAndMatchCallbacksAreSingleShot() throws Exception {
        Events hostEvents = new Events();
        Events clientEvents = new Events();
        AtomicReference<GameStateSnapshot> current = new AtomicReference<>(state(10));
        AtomicReference<Throwable> publisherError = new AtomicReference<>();
        try (P2PNetworkService host = new P2PNetworkService(hostEvents, new RecentIpRepository(tempDir.resolve("h")));
             P2PNetworkService client = new P2PNetworkService(clientEvents, new RecentIpRepository(tempDir.resolve("c")));
             P2PStatePublisher publisher = new P2PStatePublisher(host, current::get, publisherError::set)) {
            publisher.start(25);
            assertFalse(publisher.publishOnce());
            int port = host.host(0);
            client.join("127.0.0.1", port).get(3, TimeUnit.SECONDS);
            until(() -> host.isConnected() && client.isConnected());
            host.selectMode(VersusMode.NORMAL);
            until(() -> client.getLobbyState().mode() == VersusMode.NORMAL);
            host.setReady(true);
            client.setReady(true);
            until(() -> hostEvents.startCount.get() == 1 && clientEvents.startCount.get() == 1);
            until(() -> state(10).equals(clientEvents.remote.get()));
            current.set(state(99));
            until(() -> state(99).equals(clientEvents.remote.get()));
            host.finishMatch("HOST_WON");
            until(() -> hostEvents.finishCount.get() == 1 && clientEvents.finishCount.get() == 1);
            assertEquals("HOST_WON", clientEvents.result.get());
            assertNull(publisherError.get());
        }
    }
}
