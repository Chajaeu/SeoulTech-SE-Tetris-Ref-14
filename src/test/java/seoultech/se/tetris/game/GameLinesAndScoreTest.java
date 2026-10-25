package seoultech.se.tetris.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static seoultech.se.tetris.game.CoreTestSupport.*;

class GameLinesAndScoreTest {

    // 1. 삭제 대기와 실제 삭제를 구분하고 다음 블럭으로 연결한다.
    @Test
    void completedLineWaitsUntilDeleteCellsIsCalled() {
        Game game = pendingSingleLine(Game.NORMAL, 0);

        assertMaskRows(game.getDeleteWaitingCells(), 19);

        // 마지막 행 10칸 + 그 위 O의 두 칸
        assertEquals(12, occupied(board(game).getBoardCopy()));

        assertEquals(0, game.getScore());
        assertEquals(0, game.getLines());
        assertFalse(game.checkCanOperate());

        String before = contents(board(game).getBoardCopy());
        String expectedNext = contents(game.getNextMino());

        // 삭제 대기 중에는 다음 블럭이 등장하지 않는다.
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

        // 18행에 남았던 O의 두 칸이 19행으로 내려온다.
        assertEquals(2, occupied(board(game).getBoardCopy()));
        assertEquals('O', board(game).getCell(19, 4).getType());
        assertEquals('O', board(game).getCell(19, 5).getType());
        assertTrue(board(game).getCell(18, 4).isEmpty());
        assertTrue(board(game).getCell(18, 5).isEmpty());

        // 삭제 직후에도 현재 블럭은 아직 없다.
        assertNull(current(game));

        game.tick();

        assertNotNull(current(game));
        assertEquals(expectedNext, contents(shapeOf(current(game))));
        assertEquals(0, current(game).getRow());
        assertEquals(100, game.getScore());
    }

    // 2. UI에 전달한 삭제 마스크를 바꿔도 실제 삭제 대상은 유지된다.
    @Test
    void modifyingReturnedMaskDoesNotChangeDeletion() {
        Game game = pendingSingleLine(Game.NORMAL, 0);

        boolean[][] copy = game.getDeleteWaitingCells();

        copy[19][0] = false;
        copy[19] = new boolean[10];
        copy[0][0] = true;

        // 내부에서는 여전히 19행 전체만 삭제 대상으로 남아 있어야 한다.
        assertMaskRows(game.getDeleteWaitingCells(), 19);

        game.deleteCells();

        assertEquals(1, game.getLines());
        assertEquals(100, game.getScore());
        assertEquals(2, occupied(board(game).getBoardCopy()));
    }

    // 3. 난이도 3종에서 1~4줄 동시 삭제와 점수 합산을 검사한다.
    @ParameterizedTest
    @CsvSource({
        "1, 1, 100",
        "1, 2, 300",
        "1, 3, 600",
        "1, 4, 1000",
        "2, 1, 100",
        "2, 2, 300",
        "2, 3, 600",
        "2, 4, 1000",
        "3, 1, 100",
        "3, 2, 300",
        "3, 3, 600",
        "3, 4, 1000"
    })
    void simultaneousLinesAreDeletedAndScoredOnce(
            int difficulty,
            int deletedLines,
            int expectedLineScore) {

        Mino mino = new Mino('I');
        mino.setShape(mino.createRotatedCW());

        Game game = new Game(difficulty, false);

        // 세로 I의 실제 칸은 4열에 위치한다.
        // 15행에서 시작하면 한 칸 내려가서 16~19행을 차지한다.
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

        // 레벨 1에서 한 칸 낙하한 점수만 먼저 반영된다.
        assertEquals(difficulty, game.getScore());
        assertEquals(0, game.getLines());

        game.deleteCells();

        assertEquals(deletedLines, game.getLines());
        assertEquals(
                difficulty + expectedLineScore,
                game.getScore()
        );

        assertFalse(game.getIsWaitingDelete());
        assertNull(game.getDeleteWaitingCells());

        // 삭제되지 않은 I의 칸은 아래로 이동해서 남는다.
        assertEquals(
                4 - deletedLines,
                occupied(board(game).getBoardCopy())
        );

        for (int r = 16 + deletedLines; r < 20; r++) {
            assertEquals('I', board(game).getCell(r, 4).getType());
        }

        // 삭제 완료 신호가 다시 와도 중복 처리하면 안 된다.
        String afterDeletion = contents(board(game).getBoardCopy());

        game.deleteCells();

        assertEquals(deletedLines, game.getLines());
        assertEquals(
                difficulty + expectedLineScore,
                game.getScore()
        );
        assertEquals(
                afterDeletion,
                contents(board(game).getBoardCopy())
        );
    }

    // 4. 떨어져 있는 두 행을 삭제해도 남은 행의 이동 거리가 맞는다.
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

        // 16행은 아래에서 두 행이 삭제되므로 두 칸 내려온다.
        assertEquals('Z', board(game).getCell(18, 0).getType());
        assertEquals('I', board(game).getCell(18, 4).getType());

        // 18행은 아래에서 한 행만 삭제되므로 한 칸 내려온다.
        assertEquals('I', board(game).getCell(19, 4).getType());
        assertEquals('T', board(game).getCell(19, 9).getType());

        assertEquals(4, occupied(board(game).getBoardCopy()));
        assertTrue(board(game).getCell(16, 0).isEmpty());
        assertTrue(board(game).getCell(18, 9).isEmpty());
    }

    // 5. 중간에 빈 행이 있어도 탐색을 계속하며 최상단 행도 삭제한다.
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

        // 최상단 삭제는 그보다 아래에 있는 O의 위치를 바꾸지 않는다.
        assertEquals(4, occupied(board(game).getBoardCopy()));

        for (int r = 18; r < 20; r++) {
            for (int c = 4; c <= 5; c++) {
                assertEquals('O', board(game).getCell(r, c).getType());
            }
        }
    }

    // 6. 삭제할 것이 없을 때 deleteCells는 게임을 변경하지 않는다.
    @Test
    void deleteCellsDoesNothingWithoutPendingDeletion() {
        Game game = active(new Mino('O'), 18, 4);

        // 아직 삭제 탐색 전이므로 마스크가 null이다.
        assertNull(game.getDeleteWaitingCells());

        String before = contents(game.getDisplayBoard());

        game.deleteCells();

        assertNotNull(current(game));
        assertEquals(before, contents(game.getDisplayBoard()));
        assertEquals(0, game.getScore());
        assertEquals(0, game.getLines());

        // O만 고정하면 완성된 행은 없다.
        game.moveDown();

        assertFalse(game.getIsWaitingDelete());

        // 탐색 결과 마스크는 있지만 모든 칸이 false인 상태
        assertMaskRows(game.getDeleteWaitingCells());

        String afterLocking = contents(game.getDisplayBoard());

        game.deleteCells();

        assertEquals(afterLocking, contents(game.getDisplayBoard()));
        assertEquals(0, game.getScore());
        assertEquals(0, game.getLines());
        assertEquals(4, occupied(board(game).getBoardCopy()));
    }

    // 7. 일시정지 중 삭제 완료 신호는 처리하되 게임은 재개하지 않는다.
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

        game.tick();

        assertNull(current(game));
        assertEquals(expectedNext, contents(game.getNextMino()));

        game.resume();
        game.tick();

        assertFalse(game.getIsPaused());
        assertNotNull(current(game));
        assertEquals(expectedNext, contents(shapeOf(current(game))));
        assertEquals(100, game.getScore());
    }

    // 8. 레벨 경계의 직전·도달·직후를 검사한다.
    @ParameterizedTest
    @CsvSource({
        "3, 1",
        "4, 2",
        "5, 2",
        "13, 2",
        "14, 3",
        "15, 3",
        "28, 3",
        "29, 4",
        "30, 4",
        "48, 4",
        "49, 5",
        "50, 5"
    })
    void levelChangesAtExpectedLineThresholds(
            int previousLines, int expectedLevel) {

        Game game = pendingSingleLine(Game.NORMAL, previousLines);

        game.deleteCells();

        assertEquals(previousLines + 1, game.getLines());
        assertEquals(expectedLevel, game.getLevel());
    }

    // 9. 난이도별 레벨당 감소량을 실제 삭제 후 결과로 검사한다.
    @ParameterizedTest
    @CsvSource({
        "1, 4, 2, 920",
        "2, 4, 2, 900",
        "3, 4, 2, 880",
        "1, 14, 3, 840",
        "2, 14, 3, 800",
        "3, 14, 3, 760"
    })
    void difficultyControlsTickInterval(
            int difficulty,
            int previousLines,
            int expectedLevel,
            int expectedInterval) {

        Game game = pendingSingleLine(difficulty, previousLines);

        game.deleteCells();

        assertEquals(expectedLevel, game.getLevel());
        assertEquals(expectedInterval, game.getTickInterval());
    }

    // 10. 최소 간격 도달 전·도달 시·그 이후를 검사한다.
    @ParameterizedTest
    @CsvSource({
        "1, 224, 10, 280",
        "1, 274, 11, 200",
        "1, 329, 12, 200",
        "2, 139, 8, 300",
        "2, 179, 9, 200",
        "2, 224, 10, 200",
        "3, 104, 7, 280",
        "3, 139, 8, 200",
        "3, 179, 9, 200"
    })
    void tickIntervalDoesNotFallBelowMinimum(
            int difficulty,
            int previousLines,
            int expectedLevel,
            int expectedInterval) {

        Game game = pendingSingleLine(difficulty, previousLines);

        game.deleteCells();

        assertEquals(expectedLevel, game.getLevel());
        assertEquals(expectedInterval, game.getTickInterval());
    }

    // 11. 레벨 상승 이후 모든 낙하 방식에 새 점수가 적용된다.
    @ParameterizedTest
    @CsvSource({
        "1, 2",
        "2, 4",
        "3, 6"
    })
    void allDropActionsUseUpdatedLevel(
            int difficulty, int scorePerCell) {

        Game game = pendingSingleLine(difficulty, 4);

        // 낙하 거리를 정확히 정하기 위해 NEXT를 O로 준비한다.
        set(game, "nextMino", new Mino('O'));

        game.deleteCells();

        assertEquals(5, game.getLines());
        assertEquals(2, game.getLevel());
        assertEquals(100, game.getScore());

        // 생성하는 tick에는 낙하 점수를 주지 않는다.
        game.tick();

        assertEquals(0, current(game).getRow());
        assertEquals(100, game.getScore());

        game.moveDown();

        assertEquals(1, current(game).getRow());
        assertEquals(100 + scorePerCell, game.getScore());

        game.tick();

        assertEquals(2, current(game).getRow());
        assertEquals(100 + 2 * scorePerCell, game.getScore());

        // 삭제 후 19행에 남은 O 두 칸 위에 착지한다.
        // 새 O는 0행에서 17행까지 총 17칸 낙하한다.
        game.harddrop();

        assertNull(current(game));
        assertEquals(100 + 17 * scorePerCell, game.getScore());
        assertEquals(5, game.getLines());
    }

    // 마지막 행 하나가 완성되어 삭제 대기 중인 게임을 준비한다.
    private Game pendingSingleLine(
            int difficulty, int previousLines) {

        Game game = new Game(difficulty, false);

        // 검사할 누적 줄 수를 준비한다.
        // 레벨과 속도의 갱신은 실제 deleteCells()로 검사한다.
        set(game, "lines", previousLines);

        set(game, "currentMino", at(new Mino('O'), 18, 4));
        fillRow(board(game), 19, 4, 5);

        // 이미 바닥이므로 이동하지 않고 고정된다.
        // 따라서 여기서는 낙하 점수가 추가되지 않는다.
        game.moveDown();

        assertNull(current(game));
        assertTrue(game.getIsWaitingDelete());

        return game;
    }

    // 지정한 행 전체만 true이고 나머지는 false인지 검사한다.
    private void assertMaskRows(
            boolean[][] mask, int... expectedRows) {

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
                assertEquals(
                        expected,
                        mask[r][c],
                        "row=" + r + ", col=" + c
                );
            }
        }
    }
}
