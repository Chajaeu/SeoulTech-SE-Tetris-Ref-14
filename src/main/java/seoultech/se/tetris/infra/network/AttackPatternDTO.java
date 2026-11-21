package seoultech.se.tetris.infra.network;

import java.util.List;
import java.util.Objects;

/** 상대에게 전송할 공격 줄. '#'=공격 블록(회색), '.'=삭제 블록 모양을 남긴 빈칸. */
public record AttackPatternDTO(List<String> rows) {
    public AttackPatternDTO {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (rows.isEmpty() || rows.size() > 10) throw new IllegalArgumentException("Attack must contain 1-10 rows");
        for (String row : rows) {
            if (row == null || row.length() != 10 || !row.matches("[.#]{10}")) {
                throw new IllegalArgumentException("Invalid attack pattern row");
            }
        }
    }
}
