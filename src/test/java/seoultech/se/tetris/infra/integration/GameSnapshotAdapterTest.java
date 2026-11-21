package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.infra.network.*;

import org.junit.jupiter.api.Test;
import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameSnapshotAdapterTest {
    private static Cell[][] empty(int height, int width) {
        Cell[][] cells = new Cell[height][width];
        for (int r = 0; r < height; r++) for (int c = 0; c < width; c++) cells[r][c] = new Cell();
        return cells;
    }

    @Test void capturesRealGameWithoutMutatingIt() throws Exception {
        Game game = new Game(Game.NORMAL, false);
        GameStateSnapshot original = GameSnapshotAdapter.capture(game);
        assertEquals(20, original.board().size());
        assertTrue(original.board().stream().allMatch(row -> row.length() == 10));
        assertEquals(4, original.board().stream().flatMapToInt(String::chars)
                .filter(c -> c != '.').count());
        assertFalse(original.next().isEmpty());
        assertEquals(game.getScore(), original.score());
        assertEquals(game.getLines(), original.lines());
        assertEquals(game.getLevel(), original.level());
        assertEquals(game.getIsGameover(), original.gameOver());
        // fromGame은 UI 스레드에서 읽기만 하며 Game의 진행상태를 변경하지 않는다.
        assertEquals(original, GameSnapshotAdapter.capture(game));
        game.moveLeft();
        assertNotEquals(original.board(), GameSnapshotAdapter.capture(game).board());
    }

    @Test void encodesItemCellsSeparatelyFromOrdinaryBlocks() throws Exception {
        Cell[][] board = empty(20, 10);
        Cell[][] next = empty(2, 2);
        board[0][0] = new Cell('L');            // 일반 L
        board[0][1] = new Cell('L', true, 'L'); // 줄 삭제 아이템 L
        board[0][2] = new Cell('B', true, 'B'); // 폭탄
        board[0][3] = new Cell('G', false, 'G'); // 회색 공격 줄 전송용 예약 타입
        next[1][0] = new Cell('I');
        GameStateSnapshot state = GameSnapshotAdapter.capture(board, next, 123, 4, 2, false);
        assertEquals("Llb#......", state.board().get(0));
        assertEquals(List.of("..", "I."), state.next());
        assertEquals(state, GamePayloadCodec.decodeState(GamePayloadCodec.encodeState(state)));
        Cell[][] restored = GameSnapshotAdapter.decodeBoard(state);
        assertEquals('L', restored[0][0].getType());
        assertFalse(restored[0][0].isItem());
        assertTrue(restored[0][1].isItem());
        assertEquals('L', restored[0][1].getType());
        assertEquals('B', restored[0][2].getType());
        assertEquals('G', restored[0][3].getType());
        assertTrue(restored[0][4].isEmpty());
    }

    @Test void rejectsInvalidDimensionsOrUnknownCellTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> GameSnapshotAdapter.capture(empty(19, 10), empty(2, 2), 0, 0, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> GameSnapshotAdapter.capture(empty(20, 9), empty(2, 2), 0, 0, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> GameSnapshotAdapter.encodeCell(new Cell('O', false, '?')));
        assertThrows(IllegalArgumentException.class,
                () -> GameSnapshotAdapter.decodeCell('?'));
    }
}
