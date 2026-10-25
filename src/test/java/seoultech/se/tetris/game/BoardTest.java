package seoultech.se.tetris.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    private Board board;

    // 각 테스트를 실행하기 전에 새로운 빈 보드를 준비한다.
    @BeforeEach
    void setUp() {
        board = new Board(20, 10);
    }

    // 1. 보드 크기와 모든 칸의 초기 상태
    @Test
    void newBoardContainsOnlyEmptyCells() {
        assertEquals(20, board.getRows());
        assertEquals(10, board.getCols());

        for (int r = 0; r < 20; r++) {
            for (int c = 0; c < 10; c++) {
                Cell cell = board.getCell(r, c);

                assertNotNull(cell);
                assertEquals(' ', cell.getMark());
                assertFalse(cell.isItem());
                assertEquals(' ', cell.getType());
                assertTrue(cell.isEmpty());
            }
        }
    }

    // 2. 네 모서리에서 칸 저장·조회·비우기
    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "0, 9",
        "19, 0",
        "19, 9"
    })
    void cellsCanBeWrittenReadAndCleared(int row, int col) {
        Cell item = new Cell('L', true, 'L');

        board.setCell(row, col, item);

        assertCellEquals(item, board.getCell(row, col));

        board.setCell(row, col, new Cell());

        assertTrue(board.getCell(row, col).isEmpty());
    }

    // 3. 복사본의 내용과 원본 보호
    @Test
    void boardCopyPreservesValuesAndProtectsOriginal() {
        board.setCell(1, 2, new Cell('T'));
        board.setCell(19, 9, new Cell('L', true, 'L'));

        Cell[][] copy = board.getBoardCopy();

        assertEquals(20, copy.length);

        for (int r = 0; r < 20; r++) {
            assertEquals(10, copy[r].length);

            for (int c = 0; c < 10; c++) {
                assertCellEquals(board.getCell(r, c), copy[r][c]);
            }
        }

        // 복사본의 특정 칸을 바꿔도 원본은 유지되어야 한다.
        copy[19][9] = new Cell();

        assertEquals('L', board.getCell(19, 9).getMark());
        assertTrue(board.getCell(19, 9).isItem());
        assertEquals('L', board.getCell(19, 9).getType());

        // 복사본의 행 자체를 교체해도 원본은 유지되어야 한다.
        copy[0] = new Cell[10];

        assertNotNull(board.getCell(0, 0));
        assertTrue(board.getCell(0, 0).isEmpty());

        // 반대로 원본을 바꿔도 이미 받은 복사본은 유지되어야 한다.
        board.setCell(1, 2, new Cell());

        assertEquals('T', copy[1][2].getType());
    }

    // 4. O 블럭으로 네 방향 경계를 검사
    @ParameterizedTest
    @CsvSource({
        "0, 0, true",
        "0, 8, true",
        "18, 0, true",
        "18, 8, true",
        "5, 3, true",
        "-1, 3, false",
        "19, 3, false",
        "5, -1, false",
        "5, 9, false"
    })
    void placementChecksBoardBoundaries(
            int row, int col, boolean expected) {

        Mino mino = new Mino('O');

        assertPlacement(expected, mino, row, col);
    }

    // 5. 블럭 배열의 빈 여백은 보드 밖으로 나가도 된다.
    @Test
    void emptyPaddingMayExtendOutsideBoard() {
        Mino mino = new Mino('I');

        // 가로 I는 배열의 두 번째 행에 실제 칸이 있다.
        assertPlacement(true, mino, -1, 0);
        assertPlacement(false, mino, -2, 0);

        assertPlacement(true, mino, 18, 0);
        assertPlacement(false, mino, 19, 0);

        // 한 번 회전하면 실제 칸은 배열의 세 번째 열에 있다.
        mino.setShape(mino.createRotatedCW());

        assertPlacement(true, mino, 0, -2);
        assertPlacement(false, mino, 0, -3);

        assertPlacement(true, mino, 0, 7);
        assertPlacement(false, mino, 0, 8);
    }

    // 6. 일반 블럭 7종 × 회전 4방향의 충돌 검사
    @ParameterizedTest
    @ValueSource(chars = {'I', 'J', 'L', 'O', 'S', 'T', 'Z'})
    void collisionsUseOnlyOccupiedCells(char type) {
        Mino mino = new Mino(type);

        for (int turn = 0; turn < 4; turn++) {
            for (int r = 0; r < mino.getHeight(); r++) {
                for (int c = 0; c < mino.getWidth(); c++) {

                    // 후보 위치는 (5, 3).
                    // 블럭 배열의 각 칸 아래에 장애물을 하나씩 둔다.
                    board.setCell(5 + r, 3 + c, new Cell('Z'));

                    // 빈 여백 아래의 장애물은 충돌하지 않는다.
                    // 실제 블럭 칸 아래의 장애물은 충돌한다.
                    boolean expected = mino.getCell(r, c).isEmpty();

                    assertPlacement(expected, mino, 5, 3);

                    // 다음 칸을 검사하기 위해 장애물을 제거한다.
                    board.setCell(5 + r, 3 + c, new Cell());
                }
            }

            mino.setShape(mino.createRotatedCW());
        }
    }

    // 7. 직사각형 무게추의 전체 영역을 검사
    @Test
    void weightChecksItsFullTwoByFourArea() {
        Mino weight = new Mino('T', Mino.WEIGHT);

        assertTrue(board.canPlace(weight, 18, 6));

        assertFalse(board.canPlace(weight, 19, 6));
        assertFalse(board.canPlace(weight, 18, 7));
        assertFalse(board.canPlace(weight, -1, 3));
        assertFalse(board.canPlace(weight, 0, -1));

        // 2×4의 여덟 칸 모두 충돌 대상인지 검사한다.
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 4; c++) {
                board.setCell(10 + r, 3 + c, new Cell('J'));

                assertFalse(board.canPlace(weight, 10, 3));

                board.setCell(10 + r, 3 + c, new Cell());
            }
        }
    }

    // 8. 고정할 때 빈 여백으로 기존 칸을 덮어쓰면 안 된다.
    @Test
    void fixingMinoWritesOnlyOccupiedCells() {
        Mino mino = new Mino('T');

        // T의 초기 위치는 (0, 3).
        // 배열의 좌상단은 빈 여백이다.
        board.setCell(0, 3, new Cell('Z'));

        // 블럭과 떨어진 다른 칸도 그대로 유지되어야 한다.
        board.setCell(19, 9, new Cell('I'));

        board.fixMino(mino);

        assertEquals('T', board.getCell(0, 4).getType());
        assertEquals('T', board.getCell(1, 3).getType());
        assertEquals('T', board.getCell(1, 4).getType());
        assertEquals('T', board.getCell(1, 5).getType());

        assertEquals('Z', board.getCell(0, 3).getType());
        assertEquals('I', board.getCell(19, 9).getType());

        // T 네 칸 + 기존 두 칸
        assertEquals(6, countOccupiedCells());
    }

    // 9. 일반 블럭과 아이템을 고정해도 각 칸의 정보가 유지된다.
    @ParameterizedTest
    @ValueSource(chars = {' ', 'L', 'C', 'B', 'R', 'W'})
    void fixingMinoPreservesCellMetadata(char itemType) {
        Mino mino = new Mino('T', itemType);

        for (int i = 0; i < 5; i++) {
            mino.moveDown();
        }

        board.fixMino(mino);

        int expectedCount = 0;

        for (int r = 0; r < mino.getHeight(); r++) {
            for (int c = 0; c < mino.getWidth(); c++) {
                Cell source = mino.getCell(r, c);

                if (source.isEmpty()) {
                    continue;
                }

                expectedCount++;

                Cell fixed = board.getCell(
                        mino.getRow() + r,
                        mino.getCol() + c
                );

                assertCellEquals(source, fixed);
            }
        }

        assertEquals(expectedCount, countOccupiedCells());
    }

    // 아래부터는 테스트 안에서 사용하는 보조 함수다.

    // 이동용·회전용 canPlace가 같은 결과를 내는지 함께 검사한다.
    private void assertPlacement(
            boolean expected, Mino mino, int row, int col) {

        assertEquals(expected, board.canPlace(mino, row, col));

        Cell[][] shape = copyShape(mino);

        assertEquals(expected, board.canPlace(shape, row, col));
    }

    private Cell[][] copyShape(Mino mino) {
        Cell[][] shape =
                new Cell[mino.getHeight()][mino.getWidth()];

        for (int r = 0; r < mino.getHeight(); r++) {
            for (int c = 0; c < mino.getWidth(); c++) {
                shape[r][c] = mino.getCell(r, c);
            }
        }

        return shape;
    }

    private void assertCellEquals(Cell expected, Cell actual) {
        assertNotNull(actual);
        assertEquals(expected.getMark(), actual.getMark());
        assertEquals(expected.isItem(), actual.isItem());
        assertEquals(expected.getType(), actual.getType());
    }

    private int countOccupiedCells() {
        int count = 0;

        for (int r = 0; r < board.getRows(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                if (!board.getCell(r, c).isEmpty()) {
                    count++;
                }
            }
        }

        return count;
    }
}
