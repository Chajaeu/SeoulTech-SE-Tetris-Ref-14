package seoultech.se.tetris.infra.network;

import org.junit.jupiter.api.Test;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.integration.GameSnapshotAdapter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtendedGamePayloadTest {
    @Test void extendedStateContainsPausePendingAttackAndDeletionMask() throws IOException {
        List<String> emptyBoard = Collections.nCopies(20, "..........");
        List<String> queue = Collections.nCopies(10, ".#.#......");
        List<String> mask = Collections.nCopies(20, "#.........");
        GameStateSnapshot state = new GameStateSnapshot(emptyBoard, List.of("I..."),
                12, 3, 2, false, queue, true, true, mask);
        GameStateSnapshot decoded = GamePayloadCodec.decodeState(GamePayloadCodec.encodeState(state));
        assertEquals(state, decoded);
        assertTrue(decoded.paused());
        assertTrue(decoded.waitingDelete());
        assertEquals(queue, decoded.pendingAttacks());
        assertTrue(GameSnapshotAdapter.decodeDeleteWaitingCells(decoded)[0][0]);
        assertFalse(GameSnapshotAdapter.decodeDeleteWaitingCells(decoded)[0][1]);
        assertEquals(Game.ATTACK_CELL, GameSnapshotAdapter.decodePendingAttacks(decoded)[0][1].getType());
    }

    @Test void legacySixFieldPayloadStillDecodes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            for (int i = 0; i < 20; i++) out.writeUTF("..........");
            out.writeInt(0);  // no NEXT
            out.writeInt(40);
            out.writeInt(2);
            out.writeInt(1);
            out.writeBoolean(false);
        }
        var decoded = GamePayloadCodec.decodeState(Base64.getEncoder().encodeToString(bytes.toByteArray()));
        assertEquals(40, decoded.score());
        assertTrue(decoded.pendingAttacks().isEmpty());
        assertFalse(decoded.waitingDelete());
        assertFalse(decoded.paused());
    }

    @Test void malformedExtendedStateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GameStateSnapshot(
                Collections.nCopies(20, ".........."), List.of(),
                0, 0, 1, false, Collections.nCopies(10, "BAD"), false, false, List.of()));
    }
}
