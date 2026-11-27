package seoultech.se.tetris.infra.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** TCP용 길이 프레이밍. Java 객체 역직렬화는 사용하지 않는다. */
public final class MessageCodec {
    public static final int VERSION = 2; // v2: extended GAME_STATE and Core ATTACK_CELL support
    public static final int MAX_FRAME_BYTES = 512 * 1024;

    private MessageCodec() {}

    public static void write(DataOutputStream output, NetworkMessage message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream body = new DataOutputStream(bytes)) {
            body.writeInt(VERSION);
            body.writeUTF(message.type().name());
            body.writeLong(message.sequence());
            byte[] payload = message.payload().getBytes(StandardCharsets.UTF_8);
            body.writeInt(payload.length);
            body.write(payload);
        }
        byte[] frame = bytes.toByteArray();
        if (frame.length > MAX_FRAME_BYTES) throw new IOException("P2P frame too large");
        output.writeInt(frame.length);
        output.write(frame);
        output.flush();
    }

    /** 연결이 정상 종료된 경우 EOFException을 던진다. */
    public static NetworkMessage read(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > MAX_FRAME_BYTES) throw new IOException("Invalid P2P frame length: " + size);
        byte[] frame = input.readNBytes(size);
        if (frame.length != size) throw new EOFException("Truncated P2P frame");
        DataInputStream body = new DataInputStream(new ByteArrayInputStream(frame));
        if (body.readInt() != VERSION) throw new IOException("Unsupported P2P protocol version");
        final MessageType type;
        try {
            type = MessageType.valueOf(body.readUTF());
        } catch (IllegalArgumentException e) {
            throw new IOException("Unknown P2P message type", e);
        }
        long sequence = body.readLong();
        int payloadLen = body.readInt();
        if (sequence < 0 || payloadLen < 0 || payloadLen > size || payloadLen > body.available()) {
            throw new IOException("Malformed P2P message");
        }
        byte[] payload = body.readNBytes(payloadLen);
        if (body.available() != 0) throw new IOException("Trailing P2P frame data");
        return new NetworkMessage(type, sequence, new String(payload, StandardCharsets.UTF_8));
    }
}
