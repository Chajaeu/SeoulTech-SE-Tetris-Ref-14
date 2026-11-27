package seoultech.se.tetris.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static seoultech.se.tetris.game.CoreTestSupport.*;

class GameLinesAndScoreTest {

    @Test
    void completedLineWaitsUntilDeleteCellsIsCalled() {
        Game game = pendingSingleLine(Game.NORMAL, 0);
        assertMaskRows(game.getDeleteWaitingCells(), 19);
        assertEquals(12, occupied(board(game).getBoardCopy()));
        assertEquals(0, game.getScore());
        assertEquals(0, game.getLines());
        assertFalse(game.checkCanOperate());

        String before = contents(board(game).getBoardCopy());
        String expectedNext = contents(game.getNextMino());
        game.tick();

        assertNull(current(game));
        assertEquals(before, contents(board(game).getBoardCopy()));
        assertEquals(expectedNext, contents(game.getNextMino()));

        game.deleteCells();

        assertFalse(game.getIsWaitingDelete());
        assertNull(game.getDeleteWaitingCells());
        assertTrue(game.checkCanOperate());
        assertEquals(100, game.getScore());
        assertEquals(1, game.getLines());

        // 18행의 O 두 칸이 19행으로 내려온다.
        assertEquals(2, occupied(board(game).getBoardCopy()));
        assertEquals('O', board(game).getCell(19, 4).getType());
        assertEquals('O', board(game).getCell(19, 5).getType());
        assertTrue(board(game).getCell(18, 4).isEmpty());
        assertTrue(board(game).getCell(18, 5).isEmpty());

        assertNotNull(current(game));
        assertEquals(expectedNext, contents(shapeOf(current(game))));
        assertEquals(0, current(game).getRow());
        assertEquals(100, game.getScore());
    }

    @Test
    void modifyingReturnedMaskDoesNotChangeDeletion() {
        Game game = pendingSingleLine(Game.NORMAL, 0);
        boolean[][] copy = game.getDeleteWaitingCells();
        copy[19][0] = false;
        copy[19] = new boolean[10];
        copy[0][0] = true;

        assertMaskRows(game.getDeleteWaitingCells(), 19);
        game.deleteCells();

        assertEquals(1, game.getLines());
        assertEquals(100, game.getScore());
        assertEquals(2, occupied(board(game).getBoardCopy()));
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1, 100", "1, 2, 300", "1, 3, 600", "1, 4, 1000",
        "2, 1, 100", "2, 2, 300", "2, 3, 600", "2, 4, 1000",
        "3, 1, 100", "3, 2, 300", "3, 3, 600", "3, 4, 1000"
    })
    void simultaneousLinesAreDeletedAndScoredOnce(
            int difficulty, int deletedLines, int expectedLineScore) {
        Mino mino = new Mino('I');
        mino.setShape(mino.createRotatedCW());
        Game game = new Game(difficulty, false);

        // 세로 I가 한 칸 내려가면 4열의 16~19행을 차지한다.
        set(game, "currentMino", at(mino, 15, 2));
        int[] targetRows = new int[deletedLines];
        for (int i = 0; i < deletedLines; i++) {
            int row = 20 - deletedLines + i;
            targetRows[i] = row;
            fillRow(board(game), row, 4);
        }

        game.harddrop();

        assertTrue(game.getIsWaitingDelete());
        assertMaskRows(game.getDeleteWaitingCells(), targetRows);
        assertEquals(difficulty, game.getScore());
        assertEquals(0, game.getLines());

        game.deleteCells();

        assertEquals(deletedLines, game.getLines());
        assertEquals(difficulty + expectedLineScore, game.getScore());
        assertFalse(game.getIsWaitingDelete());
        assertNull(game.getDeleteWaitingCells());
        assertEquals(4 - deletedLines, occupied(board(game).getBoardCopy()));

        for (int r = 16 + deletedLines; r < 20; r++) {
            assertEquals('I', board(game).getCell(r, 4).getType());
        }

        // 같은 삭제 완료 신호가 다시 들어와도 점수와 보드는 유지된다.
        String afterDeletion = contents(board(game).getBoardCopy());
        game.deleteCells();
        assertEquals(deletedLines, game.getLines());
        assertEquals(difficulty + expectedLineScore, game.getScore());
        assertEquals(afterDeletion, contents(board(game).getBoardCopy()));
    }

    @Test
    void separatedFullRowsMoveSurvivorsCorrectly() {
        Mino mino = new Mino('I');
        mino.setShape(mino.createRotatedCW());
        Game game = active(mino, 16, 2);

        fillRow(board(game), 17, 4);
        fillRow(board(game), 19, 4);
        board(game).setCell(16, 0, new Cell('Z'));
        board(game).setCell(18, 9, new Cell('T'));

        game.harddrop();
        assertMaskRows(game.getDeleteWaitingCells(), 17, 19);
        game.deleteCells();

        assertEquals(2, game.getLines());
        assertEquals(300, game.getScore());
        assertEquals('Z', board(game).getCell(18, 0).getType());
        assertEquals('I', board(game).getCell(18, 4).getType());
        assertEquals('I', board(game).getCell(19, 4).getType());
        assertEquals('T', board(game).getCell(19, 9).getType());
        assertEquals(4, occupied(board(game).getBoardCopy()));
        assertTrue(board(game).getCell(16, 0).isEmpty());
        assertTrue(board(game).getCell(18, 9).isEmpty());
    }

    @Test
    void scanCanFindAndDeleteTopRowAcrossEmptyRows() {
        Game game = active(new Mino('O'), 18, 4);
        fillRow(board(game), 0);
        game.harddrop();

        assertMaskRows(game.getDeleteWaitingCells(), 0);
        game.deleteCells();

        assertEquals(1, game.getLines());
        assertEquals(100, game.getScore());
        for (int c = 0; c < 10; c++) {
            assertTrue(board(game).getCell(0, c).isEmpty());
        }
        assertEquals(4, occupied(board(game).getBoardCopy()));
        for (int r = 18; r < 20; r++) {
            for (int c = 4; c <= 5; c++) {
                assertEquals('O', board(game).getCell(r, c).getType());
            }
        }
    }

    @Test
    void deleteCellsDoesNothingWithoutPendingDeletion() {
        Game game = active(new Mino('O'), 18, 4);
        assertNull(game.getDeleteWaitingCells());
        String before = contents(game.getDisplayBoard());

        game.deleteCells();

        assertNotNull(current(game));
        assertEquals(before, contents(game.getDisplayBoard()));
        assertEquals(0, game.getScore());
        assertEquals(0, game.getLines());

        game.moveDown();
        assertFalse(game.getIsWaitingDelete());
        assertNull(game.getDeleteWaitingCells());
        String afterLocking = contents(game.getDisplayBoard());

        game.deleteCells();

        assertEquals(afterLocking, contents(game.getDisplayBoard()));
        assertEquals(0, game.getScore());
        assertEquals(0, game.getLines());
        assertEquals(4, occupied(board(game).getBoardCopy()));
    }

    @Test
    void deletionCanCompleteWhilePausedWithoutResuming() {
        Game game = pendingSingleLine(Game.NORMAL, 0);
        String expectedNext = contents(game.getNextMino());

        game.pause();
        game.deleteCells();

        assertEquals(1, game.getLines());
        assertEquals(100, game.getScore());
        assertTrue(game.getIsPaused());
        assertFalse(game.getIsWaitingDelete());
        assertNull(game.getDeleteWaitingCells());
        assertFalse(game.checkCanOperate());
        assertEquals(expectedNext, contents(shapeOf(current(game))));

        game.tick();
        assertEquals(0, current(game).getRow());
        assertEquals(100, game.getScore());

        game.resume();
        game.tick();

        assertFalse(game.getIsPaused());
        assertNotNull(current(game));
        assertEquals(expectedNext, contents(shapeOf(current(game))));
        assertEquals(1, current(game).getRow());
        assertEquals(100 + Game.NORMAL, game.getScore());
    }

    @ParameterizedTest
    @CsvSource({
        "3, 1", "4, 2", "5, 2",
        "13, 2", "14, 3", "15, 3",
        "28, 3", "29, 4", "30, 4",
        "48, 4", "49, 5", "50, 5"
    })
    void levelChangesAtExpectedLineThresholds(int previousLines, int expectedLevel) {
        Game game = pendingSingleLine(Game.NORMAL, previousLines);
        game.deleteCells();
        assertEquals(previousLines + 1, game.getLines());
        assertEquals(expectedLevel, game.getLevel());
    }

    @ParameterizedTest
    @CsvSource({
        "1, 4, 2, 920", "2, 4, 2, 900", "3, 4, 2, 880",
        "1, 14, 3, 840", "2, 14, 3, 800", "3, 14, 3, 760"
    })
    void difficultyControlsTickInterval(
            int difficulty, int previousLines, int expectedLevel, int expectedInterval) {
        Game game = pendingSingleLine(difficulty, previousLines);
        game.deleteCells();
        assertEquals(expectedLevel, game.getLevel());
        assertEquals(expectedInterval, game.getTickInterval());
    }

    @ParameterizedTest
    @CsvSource({
        "1, 224, 10, 280", "1, 274, 11, 200", "1, 329, 12, 200",
        "2, 139, 8, 300",  "2, 179, 9, 200",  "2, 224, 10, 200",
        "3, 104, 7, 280",  "3, 139, 8, 200",  "3, 179, 9, 200"
    })
    void tickIntervalDoesNotFallBelowMinimum(
            int difficulty, int previousLines, int expectedLevel, int expectedInterval) {
        Game game = pendingSingleLine(difficulty, previousLines);
        game.deleteCells();
        assertEquals(expectedLevel, game.getLevel());
        assertEquals(expectedInterval, game.getTickInterval());
    }

    @ParameterizedTest
    @CsvSource({"1, 2", "2, 4", "3, 6"})
    void allDropActionsUseUpdatedLevel(int difficulty, int scorePerCell) {
        Game game = pendingSingleLine(difficulty, 4);
        set(game, "nextMino", new Mino('O'));
        game.deleteCells();

        assertEquals(5, game.getLines());
        assertEquals(2, game.getLevel());
        assertEquals(100, game.getScore());
        assertEquals(0, current(game).getRow());
        assertEquals(100, game.getScore());

        game.moveDown();
        assertEquals(1, current(game).getRow());
        assertEquals(100 + scorePerCell, game.getScore());

        game.tick();
        assertEquals(2, current(game).getRow());
        assertEquals(100 + 2 * scorePerCell, game.getScore());

        // 0행에서 17행까지 내려간 뒤 기존 O 위에서 고정된다.
        Mino expectedNext = next(game);
        game.harddrop();

        assertSame(expectedNext, current(game));
        assertEquals(100 + 17 * scorePerCell, game.getScore());
        assertEquals(5, game.getLines());
    }

    private Game pendingSingleLine(int difficulty, int previousLines) {
        Game game = new Game(difficulty, false);
        set(game, "lines", previousLines);
        set(game, "currentMino", at(new Mino('O'), 18, 4));
        fillRow(board(game), 19, 4, 5);
        game.moveDown();

        assertNull(current(game));
        assertTrue(game.getIsWaitingDelete());
        return game;
    }

    private void assertMaskRows(boolean[][] mask, int... expectedRows) {
        assertNotNull(mask);
        assertEquals(20, mask.length);
        for (int r = 0; r < 20; r++) {
            assertNotNull(mask[r]);
            assertEquals(10, mask[r].length);

            boolean expected = false;
            for (int expectedRow : expectedRows) {
                if (r == expectedRow) {
                    expected = true;
                    break;
                }
            }

            for (int c = 0; c < 10; c++) {
                assertEquals(expected, mask[r][c], "row=" + r + ", col=" + c);
            }
        }
    }
}