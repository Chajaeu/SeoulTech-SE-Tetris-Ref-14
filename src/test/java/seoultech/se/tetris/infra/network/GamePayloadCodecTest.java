package seoultech.se.tetris.infra.network;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GamePayloadCodecTest {
    @Test void validStateAndAttackRoundTrip() throws IOException {
        GameStateSnapshot state = new GameStateSnapshot(java.util.Collections.nCopies(20,".........."),
                List.of(".II.",".II."), 425, 8, 3, false);
        assertEquals(state, GamePayloadCodec.decodeState(GamePayloadCodec.encodeState(state)));
        AttackPatternDTO attack = new AttackPatternDTO(List.of("##..######", "###..#####"));
        assertEquals(attack, GamePayloadCodec.decodeAttack(GamePayloadCodec.encodeAttack(attack)));
    }
    @Test void rejectInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new AttackPatternDTO(List.of("..")));
        assertThrows(IOException.class, () -> GamePayloadCodec.decodeState("bad"));
    }
}
