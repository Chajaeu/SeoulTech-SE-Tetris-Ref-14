package seoultech.se.tetris.infra.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 하나의 TCP 링크. 수신/송신은 각각 백그라운드 스레드에서 처리한다. */
final class TcpLink implements AutoCloseable {
    interface Events {
        void onMessage(NetworkMessage message);
        void onClosed(String reason, Throwable cause);
    }

    private final Socket socket;
    private final Events events;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final BlockingQueue<NetworkMessage> queue = new ArrayBlockingQueue<>(256);
    private final AtomicBoolean active = new AtomicBoolean(true);
    private volatile long lastReceivedNanos = System.nanoTime();
    private Thread reader;
    private Thread writer;

    TcpLink(Socket socket, Events events) throws IOException {
        this.socket = Objects.requireNonNull(socket);
        this.events = Objects.requireNonNull(events);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    void start() {
        reader = new Thread(this::readLoop, "tetris-p2p-reader");
        writer = new Thread(this::writeLoop, "tetris-p2p-writer");
        reader.setDaemon(true);
        writer.setDaemon(true);
        writer.start();
        reader.start();
    }

    boolean send(NetworkMessage message) {
        return active.get() && queue.offer(Objects.requireNonNull(message));
    }

    boolean isOpen() { return active.get(); }
    long millisSinceIncoming() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastReceivedNanos);
    }

    private void readLoop() {
        try {
            while (active.get()) {
                NetworkMessage message = MessageCodec.read(input);
                lastReceivedNanos = System.nanoTime();
                events.onMessage(message);
            }
        } catch (EOFException e) {
            shutdown("상대방이 연결을 종료했습니다.", null);
        } catch (IOException e) {
            shutdown("네트워크 연결이 종료되었습니다.", e);
        } catch (RuntimeException e) {
            shutdown("네트워크 메시지 처리 중 오류가 발생했습니다.", e);
        }
    }

    private void writeLoop() {
        try {
            while (active.get()) {
                NetworkMessage message = queue.poll(250, TimeUnit.MILLISECONDS);
                if (message != null) MessageCodec.write(output, message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            shutdown("네트워크 전송에 실패했습니다.", e);
        }
    }

    private void shutdown(String reason, Throwable cause) {
        if (!active.compareAndSet(true, false)) return;
        try { socket.close(); } catch (IOException ignored) { }
        queue.clear();
        if (writer != null) writer.interrupt();
        events.onClosed(reason, cause);
    }

    @Override public void close() { shutdown("연결이 종료되었습니다.", null); }
}
