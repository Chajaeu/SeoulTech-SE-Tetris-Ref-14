package game;

public final class Cell {
    // 표시 모양 / 아이템 여부 / 종류
    private final char mark;
    private final boolean item;
    private final char type;


    // 단순 생성&초기화 시 빈칸 형식으로 생성
    public Cell() {
        this(' ', false, ' ');
    }

    // 일반 블럭 생성
    public Cell(char type) {
    this('O', false, type);
    }

    // 아이템 여부 포함
    public Cell(char mark, boolean item, char type) {
        this.mark = mark;
        this.item = item;
        this.type = type;
    }


    // getter 3종
    public char getMark() {return mark;}
    public boolean isItem() {return item;}
    public char getType() {return type;}


    // 빈칸 여부 확인
    public boolean isEmpty() {
        if (type == ' '){
            return true;
        }
        return false;
    }
}
