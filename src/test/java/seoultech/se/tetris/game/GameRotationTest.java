package seoultech.se.tetris.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static seoultech.se.tetris.game.CoreTestSupport.*;

class GameRotationTest {

    // 1. 현재 위치에서 회전할 수 있으면 위치를 유지한다.
    @Test
    void rotatesAtCurrentPositionWhenPossible() {
        Game game = active(new Mino('T'), 5, 3);

        assertRotationResult(
                game,
                5,
                3,
                ".X./.XX/.X."
        );
    }

    // 2. 바닥에 닿은 T는 왼쪽 위로 한 칸 보정해서 회전한다.
    @Test
    void floorCollisionUsesUpLeftKick() {
        Game game = active(new Mino('T'), 18, 3);

        assertRotationResult(game, 17, 2, ".X./.XX/.X.");
    }

    // 3. 제자리 회전이 막히고 왼쪽에서 회전할 수 있으면 왼쪽 후보를 사용한다.
    @Test
    void fixedCellCollisionUsesFirstAvailableKick() {
        Game game = active(new Mino('T'), 5, 3);
        board(game).setCell(7, 4, new Cell('J'));

        assertRotationResult(game, 5, 2, ".X./.XX/.X.");
    }

    // 4. 후보 다섯 곳이 모두 막히면 위치, 모양, 방향을 유지한다.
    @Test
    void rotationStopsWhenAllFiveCandidatesAreBlocked() {
        Game game = active(new Mino('T'), 5, 3);
        board(game).setCell(7, 4, new Cell('J'));
        board(game).setCell(7, 3, new Cell('J'));
        board(game).setCell(4, 3, new Cell('Z'));

        assertTrue(board(game).canPlace(current(game), 5, 3));
        assertRotationRejected(game);
        assertEquals(0, current(game).getRotationState());
    }

    // 5. 위쪽 후보가 천장을 넘고 나머지 후보도 막히면 회전에 실패한다.
    @Test
    void rotationCannotKickThroughCeiling() {
        Game game = active(new Mino('T'), 0, 3);
        board(game).setCell(2, 4, new Cell('Z'));
        board(game).setCell(2, 3, new Cell('Z'));

        assertRotationRejected(game);
    }

    // 6. 세로 I가 왼쪽 벽과 오른쪽 벽에서 각각 안쪽으로 보정되어 회전한다.
    @ParameterizedTest
    @ValueSource(ints = {-2, 7})
    void verticalIRotatesAtEitherWall(int col) {
        Game game = active(new Mino('I'), 3, 3);

        // 실제 회전 함수를 써서 세로 모양과 회전 상태를 함께 준비한다.
        game.rotateCW();
        assertEquals(1, current(game).getRotationState());
        at(current(game), 3, col);

        assertTrue(board(game).canPlace(current(game), 3, col));
        int expectedCol = col < 0 ? 0 : 6;
        assertRotationResult(game, 3, expectedCol, "..../..../XXXX/....");
        assertEquals(2, current(game).getRotationState());
    }

    // 일반 블럭 7종은 빈 공간에서 네 번 회전하면 원래 모양과 방향으로 돌아온다.
    @ParameterizedTest
    @ValueSource(chars = {'I', 'J', 'L', 'S', 'T', 'Z', 'O'})
    void fourClockwiseRotationsRestoreShapeAndDirection(char type) {
        Game game = active(new Mino(type), 5, 3);
        String original = contents(shapeOf(current(game)));

        for (int turn = 1; turn <= 4; turn++) {
            game.rotateCW();
            assertEquals(turn % 4, current(game).getRotationState());
            assertEquals(5, current(game).getRow());
            assertEquals(3, current(game).getCol());
        }

        assertEquals(original, contents(shapeOf(current(game))));
    }

    // 바닥의 가로 I는 앞의 네 후보가 막혀도 다섯 번째 후보에서 회전한다.
    @Test
    void horizontalIUsesFifthKickAtFloor() {
        Game game = active(new Mino('I'), 18, 3);

        assertRotationResult(game, 16, 4, "..X./..X./..X./..X.");
        assertEquals(1, current(game).getRotationState());
    }

    // O는 제자리에서 회전하고, 부착된 L/C는 왼쪽 위에서 오른쪽 위로 이동한다.
    @ParameterizedTest
    @ValueSource(chars = {'L', 'C'})
    void squareRotatesAttachedItemInPlace(char itemType) {
        Mino mino = new Mino('O', itemType);
        Cell[][] shape = shapeOf(new Mino('O'));
        shape[0][0] = new Cell(itemType, true, itemType);
        mino.setShape(shape);

        Game game = active(mino, 18, 0);

        assertRotationResult(game, 18, 0, "X" + itemType + "/XX");
    }

    // 7. 무게추는 접촉 여부와 관계없이 회전할 수 없다.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void weightCannotRotate(boolean contacted) {
        Mino weight = new Mino('T', Mino.WEIGHT);

        if (contacted) {
            weight.setContacted();
        }

        Game game = active(weight, 5, 3);

        assertRotationRejected(game);

        assertEquals(2, current(game).getHeight());
        assertEquals(4, current(game).getWidth());
        assertEquals(contacted, current(game).getContacted());
    }

    // 8. 위쪽 보정 시 부착 아이템의 위치와 정보도 함께 반영된다.
    @ParameterizedTest
    @ValueSource(chars = {'L', 'C'})
    void upwardCorrectionPreservesAttachedItem(char itemType) {
        Mino mino = new Mino('T', itemType);

        // T의 위쪽 가운데 칸에 아이템을 둔다.
        Cell[][] initial = shapeOf(new Mino('T'));
        initial[0][1] = new Cell(itemType, true, itemType);
        mino.setShape(initial);

        Game game = active(mino, 18, 3);
        assertRotationResult(game, 17, 2, ".X./.X" + itemType + "/.X.");

        // 회전 후 아이템은 블럭 내부의 (1, 2)에 있다.
        Cell item = current(game).getCell(1, 2);
        assertEquals(itemType, item.getMark());
        assertTrue(item.isItem());
        assertEquals(itemType, item.getType());

        // 보드에서는 (17 + 1, 2 + 2) = (18, 4)에 표시된다.
        Cell displayed = game.getDisplayBoard()[18][4];
        assertEquals(itemType, displayed.getMark());
        assertTrue(displayed.isItem());
        assertEquals(itemType, displayed.getType());
    }

    // 회전을 실행하고 기대하는 위치·모양인지 검사한다.
    // 보드, NEXT, 점수, 블럭 종류, 접촉 상태는 유지되어야 한다.
    private void assertRotationResult(
            Game game,
            int expectedRow,
            int expectedCol,
            String expectedPattern) {

        String originalBoard =
                contents(board(game).getBoardCopy());
        String originalNext =
                contents(game.getNextMino());

        int originalScore = game.getScore();

        char originalType = current(game).getType();
        char originalItemType = current(game).getItemType();
        boolean originalContacted = current(game).getContacted();

        game.rotateCW();

        Mino actual = current(game);

        assertNotNull(actual);

        assertEquals(expectedRow, actual.getRow());
        assertEquals(expectedCol, actual.getCol());
        assertEquals(expectedPattern, patternOf(shapeOf(actual)));

        assertEquals(originalType, actual.getType());
        assertEquals(originalItemType, actual.getItemType());
        assertEquals(originalContacted, actual.getContacted());

        assertEquals(originalScore, game.getScore());
        assertEquals(
                originalBoard,
                contents(board(game).getBoardCopy())
        );
        assertEquals(
                originalNext,
                contents(game.getNextMino())
        );
    }

    // 회전 실패 시 위치와 각 칸의 전체 정보가 유지되는지 검사한다.
    private void assertRotationRejected(Game game) {
        Mino mino = current(game);

        String originalCells = contents(shapeOf(mino));
        String originalPattern = patternOf(shapeOf(mino));

        assertRotationResult(
                game,
                mino.getRow(),
                mino.getCol(),
                originalPattern
        );

        assertEquals(
                originalCells,
                contents(shapeOf(current(game)))
        );
    }

    // 빈칸 '.', 일반 칸 'X', 아이템은 표시 문자로 표현한다.
    private String patternOf(Cell[][] shape) {
        StringBuilder result = new StringBuilder();

        for (int r = 0; r < shape.length; r++) {
            if (r > 0) {
                result.append('/');
            }

            for (int c = 0; c < shape[r].length; c++) {
                Cell cell = shape[r][c];
                assertNotNull(cell);

                if (cell.isEmpty()) {
                    result.append('.');
                } else if (cell.isItem()) {
                    result.append(cell.getMark());
                } else {
                    result.append('X');
                }
            }
        }

        return result.toString();
    }
}
