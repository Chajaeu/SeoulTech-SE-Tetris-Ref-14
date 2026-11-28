package seoultech.se.tetris.game;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static seoultech.se.tetris.game.CoreTestSupport.*;

class GameGenerationTest {

    private static final char[] TYPES = {'L', 'J', 'T', 'S', 'Z', 'O', 'I'};

    // 가능한 난수 값을 전부 넣어 종류별 가중치를 검사한다.
    @ParameterizedTest
    @CsvSource({"1, 72, 12", "2, 70, 10", "3, 68, 8"})
    void everyRandomValueProducesTheSpecifiedWeights(
            int difficulty, int totalWeight, int iWeight) {
        Game game = new Game(difficulty, false);
        int[] values = new int[totalWeight];
        for (int i = 0; i < totalWeight; i++) {
            values[i] = i;
        }

        SequenceRandom random = new SequenceRandom(values);
        set(game, "random", random);
        Map<Character, Integer> counts = new HashMap<>();

        for (int i = 0; i < totalWeight; i++) {
            char type = selectActualType(game);
            counts.merge(type, 1, Integer::sum);
            assertEquals(totalWeight, random.boundAt(i));
        }

        assertEquals(7, counts.size());
        for (char type : TYPES) {
            int expected = type == 'I' ? iWeight : 10;
            int actual = counts.getOrDefault(type, 0);
            assertEquals(expected, actual, "difficulty=" + difficulty + ", type=" + type);
        }
        assertEquals(totalWeight, random.consumed());
    }

    // 실제 선택 함수를 난이도마다 10만 번 호출한다.
    @ParameterizedTest
    @CsvSource({"1, 72, 12", "2, 70, 10", "3, 68, 8"})
    void actualDistributionStaysWithinFivePercentRelativeError(
            int difficulty, int totalWeight, int iWeight) {
        final int samples = 100_000;
        final long seed = 20261023L;

        Game game = new Game(difficulty, false);
        set(game, "random", new Random(seed));
        Map<Character, Integer> counts = new HashMap<>();

        for (int i = 0; i < samples; i++) {
            char type = selectActualType(game);
            counts.merge(type, 1, Integer::sum);
        }

        assertEquals(7, counts.size());
        for (char type : TYPES) {
            int weight = type == 'I' ? iWeight : 10;
            double expected = samples * (double) weight / totalWeight;
            int actual = counts.getOrDefault(type, 0);
            double relativeError = Math.abs(actual - expected) / expected;

            System.out.printf(
                    "difficulty=%d type=%c samples=%d "
                            + "expected=%.2f actual=%d relativeError=%.2f%%%n",
                    difficulty, type, samples, expected, actual, relativeError * 100
            );

            assertTrue(relativeError <= 0.05,
                    "difficulty=" + difficulty + ", type=" + type
                            + ", expected=" + expected + ", actual=" + actual
                            + ", relativeError=" + relativeError);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void normalNextGenerationPreservesOrder(int difficulty) {
        Game game = new Game(difficulty, false);
        SequenceRandom random = new SequenceRandom(0, 10, 20, 30, 40, 50, 60);
        set(game, "random", random);

        for (char expectedType : TYPES) {
            String previousNext = contents(game.getNextMino());
            char previousType = next(game).getType();

            set(game, "currentMino", null);
            game.tick();

            assertEquals(previousType, current(game).getType());
            assertEquals(previousNext, contents(shapeOf(current(game))));
            assertEquals(0, current(game).getRow());

            assertEquals(expectedType, next(game).getType());
            assertEquals(Mino.NO_ITEM, next(game).getItemType());
            assertEquals(contents(shapeOf(new Mino(expectedType))), contents(game.getNextMino()));
            assertNotSame(current(game), next(game));
            assertEquals(0, game.getScore());
        }

        assertEquals(7, random.consumed());
        assertEquals(0, (int) get(game, "givenItemNumber"));
    }

    @ParameterizedTest
    @CsvSource({
        "false, 0, 0, false",
        "false, 9, 0, false",
        "false, 10, 0, false",
        "false, 20, 0, false",
        "false, 100, 0, false",
        "true, 0, 0, false",
        "true, 9, 0, false",
        "true, 10, 0, true",
        "true, 10, 1, false",
        "true, 19, 1, false",
        "true, 20, 1, true",
        "true, 20, 2, false",
        "true, 29, 2, false",
        "true, 30, 2, true",
        "true, 30, 3, false"
    })
    void itemAwardDependsOnModeLinesAndPreviousAwards(
            boolean itemMode, int lines, int given, boolean expectedAward) {
        Game game = new Game(Game.NORMAL, itemMode);
        set(game, "lines", lines);
        set(game, "givenItemNumber", given);
        set(game, "currentMino", null);

        SequenceRandom random = expectedAward
                ? new SequenceRandom(0, 0)
                : new SequenceRandom(0);
        set(game, "random", random);

        game.tick();

        assertEquals(expectedAward ? Mino.LINE_CLEAR : Mino.NO_ITEM, next(game).getItemType());
        assertEquals(given + (expectedAward ? 1 : 0), (int) get(game, "givenItemNumber"));
        assertEquals(expectedAward ? 2 : 1, random.consumed());

        if (expectedAward) {
            assertEquals(5, random.boundAt(0));
            assertEquals(70, random.boundAt(1));
        } else {
            assertEquals(70, random.boundAt(0));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, 72, 0, L, 4, 4, 4",
        "1, 72, 1, W, 2, 4, 8",
        "1, 72, 2, C, 4, 4, 4",
        "1, 72, 3, B, 1, 1, 1",
        "1, 72, 4, R, 1, 1, 1",
        "2, 70, 0, L, 4, 4, 4",
        "2, 70, 1, W, 2, 4, 8",
        "2, 70, 2, C, 4, 4, 4",
        "2, 70, 3, B, 1, 1, 1",
        "2, 70, 4, R, 1, 1, 1",
        "3, 68, 0, L, 4, 4, 4",
        "3, 68, 1, W, 2, 4, 8",
        "3, 68, 2, C, 4, 4, 4",
        "3, 68, 3, B, 1, 1, 1",
        "3, 68, 4, R, 1, 1, 1"
    })
    void everyItemTypeIsGeneratedCorrectly(
            int difficulty, int expectedBound, int itemIndex, char expectedItem,
            int expectedHeight, int expectedWidth, int expectedOccupied) {
        Game game = new Game(difficulty, true);
        String previousNext = contents(game.getNextMino());
        set(game, "lines", 10);
        set(game, "currentMino", null);

        // 첫 난수는 아이템 종류, 두 번째는 바탕 블럭 종류다. 60은 항상 I다.
        SequenceRandom random = new SequenceRandom(itemIndex, 60);
        set(game, "random", random);
        game.tick();

        assertEquals(previousNext, contents(shapeOf(current(game))));
        assertEquals(Mino.NO_ITEM, current(game).getItemType());

        Mino generated = next(game);
        assertEquals(expectedItem, generated.getItemType());
        assertEquals('I', generated.getType());
        assertEquals(expectedHeight, generated.getHeight());
        assertEquals(expectedWidth, generated.getWidth());
        assertEquals(expectedOccupied, occupied(game.getNextMino()));

        int itemCells = 0;
        for (Cell[] row : game.getNextMino()) {
            for (Cell cell : row) {
                if (cell.isItem()) {
                    itemCells++;
                    assertEquals(expectedItem, cell.getMark());
                    assertEquals(expectedItem, cell.getType());
                } else if (!cell.isEmpty()) {
                    assertEquals('O', cell.getMark());
                    assertEquals('I', cell.getType());
                }
            }
        }

        boolean attached = expectedItem == Mino.LINE_CLEAR || expectedItem == Mino.COLUMN_CLEAR;
        assertEquals(attached ? 1 : expectedOccupied, itemCells);
        assertEquals(1, (int) get(game, "givenItemNumber"));
        assertEquals(2, random.consumed());
        assertEquals(5, random.boundAt(0));
        assertEquals(expectedBound, random.boundAt(1));

        if (expectedItem == Mino.COLUMN_CLEAR) {
            Mino reserved = (Mino) get(game, "subMino");
            assertNotNull(reserved);
            assertEquals('I', reserved.getType());
            assertEquals(Mino.NO_ITEM, reserved.getItemType());
            assertEquals(contents(shapeOf(new Mino('I'))), contents(shapeOf(reserved)));
        } else {
            assertNull(get(game, "subMino"));
        }
    }

    @Test
    void reservedMinoHasPriorityWithoutConsumingItemAward() {
        Game game = new Game(Game.NORMAL, true);
        Mino reserved = new Mino('I');
        String expectedReserved = contents(shapeOf(reserved));
        set(game, "lines", 10);
        set(game, "subMino", reserved);
        set(game, "currentMino", null);

        SequenceRandom random = new SequenceRandom(0, 0);
        set(game, "random", random);
        game.tick();

        assertEquals(expectedReserved, contents(game.getNextMino()));
        assertEquals(Mino.NO_ITEM, next(game).getItemType());
        assertNull(get(game, "subMino"));
        assertEquals(0, (int) get(game, "givenItemNumber"));
        assertEquals(0, random.consumed());

        set(game, "currentMino", null);
        game.tick();

        assertEquals(expectedReserved, contents(shapeOf(current(game))));
        assertEquals(Mino.LINE_CLEAR, next(game).getItemType());
        assertEquals(1, (int) get(game, "givenItemNumber"));
        assertEquals(2, random.consumed());
    }

    @Test
    void creatingColumnItemReservesIAndPreservesSpawnOrder() {
        Game game = new Game(Game.NORMAL, true);
        set(game, "lines", 10);
        set(game, "level", 2);
        set(game, "tickInterval", 900);
        set(game, "currentMino", null);
        set(game, "nextMino", new Mino('O'));

        // C 선택 → 바탕 I 선택 → 예약 처리 이후 일반 J 선택
        SequenceRandom random = new SequenceRandom(2, 60, 10);
        set(game, "random", random);
        game.tick();

        assertEquals('O', current(game).getType());
        assertEquals(Mino.COLUMN_CLEAR, next(game).getItemType());
        assertEquals(1, (int) get(game, "givenItemNumber"));
        String expectedColumn = contents(game.getNextMino());

        Mino reserved = (Mino) get(game, "subMino");
        assertNotNull(reserved);
        assertEquals('I', reserved.getType());
        assertEquals(Mino.NO_ITEM, reserved.getItemType());
        String expectedI = contents(shapeOf(reserved));

        game.harddrop();

        assertEquals(Mino.COLUMN_CLEAR, current(game).getItemType());
        assertEquals(expectedColumn, contents(shapeOf(current(game))));
        assertEquals(expectedI, contents(game.getNextMino()));
        assertEquals(Mino.NO_ITEM, next(game).getItemType());
        assertNull(get(game, "subMino"));
        assertEquals(1, (int) get(game, "givenItemNumber"));
        assertEquals(2, random.consumed());

        game.harddrop();
        assertTrue(game.getIsWaitingDelete());
        game.deleteCells();

        assertEquals('I', current(game).getType());
        assertEquals(Mino.NO_ITEM, current(game).getItemType());
        assertEquals(expectedI, contents(shapeOf(current(game))));
        assertEquals('J', next(game).getType());
        assertEquals(Mino.NO_ITEM, next(game).getItemType());
        assertEquals(1, (int) get(game, "givenItemNumber"));
        assertEquals(3, random.consumed());
    }

    @Test
    void itemAwardRepeatsOnlyAfterAnotherTenLines() {
        Game game = new Game(Game.NORMAL, true);
        set(game, "lines", 10);
        set(game, "currentMino", null);

        SequenceRandom random = new SequenceRandom(0, 0, 10, 3, 20);
        set(game, "random", random);
        game.tick();

        assertEquals(Mino.LINE_CLEAR, next(game).getItemType());
        assertEquals(1, (int) get(game, "givenItemNumber"));
        String firstItem = contents(game.getNextMino());

        set(game, "currentMino", null);
        game.tick();

        assertEquals(firstItem, contents(shapeOf(current(game))));
        assertEquals('J', next(game).getType());
        assertEquals(Mino.NO_ITEM, next(game).getItemType());
        assertEquals(1, (int) get(game, "givenItemNumber"));
        assertEquals(3, random.consumed());

        set(game, "lines", 20);
        set(game, "currentMino", null);
        game.tick();

        assertEquals('J', current(game).getType());
        assertEquals(Mino.BOMB, next(game).getItemType());
        assertEquals(2, (int) get(game, "givenItemNumber"));
        assertEquals(5, random.consumed());
    }

    @Test
    void actualLineDeletionTriggersAwardAtNextReplenishment() {
        Game game = new Game(Game.NORMAL, true);
        set(game, "lines", 9);
        set(game, "level", 2);
        set(game, "tickInterval", 900);
        set(game, "currentMino", at(new Mino('O'), 18, 4));
        set(game, "nextMino", new Mino('J'));

        String previousNext = contents(game.getNextMino());
        fillRow(board(game), 19, 4, 5);

        SequenceRandom random = new SequenceRandom(0, 60);
        set(game, "random", random);
        game.moveDown();

        assertTrue(game.getIsWaitingDelete());
        assertEquals(9, game.getLines());
        assertEquals(0, (int) get(game, "givenItemNumber"));

        game.tick();
        assertNull(current(game));
        assertEquals(previousNext, contents(game.getNextMino()));
        assertEquals(0, random.consumed());

        game.deleteCells();

        assertEquals(10, game.getLines());
        assertEquals(0, current(game).getRow());
        assertEquals(previousNext, contents(shapeOf(current(game))));
        assertEquals(Mino.LINE_CLEAR, next(game).getItemType());
        assertEquals('I', next(game).getType());
        assertEquals(1, (int) get(game, "givenItemNumber"));
        assertEquals(2, random.consumed());
    }

    private char selectActualType(Game game) {
        try {
            Method method = Game.class.getDeclaredMethod("selectMinoType");
            method.setAccessible(true);
            return (char) method.invoke(game);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("실제 블럭 선택 함수 호출 실패", e);
        }
    }

    // 지정한 난수를 순서대로 반환하고 nextInt의 호출 범위를 기록한다.
    private static final class SequenceRandom extends Random {
        private final int[] values;
        private final int[] bounds;
        private int cursor;

        SequenceRandom(int... values) {
            this.values = values;
            this.bounds = new int[values.length];
        }

        @Override
        public int nextInt(int bound) {
            assertTrue(cursor < values.length, "예상보다 많은 난수 호출이 발생했습니다.");
            int value = values[cursor];
            assertTrue(value >= 0 && value < bound,
                    "준비한 난수 " + value + "가 호출 범위 " + bound + " 밖입니다.");

            bounds[cursor] = bound;
            cursor++;
            return value;
        }

        int consumed() {
            return cursor;
        }

        int boundAt(int index) {
            return bounds[index];
        }
    }
}