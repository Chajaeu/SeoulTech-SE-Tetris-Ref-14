package seoultech.se.tetris.infra.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** 로컬 네트워크에서 한 명의 클라이언트를 받는 TCP 서버. */
public final class P2PServer implements AutoCloseable {
    public interface Events {
        void onConnected();
        void onMessage(NetworkMessage message);
        void onDisconnected(String reason, Throwable error);
    }

    private final ServerSocket serverSocket;
    private final Events events;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile TcpLink link;
    private final Thread accepter;

    public P2PServer(int port, Events events) throws IOException {
        this.events = Objects.requireNonNull(events);
        this.serverSocket = new ServerSocket(port);
        this.accepter = new Thread(this::acceptLoop, "tetris-p2p-accept");
        accepter.setDaemon(true);
    }

    /** start 후에 접속을 받는다. 생성 중 발생할 수 있는 콜백 경쟁을 방지한다. */
    public void start() { accepter.start(); }
    public int port() { return serverSocket.getLocalPort(); }
    public boolean isConnected() { return link != null && link.isOpen(); }
    public long millisSinceIncoming() { return link == null ? Long.MAX_VALUE : link.millisSinceIncoming(); }

    public boolean send(NetworkMessage message) {
        TcpLink current = link;
        return current != null && current.send(message);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                if (!running.get() || (link != null && link.isOpen())) {
                    socket.close();
                    continue;
                }
                final TcpLink[] holder = new TcpLink[1];
                holder[0] = new TcpLink(socket, new TcpLink.Events() {
                    @Override public void onMessage(NetworkMessage message) { events.onMessage(message); }
                    @Override public void onClosed(String reason, Throwable cause) {
                        if (link == holder[0]) link = null;
                        events.onDisconnected(reason, cause);
                    }
                });
                link = holder[0];
                events.onConnected();
                holder[0].start();
            } catch (IOException e) {
                if (running.get()) events.onDisconnected("서버 접속 대기 실패", e);
                break;
            }
        }
    }

    @Override public void close() {
        running.set(false);
        try { serverSocket.close(); } catch (IOException ignored) { }
        TcpLink current = link;
        if (current != null) current.close();
        accepter.interrupt();
    }
}
