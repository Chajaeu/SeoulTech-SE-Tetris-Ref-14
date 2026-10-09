package game;

public class Game {
    public static final int ROWS = 20;
    public static final int COLS = 10;

    private final Board board;
    private Mino currentMino;

    public Game() {
        board = new Board(ROWS, COLS);
    }
}
