package seoultech.se.tetris.infra.network;

import java.util.List;
import java.util.Objects;

/**
 * Immutable state for drawing the remote player. Core Game objects must never be shared
 * with the network receiver thread. Rows are top-to-bottom, columns left-to-right.
 * A full board is 20x10; pending attack display is 10x10; deletion mask is 20x10.
 */
public record GameStateSnapshot(List<String> board, List<String> next,
                                int score, int lines, int level, boolean gameOver,
                                List<String> pendingAttacks, boolean paused,
                                boolean waitingDelete, List<String> deleteWaitingCells) {
    /** Preserves source compatibility with the earlier, six-field networking API. */
    public GameStateSnapshot(List<String> board, List<String> next,
                             int score, int lines, int level, boolean gameOver) {
        this(board, next, score, lines, level, gameOver, List.of(), false, false, List.of());
    }

    public GameStateSnapshot {
        board = List.copyOf(Objects.requireNonNull(board, "board"));
        next = List.copyOf(Objects.requireNonNull(next, "next"));
        pendingAttacks = List.copyOf(Objects.requireNonNull(pendingAttacks, "pendingAttacks"));
        deleteWaitingCells = List.copyOf(Objects.requireNonNull(deleteWaitingCells, "deleteWaitingCells"));
        if (board.size() != 20) throw new IllegalArgumentException("board must contain 20 rows");
        if (next.size() > 4) throw new IllegalArgumentException("next preview too large");
        for (String row : board) validateRow(row, 10);
        for (String row : next) validateRow(row, -1);
        if (score < 0 || lines < 0 || level < 1) throw new IllegalArgumentException("negative game values");
        if (!pendingAttacks.isEmpty() && pendingAttacks.size() != 10)
            throw new IllegalArgumentException("pendingAttacks must have 10 rows when provided");
        for (String row : pendingAttacks) validateMaskRow(row, 10);
        if (waitingDelete != (deleteWaitingCells.size() == 20))
            throw new IllegalArgumentException("deleteWaitingCells must be 20 rows iff waitingDelete");
        for (String row : deleteWaitingCells) validateMaskRow(row, 10);
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

    private static void validateMaskRow(String row, int width) {
        if (row == null || row.length() != width || !row.matches("[.#]{10}"))
            throw new IllegalArgumentException("invalid mask row");
    }
}
