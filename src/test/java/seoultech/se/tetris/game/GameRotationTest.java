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

    // 2. 바닥 때문에 회전할 수 없으면 한 칸 위에서 회전한다.
    @Test
    void floorCollisionUsesOneRowUpwardCorrection() {
        Game game = active(new Mino('T'), 18, 3);

        assertRotationResult(
                game,
                17,
                3,
                ".X./.XX/.X."
        );
    }

    // 3. 고정 블럭에 막힌 경우에도 위쪽 보정을 적용한다.
    @Test
    void fixedCellCollisionUsesUpwardCorrection() {
        Game game = active(new Mino('T'), 5, 3);

        // 현재 위치에서 회전하면 이 칸과 겹친다.
        board(game).setCell(7, 4, new Cell('J'));

        assertRotationResult(
                game,
                4,
                3,
                ".X./.XX/.X."
        );
    }

    // 4. 현재 위치와 한 칸 위가 모두 막히면 상태를 유지한다.
    @Test
    void rotationStopsWhenOneRowCorrectionAlsoFails() {
        Game game = active(new Mino('T'), 5, 3);

        // 제자리 회전을 막는 칸
        board(game).setCell(7, 4, new Cell('J'));

        // 한 칸 위에서의 회전을 막는 칸
        board(game).setCell(5, 5, new Cell('Z'));

        // 회전 전 블럭은 정상적으로 놓을 수 있는 상태다.
        assertTrue(
                board(game).canPlace(current(game), 5, 3)
        );

        // 두 칸 위에서는 회전할 수 있지만,
        // 현재 규칙은 한 칸 위까지만 보정한다.
        assertTrue(
                board(game).canPlace(
                        current(game).createRotatedCW(),
                        3,
                        3
                )
        );

        assertRotationRejected(game);
    }

    // 5. 위쪽 보정으로 실제 블럭 칸이 보드 위를 벗어나면 실패한다.
    @Test
    void upwardCorrectionCannotCrossTopBoundary() {
        Game game = active(new Mino('T'), 0, 3);

        // 제자리 회전은 아래 장애물 때문에 불가능하다.
        board(game).setCell(2, 4, new Cell('Z'));

        // 한 칸 올리면 회전한 블럭의 실제 칸이 -1행으로 나간다.
        assertRotationRejected(game);
    }

    // 6. 좌우 벽을 벗어나는 회전은 거부한다.
    @ParameterizedTest
    @ValueSource(ints = {-2, 7})
    void rotationCannotCrossSideWalls(int col) {
        Mino mino = new Mino('I');

        // 세로 I로 준비한다.
        mino.setShape(mino.createRotatedCW());

        Game game = active(mino, 3, col);

        // 세로 I 자체는 보드 안에 정상적으로 놓인다.
        assertTrue(board(game).canPlace(mino, 3, col));

        // 가로로 회전하면 왼쪽 또는 오른쪽 벽을 벗어난다.
        assertRotationRejected(game);
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

        // 아이템을 T의 위쪽 가운데 칸에 고정해서 준비한다.
        Cell[][] initial = shapeOf(new Mino('T'));
        initial[0][1] = new Cell(itemType, true, itemType);
        mino.setShape(initial);

        Game game = active(mino, 18, 3);

        assertRotationResult(
                game,
                17,
                3,
                ".X./.X" + itemType + "/.X."
        );

        // 회전한 Mino 내부에서는 (1, 2)에 있어야 한다.
        Cell item = current(game).getCell(1, 2);

        assertEquals(itemType, item.getMark());
        assertTrue(item.isItem());
        assertEquals(itemType, item.getType());

        // 보드 표시 위치는 (17 + 1, 3 + 2) = (18, 5).
        Cell displayed = game.getDisplayBoard()[18][5];

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
