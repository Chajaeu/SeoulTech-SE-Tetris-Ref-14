package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.game.Board;
import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.network.AttackPatternDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bridges the actual Core signatures Board attack() / attacked(Board).
 * The previously shared Core document describes boolean[][], but the checked-in
 * source uses Board; true == ATTACK_CELL and false == empty cell.
 */
public final class CoreAttackAdapter {
    private CoreAttackAdapter() {}

    /** Core's empty 0-row Board means "no attack"; do not send a network event. */
    public static Optional<AttackPatternDTO> fromBoard(Board attack) {
        Objects.requireNonNull(attack, "attack");
        int count = attack.getRows();
        if (count == 0) return Optional.empty();
        if (count > 10 || attack.getCols() != Game.COLS)
            throw new IllegalArgumentException("Invalid Core attack board size");
        List<String> rows = new ArrayList<>(count);
        for (int r = 0; r < count; r++) {
            StringBuilder row = new StringBuilder(Game.COLS);
            for (int c = 0; c < Game.COLS; c++) {
                Cell cell = attack.getCell(r, c);
                if (cell == null) throw new IllegalArgumentException("Null attack Cell");
                if (cell.isEmpty()) row.append('.');
                else if (cell.getType() == Game.ATTACK_CELL) row.append('#');
                else throw new IllegalArgumentException("Non-attack cell in Core attack Board");
            }
            rows.add(row.toString());
        }
        return Optional.of(new AttackPatternDTO(rows));
    }

    /** Incoming network attack -> Core Board for Game.attacked(Board). */
    public static Board toBoard(AttackPatternDTO attack) {
        Objects.requireNonNull(attack, "attack");
        Board board = new Board(attack.rows().size(), Game.COLS);
        for (int r = 0; r < attack.rows().size(); r++) {
            String row = attack.rows().get(r);
            for (int c = 0; c < Game.COLS; c++) {
                if (row.charAt(c) == '#') board.setCell(r, c, new Cell(Game.ATTACK_CELL));
            }
        }
        return board;
    }
}
