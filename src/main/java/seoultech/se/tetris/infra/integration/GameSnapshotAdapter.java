package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.infra.network.GameStateSnapshot;

import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.game.Mino;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts Core's live Game into an immutable transport state. Must be called
 * from the game-update thread, never a TCP receiver or scheduled publisher thread.
 * '.' empty; I/J/L/O/S/T/Z regular; lowercase l/w/c/b/r item; '#' gray ATTACK_CELL.
 */
public final class GameSnapshotAdapter {
    private GameSnapshotAdapter() {}

    public static GameStateSnapshot capture(Game game) {
        Objects.requireNonNull(game, "game");
        return new GameStateSnapshot(encodeRows(game.getDisplayBoard(), Game.ROWS, Game.COLS),
                encodeRows(game.getNextMino(), -1, -1),
                game.getScore(), game.getLines(), game.getLevel(), game.getIsGameover(),
                encodeAttackDisplay(game.getAttackedLines()), game.getIsPaused(),
                game.getIsWaitingDelete(), encodeDeletionMask(game.getDeleteWaitingCells()));
    }

    /** Kept for existing callers that provide only the original six state fields. */
    public static GameStateSnapshot capture(Cell[][] display, Cell[][] preview,
                                             int score, int lines, int level, boolean gameOver) {
        return new GameStateSnapshot(encodeRows(display, Game.ROWS, Game.COLS),
                encodeRows(preview, -1, -1), score, lines, level, gameOver);
    }

    private static List<String> encodeAttackDisplay(Cell[][] cells) {
        Objects.requireNonNull(cells, "attackedLines");
        if (cells.length != 10) throw new IllegalArgumentException("Attack display must have 10 rows");
        List<String> rows = new ArrayList<>(10);
        for (Cell[] cellsRow : cells) {
            if (cellsRow == null || cellsRow.length != Game.COLS)
                throw new IllegalArgumentException("Invalid attack display width");
            StringBuilder row = new StringBuilder(Game.COLS);
            for (Cell cell : cellsRow) {
                if (cell == null) throw new IllegalArgumentException("Null attack display cell");
                if (cell.isEmpty()) row.append('.');
                else if (cell.getType() == Game.ATTACK_CELL) row.append('#');
                else throw new IllegalArgumentException("Unexpected attack display cell");
            }
            rows.add(row.toString());
        }
        return rows;
    }

    private static List<String> encodeDeletionMask(boolean[][] mask) {
        if (mask == null) return List.of();
        if (mask.length != Game.ROWS) throw new IllegalArgumentException("Delete mask height");
        List<String> rows = new ArrayList<>(Game.ROWS);
        for (boolean[] line : mask) {
            if (line == null || line.length != Game.COLS)
                throw new IllegalArgumentException("Delete mask width");
            StringBuilder row = new StringBuilder(Game.COLS);
            for (boolean flag : line) row.append(flag ? '#' : '.');
            rows.add(row.toString());
        }
        return rows;
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
        if (type == Game.ATTACK_CELL) return '#';
        if (type == 'G' || type == '#') return 'g'; // legacy mock gray cell, not Core's 'A'
        if ("IJLOSTZ".indexOf(type) >= 0) return type;
        throw new IllegalArgumentException("Unknown normal cell type: " + type);
    }

    public static Cell[][] decodeBoard(GameStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return decodeRows(snapshot.board());
    }
    public static Cell[][] decodeNext(GameStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return decodeRows(snapshot.next());
    }
    public static Cell[][] decodePendingAttacks(GameStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.pendingAttacks().isEmpty()) return emptyCells(10, Game.COLS);
        return decodeRows(snapshot.pendingAttacks());
    }
    public static boolean[][] decodeDeleteWaitingCells(GameStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.waitingDelete()) return null;
        boolean[][] mask = new boolean[Game.ROWS][Game.COLS];
        for (int r = 0; r < Game.ROWS; r++) {
            for (int c = 0; c < Game.COLS; c++)
                mask[r][c] = snapshot.deleteWaitingCells().get(r).charAt(c) == '#';
        }
        return mask;
    }

    private static Cell[][] emptyCells(int height, int width) {
        Cell[][] result = new Cell[height][width];
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++) result[r][c] = new Cell();
        return result;
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
            case '#' -> new Cell(Game.ATTACK_CELL);
            case 'g' -> new Cell('G', false, 'G');
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
