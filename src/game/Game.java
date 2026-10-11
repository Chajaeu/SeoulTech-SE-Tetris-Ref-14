package game;

public class Game {
    // 보드 크기 상수(20x10 고정)
    public static final int ROWS = 20;
    public static final int COLS = 10;

    // Mino 블럭 타입(일반)
    private final char[] minoTypes = {'I', 'L', 'J', 'T', 'S', 'Z', 'O'};

    // 보드&블럭 관리
    private final Board board;
    private Mino currentMino;

    public Game() {
        board = new Board(ROWS, COLS);
    }
}
