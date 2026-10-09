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


    // board의 크기 조회 용 getter
    public int getRows() {
        return rows;
    }
    public int getCols() {
        return cols;
    }

    // 보드 각 칸(cell) 조회
    public Cell getCell(int row, int col) {
        return board[row][col];
    }

    // 보드 자체를 copy해서 반환(조회) - 본래의 보드 배열 영향&접근 X
    public Cell[][] getBoardCopy() {
        Cell[][] copy = new Cell[rows][cols];
        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                Cell temp = board[row][col];
                copy[row][col] = new Cell(temp.getMark(), temp.isItem(), temp.getType());
            }
        }
        return copy;
    }
}
