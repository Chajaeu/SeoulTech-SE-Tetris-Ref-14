package seoultech.se.tetris.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.GameMode;
import seoultech.se.tetris.infra.dto.ScoreDTO;
import seoultech.se.tetris.infra.dto.SettingsDTO;
import seoultech.se.tetris.infra.integration.GameSessionService;
import seoultech.se.tetris.infra.persistence.ScoreRepository;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionServiceTest {
    @TempDir
    Path tempDir;

    private GameSessionService service() {
        return new GameSessionService(
                new SettingsRepository(tempDir), new ScoreRepository(tempDir));
    }

    @Test
    void startsWithDefaultDifficultyAndNormalMode() {
        GameSessionService session = service();
        Game game = session.startGame(GameMode.NORMAL);

        assertSame(game, session.getGame());
        assertEquals(Game.NORMAL, game.getDifficulty());
        assertFalse(game.getItemMode());
        assertEquals(GameMode.NORMAL, session.getGameMode());
        assertEquals(Difficulty.NORMAL, session.getDifficulty());
        assertFalse(session.canSubmitScore());
    }

    @Test
    void mapsEveryStoredDifficultyToCoreConstant() {
        SettingsRepository settingsRepo = new SettingsRepository(tempDir);
        GameSessionService session = service();

        for (Difficulty difficulty : Difficulty.values()) {
            SettingsDTO settings = SettingsDTO.defaults();
            settings.setDifficulty(difficulty);
            assertTrue(settingsRepo.saveSettings(settings));

            Game game = session.startGame(GameMode.NORMAL);
            int expected = switch (difficulty) {
                case EASY -> Game.EASY;
                case NORMAL -> Game.NORMAL;
                case HARD -> Game.HARD;
            };
            assertEquals(expected, game.getDifficulty());
            assertEquals(difficulty, session.getDifficulty());
        }
    }

    @Test
    void startsItemModeWhenRequested() {
        Game game = service().startGame(GameMode.ITEM);
        assertTrue(game.getItemMode());
    }

    @Test
    void cannotSubmitBeforeGameOver() {
        GameSessionService session = service();
        session.startGame(GameMode.NORMAL);

        assertFalse(session.canSubmitScore());
        assertFalse(session.saveFinalScore("Player"));
        assertTrue(session.getTopScores(GameMode.NORMAL, Difficulty.NORMAL).isEmpty());
    }

    @Test
    void savesGameOverScoreToCorrectModeAndDifficultyExactlyOnce() {
        SettingsDTO settings = SettingsDTO.defaults();
        settings.setDifficulty(Difficulty.HARD);
        assertTrue(new SettingsRepository(tempDir).saveSettings(settings));

        GameSessionService session = service();
        Game game = session.startGame(GameMode.ITEM);
        game.moveDown(); // Core가 실제로 점수를 올리는 동작
        int finalScore = game.getScore();
        assertTrue(finalScore > 0);
        game.gameover(); // 실제 UI에서는 Core가 게임 오버 상태가 된 시점에 호출

        assertTrue(session.canSubmitScore());
        assertTrue(session.saveFinalScore("Player"));
        assertFalse(session.canSubmitScore());
        assertFalse(session.saveFinalScore("Player"));

        List<ScoreDTO> saved = session.getTopScores(GameMode.ITEM, Difficulty.HARD);
        assertEquals(1, saved.size());
        assertEquals(finalScore, saved.get(0).getScore());
        assertEquals("Player", saved.get(0).getPlayerName());
        assertTrue(session.getTopScores(GameMode.NORMAL, Difficulty.HARD).isEmpty());
    }

    @Test
    void topTenFailureDoesNotSave() {
        ScoreRepository repository = new ScoreRepository(tempDir);
        for (int i = 0; i < 10; i++) {
            assertTrue(repository.saveScore("Existing" + i, 1000 + i,
                    GameMode.NORMAL, Difficulty.NORMAL));
        }
        GameSessionService session = service();
        Game game = session.startGame(GameMode.NORMAL);
        game.gameover();

        assertFalse(session.canSubmitScore());
        assertFalse(session.saveFinalScore("OutOfRank"));
        assertEquals(10, repository.getTopScores(GameMode.NORMAL, Difficulty.NORMAL).size());
    }

    @Test
    void invalidNameCanBeRetried() {
        GameSessionService session = service();
        session.startGame(GameMode.NORMAL).gameover();

        assertFalse(session.saveFinalScore("  "));
        assertTrue(session.canSubmitScore());
        assertTrue(session.saveFinalScore("Valid"));
        assertEquals(1, session.getTopScores(GameMode.NORMAL, Difficulty.NORMAL).size());
    }

    @Test
    void newGameResetsSubmissionGuard() {
        GameSessionService session = service();
        session.startGame(GameMode.NORMAL).gameover();
        assertTrue(session.saveFinalScore("First"));

        session.startGame(GameMode.ITEM).gameover();
        assertTrue(session.canSubmitScore());
        assertTrue(session.saveFinalScore("Second"));
        assertEquals(1, session.getTopScores(GameMode.NORMAL, Difficulty.NORMAL).size());
        assertEquals(1, session.getTopScores(GameMode.ITEM, Difficulty.NORMAL).size());
    }

    @Test
    void persistsScoresAcrossRepositoryInstances() {
        GameSessionService session = service();
        session.startGame(GameMode.NORMAL).gameover();
        assertTrue(session.saveFinalScore("Persisted"));

        GameSessionService reopened = service();
        assertEquals("Persisted", reopened.getTopScores(
                GameMode.NORMAL, Difficulty.NORMAL).get(0).getPlayerName());
    }

    @Test
    void rejectsNullMode() {
        GameSessionService session = service();
        assertThrows(NullPointerException.class, () -> session.startGame(null));
    }
}
