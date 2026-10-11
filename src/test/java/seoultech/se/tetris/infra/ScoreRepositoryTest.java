package seoultech.se.tetris.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.GameMode;
import seoultech.se.tetris.infra.dto.ScoreDTO;
import seoultech.se.tetris.infra.persistence.ScoreRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoreRepositoryTest {

    @TempDir
    Path tempDir;

    // 기록이 없으면 빈 리스트 반환
    @Test
    void emptyScoreboard() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        assertTrue(repo.getTopScores(
            GameMode.NORMAL, Difficulty.NORMAL
        ).isEmpty());
    }

    // 저장한 점수가 재실행 후에도 유지되는지 검사
    @Test
    void saveAndReloadScore() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        ScoreDTO score = new ScoreDTO(
            "김철수", 500,
            GameMode.NORMAL, Difficulty.EASY
        );

        assertTrue(repo.saveScore(score));

        ScoreRepository newRepo =
            new ScoreRepository(tempDir);

        List<ScoreDTO> result = newRepo.getTopScores(
            GameMode.NORMAL, Difficulty.EASY
        );

        assertEquals(1, result.size());
        assertEquals("김철수", result.get(0).getPlayerName());
        assertEquals(500, result.get(0).getScore());
        assertEquals(score.getId(), result.get(0).getId());
    }

    // 점수 내림차순 정렬 및 TOP 10 제한
    @Test
    void sortScoresAndLimitToTen() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        for (int i = 0; i < 12; i++) {
            assertTrue(repo.saveScore(
                "Player" + i, 100 + i,
                GameMode.NORMAL, Difficulty.NORMAL
            ));
        }

        List<ScoreDTO> top = repo.getTopScores(
            GameMode.NORMAL, Difficulty.NORMAL
        );

        assertEquals(10, top.size());
        assertEquals(111, top.get(0).getScore());
        assertEquals(102, top.get(9).getScore());
    }

    // 일반 모드와 아이템 모드 구분
    @Test
    void separateGameModes() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        assertTrue(repo.saveScore(
            "Normal", 100,
            GameMode.NORMAL, Difficulty.NORMAL
        ));

        assertTrue(repo.saveScore(
            "Item", 200,
            GameMode.ITEM, Difficulty.NORMAL
        ));

        assertEquals(1, repo.getTopScores(
            GameMode.NORMAL, Difficulty.NORMAL
        ).size());

        assertEquals(1, repo.getTopScores(
            GameMode.ITEM, Difficulty.NORMAL
        ).size());
    }

    // 난이도별 기록 구분
    @Test
    void separateDifficulties() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        for (Difficulty difficulty : Difficulty.values()) {
            assertTrue(repo.saveScore(
                difficulty.name(), 100,
                GameMode.NORMAL, difficulty
            ));
        }

        for (Difficulty difficulty : Difficulty.values()) {
            List<ScoreDTO> result = repo.getTopScores(
                GameMode.NORMAL, difficulty
            );

            assertEquals(1, result.size());
            assertEquals(
                difficulty.name(),
                result.get(0).getPlayerName()
            );
        }
    }

    // TOP 10 진입 여부 검사
    @Test
    void qualifyForTopTen() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        assertTrue(repo.qualifiesForTopTen(
            0, GameMode.NORMAL, Difficulty.NORMAL
        ));

        for (int i = 0; i < 10; i++) {
            assertTrue(repo.saveScore(
                "Player" + i, 100 + i,
                GameMode.NORMAL, Difficulty.NORMAL
            ));
        }

        assertFalse(repo.qualifiesForTopTen(
            99, GameMode.NORMAL, Difficulty.NORMAL
        ));

        // 10위 동점은 기존 기록 우선
        assertFalse(repo.qualifiesForTopTen(
            100, GameMode.NORMAL, Difficulty.NORMAL
        ));

        assertTrue(repo.qualifiesForTopTen(
            101, GameMode.NORMAL, Difficulty.NORMAL
        ));
    }

    // 초기화하면 기록이 없어지는지 검사
    @Test
    void resetScoreboard() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        assertTrue(repo.saveScore(
            "Player", 300,
            GameMode.ITEM, Difficulty.HARD
        ));

        assertTrue(repo.resetScoreboard());

        assertTrue(repo.getTopScores(
            GameMode.ITEM, Difficulty.HARD
        ).isEmpty());

        assertFalse(Files.exists(repo.getScoreFile()));
    }

    // 잘못된 사용자 이름과 점수 저장 방지
    @Test
    void rejectInvalidScores() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        assertFalse(repo.saveScore(
            "", 100, GameMode.NORMAL, Difficulty.NORMAL
        ));

        assertFalse(repo.saveScore(
            "Player", -1, GameMode.NORMAL, Difficulty.NORMAL
        ));

        assertFalse(repo.saveScore(
            "Player", 100, null, Difficulty.NORMAL
        ));

        assertFalse(repo.saveScore((ScoreDTO) null));

        assertFalse(Files.exists(repo.getScoreFile()));
    }

    // 손상된 파일을 새 기록으로 덮어쓰지 않는지 검사
    @Test
    void handleCorruptedScoreFile() throws IOException {
        ScoreRepository repo = new ScoreRepository(tempDir);

        Files.writeString(
            repo.getScoreFile(),
            "version=1\ncount=INVALID\n"
        );

        assertTrue(repo.getTopScores(
            GameMode.NORMAL, Difficulty.NORMAL
        ).isEmpty());

        assertFalse(repo.saveScore(
            "Player", 100,
            GameMode.NORMAL, Difficulty.NORMAL
        ));

        assertTrue(Files.readString(
            repo.getScoreFile()
        ).contains("INVALID"));
    }

    // 특수문자가 포함된 이름도 정상 저장되는지 검사
    @Test
    void saveSpecialCharacterName() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        assertTrue(repo.saveScore(
            "한=글:#", 777,
            GameMode.ITEM, Difficulty.HARD
        ));

        ScoreRepository newRepo =
            new ScoreRepository(tempDir);

        assertEquals(
            "한=글:#",
            newRepo.getTopScores(
                GameMode.ITEM, Difficulty.HARD
            ).get(0).getPlayerName()
        );
    }

    // 동점은 먼저 저장된 기록을 우선
    @Test
    void keepOriginalOrderForTies() {
        ScoreRepository repo = new ScoreRepository(tempDir);

        assertTrue(repo.saveScore(
            "First", 200,
            GameMode.NORMAL, Difficulty.NORMAL
        ));

        assertTrue(repo.saveScore(
            "Second", 200,
            GameMode.NORMAL, Difficulty.NORMAL
        ));

        List<ScoreDTO> top = repo.getTopScores(
            GameMode.NORMAL, Difficulty.NORMAL
        );

        assertEquals("First", top.get(0).getPlayerName());
        assertEquals("Second", top.get(1).getPlayerName());
    }
}