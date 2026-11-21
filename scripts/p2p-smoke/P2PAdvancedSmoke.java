package seoultech.se.tetris.infra.network;

import seoultech.se.tetris.infra.persistence.RecentIpRepository;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Gradle 없이 Java 21만으로 확인하는 추가 회귀/통합 스모크 테스트. */
public final class P2PAdvancedSmoke {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void until(String name, BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end && !condition.getAsBoolean()) Thread.sleep(10);
        check(condition.getAsBoolean(), "timeout: " + name);
    }
    private static GameStateSnapshot snapshot(int score) {
        return new GameStateSnapshot(Collections.nCopies(20, ".........."), List.of("IIII"), score, 0, 1, false);
    }

    private static void rejectedPortCanRetry() throws Exception {
        Path dir = Files.createTempDirectory("p2p-port-failure-");
        try (ServerSocket reserved = new ServerSocket(0);
             P2PNetworkService host = new P2PNetworkService(new NetworkListener() {}, new RecentIpRepository(dir))) {
            try {
                host.host(reserved.getLocalPort());
                throw new AssertionError("Expected port-in-use failure");
            } catch (IOException expected) { /* port already in use */ }
            check(host.getLobbyState().phase() == LobbyPhase.DISCONNECTED,
                    "Bind failure must restore DISCONNECTED");
            check(host.host(0) > 0, "Retry host after failed bind");
        }
    }

    private static void stateQueueCoalescesAndProtectsAttack() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket sender = new Socket("127.0.0.1", server.getLocalPort());
             Socket receiver = server.accept()) {
            receiver.setSoTimeout(3_000);
            try (TcpLink link = new TcpLink(sender, new TcpLink.Events() {
                public void onMessage(NetworkMessage message) {}
                public void onClosed(String reason, Throwable cause) {}
            })) {
                for (long i = 1; i <= 1_000; i++) {
                    check(link.send(new NetworkMessage(MessageType.GAME_STATE, i, "state-" + i)),
                            "Queue must accept latest state");
                }
                check(link.send(new NetworkMessage(MessageType.ATTACK, 1_001, "attack")),
                        "Attack must not be blocked by redundant snapshots");
                link.start();
                DataInputStream stream = new DataInputStream(receiver.getInputStream());
                NetworkMessage recent = MessageCodec.read(stream);
                NetworkMessage attack = MessageCodec.read(stream);
                check(recent.type() == MessageType.GAME_STATE && recent.sequence() == 1_000,
                        "Only latest queued snapshot must be sent");
                check(attack.type() == MessageType.ATTACK && attack.sequence() == 1_001,
                        "Attack must be preserved");
            }
        }
    }

    private static class Events implements NetworkListener {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger ends = new AtomicInteger();
        final AtomicReference<GameStateSnapshot> remoteState = new AtomicReference<>();
        final AtomicReference<String> result = new AtomicReference<>();
        @Override public void onMatchStarted(VersusMode mode, int seconds) { starts.incrementAndGet(); }
        @Override public void onMatchFinished(String value) { ends.incrementAndGet(); result.set(value); }
        @Override public void onRemoteGameState(GameStateSnapshot state) { remoteState.set(state); }
    }

    private static void publisherAndMatchCallbacks() throws Exception {
        Path dir = Files.createTempDirectory("p2p-publisher-");
        Events h = new Events(), c = new Events();
        AtomicReference<GameStateSnapshot> local = new AtomicReference<>(snapshot(10));
        AtomicReference<Throwable> err = new AtomicReference<>();
        try (P2PNetworkService host = new P2PNetworkService(h, new RecentIpRepository(dir.resolve("h")));
             P2PNetworkService client = new P2PNetworkService(c, new RecentIpRepository(dir.resolve("c")));
             P2PStatePublisher publisher = new P2PStatePublisher(host, local::get, err::set)) {
            publisher.start(P2PStatePublisher.DEFAULT_UPDATES_PER_SECOND);
            check(!publisher.publishOnce(), "Not playing must not send game data");
            int port = host.host(0);
            client.join("127.0.0.1", port).get(3, TimeUnit.SECONDS);
            until("host connect", host::isConnected);
            until("client connect", client::isConnected);
            host.selectMode(VersusMode.NORMAL);
            until("client mode", () -> client.getLobbyState().mode() == VersusMode.NORMAL);
            host.setReady(true);
            client.setReady(true);
            until("started callbacks", () -> h.starts.get() == 1 && c.starts.get() == 1);
            until("automatic state", () -> snapshot(10).equals(c.remoteState.get()));
            local.set(snapshot(55));
            until("updated state", () -> snapshot(55).equals(c.remoteState.get()));
            host.finishMatch("HOST_WON");
            until("finished callbacks", () -> h.ends.get() == 1 && c.ends.get() == 1);
            check("HOST_WON".equals(c.result.get()), "Client result callback");
            check(err.get() == null, "Publisher exception: " + err.get());
        }
    }


    private static void restartAfterDisconnect() throws Exception {
        Path dir = Files.createTempDirectory("p2p-restart-");
        try (P2PNetworkService host = new P2PNetworkService(new NetworkListener() {},
                new RecentIpRepository(dir.resolve("host")));
             P2PNetworkService oldClient = new P2PNetworkService(new NetworkListener() {},
                     new RecentIpRepository(dir.resolve("old")))) {
            int firstPort = host.host(0);
            oldClient.join("127.0.0.1", firstPort).get(3, TimeUnit.SECONDS);
            until("first connection", () -> host.isConnected() && oldClient.isConnected());
            oldClient.stop();
            until("host disconnected", () -> host.getLobbyState().phase() == LobbyPhase.DISCONNECTED);
            int secondPort = host.host(0);
            try (P2PNetworkService nextClient = new P2PNetworkService(new NetworkListener() {},
                    new RecentIpRepository(dir.resolve("next")))) {
                nextClient.join("127.0.0.1", secondPort).get(3, TimeUnit.SECONDS);
                until("second connection", () -> host.isConnected() && nextClient.isConnected());
                Thread.sleep(100); // 이전 세션의 종료 콜백이 새 연결을 종료시키지 않는지 확인
                check(host.getLobbyState().phase() == LobbyPhase.WAITING,
                        "Stale callback must not close next session");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        rejectedPortCanRetry();
        stateQueueCoalescesAndProtectsAttack();
        publisherAndMatchCallbacks();
        restartAfterDisconnect();
        System.out.println("PASS: bind retry, state coalescing, attack priority, auto-publish, match callbacks, reconnect isolation");
    }
}
