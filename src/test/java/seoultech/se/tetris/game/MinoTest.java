package seoultech.se.tetris.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MinoTest {

    // 1. 일반 블럭 7종의 초기 모양과 정보
    @ParameterizedTest
    @CsvSource({
        "I, ..../XXXX/..../...., 3",
        "J, X../XXX/..., 3",
        "L, ..X/XXX/..., 3",
        "O, XX/XX, 4",
        "S, .XX/XX./..., 3",
        "T, .X./XXX/..., 3",
        "Z, XX./.XX/..., 3"
    })
    void normalMinoHasExpectedInitialShape(
            char type, String expectedPattern, int expectedCol) {

        Mino mino = new Mino(type);
        String[] expectedRows = expectedPattern.split("/");

        assertEquals(type, mino.getType());
        assertEquals(Mino.NO_ITEM, mino.getItemType());

        assertEquals(0, mino.getRow());
        assertEquals(expectedCol, mino.getCol());
        assertFalse(mino.getContacted());

        assertEquals(expectedRows.length, mino.getHeight());
        assertEquals(expectedRows[0].length(), mino.getWidth());
        assertEquals(expectedRows.length, mino.getSize());

        assertEquals(expectedPattern, patternOf(copyShape(mino)));

        int occupiedCount = 0;

        for (int r = 0; r < mino.getHeight(); r++) {
            for (int c = 0; c < mino.getWidth(); c++) {
                Cell cell = mino.getCell(r, c);

                assertNotNull(cell);

                if (cell.isEmpty()) {
                    assertCellEquals(new Cell(), cell);
                } else {
                    occupiedCount++;
                    assertCellEquals(new Cell(type), cell);
                }
            }
        }

        assertEquals(4, occupiedCount);
    }

    // 2. 시계방향 90도 회전 결과와 원본 유지
    @ParameterizedTest
    @CsvSource({
        "I, ..X./..X./..X./..X.",
        "J, .XX/.X./.X.",
        "L, .X./.X./.XX",
        "O, XX/XX",
        "S, .X./.XX/..X",
        "T, .X./.XX/.X.",
        "Z, ..X/.XX/.X."
    })
    void clockwiseRotationProducesExpectedShape(
            char type, String expectedPattern) {

        Mino mino = new Mino(type);

        Cell[][] original = copyShape(mino);
        int originalRow = mino.getRow();
        int originalCol = mino.getCol();

        Cell[][] rotated = mino.createRotatedCW();

        assertEquals(expectedPattern, patternOf(rotated));

        // 회전 후보를 만드는 것만으로 원본이 바뀌면 안 된다.
        assertPositionAndShape(
                mino, originalRow, originalCol, original
        );

        // 회전해도 각 칸의 종류와 표시 정보는 유지되어야 한다.
        for (Cell[] row : rotated) {
            for (Cell cell : row) {
                Cell expected =
                        cell.isEmpty() ? new Cell() : new Cell(type);

                assertCellEquals(expected, cell);
            }
        }

        // setShape를 호출했을 때 실제 모양이 바뀐다.
        mino.setShape(rotated);

        assertPositionAndShape(
                mino, originalRow, originalCol, rotated
        );
    }

    // 3. 네 번 회전하면 처음 모양과 정보로 돌아온다.
    @ParameterizedTest
    @ValueSource(chars = {'I', 'J', 'L', 'O', 'S', 'T', 'Z'})
    void fourRotationsRestoreOriginalShape(char type) {
        Mino mino = new Mino(type);

        mino.moveDown();
        mino.moveRight();

        Cell[][] original = copyShape(mino);
        int originalRow = mino.getRow();
        int originalCol = mino.getCol();

        for (int i = 0; i < 4; i++) {
            mino.setShape(mino.createRotatedCW());
        }

        assertPositionAndShape(
                mino, originalRow, originalCol, original
        );

        assertEquals(type, mino.getType());
        assertEquals(Mino.NO_ITEM, mino.getItemType());
    }

    // 4. 이동 함수는 위치만 변경한다.
    @ParameterizedTest
    @ValueSource(chars = {' ', 'L', 'C', 'B', 'R', 'W'})
    void movementChangesOnlyPosition(char itemType) {
        Mino mino = new Mino('T', itemType);

        Cell[][] original = copyShape(mino);
        int row = mino.getRow();
        int col = mino.getCol();

        mino.moveLeft();
        assertPositionAndShape(mino, row, col - 1, original);

        mino.moveRight();
        assertPositionAndShape(mino, row, col, original);

        mino.moveDown();
        assertPositionAndShape(mino, row + 1, col, original);

        mino.moveUp();
        assertPositionAndShape(mino, row, col, original);

        assertEquals('T', mino.getType());
        assertEquals(itemType, mino.getItemType());
        assertFalse(mino.getContacted());
    }

    // 5. L/C는 일반 블럭의 실제 네 칸 중 정확히 한 칸에 부착된다.
    @ParameterizedTest
    @ValueSource(chars = {'I', 'J', 'L', 'O', 'S', 'T', 'Z'})
    void attachedItemOccupiesExactlyOneRealCell(char type) {
        Mino normal = new Mino(type);

        char[] itemTypes = {
            Mino.LINE_CLEAR,
            Mino.COLUMN_CLEAR
        };

        for (char itemType : itemTypes) {
            // 여러 번 생성하되, 특정 위치가 나와야 한다고 요구하지 않는다.
            for (int trial = 0; trial < 20; trial++) {
                Mino mino = new Mino(type, itemType);

                assertEquals(type, mino.getType());
                assertEquals(itemType, mino.getItemType());

                assertEquals(normal.getHeight(), mino.getHeight());
                assertEquals(normal.getWidth(), mino.getWidth());
                assertEquals(normal.getRow(), mino.getRow());
                assertEquals(normal.getCol(), mino.getCol());
                assertFalse(mino.getContacted());

                int occupiedCount = 0;
                int itemCount = 0;

                for (int r = 0; r < normal.getHeight(); r++) {
                    for (int c = 0; c < normal.getWidth(); c++) {
                        Cell original = normal.getCell(r, c);
                        Cell actual = mino.getCell(r, c);

                        assertNotNull(actual);

                        if (original.isEmpty()) {
                            // 원래 빈 여백에는 아이템이 생기면 안 된다.
                            assertCellEquals(new Cell(), actual);
                            continue;
                        }

                        assertFalse(actual.isEmpty());
                        occupiedCount++;

                        if (actual.isItem()) {
                            itemCount++;

                            assertCellEquals(
                                    new Cell(itemType, true, itemType),
                                    actual
                            );
                        } else {
                            assertCellEquals(new Cell(type), actual);
                        }
                    }
                }

                assertEquals(4, occupiedCount);
                assertEquals(1, itemCount);
            }
        }
    }

    // 6. 부착 아이템도 블럭과 함께 시계방향으로 회전한다.
    @ParameterizedTest
    @ValueSource(chars = {'L', 'C'})
    void attachedItemRotatesWithTheShape(char itemType) {
        Mino mino = new Mino('T', itemType);

        // 회전 결과를 정확히 비교하려고 아이템 시작 위치를 고정한다.
        Cell[][] initial = copyShape(new Mino('T'));
        initial[0][1] = new Cell(itemType, true, itemType);
        mino.setShape(initial);

        Cell[][] original = copyShape(mino);
        int originalRow = mino.getRow();
        int originalCol = mino.getCol();

        String[] expectedPatterns = {
            ".X./.X" + itemType + "/.X.",
            ".../XXX/." + itemType + ".",
            ".X./" + itemType + "X./.X.",
            "." + itemType + "./XXX/..."
        };

        int[][] expectedItemPositions = {
            {1, 2},
            {2, 1},
            {1, 0},
            {0, 1}
        };

        for (int turn = 0; turn < 4; turn++) {
            mino.setShape(mino.createRotatedCW());

            assertEquals(
                    expectedPatterns[turn],
                    patternOf(copyShape(mino))
            );

            int itemRow = expectedItemPositions[turn][0];
            int itemCol = expectedItemPositions[turn][1];

            assertCellEquals(
                    new Cell(itemType, true, itemType),
                    mino.getCell(itemRow, itemCol)
            );
        }

        assertPositionAndShape(
                mino, originalRow, originalCol, original
        );

        assertEquals('T', mino.getType());
        assertEquals(itemType, mino.getItemType());
    }

    // 7. W/B/R의 고유 크기, 위치, 각 칸의 아이템 정보
    @ParameterizedTest
    @CsvSource({
        "W, 2, 4, 3",
        "B, 2, 2, 4",
        "R, 1, 1, 4"
    })
    void independentItemsHaveExpectedShape(
            char itemType,
            int expectedHeight,
            int expectedWidth,
            int expectedCol) {

        Mino mino = new Mino('T', itemType);

        assertEquals('T', mino.getType());
        assertEquals(itemType, mino.getItemType());

        assertEquals(expectedHeight, mino.getHeight());
        assertEquals(expectedWidth, mino.getWidth());

        assertEquals(0, mino.getRow());
        assertEquals(expectedCol, mino.getCol());
        assertFalse(mino.getContacted());

        for (int r = 0; r < expectedHeight; r++) {
            for (int c = 0; c < expectedWidth; c++) {
                assertCellEquals(
                        new Cell(itemType, true, itemType),
                        mino.getCell(r, c)
                );
            }
        }
    }

    // 8. 정사각형 독립 아이템 B/R은 회전해도 모양과 정보가 같다.
    @ParameterizedTest
    @ValueSource(chars = {'B', 'R'})
    void squareItemsPreserveShapeWhenRotated(char itemType) {
        Mino mino = new Mino('T', itemType);

        Cell[][] original = copyShape(mino);
        int originalRow = mino.getRow();
        int originalCol = mino.getCol();

        for (int turn = 0; turn < 4; turn++) {
            Cell[][] rotated = mino.createRotatedCW();

            assertShapeEquals(original, rotated);

            mino.setShape(rotated);

            assertPositionAndShape(
                    mino, originalRow, originalCol, original
            );
        }

        assertEquals(itemType, mino.getItemType());
    }

    // 9. 접촉 상태는 처음 false이고, 기록 후 유지된다.
    @Test
    void weightContactStateRemainsRecorded() {
        Mino weight = new Mino('T', Mino.WEIGHT);

        assertFalse(weight.getContacted());

        weight.setContacted();
        assertTrue(weight.getContacted());

        // 다시 호출해도 true가 유지된다.
        weight.setContacted();
        assertTrue(weight.getContacted());

        // 이후 아래로 이동해도 접촉 기록이 사라지지 않는다.
        weight.moveDown();
        assertTrue(weight.getContacted());
    }

    // 아래부터 테스트용 보조 함수

    private Cell[][] copyShape(Mino mino) {
        Cell[][] result =
                new Cell[mino.getHeight()][mino.getWidth()];

        for (int r = 0; r < mino.getHeight(); r++) {
            for (int c = 0; c < mino.getWidth(); c++) {
                result[r][c] = mino.getCell(r, c);
            }
        }

        return result;
    }

    // 빈칸 '.', 일반 칸 'X', 아이템은 표시 문자로 표현한다.
    // 행과 행 사이는 '/'로 구분한다.
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

    private void assertPositionAndShape(
            Mino mino,
            int expectedRow,
            int expectedCol,
            Cell[][] expectedShape) {

        assertEquals(expectedRow, mino.getRow());
        assertEquals(expectedCol, mino.getCol());
        assertShapeEquals(expectedShape, copyShape(mino));
    }

    private void assertShapeEquals(
            Cell[][] expected, Cell[][] actual) {

        assertEquals(expected.length, actual.length);

        for (int r = 0; r < expected.length; r++) {
            assertEquals(expected[r].length, actual[r].length);

            for (int c = 0; c < expected[r].length; c++) {
                assertCellEquals(expected[r][c], actual[r][c]);
            }
        }
    }

    private void assertCellEquals(Cell expected, Cell actual) {
        assertNotNull(actual);
        assertEquals(expected.getMark(), actual.getMark());
        assertEquals(expected.isItem(), actual.isItem());
        assertEquals(expected.getType(), actual.getType());
    }

    // 10. 아이템 Cell 대체 위치 확률 균등 분포 확률
    @ParameterizedTest(name = "{0} 블럭의 {1} 아이템 부착 위치 분포")
    @CsvSource({
        "I, L", "I, C",
        "J, L", "J, C",
        "L, L", "L, C",
        "O, L", "O, C",
        "S, L", "S, C",
        "T, L", "T, C",
        "Z, L", "Z, C"
    })
    void attachedItemPositionsAreApproximatelyUniform(
            char type, char itemType) {

        final int samples = 100_000;
        final double expectedCount = samples / 4.0;

        // 실제 블럭 칸을 위에서 아래, 왼쪽에서 오른쪽 순서로 구분한다.
        int[] counts = new int[4];

        for (int trial = 0; trial < samples; trial++) {
            Mino mino = new Mino(type, itemType);

            int occupiedCount = 0;
            int itemCount = 0;
            int itemIndex = -1;

            for (int r = 0; r < mino.getHeight(); r++) {
                for (int c = 0; c < mino.getWidth(); c++) {
                    Cell cell = mino.getCell(r, c);

                    if (cell.isEmpty()) {
                        continue;
                    }

                    if (cell.isItem()) {
                        itemIndex = occupiedCount;
                        itemCount++;
                    }

                    occupiedCount++;
                }
            }

            assertEquals(4, occupiedCount);
            assertEquals(1, itemCount);

            counts[itemIndex]++;
        }

        for (int position = 0; position < counts.length; position++) {
            double relativeError =
                    Math.abs(counts[position] - expectedCount)
                    / expectedCount;

            assertTrue(
                    relativeError <= 0.05,
                    "type=" + type
                    + ", item=" + itemType
                    + ", position=" + (position + 1)
                    + ", expected=" + expectedCount
                    + ", actual=" + counts[position]
                    + ", relativeError=" + (relativeError * 100) + "%"
            );
        }
    }
}
