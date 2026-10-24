package seoultech.se.tetris.infra.integration;

import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.GameMode;
import seoultech.se.tetris.infra.dto.ScoreDTO;
import seoultech.se.tetris.infra.dto.SettingsDTO;
import seoultech.se.tetris.infra.persistence.ScoreRepository;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

import java.util.List;
import java.util.Objects;

/**
 * Core 게임과 Infra 설정/스코어보드를 연결하는 게임 세션 서비스.
 * UI는 startGame()으로 Game 객체를 받아 조작하고,
 * 게임 종료 시 canSubmitScore() / saveFinalScore()을 호출한다.
 */
public final class GameSessionService {
    private final SettingsRepository settingsRepository;
    private final ScoreRepository scoreRepository;

    private Game game;
    private GameMode mode;
    private Difficulty difficulty;
    private boolean scoreSubmitted;

    /** 실제 사용자 홈 디렉터리에 설정과 점수를 저장한다. */
    public GameSessionService() {
        this(new SettingsRepository(), new ScoreRepository());
    }

    /** 테스트에서 임시 디렉터리를 지정할 수 있도록 Repository를 주입한다. */
    public GameSessionService(SettingsRepository settingsRepository,
                              ScoreRepository scoreRepository) {
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.scoreRepository = Objects.requireNonNull(scoreRepository, "scoreRepository");
    }

    /** 마지막으로 저장한 난이도와 선택한 게임 모드로 새 게임을 만든다. */
    public Game startGame(GameMode selectedMode) {
        Objects.requireNonNull(selectedMode, "selectedMode");

        SettingsDTO settings = settingsRepository.loadSettings();
        Difficulty selectedDifficulty = settings.getDifficulty();
        int coreDifficulty = switch (selectedDifficulty) {
            case EASY -> Game.EASY;
            case NORMAL -> Game.NORMAL;
            case HARD -> Game.HARD;
        };

        Game newGame = new Game(coreDifficulty, selectedMode == GameMode.ITEM);
        game = newGame;
        mode = selectedMode;
        difficulty = selectedDifficulty;
        scoreSubmitted = false;
        return newGame;
    }

    /** UI에서 이동, 회전, tick, 게임 종료 상태를 확인할 Core 객체. */
    public Game getGame() {
        return game;
    }

    public GameMode getGameMode() {
        return mode;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    /** 종료된 게임의 기록이 아직 저장되지 않았고 TOP 10 대상인지 판단. */
    public boolean canSubmitScore() {
        return game != null
                && game.getIsGameover()
                && !scoreSubmitted
                && scoreRepository.qualifiesForTopTen(game.getScore(), mode, difficulty);
    }

    /**
     * UI가 게임 오버 화면에서 이름을 입력받은 뒤 정확히 한 번 호출한다.
     * 게임 종료 전, TOP 10 탈락, 잘못된 이름, 저장 실패라면 false.
     */
    public boolean saveFinalScore(String playerName) {
        if (!canSubmitScore()) {
            return false;
        }

        boolean saved = scoreRepository.saveScore(playerName, game.getScore(), mode, difficulty);
        if (saved) {
            scoreSubmitted = true;
        }
        return saved;
    }

    /** UI 스코어보드 화면에서 모드/난이도별 TOP 10 조회. */
    public List<ScoreDTO> getTopScores(GameMode selectedMode, Difficulty selectedDifficulty) {
        return scoreRepository.getTopScores(selectedMode, selectedDifficulty);
    }
}
