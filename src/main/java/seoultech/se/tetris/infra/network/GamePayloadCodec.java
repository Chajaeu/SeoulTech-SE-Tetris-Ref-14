package seoultech.se.tetris.infra.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Core DTO와 네트워크 payload 사이의 안정적인 변환 계층. */
public final class GamePayloadCodec {
    private GamePayloadCodec() {}

    public static String encodeState(GameStateSnapshot state) {
        return write(out -> {
            for (String row : state.board()) out.writeUTF(row);
            out.writeInt(state.next().size());
            for (String row : state.next()) out.writeUTF(row);
            out.writeInt(state.score());
            out.writeInt(state.lines());
            out.writeInt(state.level());
            out.writeBoolean(state.gameOver());
        });
    }

    public static GameStateSnapshot decodeState(String payload) throws IOException {
        try (var in = decode(payload)) {
            List<String> board = new ArrayList<>();
            for (int i = 0; i < 20; i++) board.add(in.readUTF());
            int count = in.readInt();
            if (count < 0 || count > 4) throw new IOException("Invalid next count");
            List<String> next = new ArrayList<>();
            for (int i = 0; i < count; i++) next.add(in.readUTF());
            GameStateSnapshot snapshot = new GameStateSnapshot(board, next, in.readInt(), in.readInt(),
                    in.readInt(), in.readBoolean());
            if (in.available() != 0) throw new IOException("Trailing state payload");
            return snapshot;
        } catch (IllegalArgumentException e) { throw new IOException("Invalid state payload", e); }
    }

    public static String encodeAttack(AttackPatternDTO attack) {
        return write(out -> {
            out.writeInt(attack.rows().size());
            for (String row : attack.rows()) out.writeUTF(row);
        });
    }

    public static AttackPatternDTO decodeAttack(String payload) throws IOException {
        try (var in = decode(payload)) {
            int count = in.readInt();
            if (count < 1 || count > 10) throw new IOException("Invalid attack count");
            List<String> rows = new ArrayList<>();
            for (int i = 0; i < count; i++) rows.add(in.readUTF());
            AttackPatternDTO attack = new AttackPatternDTO(rows);
            if (in.available() != 0) throw new IOException("Trailing attack payload");
            return attack;
        } catch (IllegalArgumentException e) { throw new IOException("Invalid attack payload", e); }
    }

    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static String write(Writer writer) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) { writer.write(out); }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private static DataInputStream decode(String payload) throws IOException {
        try {
            byte[] bytes = Base64.getDecoder().decode(payload);
            if (bytes.length > MessageCodec.MAX_FRAME_BYTES) throw new IOException("Payload too large");
            return new DataInputStream(new ByteArrayInputStream(bytes));
        } catch (IllegalArgumentException e) { throw new IOException("Invalid base64 payload", e); }
    }
}
