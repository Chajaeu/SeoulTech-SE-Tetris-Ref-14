package game;

public final class Cell {
    private final char mark;
    private final boolean item;
    private final char type;


    // 단순 생성&초기화 시 빈칸 형식으로 생성
    public Cell() {
        this(' ', false, ' ');
    }

    public Cell(char type) {
    this('O', false, type);
    }

    public Cell(char mark, boolean item, char type) {
        this.mark = mark;
        this.item = item;
        this.type = type;
    }


    // getter 3종
    public char getMark() {
        return mark;
    }

    public boolean isItem() {
        return item;
    }

    public char getType() {
        return type;
    }


    // 빈칸 여부 확인
    public boolean isEmpty() {
        if (type == ' '){
            return true;
        }
        return false;
    }
}
