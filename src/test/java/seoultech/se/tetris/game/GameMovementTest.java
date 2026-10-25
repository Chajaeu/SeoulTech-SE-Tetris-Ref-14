package seoultech.se.tetris.game;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static seoultech.se.tetris.game.CoreTestSupport.*;

class GameMovementTest {

    // 1. 난이도 3종 × 일반/아이템 모드의 초기 상태
    @ParameterizedTest
    @CsvSource({
        "1, false",
        "2, false",
        "3, false",
        "1, true",
        "2, true",
        "3, true"
    })
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

        // 고정된 보드는 비어 있지만 화면에는 현재 블럭이 보인다.
        assertEquals(0, occupied(board(game).getBoardCopy()));
        assertEquals(4, occupied(game.getDisplayBoard()));
        assertEquals(4, occupied(game.getNextMino()));

        assertEquals(0, (int) get(game, "givenItemNumber"));
        assertNull(get(game, "subMino"));
    }

    // 2. UI에 전달한 배열을 수정해도 게임 내부는 유지된다.
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

        assertEquals(
                originalDisplay,
                contents(game.getDisplayBoard())
        );
        assertEquals(
                originalNext,
                contents(game.getNextMino())
        );

        assertEquals(0, occupied(board(game).getBoardCopy()));
    }

    // 3. 좌우 이동은 한 칸씩 이동하며 점수를 올리지 않는다.
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

    // 4. 벽이나 고정된 블럭 때문에 이동할 수 없으면 상태 유지
    @Test
    void sideWallsAndFixedCellsRejectMovement() {
        Game game = active(new Mino('O'), 5, 0);
        Mino mino = current(game);

        // 왼쪽 벽
        assertUnchanged(game, game::moveLeft);

        // 오른쪽 벽
        at(mino, 5, 8);
        assertUnchanged(game, game::moveRight);

        // 양옆의 고정된 블럭
        at(mino, 5, 4);
        board(game).setCell(5, 3, new Cell('J'));
        board(game).setCell(6, 6, new Cell('Z'));

        assertUnchanged(game, game::moveLeft);
        assertUnchanged(game, game::moveRight);

        assertEquals(4, current(game).getCol());
        assertEquals(0, game.getScore());
        assertEquals(2, occupied(board(game).getBoardCopy()));
    }

    // 5. 수동 낙하와 tick은 실제 한 칸 이동에 점수를 준다.
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

    // 6. 아래 이동 실패 시 고정하되 낙하 점수는 추가하지 않는다.
    @Test
    void failedDownwardMoveLocksWithoutDropScore() {
        Game game = active(new Mino('O'), 5, 4);

        board(game).setCell(7, 4, new Cell('J'));

        game.moveDown();

        assertNull(current(game));
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

    // 7. 하드드롭은 바닥에 고정되고 다음 tick에서 NEXT가 등장한다.
    @Test
    void hardDropLocksAtFloorThenNextTickSpawnsNextMino() {
        Game game = active(new Mino('O'), 0, 4);

        Mino expectedNext = next(game);
        String expectedNextShape = contents(game.getNextMino());

        game.harddrop();

        assertNull(current(game));
        assertEquals(18 * Game.NORMAL, game.getScore());

        for (int r = 18; r < 20; r++) {
            for (int c = 4; c <= 5; c++) {
                assertEquals('O', board(game).getCell(r, c).getType());
            }
        }

        assertEquals(4, occupied(board(game).getBoardCopy()));
        assertEquals(expectedNextShape, contents(game.getNextMino()));

        game.tick();

        assertNotNull(current(game));
        assertEquals(expectedNext.getType(), current(game).getType());
        assertEquals(
                expectedNext.getItemType(),
                current(game).getItemType()
        );
        assertEquals(
                expectedNextShape,
                contents(shapeOf(current(game)))
        );

        // 생성한 tick에서는 추가 낙하하지 않는다.
        assertEquals(0, current(game).getRow());
        assertEquals(18 * Game.NORMAL, game.getScore());
        assertEquals(4, occupied(game.getNextMino()));
    }

    // 8. 하드드롭은 고정된 블럭을 통과하지 않는다.
    @Test
    void hardDropStopsAboveFixedCells() {
        Game game = active(new Mino('O'), 0, 4);

        board(game).setCell(10, 5, new Cell('Z'));

        game.harddrop();

        assertNull(current(game));
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

    // 9. 일시정지·게임오버·삭제 대기 중 일반 조작과 tick 차단
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

        // 각각 실행한 직후 확인해서 좌우 이동 등이 상쇄되지 않게 한다.
        assertUnchanged(game, game::moveLeft);
        assertUnchanged(game, game::moveRight);
        assertUnchanged(game, game::moveDown);
        assertUnchanged(game, game::rotateCW);
        assertUnchanged(game, game::harddrop);
        assertUnchanged(game, game::tick);
    }

    // 10. 재개하면 다시 진행되지만 게임오버가 해제되지는 않는다.
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

    // 11. 고정 후 현재 블럭이 없는 동안 수동 조작은 무시된다.
    @Test
    void noCurrentMinoIgnoresManualActionsUntilNextTick() {
        Game game = active(new Mino('O'), 18, 4);

        game.moveDown();

        assertNull(current(game));
        assertEquals(0, game.getScore());

        assertUnchanged(game, game::moveLeft);
        assertUnchanged(game, game::moveRight);
        assertUnchanged(game, game::moveDown);
        assertUnchanged(game, game::rotateCW);
        assertUnchanged(game, game::harddrop);

        String expectedNextShape = contents(game.getNextMino());

        game.tick();

        assertNotNull(current(game));
        assertEquals(
                expectedNextShape,
                contents(shapeOf(current(game)))
        );
        assertEquals(0, current(game).getRow());
        assertEquals(0, game.getScore());
    }

    // 12. 새 블럭의 생성 위치가 막혀 있으면 게임오버
    @Test
    void blockedSpawnTriggersGameOver() {
        Game game = active(new Mino('O'), 18, 0);

        set(game, "nextMino", new Mino('O'));
        board(game).setCell(0, 4, new Cell('Z'));

        game.harddrop();
        game.tick();

        assertTrue(game.getIsGameover());
        assertFalse(game.checkCanOperate());
        assertEquals(0, game.getScore());

        assertUnchanged(game, game::moveDown);
        assertUnchanged(game, game::harddrop);
        assertUnchanged(game, game::tick);
    }

    // 13. 새 Game은 이전 게임의 점수·보드·상태를 이어받지 않는다.
    @Test
    void newGameDoesNotInheritPreviousGameState() {
        Game previous = gameWaitingForDeletion();

        previous.deleteCells();
        previous.pause();
        previous.gameover();

        // 이전 게임에 실제로 기록이 있는 상태에서 비교한다.
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

    // 실제로 한 줄을 완성해서 삭제 대기 상태를 만든다.
    private Game gameWaitingForDeletion() {
        Game game = active(new Mino('O'), 18, 4);

        fillRow(board(game), 19, 4, 5);

        game.moveDown();

        assertNull(current(game));
        assertTrue(game.getIsWaitingDelete());

        return game;
    }

    // 행동을 한 번 실행하고 게임 상태가 그대로인지 검사한다.
    private void assertUnchanged(Game game, Runnable action) {
        Mino originalCurrent = current(game);
        Mino originalNext = next(game);

        String display = contents(game.getDisplayBoard());
        String fixedBoard = contents(board(game).getBoardCopy());
        String nextShape = contents(game.getNextMino());
        String deletionMask =
                Arrays.deepToString(game.getDeleteWaitingCells());

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
        assertEquals(
                deletionMask,
                Arrays.deepToString(game.getDeleteWaitingCells())
        );

        assertEquals(score, game.getScore());
        assertEquals(lines, game.getLines());
        assertEquals(level, game.getLevel());
        assertEquals(interval, game.getTickInterval());

        assertEquals(paused, game.getIsPaused());
        assertEquals(gameover, game.getIsGameover());
        assertEquals(waiting, game.getIsWaitingDelete());
    }
}
