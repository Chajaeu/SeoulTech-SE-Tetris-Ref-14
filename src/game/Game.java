package game;

import java.util.Random;

public class Game {
    // 상수
    // 보드 크기(20x10 고정)
    public static final int ROWS = 20;
    public static final int COLS = 10;
    // 난이도
    public static final int EASY = 1;
    public static final int NORMAL = 2;
    public static final int HARD = 3;
    // 내부 상수
    // tick 간격
    private static final int INITIAL_TICK_INTERVAL = 1100; //ms
    private static final int MIN_TICK_INTERVAL = 200;


    // 필드
    // Mino 블럭 타입(일반)
    private final char[] minoTypes = {'L', 'J', 'T', 'S', 'Z', 'O', 'I'};
    private Random random;

    // 보드&블럭 관리
    private final Board board;
    private Mino nextMino;
    private Mino currentMino;

    private int tickInterval;

    private int score;
    private int lines;
    private int level;

    private int difficulty;
    private boolean itemMode;

    private boolean isPaused;
    private boolean isGameover;
    private boolean isWaitingDelete;

    private boolean[][] deleteWaitingCells;
    


    public Game(int difficulty, boolean itemMode) {
        board = new Board(ROWS, COLS);
        random = new Random(); // seed 고정 가능
        score = 0;
        lines = 0;
        this.difficulty = difficulty;
        this.itemMode = itemMode;
        updateLevel();
        updateTickInterval();
        isPaused = false;
        isGameover = false;
        isWaitingDelete = false;
        deleteWaitingCells = null;

        nextMino = new Mino(selectMinoType());
        createNewMino();
    }

    // 사용 가능성이 있으므로 유지
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


    private char selectMinoType() {
        int bound = difficulty == EASY ? 72 : difficulty == HARD ? 68 : 70;
        int index = random.nextInt(bound) / 10;

        if (index < 6) {
            return minoTypes[index];
        }
        return minoTypes[6];
    }
    private void createNewMino() {
        currentMino = nextMino;
        nextMino = new Mino(selectMinoType());

        if (!board.canPlace(currentMino, currentMino.getRow(), currentMino.getCol())) {
            gameover();
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

    public int getScore() {
        return score;
    }
    public int getLines() {
        return lines;
    }
    public int getLevel() {
        return level;
    }

    public int getDifficulty() {
        return difficulty;
    }
    public boolean getItemMode() {
        return itemMode;
    }

    public boolean getIsPaused() {
        return isPaused;
    }
    public boolean getIsGameover() {
        return isGameover;
    }
    // UI에서는 getIsWaitingDelete()를 체크하다 true면 (애니처리중에는 확인X)
    // getDeleteWaitingCells() 호출해서 애니메이션 처리 후 
    // deleteCells() 호출
    public boolean getIsWaitingDelete() {
        return isWaitingDelete;
    }
    public boolean[][] getDeleteWaitingCells() {
        if (deleteWaitingCells == null) return null;
        boolean[][] temp = new boolean[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                temp[r][c] = deleteWaitingCells[r][c];
            }
        }
        return temp;
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
            dropScore();
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
            dropScore();
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


        // 아이템 중 고정하면 안되는 종류시 if - s/c 에서 처리 후 return


        board.fixMino(currentMino);
        currentMino = null;
        
        // 삭제 필요 칸 탐색 함수
        searchDeleteCells();
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



    
    private void searchDeleteCells() {
        deleteWaitingCells = new boolean[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                deleteWaitingCells[r][c] = false;
            }
        }

        for (int r = 0; r < ROWS; r++) {
            boolean fullLine = true;
            for (int c = 0; c < COLS; c++) {
                Cell cell = board.getCell(r, c);

                if (cell.isEmpty()) {
                    fullLine = false;
                    continue;
                }

                if (cell.isItem()) {


                    // switch-case로 작동시키기, bomb, lineClear 등 작동


                    isWaitingDelete = true;
                }
            }

            if (fullLine) {
                isWaitingDelete = true;
                for (int c = 0; c < COLS; c++) {
                    deleteWaitingCells[r][c] = true;
                }
            }
        }
        
    }
    // UI에서 애니메이션 이후 호출
    public void deleteCells() {
        if (deleteWaitingCells == null || isWaitingDelete == false) return;

        int tempDeleteLines = 0;
        int tempRows = 0;

        for (int r = ROWS - 1; r >= tempRows; r--) {
            boolean fullLine = true;
            for (int c = 0; c < COLS; c++) {
                if (!deleteWaitingCells[r][c]) {
                    fullLine = false;
                    continue;
                }

                board.setCell(r, c, new Cell());
                
            }

            if (fullLine) {
                for (int i = r; i >= tempRows + 1; i--) {
                    for (int c = 0; c < COLS; c++) {
                        board.setCell(i, c, board.getCell(i-1, c));

                        deleteWaitingCells[i][c] = deleteWaitingCells[i-1][c];

                    }
                }
                for (int c = 0; c < COLS; c++) {
                    board.setCell(tempRows, c, new Cell());
                }

                tempDeleteLines++;
                tempRows++;
                r++;
            }
        }

        // 삭제 줄 수에 따른 처리
        lineScore(tempDeleteLines);
        lines += tempDeleteLines;
        updateLevel();
        updateTickInterval();

        deleteWaitingCells = null;
        isWaitingDelete = false;
    }
    

    // 삭제된 줄 수에 따른 레벨 기준(5 15 30 50 ...)
    private void updateLevel() {
        int t = 0;
        int i = 0;
        do {
            t += (i+1) * 5;
            i++;
        } while(t <= lines);

        level = i;
    }

    private void updateTickInterval() {
        int decr = difficulty == EASY ? 80 : difficulty == HARD ? 120 : 100;

        tickInterval = Math.max(INITIAL_TICK_INTERVAL - decr * level, MIN_TICK_INTERVAL);
    }



    private void dropScore() {
        score += level * difficulty;
    }
    private void lineScore(int lines) {
        if (lines <= 0) return; // 줄수 0 혹은 음수(잘못된값)
        else if (lines == 1) score += 100;
        else if (lines == 2) score += 300;
        else if (lines == 3) score += 600;
        else score += 1000;     // 줄수 4 이상
    }


    public void pause() {
        isPaused = true;
    }

    public void resume() {
        isPaused = false;
    }

    public void gameover() {
        isGameover = true;
    }
}
