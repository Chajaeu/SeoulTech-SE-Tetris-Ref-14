package game;

import java.util.Random;

public class Game {
    // 상수
    // 보드 크기 상수(20x10 고정)
    public static final int ROWS = 20;
    public static final int COLS = 10;

    private static final int INITIAL_TICK_INTERVAL = 1000; //ms
    private static final int MIN_TICK_INTERVAL = 200;


    // 필드
    // Mino 블럭 타입(일반)
    private final char[] minoTypes = {'I', 'L', 'J', 'T', 'S', 'Z', 'O'};
    private Random random;

    // 보드&블럭 관리
    private final Board board;
    private Mino nextMino;
    private Mino currentMino;

    private int tickInterval;

    private int score;

    private boolean isPaused;
    private boolean isGameover;
    private boolean isWaitingDelete;


    public Game() {
        board = new Board(ROWS, COLS);
        random = new Random(); // seed 고정 가능
        tickInterval = INITIAL_TICK_INTERVAL;
        score = 0;
        isPaused = false;
        isGameover = false;
        isWaitingDelete = false;

        nextMino = new Mino(minoTypes[random.nextInt(minoTypes.length)]);
        createNewMino();
    }


    public void startGame() {
        if (currentMino == null) {
            createNewMino();
        }
    }
    
    // if (!checkCanOperate()) return;
    //  if (!checkCanOperate() || currentMino == null) return;
    public boolean checkCanOperate() {
        if (!isPaused && !isGameover && !isWaitingDelete) {
            return true;
        }
        return false;
    }


    private void createNewMino() {
        currentMino = nextMino;
        nextMino = new Mino(minoTypes[random.nextInt(minoTypes.length)]);

        if (!board.canPlace(currentMino, currentMino.getRow(), currentMino.getCol())) {
            // 게임오버 판정
        }
    }



    // getter
    // 화면 표시용
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
    // 드랍 틱 간격(줄 삭제 시에만 한번씩 확인하면 됨)
    public int getTickInterval() {
        return tickInterval;
    }


    // moveCheck&move
    public void moveLeft() {
        if (!checkCanOperate() || currentMino == null) return;

        if (board.canPlace(currentMino, currentMino.getRow(), currentMino.getCol() - 1)) {
            currentMino.moveLeft();
        }
    }

    public void moveRight() {
        if (!checkCanOperate() || currentMino == null) return;

        if (board.canPlace(currentMino, currentMino.getRow(), currentMino.getCol() + 1)) {
            currentMino.moveRight();
        }
    }

    // softdrop(수동낙하)
    public void moveDown() {    
        if (!checkCanOperate() || currentMino == null) return;

        if (board.canPlace(currentMino, currentMino.getRow() + 1, currentMino.getCol())) {
            currentMino.moveDown();
            score++;
        }
        else {  // 아래로 가야하는데 못가면 고정
            placeMino();
        }
    }

    // harddrop(즉시고정)
    public void harddrop() {
        if (!checkCanOperate() || currentMino == null) return;

        while(board.canPlace(currentMino, currentMino.getRow() + 1, currentMino.getCol())){
            currentMino.moveDown();
            score++;
        }
        placeMino();
    }

    // 자동낙하(낙하 틱 당 행동이 추가될 경우를 대비)
    public void tick() {
        if (!checkCanOperate()) return;

        if (currentMino == null) {
            createNewMino();
            return;
        }
        moveDown();
    }

    // 현재 블럭 고정
    private void placeMino() {
        board.fixMino(currentMino);
        currentMino = null;
        
        // 삭제 필요 칸 탐색 함수
        // (-> 삭제 필요 칸 존재시 UI에 전달, isWaitingDelete = true;
        // -> UI에서 삭제 함수 실행, isWaitingDelete = false; )
    }


    // rotateCheck&rotate
    public void rotateCW() {
        if (!checkCanOperate() || currentMino == null) return;

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
