package seoultech.se.tetris.infra.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

/** 클라이언트 TCP 연결. connect()는 블로킹이므로 UI 스레드에서 직접 호출하지 않는다. */
public final class P2PClient implements AutoCloseable {
    public interface Events {
        void onConnected();
        void onMessage(NetworkMessage message);
        void onDisconnected(String reason, Throwable error);
    }

    private final Events events;
    private volatile TcpLink link;

    public P2PClient(Events events) { this.events = Objects.requireNonNull(events); }

    public synchronized void connect(String host, int port, int timeoutMillis) throws IOException {
        if (isConnected()) throw new IllegalStateException("Already connected");
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis must be positive");
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
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
        } catch (IOException | RuntimeException e) {
            try { socket.close(); } catch (IOException ignored) { }
            throw e;
        }
    }

    public boolean isConnected() { return link != null && link.isOpen(); }
    public boolean send(NetworkMessage message) {
        TcpLink current = link;
        return current != null && current.send(message);
    }
    public long millisSinceIncoming() { return link == null ? Long.MAX_VALUE : link.millisSinceIncoming(); }
    @Override public void close() {
        TcpLink current = link;
        if (current != null) current.close();
    }
}
