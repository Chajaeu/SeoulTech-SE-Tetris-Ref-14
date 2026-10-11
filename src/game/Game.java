package game;

import java.util.Random;

public class Game {
    // 보드 크기 상수(20x10 고정)
    public static final int ROWS = 20;
    public static final int COLS = 10;

    // Mino 블럭 타입(일반)
    private final char[] minoTypes = {'I', 'L', 'J', 'T', 'S', 'Z', 'O'};
    private Random random;

    // 보드&블럭 관리
    private final Board board;
    private Mino nextMino;
    private Mino currentMino;


    public Game() {
        board = new Board(ROWS, COLS);
        random = new Random(); // seed 고정 가능

        nextMino = new Mino(minoTypes[random.nextInt(minoTypes.length)]);
        createNewMino();
    }


    public void startGame() {
        if (currentMino == null) {
            createNewMino();
        }
    }
    

    private void createNewMino() {
        currentMino = nextMino;
        nextMino = new Mino(minoTypes[random.nextInt(minoTypes.length)]);

        if (!board.canPlace(currentMino, currentMino.getRow(), currentMino.getCol())) {
            // 게임오버 판정
        }
    }



    // getter
    public Cell[][] getDisplayBoard() {
        Cell[][] tempBoard = board.getBoardCopy();

        if (currentMino == null) {
            return tempBoard;
        }

        for (int r = 0; r < currentMino.getHeight(); r++) {
            for (int c = 0; c < currentMino.getWidth(); c++) {
                Cell cell = currentMino.getCell(r, c);
                if (!cell.isEmpty()) {
                    tempBoard[currentMino.getRow() + r][currentMino.getCol() + c] = cell;
                }
            }
        }
        return tempBoard;
    }

    public Cell[][] getNextMino() {
        Cell[][] tempMino = new Cell[nextMino.getHeight()][nextMino.getWidth()];
        for (int r = 0; r < nextMino.getHeight(); r++) {
            for (int c = 0; c < nextMino.getWidth(); c++) {
                tempMino[r][c] = nextMino.getCell(r, c);
            }
        }
        return tempMino;
    }



    // moveCheck&move
    public void moveLeft() {
        if (board.canPlace(currentMino, currentMino.getRow(), currentMino.getCol() - 1)) {
            currentMino.moveLeft();
        }
    }

    public void moveRight() {
        if (board.canPlace(currentMino, currentMino.getRow(), currentMino.getCol() + 1)) {
            currentMino.moveRight();
        }
    }

    public void moveDown() {
        if (board.canPlace(currentMino, currentMino.getRow() + 1, currentMino.getCol())) {
            currentMino.moveDown();
        }
        else {
            // 블럭 고정
        }
    }
   

    // rotateCheck&rotate
    public void rotateCW() {
        Cell[][] rotated = currentMino.createRotatedCW();
        int row = currentMino.getRow();
        int col = currentMino.getCol();
        if (board.canPlace(rotated, row, col)) {
            currentMino.setShape(rotated);
        } 
        else if (board.canPlace(rotated, row - 1, col)) {
            currentMino.setShape(rotated);
            currentMino.moveUp();
        }
    }
}
