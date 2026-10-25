package seoultech.se.tetris.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellTest {

    // 1. 기본 생성자로 만들면 빈칸이어야 한다.
    @Test
    void emptyCellHasBlankMetadata() {
        Cell cell = new Cell();

        assertEquals(' ', cell.getMark());
        assertFalse(cell.isItem());
        assertEquals(' ', cell.getType());
        assertTrue(cell.isEmpty());
    }

    // 2. 일반 블럭 7종의 표시, 종류, 빈칸 여부를 검사한다.
    @ParameterizedTest
    @ValueSource(chars = {'I', 'J', 'L', 'O', 'S', 'T', 'Z'})
    void normalCellPreservesType(char type) {
        Cell cell = new Cell(type);

        assertEquals('O', cell.getMark());
        assertFalse(cell.isItem());
        assertEquals(type, cell.getType());
        assertFalse(cell.isEmpty());
    }

    // 3. 아이템 5종의 정보가 그대로 저장되는지 검사한다.
    @ParameterizedTest
    @ValueSource(chars = {'L', 'W', 'C', 'B', 'R'})
    void itemCellPreservesMetadata(char type) {
        Cell cell = new Cell(type, true, type);

        assertEquals(type, cell.getMark());
        assertTrue(cell.isItem());
        assertEquals(type, cell.getType());
        assertFalse(cell.isEmpty());
    }

    // 4. 같은 L이라도 일반 블럭과 줄 삭제 아이템을 구분해야 한다.
    @Test
    void normalLAndLineItemAreDistinguishable() {
        Cell normalCell = new Cell('L');
        Cell itemCell = new Cell('L', true, 'L');

        assertFalse(normalCell.isItem());
        assertTrue(itemCell.isItem());
    }

    // 5. 빈칸 판정은 표시 문자(mark)가 아니라 종류(type)를 따른다.
    @Test
    void emptinessUsesTypeRatherThanDisplayMark() {
        Cell emptyTypeCell = new Cell('X', false, ' ');
        Cell occupiedTypeCell = new Cell(' ', false, 'T');

        assertTrue(emptyTypeCell.isEmpty());
        assertFalse(occupiedTypeCell.isEmpty());
    }
}
