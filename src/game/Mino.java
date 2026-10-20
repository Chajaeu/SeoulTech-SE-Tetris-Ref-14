package game;

import java.util.Random;

public class Mino {
    // 아이템 타입 상수
    public static final char NO_ITEM = ' ';
    public static final char LINE_CLEAR = 'L';
    public static final char WEIGHT = 'W';
    public static final char COLUMN_CLEAR = 'C';
    public static final char BOMB = 'B';
    public static final char REPLICATION = 'R';

    private final char type;
    private final char itemType;

    private int row;
    private int col;

    private Cell[][] shape;

    // 블럭 생성
    public Mino(char type) {
        this(type, NO_ITEM);
    }

    public Mino(char type, char itemType) {
        this.type = type;
        this.itemType = itemType;
        
        switch (itemType) {
            case NO_ITEM:
                defaultMino();
                break;

            case LINE_CLEAR:
                defaultMino();
                setItemCell();
                break;

            case COLUMN_CLEAR:
                defaultMino();
                setItemCell();
                break;

            case WEIGHT:

                break;

            case BOMB:

                break;

            case REPLICATION:
                shape = new Cell[1][1];
                shape[0][0] = new Cell(itemType, true, itemType);
                row = 0;
                col = 4;
                break;

            default:
                break;
        }
    }

    private void defaultMino() {
        // 타입에 따른 배열크기 결정(회전 고려 정사각형)
            int size = type == 'O' ? 2 : type == 'I' ? 4 : 3;
            shape = emptyShape(size, size);
            switch (type) {
                case 'I':
                    fill(1, 0); fill(1, 1); fill(1, 2); fill(1, 3);
                    break;
                case 'L':
                    fill(0, 2); fill(1, 0); fill(1, 1); fill(1, 2);
                    break;
                case 'J':
                    fill(0, 0); fill(1, 0); fill(1, 1); fill(1, 2);
                    break;
                case 'T':
                    fill(0, 1); fill(1, 0); fill(1, 1); fill(1, 2);
                    break;
                case 'S':
                    fill(0, 1); fill(0, 2); fill(1, 0); fill(1, 1);
                    break;
                case 'Z':
                    fill(0, 0); fill(0, 1); fill(1, 1); fill(1, 2);
                    break;
                case 'O':
                    fill(0, 0); fill(0, 1); fill(1, 0); fill(1, 1);
                    break;
                default:
                    throw new IllegalArgumentException("잘못된 블럭 타입 : " + type);
            }
            // 가능한 한 중앙에 맞게 최초 위치 설정
            row = 0;
            col = type == 'O' ? 4 : 3;
    }

    // 일반 블럭 생성 시, 지정 위치에 실제 칸 채우는 함수
    private void fill(int row, int col) {
        shape[row][col] = new Cell('O', false, type);
    }

    // arg로 받은 높이&너비(행렬) 크기로 배열 생성, 빈 cell로 초기화(2*4 사이즈 무게추 고려)
    private Cell[][] emptyShape(int height, int width) {
        Cell[][] result = new Cell[height][width];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                result[r][c] = new Cell();
            }
        }
        return result;
    }

    private void setItemCell() {
        Random random = new Random();
        int selectNum = random.nextInt(4);
        int i = 0;
        for (int r = 0; r < getSize(); r++) {
            for (int c = 0; c < getSize(); c++) {
                if (shape[r][c].isEmpty()) { 
                    continue;
                }
                
                if (i == selectNum) {
                    shape[r][c] = new Cell(itemType, true, itemType);
                    return;
                }

                i++;
            }
        }

    }


    // getter
    // 블럭 종류
    public char getType() {return type;}
    public char getItemType() {return itemType;}
    // 현위치
    public int getRow() {return row;}
    public int getCol() {return col;}
    // 직사각형(무게추) 블럭 외에는 size 하나만 받아도 충분
    public int getSize() {return getHeight();}
    public int getHeight() {return shape.length;}
    public int getWidth() {return shape[0].length;}
    // 특정 칸 cell 정보
    public Cell getCell(int row, int col) { return shape[row][col]; }
    

    // 블럭 위치 변경
    public void moveLeft() {col--;}
    public void moveRight() {col++;}
    public void moveDown() {row++;}
    public void moveUp() {row--;}     // 회전 시 사용 가능


    // 직사각형(무게추) 블럭은 어차피 회전 X
    public Cell[][] createRotatedCW() {
        int size = getSize();
        Cell[][] rotated = new Cell[size][size];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                rotated[c][size - 1 - r] = shape[r][c];
            }
        }
        return rotated;
    }

    public void setShape(Cell[][] shape) {
        this.shape = shape;
    }
}

