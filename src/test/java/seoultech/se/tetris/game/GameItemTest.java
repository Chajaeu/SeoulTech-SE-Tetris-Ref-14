package seoultech.se.tetris.game;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static seoultech.se.tetris.game.CoreTestSupport.*;

class GameItemTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void lineItemDeletesItsRowEvenWhenIncomplete(int itemRow) {
        Game game = itemGame(attached(Mino.LINE_CLEAR, itemRow, 0), 18, 4);
        game.moveDown();

        boolean[][] mask = game.getDeleteWaitingCells();
        assertTrue(game.getIsWaitingDelete());
        assertEquals(10, marked(mask));
        assertRowMarked(mask, 18 + itemRow);
        assertEquals(4, occupied(board(game).getBoardCopy()));
        assertEquals(0, game.getScore());

        game.deleteCells();

        assertEquals(1, game.getLines());
        assertEquals(100, game.getScore());
        assertEquals(2, occupied(board(game).getBoardCopy()));
        assertEquals('O', board(game).getCell(19, 4).getType());
        assertEquals('O', board(game).getCell(19, 5).getType());
        assertFalse(game.getIsWaitingDelete());
        assertNull(game.getDeleteWaitingCells());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void lineItemCombinesWithCompletedRowWithoutDoubleCounting(int itemRow) {
        Game game = itemGame(attached(Mino.LINE_CLEAR, itemRow, 0), 18, 4);
        fillRow(board(game), 19, 4, 5);
        game.moveDown();

        boolean[][] mask = game.getDeleteWaitingCells();
        int expectedLines = itemRow == 0 ? 2 : 1;
        int expectedScore = itemRow == 0 ? 300 : 100;

        assertEquals(10 * expectedLines, marked(mask));
        assertRowMarked(mask, 19);
        if (itemRow == 0) {
            assertRowMarked(mask, 18);
        }

        game.deleteCells();

        assertEquals(expectedLines, game.getLines());
        assertEquals(expectedScore, game.getScore());
        assertEquals(itemRow == 0 ? 0 : 2, occupied(board(game).getBoardCopy()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 9})
    void columnItemDeletesOnlyItsColumn(int targetCol) {
        int minoCol = targetCol == 9 ? 8 : targetCol;
        int itemCol = targetCol - minoCol;
        int remainingCol = minoCol + (itemCol == 0 ? 1 : 0);
        int outsideCol = targetCol == 9 ? 0 : 9;

        Game game = itemGame(attached(Mino.COLUMN_CLEAR, 0, itemCol), 18, minoCol);
        board(game).setCell(5, targetCol, new Cell('T'));
        board(game).setCell(5, outsideCol, new Cell('Z'));
        game.moveDown();

        boolean[][] mask = game.getDeleteWaitingCells();
        assertEquals(20, marked(mask));
        assertColumnMarked(mask, targetCol);

        game.deleteCells();

        for (int r = 0; r < 20; r++) {
            assertTrue(board(game).getCell(r, targetCol).isEmpty());
        }
        assertEquals('Z', board(game).getCell(5, outsideCol).getType());
        assertEquals('O', board(game).getCell(18, remainingCol).getType());
        assertEquals('O', board(game).getCell(19, remainingCol).getType());
        assertEquals(3, occupied(board(game).getBoardCopy()));
        assertEquals(0, game.getLines());
        assertEquals(0, game.getScore());
    }

    @Test
    void columnItemCombinesWithCompletedRow() {
        Game game = itemGame(attached(Mino.COLUMN_CLEAR, 0, 0), 18, 4);
        fillRow(board(game), 19, 4, 5);
        board(game).setCell(5, 4, new Cell('T'));
        board(game).setCell(5, 5, new Cell('Z'));
        game.moveDown();

        boolean[][] mask = game.getDeleteWaitingCells();
        // 열 20칸 + 행 10칸 - 교차하는 1칸
        assertEquals(29, marked(mask));
        assertColumnMarked(mask, 4);
        assertRowMarked(mask, 19);

        game.deleteCells();

        assertEquals(1, game.getLines());
        assertEquals(100, game.getScore());
        for (int r = 0; r < 20; r++) {
            assertTrue(board(game).getCell(r, 4).isEmpty());
        }
        assertEquals('Z', board(game).getCell(6, 5).getType());
        assertEquals('O', board(game).getCell(19, 5).getType());
        assertEquals(2, occupied(board(game).getBoardCopy()));
    }

    @Test
    void reservedIAppearsImmediatelyAfterColumnItem() {
        Game game = itemGame(new Mino('O'), 18, 0);
        Mino columnItem = attached(Mino.COLUMN_CLEAR, 0, 0);
        Mino reservedI = new Mino('I');

        String expectedColumn = contents(shapeOf(columnItem));
        String expectedI = contents(shapeOf(reservedI));
        set(game, "nextMino", columnItem);
        set(game, "subMino", reservedI);

        game.harddrop();

        assertEquals(Mino.COLUMN_CLEAR, current(game).getItemType());
        assertEquals(expectedColumn, contents(shapeOf(current(game))));
        assertEquals('I', next(game).getType());
        assertEquals(Mino.NO_ITEM, next(game).getItemType());
        assertEquals(expectedI, contents(game.getNextMino()));
        assertNull(get(game, "subMino"));

        game.harddrop();
        assertTrue(game.getIsWaitingDelete());
        game.deleteCells();

        assertEquals('I', current(game).getType());
        assertEquals(Mino.NO_ITEM, current(game).getItemType());
        assertEquals(expectedI, contents(shapeOf(current(game))));
        assertEquals(Mino.NO_ITEM, next(game).getItemType());
        assertEquals(4, occupied(game.getNextMino()));
        assertNotSame(current(game), next(game));
        assertNull(get(game, "subMino"));
    }

    @ParameterizedTest
    @ValueSource(chars = {'I', 'J', 'L', 'O', 'S', 'T', 'Z'})
    void replicationCopiesTypeBelowAndRestoresOriginalNext(char targetType) {
        Game game = itemGame(new Mino('T', Mino.REPLICATION), 0, 4);
        Mino originalNext = new Mino('J', Mino.LINE_CLEAR);
        String originalPreview = contents(shapeOf(originalNext));
        set(game, "nextMino", originalNext);
        board(game).setCell(10, 4, new Cell(targetType));

        game.harddrop();

        assertEquals(0, current(game).getRow());
        assertEquals(9 * Game.NORMAL, game.getScore());
        assertEquals(1, occupied(board(game).getBoardCopy()));
        assertEquals(targetType, board(game).getCell(10, 4).getType());
        assertEquals(contents(shapeOf(new Mino(targetType))), contents(shapeOf(current(game))));
        assertEquals(targetType, current(game).getType());
        assertEquals(Mino.NO_ITEM, current(game).getItemType());

        // 기존 NEXT의 부착 아이템과 위치까지 보존한다.
        assertEquals('J', next(game).getType());
        assertEquals(Mino.LINE_CLEAR, next(game).getItemType());
        assertEquals(originalPreview, contents(game.getNextMino()));
        assertNull(get(game, "subMino"));

        game.harddrop();

        assertEquals('J', current(game).getType());
        assertEquals(Mino.LINE_CLEAR, current(game).getItemType());
        assertEquals(originalPreview, contents(shapeOf(current(game))));
        assertEquals(0, current(game).getRow());
    }

    @Test
    void replicationAtFloorCopiesOriginalNextType() {
        Game game = itemGame(new Mino('T', Mino.REPLICATION), 0, 4);
        Mino originalNext = new Mino('J', Mino.LINE_CLEAR);
        String originalPreview = contents(shapeOf(originalNext));
        set(game, "nextMino", originalNext);

        game.harddrop();

        assertEquals(0, current(game).getRow());
        assertEquals(19 * Game.NORMAL, game.getScore());
        assertEquals(0, occupied(board(game).getBoardCopy()));
        assertEquals('J', current(game).getType());
        assertEquals(Mino.NO_ITEM, current(game).getItemType());
        assertEquals(Mino.LINE_CLEAR, next(game).getItemType());
        assertEquals(originalPreview, contents(game.getNextMino()));
        assertNull(get(game, "subMino"));
    }

    // 중앙, 네 변, 네 모서리에서 폭발 범위를 검사한다.
    @ParameterizedTest
    @CsvSource({
        "8, 4, 7, 10, 3, 6",
        "0, 4, 0, 2, 3, 6",
        "18, 4, 17, 19, 3, 6",
        "8, 0, 7, 10, 0, 2",
        "8, 8, 7, 10, 7, 9",
        "0, 0, 0, 2, 0, 2",
        "0, 8, 0, 2, 7, 9",
        "18, 0, 17, 19, 0, 2",
        "18, 8, 17, 19, 7, 9"
    })
    void bombMarksOnlyItsClippedExplosionArea(
            int row, int col, int top, int bottom, int left, int right) {
        Game game = itemGame(new Mino('T', Mino.BOMB), row, col);

        if (row + 2 < 20) {
            board(game).setCell(row + 2, col, new Cell('J'));
        }
        int outsideCol = col <= 4 ? 9 : 0;
        board(game).setCell(15, outsideCol, new Cell('Z'));
        game.moveDown();

        boolean[][] mask = game.getDeleteWaitingCells();
        assertEquals((bottom - top + 1) * (right - left + 1), marked(mask));

        for (int r = 0; r < 20; r++) {
            for (int c = 0; c < 10; c++) {
                boolean expected = r >= top && r <= bottom && c >= left && c <= right;
                assertEquals(expected, mask[r][c], "row=" + r + ", col=" + c);
            }
        }
        assertEquals('B', board(game).getCell(row, col).getType());

        game.deleteCells();

        assertEquals('Z', board(game).getCell(15, outsideCol).getType());
        assertEquals(1, occupied(board(game).getBoardCopy()));
        assertEquals(0, game.getLines());
        assertEquals(0, game.getScore());
        assertFalse(game.getIsWaitingDelete());
        assertNull(game.getDeleteWaitingCells());
    }

    @Test
    void bombCombinesWithCompletedRow() {
        Game game = itemGame(new Mino('T', Mino.BOMB), 18, 4);
        fillRow(board(game), 19, 4, 5);
        board(game).setCell(16, 0, new Cell('Z'));
        game.moveDown();

        boolean[][] mask = game.getDeleteWaitingCells();
        assertEquals(18, marked(mask));
        assertRowMarked(mask, 19);
        for (int r = 17; r <= 18; r++) {
            for (int c = 3; c <= 6; c++) {
                assertTrue(mask[r][c]);
            }
        }

        game.deleteCells();

        assertEquals(1, game.getLines());
        assertEquals(100, game.getScore());
        assertEquals('Z', board(game).getCell(17, 0).getType());
        assertEquals(1, occupied(board(game).getBoardCopy()));
    }

    @Test
    void weightMovesHorizontallyBeforeContact() {
        Game game = itemGame(new Mino('T', Mino.WEIGHT), 5, 3);
        Mino weight = current(game);

        game.moveLeft();
        assertEquals(2, weight.getCol());
        game.moveRight();
        assertEquals(3, weight.getCol());

        at(weight, 5, 0);
        game.moveLeft();
        assertEquals(0, weight.getCol());

        at(weight, 5, 6);
        game.moveRight();
        assertEquals(6, weight.getCol());

        assertFalse(weight.getContacted());
        assertEquals(0, game.getScore());
        assertEquals(0, occupied(board(game).getBoardCopy()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"left", "right"})
    void firstContactImmediatelyBlocksHorizontalMovement(String direction) {
        Game game = itemGame(new Mino('T', Mino.WEIGHT), 5, 3);
        board(game).setCell(7, 6, new Cell('J'));

        if (direction.equals("left")) {
            game.moveLeft();
        } else {
            game.moveRight();
        }

        assertEquals(5, current(game).getRow());
        assertEquals(3, current(game).getCol());
        assertTrue(current(game).getContacted());
        assertEquals('J', board(game).getCell(7, 6).getType());
        assertEquals(0, game.getScore());
    }

    @ParameterizedTest
    @ValueSource(strings = {"moveDown", "tick"})
    void weightCrushesFourColumnsAndKeepsContact(String action) {
        Game game = itemGame(new Mino('T', Mino.WEIGHT), 5, 3);
        fillRow(board(game), 7);
        dropOne(game, action);

        assertEquals(6, current(game).getRow());
        assertTrue(current(game).getContacted());
        assertEquals(Game.NORMAL, game.getScore());
        for (int c = 0; c < 10; c++) {
            if (c >= 3 && c <= 6) {
                assertTrue(board(game).getCell(7, c).isEmpty());
            } else {
                assertEquals('J', board(game).getCell(7, c).getType());
            }
        }

        dropOne(game, action);
        assertEquals(7, current(game).getRow());
        assertEquals(2 * Game.NORMAL, game.getScore());

        // 장애물이 사라져도 첫 접촉 이후의 좌우 제한은 유지된다.
        game.moveLeft();
        assertEquals(3, current(game).getCol());
        game.moveRight();
        assertEquals(3, current(game).getCol());
        assertTrue(current(game).getContacted());
        assertEquals(6, occupied(board(game).getBoardCopy()));
        assertEquals(0, game.getLines());
        assertFalse(game.getIsWaitingDelete());
    }

    @Test
    void weightHardDropCrushesStackAndDisappearsAtFloor() {
        Game game = itemGame(new Mino('T', Mino.WEIGHT), 0, 3);
        for (int r = 7; r < 20; r++) {
            fillRow(board(game), r);
        }

        Mino expectedNext = next(game);
        assertTimeoutPreemptively(Duration.ofSeconds(3), game::harddrop);

        assertSame(expectedNext, current(game));
        assertEquals(18 * Game.NORMAL, game.getScore());
        assertEquals(0, game.getLines());
        assertFalse(game.getIsWaitingDelete());

        for (int r = 7; r < 20; r++) {
            for (int c = 0; c < 10; c++) {
                if (c >= 3 && c <= 6) {
                    assertTrue(board(game).getCell(r, c).isEmpty());
                } else {
                    assertEquals('J', board(game).getCell(r, c).getType());
                }
            }
        }
        assertEquals(13 * 6, occupied(board(game).getBoardCopy()));

        game.tick();
        assertEquals(1, current(game).getRow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"left", "right"})
    void weightAtFloorCannotSlideOrScoreRemoval(String direction) {
        Game game = itemGame(new Mino('T', Mino.WEIGHT), 18, 3);

        if (direction.equals("left")) {
            game.moveLeft();
        } else {
            game.moveRight();
        }

        assertEquals(3, current(game).getCol());
        assertTrue(current(game).getContacted());

        Mino expectedNext = next(game);
        game.moveDown();

        assertSame(expectedNext, current(game));
        assertEquals(0, game.getScore());
        assertEquals(0, occupied(board(game).getBoardCopy()));
        assertFalse(game.getIsWaitingDelete());
    }

    private Game itemGame(Mino mino, int row, int col) {
        Game game = new Game(Game.NORMAL, true);
        set(game, "currentMino", at(mino, row, col));
        return game;
    }

    private Mino attached(char itemType, int itemRow, int itemCol) {
        Mino mino = new Mino('O', itemType);
        Cell[][] cells = shapeOf(new Mino('O'));
        cells[itemRow][itemCol] = new Cell(itemType, true, itemType);
        mino.setShape(cells);
        return mino;
    }

    private void dropOne(Game game, String action) {
        if (action.equals("tick")) {
            game.tick();
        } else {
            game.moveDown();
        }
    }

    private int marked(boolean[][] mask) {
        assertNotNull(mask);
        assertEquals(20, mask.length);
        int count = 0;
        for (boolean[] row : mask) {
            assertNotNull(row);
            assertEquals(10, row.length);
            for (boolean cell : row) {
                if (cell) {
                    count++;
                }
            }
        }
        return count;
    }

    private void assertRowMarked(boolean[][] mask, int row) {
        for (int c = 0; c < 10; c++) {
            assertTrue(mask[row][c]);
        }
    }

    private void assertColumnMarked(boolean[][] mask, int col) {
        for (int r = 0; r < 20; r++) {
            assertTrue(mask[r][col]);
        }
    }
}