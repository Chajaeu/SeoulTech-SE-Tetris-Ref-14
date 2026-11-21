package seoultech.se.tetris.infra.network;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {
    @Test void roundTrip() throws IOException {
        var source = new NetworkMessage(MessageType.GAME_STATE, 123, "한글 메시지 🎮");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        MessageCodec.write(new DataOutputStream(b), source);
        assertEquals(source, MessageCodec.read(new DataInputStream(new ByteArrayInputStream(b.toByteArray()))));
    }

    @Test void rejectsInvalidFrameLength() {
        assertThrows(IOException.class, () -> MessageCodec.read(new DataInputStream(
                new ByteArrayInputStream(new byte[]{127,127,127,127}))));
    }

    @Test void rejectsOversizedPayload() {
        NetworkMessage message = new NetworkMessage(MessageType.ERROR, 1, "X".repeat(MessageCodec.MAX_FRAME_BYTES));
        assertThrows(IOException.class, () -> MessageCodec.write(new DataOutputStream(new ByteArrayOutputStream()), message));
    }
}
