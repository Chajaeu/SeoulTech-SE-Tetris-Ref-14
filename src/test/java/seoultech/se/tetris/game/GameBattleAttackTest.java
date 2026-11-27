package seoultech.se.tetris.game;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static seoultech.se.tetris.game.CoreTestSupport.*;

class GameBattleAttackTest {

    private Game battle(Mino mino, int row, int col, boolean itemMode) {
        Game game = new Game(Game.NORMAL, itemMode, true);
        set(game, "currentMino", at(mino, row, col));
        set(game, "nextMino", new Mino('O'));
        return game;
    }

    // T가 메우는 칸은 위 행의 4열, 아래 행의 3~5열이다.
    private Game twoLineSender(boolean itemMode) {
        Game sender = battle(new Mino('T'), 18, 3, itemMode);
        fillRow(board(sender), 18, 4);
        fillRow(board(sender), 19, 3, 4, 5);
        return sender;
    }

    private boolean[] attackRow(int... gaps) {
        boolean[] row = new boolean[Game.COLS];
        Arrays.fill(row, true);
        for (int col : gaps) {
            row[col] = false;
        }
        return row;
    }

    private boolean[][] incoming(int rows, int firstGap) {
        boolean[][] attack = new boolean[rows][];
        for (int row = 0; row < rows; row++) {
            attack[row] = attackRow((firstGap + row) % Game.COLS);
        }
        return attack;
    }

    private void assertRow(boolean[] expected, Cell[] actual) {
        assertEquals(expected.length, actual.length);
        for (int col = 0; col < actual.length; col++) {
            assertEquals(expected[col] ? Game.ATTACK_CELL : ' ', actual[col].getType(),
                    "열 " + col);
            assertFalse(actual[col].isItem());
        }
    }

    private void assertPending(Game game, boolean[]... rows) {
        Cell[][] preview = game.getAttackedLines();
        assertEquals(10, preview.length);
        int first = preview.length - rows.length;
        for (int row = 0; row < preview.length; row++) {
            assertRow(row < first ? new boolean[Game.COLS] : rows[row - first], preview[row]);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void onlyTwoOrMoreCompletedRowsGenerateAnAttack(int lines) {
        Mino vertical = new Mino('I');
        vertical.setShape(vertical.createRotatedCW());
        Game sender = battle(vertical, 16, 3, false);
        for (int row = Game.ROWS - lines; row < Game.ROWS; row++) {
            fillRow(board(sender), row, 5);
        }

        sender.harddrop();

        boolean[][] attack = sender.attack();
        assertEquals(lines < 2 ? 0 : lines, attack.length);
        for (boolean[] row : attack) {
            assertArrayEquals(attackRow(5), row);
        }
        assertEquals(0, sender.attack().length);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void twoGamesTransferRowsOnceAndKeepTheirOrder(boolean itemMode) {
        Game sender = twoLineSender(itemMode);
        Game receiver = battle(new Mino('O'), 10, 7, itemMode);
        boolean[] top = attackRow(4);
        boolean[] bottom = attackRow(3, 4, 5);
        String before = contents(receiver.getDisplayBoard());

        sender.harddrop();
        assertTrue(sender.getIsWaitingDelete());

        // UI의 삭제 애니메이션 완료 시점과 같은 순서로 호출한다.
        sender.deleteCells();
        boolean[][] attack = sender.attack();
        assertEquals(2, attack.length);
        assertArrayEquals(top, attack[0]);
        assertArrayEquals(bottom, attack[1]);

        receiver.attacked(attack);
        receiver.attacked(sender.attack());
        assertPending(receiver, top, bottom);
        assertEquals(before, contents(receiver.getDisplayBoard()));

        receiver.harddrop();

        Cell[][] display = receiver.getDisplayBoard();
        assertRow(top, display[18]);
        assertRow(bottom, display[19]);
        assertEquals('O', display[16][7].getType());
        assertPending(receiver);

        // 다음 턴에 같은 공격이 다시 적용되지 않는다.
        receiver.harddrop();
        assertRow(top, receiver.getDisplayBoard()[18]);
        assertRow(bottom, receiver.getDisplayBoard()[19]);
        assertEquals('O', board(receiver).getCell(16, 7).getType());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void singlePlayerNeverGeneratesAttacks(boolean itemMode) {
        Game game = new Game(Game.NORMAL, itemMode);
        set(game, "currentMino", at(new Mino('T'), 18, 3));
        fillRow(board(game), 18, 4);
        fillRow(board(game), 19, 3, 4, 5);

        game.harddrop();

        assertTrue(game.getIsWaitingDelete());
        assertEquals(0, game.attack().length);
        game.deleteCells();
        assertEquals(2, game.getLines());
        assertEquals(0, game.attack().length);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "3,2", "8,2", "8,4", "10,2", "0,12"})
    void queuedRowsRespectArrivalOrderAndCapacity(int first, int second) {
        Game game = battle(new Mino('O'), 18, 4, false);
        game.attacked(incoming(first, 0));
        game.attacked(incoming(second, first));

        boolean[][] kept = incoming(Math.min(first + second, 10), 0);
        assertPending(game, kept);

        game.moveDown();

        for (int row = 0; row < kept.length; row++) {
            assertRow(kept[row], game.getDisplayBoard()[Game.ROWS - kept.length + row]);
        }
        assertEquals('O', board(game).getCell(18 - kept.length, 4).getType());
        assertPending(game);
    }

    @Test
    void receiverOwnsItsQueuedRowsAndPreviewIsReadOnlyToCallers() {
        Game game = battle(new Mino('O'), 18, 4, false);
        boolean[][] incoming = incoming(2, 0);
        game.attacked(incoming);

        incoming[0][1] = false;
        incoming[1] = null;
        Cell[][] preview = game.getAttackedLines();
        preview[8][1] = new Cell();
        preview[9] = null;

        assertPending(game, attackRow(0), attackRow(1));
    }

    @Test
    void attacksReceivedBeforeAndDuringDeletionRiseAfterTheAnimation() {
        Game game = battle(new Mino('O'), 18, 4, false);
        fillRow(board(game), 19, 4, 5);
        game.attacked(incoming(1, 2));
        game.harddrop();
        game.attacked(incoming(1, 7));

        game.tick();
        assertTrue(game.getIsWaitingDelete());
        assertFalse(game.hasCurrentMino());
        assertPending(game, attackRow(2), attackRow(7));
        assertEquals('O', game.getDisplayBoard()[19][4].getType());

        game.deleteCells();

        assertEquals(1, game.getLines());
        assertEquals('O', board(game).getCell(17, 4).getType());
        assertRow(attackRow(2), game.getDisplayBoard()[18]);
        assertRow(attackRow(7), game.getDisplayBoard()[19]);
        assertPending(game);
        assertTrue(game.hasCurrentMino());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void itemDeletionOnlyCountsNaturallyCompletedRowsForAttacks(int naturalRows) {
        // 폭발 마스크가 생겨도 실제로 가득 찬 행만 공격 줄 수에 들어간다.
        Game game = battle(new Mino('O', Mino.BOMB), 18, 4, true);
        for (int row = Game.ROWS - naturalRows; row < Game.ROWS; row++) {
            fillRow(board(game), row, 4, 5);
        }

        game.harddrop();

        assertTrue(game.getIsWaitingDelete());
        assertTrue(game.getDeleteWaitingCells()[17][3]);
        boolean[][] attack = game.attack();
        assertEquals(naturalRows == 2 ? 2 : 0, attack.length);
        for (boolean[] row : attack) {
            assertArrayEquals(attackRow(4, 5), row);
        }
    }

    @ParameterizedTest
    @ValueSource(chars = {Mino.LINE_CLEAR, Mino.COLUMN_CLEAR, Mino.BOMB})
    void clearingItemsWaitForDeleteCellsBeforeRaisingTheBoard(char item) {
        Game game = battle(new Mino('O', item), 18, 4, true);
        game.attacked(incoming(1, 0));
        game.harddrop();

        assertTrue(game.getIsWaitingDelete());
        assertPending(game, attackRow(0));

        game.deleteCells();

        assertRow(attackRow(0), game.getDisplayBoard()[19]);
        assertPending(game);
        assertTrue(game.hasCurrentMino());
    }

    @ParameterizedTest
    @ValueSource(chars = {Mino.REPLICATION, Mino.WEIGHT})
    void specialItemsApplyTheQueueWhenTheirTurnEnds(char item) {
        Game game = battle(new Mino('T', item), 0, 3, true);
        game.attacked(incoming(1, 1));

        game.harddrop();

        assertPending(game);
        assertRow(attackRow(1), game.getDisplayBoard()[19]);
        assertFalse(game.getIsWaitingDelete());
        assertTrue(game.hasCurrentMino());
    }

    @Test
    void replicatingAnAttackCellUsesTheNextTypeAndRestoresThePreview() {
        Game game = battle(new Mino('T', Mino.REPLICATION), 18, 4, true);
        board(game).setCell(19, 4, new Cell(Game.ATTACK_CELL));
        Mino reserved = new Mino('J', Mino.LINE_CLEAR);
        set(game, "nextMino", reserved);

        game.moveDown();

        assertEquals('J', current(game).getType());
        assertEquals(Mino.NO_ITEM, current(game).getItemType());
        assertSame(reserved, next(game));
    }

    @Test
    void overflowShowsTheRaisedFinalBoardAndStopsSpawning() {
        Game game = battle(new Mino('O'), 18, 7, false);
        board(game).setCell(1, 9, new Cell('T'));
        board(game).setCell(2, 0, new Cell('J'));
        game.attacked(incoming(2, 0));

        game.harddrop();

        assertTrue(game.getIsGameover());
        assertFalse(game.hasCurrentMino());
        assertEquals('J', game.getDisplayBoard()[0][0].getType());
        assertRow(attackRow(0), game.getDisplayBoard()[18]);
        assertRow(attackRow(1), game.getDisplayBoard()[19]);
        assertPending(game);

        String finalBoard = contents(game.getDisplayBoard());
        game.tick();
        assertEquals(finalBoard, contents(game.getDisplayBoard()));
    }

    @Test
    void riseCanBlockNextSpawnWithoutOverflow() {
        Game game = battle(new Mino('O'), 18, 7, false);
        board(game).setCell(2, 4, new Cell('J'));
        game.attacked(incoming(2, 0));

        game.moveDown();

        assertTrue(game.getIsGameover());
        assertEquals('J', board(game).getCell(0, 4).getType());
        assertRow(attackRow(1), game.getDisplayBoard()[19]);
        assertPending(game);
    }
}