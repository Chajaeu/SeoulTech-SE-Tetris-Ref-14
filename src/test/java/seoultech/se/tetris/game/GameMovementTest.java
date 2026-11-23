package seoultech.se.tetris.game;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static seoultech.se.tetris.game.CoreTestSupport.*;

class GameMovementTest {

    @ParameterizedTest
    @CsvSource({"1, false", "2, false", "3, false", "1, true", "2, true", "3, true"})
    void newGameHasCleanState(int difficulty, boolean itemMode) {
        Game game = new Game(difficulty, itemMode);

        assertEquals(difficulty, game.getDifficulty());
        assertEquals(itemMode, game.getItemMode());
        assertEquals(0, game.getScore());
        assertEquals(0, game.getLines());
        assertEquals(1, game.getLevel());
        assertEquals(1000, game.getTickInterval());
        assertFalse(game.getIsPaused());
        assertFalse(game.getIsGameover());
        assertFalse(game.getIsWaitingDelete());
        assertTrue(game.checkCanOperate());
        assertNull(game.getDeleteWaitingCells());

        assertEquals(20, board(game).getRows());
        assertEquals(10, board(game).getCols());
        assertNotNull(current(game));
        assertNotNull(next(game));
        assertEquals(Mino.NO_ITEM, current(game).getItemType());
        assertEquals(Mino.NO_ITEM, next(game).getItemType());
        assertEquals(0, occupied(board(game).getBoardCopy()));
        assertEquals(4, occupied(game.getDisplayBoard()));
        assertEquals(4, occupied(game.getNextMino()));
        assertEquals(0, (int) get(game, "givenItemNumber"));
        assertNull(get(game, "subMino"));
    }

    @Test
    void displayAndNextArraysCannotModifyGame() {
        Game game = new Game(Game.NORMAL, false);
        String originalDisplay = contents(game.getDisplayBoard());
        String originalNext = contents(game.getNextMino());

        Cell[][] display = game.getDisplayBoard();
        display[0][0] = new Cell('B', true, 'B');
        display[1] = new Cell[10];

        Cell[][] preview = game.getNextMino();
        preview[0][0] = new Cell('R', true, 'R');
        preview[1] = new Cell[preview[1].length];

        assertEquals(originalDisplay, contents(game.getDisplayBoard()));
        assertEquals(originalNext, contents(game.getNextMino()));
        assertEquals(0, occupied(board(game).getBoardCopy()));
    }

    @Test
    void leftAndRightMoveOneCellWithoutScoring() {
        Game game = active(new Mino('O'), 5, 4);

        game.moveLeft();
        assertEquals(5, current(game).getRow());
        assertEquals(3, current(game).getCol());

        game.moveRight();
        assertEquals(5, current(game).getRow());
        assertEquals(4, current(game).getCol());
        assertEquals(0, game.getScore());
        assertEquals(0, occupied(board(game).getBoardCopy()));
    }

    @Test
    void sideWallsAndFixedCellsRejectMovement() {
        Game game = active(new Mino('O'), 5, 0);
        Mino mino = current(game);

        assertUnchanged(game, game::moveLeft);
        at(mino, 5, 8);
        assertUnchanged(game, game::moveRight);

        at(mino, 5, 4);
        board(game).setCell(5, 3, new Cell('J'));
        board(game).setCell(6, 6, new Cell('Z'));

        assertUnchanged(game, game::moveLeft);
        assertUnchanged(game, game::moveRight);
        assertEquals(4, current(game).getCol());
        assertEquals(0, game.getScore());
        assertEquals(2, occupied(board(game).getBoardCopy()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void softDropAndTickScoreActualMovement(int difficulty) {
        Game game = new Game(difficulty, false);
        set(game, "currentMino", at(new Mino('O'), 5, 4));

        game.moveDown();
        assertEquals(6, current(game).getRow());
        assertEquals(4, current(game).getCol());
        assertEquals(difficulty, game.getScore());

        game.tick();
        assertEquals(7, current(game).getRow());
        assertEquals(4, current(game).getCol());
        assertEquals(2 * difficulty, game.getScore());
        assertEquals(0, occupied(board(game).getBoardCopy()));
    }

    @Test
    void failedDownwardMoveLocksWithoutDropScore() {
        Game game = active(new Mino('O'), 5, 4);
        board(game).setCell(7, 4, new Cell('J'));
        Mino expectedNext = next(game);

        game.moveDown();

        assertSame(expectedNext, current(game));
        assertEquals(0, game.getScore());
        for (int r = 5; r <= 6; r++) {
            for (int c = 4; c <= 5; c++) {
                assertEquals('O', board(game).getCell(r, c).getType());
            }
        }
        assertEquals('J', board(game).getCell(7, 4).getType());
        assertEquals(5, occupied(board(game).getBoardCopy()));
        assertFalse(game.getIsWaitingDelete());
    }

    @Test
    void hardDropLocksAtFloorAndSpawnsNextImmediately() {
        Game game = active(new Mino('O'), 0, 4);
        Mino expectedNext = next(game);
        String expectedNextShape = contents(game.getNextMino());

        game.harddrop();

        assertSame(expectedNext, current(game));
        assertEquals(18 * Game.NORMAL, game.getScore());
        for (int r = 18; r < 20; r++) {
            for (int c = 4; c <= 5; c++) {
                assertEquals('O', board(game).getCell(r, c).getType());
            }
        }
        assertEquals(4, occupied(board(game).getBoardCopy()));
        assertEquals(expectedNext.getType(), current(game).getType());
        assertEquals(expectedNext.getItemType(), current(game).getItemType());
        assertEquals(expectedNextShape, contents(shapeOf(current(game))));

        // 고정 입력 하나로 새 블럭까지 떨어지지 않는다.
        assertEquals(0, current(game).getRow());
        assertEquals(18 * Game.NORMAL, game.getScore());
        assertEquals(4, occupied(game.getNextMino()));

        game.tick();
        assertEquals(1, current(game).getRow());
        assertEquals(19 * Game.NORMAL, game.getScore());
    }

    @Test
    void hardDropStopsAboveFixedCells() {
        Game game = active(new Mino('O'), 0, 4);
        board(game).setCell(10, 5, new Cell('Z'));
        Mino expectedNext = next(game);

        game.harddrop();

        assertSame(expectedNext, current(game));
        assertEquals(8 * Game.NORMAL, game.getScore());
        for (int r = 8; r <= 9; r++) {
            for (int c = 4; c <= 5; c++) {
                assertEquals('O', board(game).getCell(r, c).getType());
            }
        }
        assertEquals('Z', board(game).getCell(10, 5).getType());
        assertTrue(board(game).getCell(11, 5).isEmpty());
        assertEquals(5, occupied(board(game).getBoardCopy()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pause", "gameover", "waiting"})
    void stoppedGameRejectsGameplayActions(String state) {
        Game game;
        if (state.equals("waiting")) {
            game = gameWaitingForDeletion();
        } else {
            game = active(new Mino('T'), 5, 3);
            if (state.equals("pause")) {
                game.pause();
            } else {
                game.gameover();
            }
        }

        assertFalse(game.checkCanOperate());
        assertUnchanged(game, game::moveLeft);
        assertUnchanged(game, game::moveRight);
        assertUnchanged(game, game::moveDown);
        assertUnchanged(game, game::rotateCW);
        assertUnchanged(game, game::harddrop);
        assertUnchanged(game, game::tick);
    }

    @Test
    void resumeAllowsMovementButDoesNotClearGameOver() {
        Game game = active(new Mino('O'), 5, 4);
        game.pause();

        assertTrue(game.getIsPaused());
        assertFalse(game.checkCanOperate());

        game.resume();
        assertFalse(game.getIsPaused());
        assertTrue(game.checkCanOperate());

        game.tick();
        assertEquals(6, current(game).getRow());
        assertEquals(Game.NORMAL, game.getScore());

        game.gameover();
        game.pause();
        game.resume();

        assertFalse(game.getIsPaused());
        assertTrue(game.getIsGameover());
        assertFalse(game.checkCanOperate());
        assertUnchanged(game, game::tick);
    }

    @Test
    void deletionWaitIgnoresManualActionsUntilDeletionCompletes() {
        Game game = gameWaitingForDeletion();
        assertNull(current(game));
        assertEquals(0, game.getScore());

        assertUnchanged(game, game::moveLeft);
        assertUnchanged(game, game::moveRight);
        assertUnchanged(game, game::moveDown);
        assertUnchanged(game, game::rotateCW);
        assertUnchanged(game, game::harddrop);

        String expectedNextShape = contents(game.getNextMino());
        assertUnchanged(game, game::tick);
        game.deleteCells();

        assertNotNull(current(game));
        assertEquals(expectedNextShape, contents(shapeOf(current(game))));
        assertEquals(0, current(game).getRow());
        assertEquals(100, game.getScore());
    }

    @Test
    void blockedSpawnTriggersGameOver() {
        Game game = active(new Mino('O'), 18, 0);
        set(game, "nextMino", new Mino('O'));
        board(game).setCell(0, 4, new Cell('Z'));

        game.harddrop();

        assertTrue(game.getIsGameover());
        assertFalse(game.checkCanOperate());
        assertEquals(0, game.getScore());
        assertUnchanged(game, game::moveDown);
        assertUnchanged(game, game::harddrop);
        assertUnchanged(game, game::tick);
    }

    @Test
    void newGameDoesNotInheritPreviousGameState() {
        Game previous = gameWaitingForDeletion();
        previous.deleteCells();
        previous.pause();
        previous.gameover();
        assertEquals(100, previous.getScore());
        assertEquals(1, previous.getLines());

        Game fresh = new Game(Game.EASY, true);
        assertEquals(0, occupied(board(fresh).getBoardCopy()));
        assertEquals(0, fresh.getScore());
        assertEquals(0, fresh.getLines());
        assertEquals(1, fresh.getLevel());
        assertEquals(1000, fresh.getTickInterval());
        assertFalse(fresh.getIsPaused());
        assertFalse(fresh.getIsGameover());
        assertFalse(fresh.getIsWaitingDelete());
        assertNull(fresh.getDeleteWaitingCells());
        assertEquals(Game.EASY, fresh.getDifficulty());
        assertTrue(fresh.getItemMode());
        assertTrue(fresh.checkCanOperate());
    }

    private Game gameWaitingForDeletion() {
        Game game = active(new Mino('O'), 18, 4);
        fillRow(board(game), 19, 4, 5);
        game.moveDown();
        assertNull(current(game));
        assertTrue(game.getIsWaitingDelete());
        return game;
    }

    private void assertUnchanged(Game game, Runnable action) {
        Mino originalCurrent = current(game);
        Mino originalNext = next(game);
        String display = contents(game.getDisplayBoard());
        String fixedBoard = contents(board(game).getBoardCopy());
        String nextShape = contents(game.getNextMino());
        String deletionMask = Arrays.deepToString(game.getDeleteWaitingCells());
        int score = game.getScore();
        int lines = game.getLines();
        int level = game.getLevel();
        int interval = game.getTickInterval();
        boolean paused = game.getIsPaused();
        boolean gameover = game.getIsGameover();
        boolean waiting = game.getIsWaitingDelete();

        action.run();

        assertSame(originalCurrent, current(game));
        assertSame(originalNext, next(game));
        assertEquals(display, contents(game.getDisplayBoard()));
        assertEquals(fixedBoard, contents(board(game).getBoardCopy()));
        assertEquals(nextShape, contents(game.getNextMino()));
        assertEquals(deletionMask, Arrays.deepToString(game.getDeleteWaitingCells()));
        assertEquals(score, game.getScore());
        assertEquals(lines, game.getLines());
        assertEquals(level, game.getLevel());
        assertEquals(interval, game.getTickInterval());
        assertEquals(paused, game.getIsPaused());
        assertEquals(gameover, game.getIsGameover());
        assertEquals(waiting, game.getIsWaitingDelete());
    }
}