package seoultech.se.tetris.infra.network;

import java.util.List;
import java.util.Objects;

/**
 * P2P 화면 표시용 읽기 전용 스냅샷. board는 위에서 아래로 20개 행,
 * 각 행은 10개 ASCII 코드 문자(. = 빈칸, 그 외는 색상/블록/아이템 식별값).
 * UI와 Core에서 실제 Cell의 대응표를 최종 합의해야 한다.
 */
public record GameStateSnapshot(List<String> board, List<String> next,
                                int score, int lines, int level, boolean gameOver) {
    public GameStateSnapshot {
        board = List.copyOf(Objects.requireNonNull(board, "board"));
        next = List.copyOf(Objects.requireNonNull(next, "next"));
        if (board.size() != 20) throw new IllegalArgumentException("board must contain 20 rows");
        if (next.size() > 4) throw new IllegalArgumentException("next preview too large");
        for (String row : board) validateRow(row, 10);
        for (String row : next) validateRow(row, -1);
        if (score < 0 || lines < 0 || level < 1) throw new IllegalArgumentException("negative game values");
    }
    private static void validateRow(String row, int width) {
        if (row == null || (width > 0 && row.length() != width)
                || (width < 0 && (row.isEmpty() || row.length() > 4))) {
            throw new IllegalArgumentException("invalid snapshot row");
        }
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c < 32 || c > 126) throw new IllegalArgumentException("non-ASCII cell");
        }
    }
}
