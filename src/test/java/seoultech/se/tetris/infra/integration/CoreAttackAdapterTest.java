package seoultech.se.tetris.infra.integration;

import org.junit.jupiter.api.Test;
import seoultech.se.tetris.game.Board;
import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.network.AttackPatternDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreAttackAdapterTest {
    @Test void coreBoardToNetworkDtoAndBackPreservesHolesAndRowOrder() {
        Board first = new Board(2, Game.COLS);
        for (int r = 0; r < 2; r++) for (int c = 0; c < Game.COLS; c++) {
            if (c != r && c != r + 3) first.setCell(r, c, new Cell(Game.ATTACK_CELL));
        }
        AttackPatternDTO attack = CoreAttackAdapter.fromBoard(first).orElseThrow();
        assertEquals(List.of(".##.######", "#.##.#####"), attack.rows());
        Board decoded = CoreAttackAdapter.toBoard(attack);
        assertEquals(attack, CoreAttackAdapter.fromBoard(decoded).orElseThrow());
        Game game = new Game(Game.NORMAL, false, true);
        game.attacked(decoded);
        var pending = GameSnapshotAdapter.capture(game);
        assertEquals(10, pending.pendingAttacks().size());
        assertEquals(attack.rows(), pending.pendingAttacks().subList(8, 10));
        Cell[][] remote = GameSnapshotAdapter.decodePendingAttacks(pending);
        assertEquals(Game.ATTACK_CELL, remote[9][0].getType());
        assertTrue(remote[9][1].isEmpty());
    }

    @Test void emptyCoreAttackDoesNotGenerateAnEvent() {
        assertTrue(CoreAttackAdapter.fromBoard(new Board(0, Game.COLS)).isEmpty());
    }

    @Test void malformedCoreAttackIsRejected() {
        Board invalid = new Board(1, Game.COLS);
        invalid.setCell(0, 0, new Cell('I'));
        assertThrows(IllegalArgumentException.class, () -> CoreAttackAdapter.fromBoard(invalid));
    }
}
