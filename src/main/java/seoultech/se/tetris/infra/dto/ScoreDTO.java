package seoultech.se.tetris.infra.dto;

import java.util.UUID;

// 스코어보드에 저장할 점수 정보
public final class ScoreDTO {

    private final String id;
    private final String playerName;
    private final int score;
    private final GameMode gameMode;
    private final Difficulty difficulty;

    public ScoreDTO(String playerName, int score,
                    GameMode gameMode, Difficulty difficulty) {

        this(UUID.randomUUID().toString(),
             playerName, score, gameMode, difficulty);
    }

    private ScoreDTO(String id, String playerName, int score,
                     GameMode gameMode, Difficulty difficulty) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Invalid score id");
        }

        UUID.fromString(id);

        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException(
                "Player name is required"
            );
        }

        String name = playerName.trim();

        if (name.length() > 20 ||
            name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                "Player name must be 1-20 characters"
            );
        }

        if (score < 0 || gameMode == null || difficulty == null) {
            throw new IllegalArgumentException(
                "Invalid score, mode or difficulty"
            );
        }

        this.id = id;
        this.playerName = name;
        this.score = score;
        this.gameMode = gameMode;
        this.difficulty = difficulty;
    }

    // 저장된 파일의 기록을 다시 객체로 변환
    public static ScoreDTO fromStoredRecord(
            String id,
            String playerName,
            int score,
            GameMode gameMode,
            Difficulty difficulty) {

        return new ScoreDTO(
            id, playerName, score, gameMode, difficulty
        );
    }

    public String getId() {
        return id;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getScore() {
        return score;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }
}