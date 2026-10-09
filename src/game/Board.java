package game;

public class Board {
    private final Cell[][] board;
    private final int rows;
    private final int cols;

    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        board = new Cell[rows][cols];
        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                board[row][col] = new Cell();
            }
        }
    }
}
