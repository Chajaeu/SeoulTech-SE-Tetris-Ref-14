package seoultech.se.tetris.infra.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Binary encoding for network DTOs. Core Game objects never cross this boundary. */
public final class GamePayloadCodec {
    private static final int EXTENDED_STATE_VERSION = 2;
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
            // Trailing extension: v1 state payloads remain readable by this decoder.
            // Both peers must run the new package to read extended v2 payloads.
            out.writeByte(EXTENDED_STATE_VERSION);
            out.writeBoolean(state.paused());
            out.writeBoolean(state.waitingDelete());
            out.writeInt(state.pendingAttacks().size());
            for (String row : state.pendingAttacks()) out.writeUTF(row);
            out.writeInt(state.deleteWaitingCells().size());
            for (String row : state.deleteWaitingCells()) out.writeUTF(row);
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
            int score = in.readInt();
            int lines = in.readInt();
            int level = in.readInt();
            boolean gameOver = in.readBoolean();
            if (in.available() == 0) {
                // Previously issued 6-field state payload.
                return new GameStateSnapshot(board, next, score, lines, level, gameOver);
            }
            int version = in.readUnsignedByte();
            if (version != EXTENDED_STATE_VERSION) throw new IOException("Unsupported state extension");
            boolean paused = in.readBoolean();
            boolean waitingDelete = in.readBoolean();
            int attacksCount = in.readInt();
            if (attacksCount != 0 && attacksCount != 10) throw new IOException("Invalid attack display count");
            List<String> pending = new ArrayList<>(attacksCount);
            for (int i = 0; i < attacksCount; i++) pending.add(in.readUTF());
            int deletionCount = in.readInt();
            if (deletionCount != 0 && deletionCount != 20) throw new IOException("Invalid deletion mask count");
            List<String> deletion = new ArrayList<>(deletionCount);
            for (int i = 0; i < deletionCount; i++) deletion.add(in.readUTF());
            GameStateSnapshot snapshot = new GameStateSnapshot(board, next, score, lines,
                    level, gameOver, pending, paused, waitingDelete, deletion);
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
