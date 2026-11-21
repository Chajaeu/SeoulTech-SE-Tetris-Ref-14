package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.infra.network.*;

import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.game.Mino;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 기존 Game/Cell의 화면 정보를 P2P 읽기 전용 DTO로 복사한다.
 * Game은 mutable이므로 {@link #capture(Game)}는 반드시 Game을 조작하는 스레드에서 호출한다.
 * 네트워크 스레드에서는 이 불변 결과만 읽어야 한다.
 *
 * wire code: '.' 비어 있음, I/J/L/O/S/T/Z 일반 블록, l/w/c/b/r 아이템,
 * '#' 회색 공격 블록(전송용 예약 코드). 상대 UI의 색상 매핑은 별도 연동 필요.
 */
public final class GameSnapshotAdapter {
    private GameSnapshotAdapter() {}

    public static GameStateSnapshot capture(Game game) {
        Objects.requireNonNull(game, "game");
        return capture(game.getDisplayBoard(), game.getNextMino(),
                game.getScore(), game.getLines(), game.getLevel(), game.getIsGameover());
    }

    /** Core의 차후 API 변경 시 이 메서드만 사용하면 Game 직접 의존을 피할 수 있다. */
    public static GameStateSnapshot capture(Cell[][] display, Cell[][] preview,
                                             int score, int lines, int level, boolean gameOver) {
        return new GameStateSnapshot(encodeRows(display, 20, 10),
                encodeRows(preview, -1, -1), score, lines, level, gameOver);
    }

    private static List<String> encodeRows(Cell[][] cells, int height, int width) {
        Objects.requireNonNull(cells, "cells");
        if (height >= 0 && cells.length != height)
            throw new IllegalArgumentException("Expected " + height + " board rows");
        if (height < 0 && cells.length > 4)
            throw new IllegalArgumentException("Preview must have at most 4 rows");
        List<String> rows = new ArrayList<>(cells.length);
        for (Cell[] source : cells) {
            if (source == null || (width >= 0 && source.length != width)
                    || (width < 0 && (source.length < 1 || source.length > 4)))
                throw new IllegalArgumentException("Invalid Cell row width");
            StringBuilder row = new StringBuilder(source.length);
            for (Cell cell : source) row.append(encodeCell(cell));
            rows.add(row.toString());
        }
        return rows;
    }

    public static char encodeCell(Cell cell) {
        if (cell == null || cell.isEmpty()) return '.';
        char type = cell.getType();
        if (cell.isItem()) {
            return switch (type) {
                case Mino.LINE_CLEAR -> 'l';
                case Mino.WEIGHT -> 'w';
                case Mino.COLUMN_CLEAR -> 'c';
                case Mino.BOMB -> 'b';
                case Mino.REPLICATION -> 'r';
                default -> throw new IllegalArgumentException("Unknown item cell type: " + type);
            };
        }
        if (type == 'G' || type == '#') return '#';
        if ("IJLOSTZ".indexOf(type) >= 0) return type;
        throw new IllegalArgumentException("Unknown normal cell type: " + type);
    }

    /** 네트워크 스냅샷을 UI 렌더러에서 사용할 Cell 데이터로 복원. 기존 Game은 변경하지 않는다. */
    public static Cell[][] decodeBoard(GameStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return decodeRows(snapshot.board());
    }

    public static Cell[][] decodeNext(GameStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return decodeRows(snapshot.next());
    }

    private static Cell[][] decodeRows(List<String> rows) {
        Cell[][] result = new Cell[rows.size()][];
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r);
            result[r] = new Cell[row.length()];
            for (int c = 0; c < row.length(); c++) result[r][c] = decodeCell(row.charAt(c));
        }
        return result;
    }

    public static Cell decodeCell(char code) {
        return switch (code) {
            case '.' -> new Cell();
            case '#' -> new Cell('G', false, 'G');
            case 'l' -> new Cell('L', true, Mino.LINE_CLEAR);
            case 'w' -> new Cell('W', true, Mino.WEIGHT);
            case 'c' -> new Cell('C', true, Mino.COLUMN_CLEAR);
            case 'b' -> new Cell('B', true, Mino.BOMB);
            case 'r' -> new Cell('R', true, Mino.REPLICATION);
            default -> {
                if ("IJLOSTZ".indexOf(code) < 0)
                    throw new IllegalArgumentException("Unknown wire cell code: " + code);
                yield new Cell(code);
            }
        };
    }
}
