package seoultech.se.tetris.game;

import java.lang.reflect.Field;

final class CoreTestSupport {

    private CoreTestSupport() {
    }

    // 테스트에서 Game의 내부 필드를 읽는다.
    static Object get(Game game, String name) {
        try {
            Field field = Game.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(game);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "테스트 준비 중 필드 조회 실패: " + name, e
            );
        }
    }

    // 테스트 시작 조건을 만들기 위해 내부 필드를 설정한다.
    static void set(Game game, String name, Object value) {
        try {
            Field field = Game.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(game, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "테스트 준비 중 필드 설정 실패: " + name, e
            );
        }
    }

    static Board board(Game game) {
        return (Board) get(game, "board");
    }

    static Mino current(Game game) {
        return (Mino) get(game, "currentMino");
    }

    static Mino next(Game game) {
        return (Mino) get(game, "nextMino");
    }

    // Mino를 테스트에 필요한 위치로 옮긴다.
    static Mino at(Mino mino, int row, int col) {
        while (mino.getRow() < row) {
            mino.moveDown();
        }
        while (mino.getRow() > row) {
            mino.moveUp();
        }
        while (mino.getCol() < col) {
            mino.moveRight();
        }
        while (mino.getCol() > col) {
            mino.moveLeft();
        }

        return mino;
    }

    // 원하는 현재 블럭과 위치를 가진 Normal 게임을 준비한다.
    static Game active(Mino mino, int row, int col) {
        Game game = new Game(Game.NORMAL, false);
        set(game, "currentMino", at(mino, row, col));
        return game;
    }

    // 지정한 열을 제외하고 한 행을 채운다.
    static void fillRow(Board board, int row, int... gaps) {
        for (int c = 0; c < board.getCols(); c++) {
            boolean isGap = false;

            for (int gap : gaps) {
                if (c == gap) {
                    isGap = true;
                    break;
                }
            }

            board.setCell(
                    row,
                    c,
                    isGap ? new Cell() : new Cell('J')
            );
        }
    }

    static Cell[][] shapeOf(Mino mino) {
        Cell[][] result =
                new Cell[mino.getHeight()][mino.getWidth()];

        for (int r = 0; r < mino.getHeight(); r++) {
            for (int c = 0; c < mino.getWidth(); c++) {
                result[r][c] = mino.getCell(r, c);
            }
        }

        return result;
    }

    static int occupied(Cell[][] cells) {
        int count = 0;

        for (Cell[] row : cells) {
            for (Cell cell : row) {
                if (!cell.isEmpty()) {
                    count++;
                }
            }
        }

        return count;
    }

    // 모양뿐 아니라 각 칸의 전체 정보를 비교하기 위한 문자열
    static String contents(Cell[][] cells) {
        StringBuilder result = new StringBuilder();

        for (Cell[] row : cells) {
            for (Cell cell : row) {
                result.append(cell.getMark())
                        .append(':')
                        .append(cell.isItem())
                        .append(':')
                        .append(cell.getType())
                        .append('|');
            }

            result.append('/');
        }

        return result.toString();
    }
}
