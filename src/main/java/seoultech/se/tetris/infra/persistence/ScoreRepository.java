package seoultech.se.tetris.infra.persistence;

import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.GameMode;
import seoultech.se.tetris.infra.dto.ScoreDTO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

// 점수 저장, 조회 및 스코어보드 초기화 담당
public class ScoreRepository {

    private static final int TOP_LIMIT = 10;

    private final Path directory;
    private final Path scoreFile;

    // 기본 저장 위치
    public ScoreRepository() {
        this(Path.of(
                System.getProperty("user.home"),
                ".se-tetris-team14"));
    }

    // 테스트에서는 임시 디렉터리 사용
    public ScoreRepository(Path directory) {
        this.directory = Objects.requireNonNull(
                directory, "directory");
        this.scoreFile = directory.resolve("scores.properties");
    }

    public Path getScoreFile() {
        return scoreFile;
    }

    // 이름과 점수를 전달받아 저장
    public boolean saveScore(String playerName, int score,
            GameMode gameMode,
            Difficulty difficulty) {

        try {
            return saveScore(new ScoreDTO(
                    playerName, score, gameMode, difficulty));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    // ScoreDTO 객체를 파일에 저장
    public synchronized boolean saveScore(ScoreDTO newScore) {

        if (newScore == null) {
            return false;
        }

        Path temp = null;

        try {
            List<ScoreDTO> scores = readAll();
            scores.add(newScore);

            Files.createDirectories(directory);

            temp = Files.createTempFile(
                    directory, "scores-", ".tmp");

            Properties props = new Properties();

            props.setProperty("version", "1");
            props.setProperty(
                    "count", Integer.toString(scores.size()));

            for (int i = 0; i < scores.size(); i++) {

                ScoreDTO s = scores.get(i);
                String key = "score." + i + ".";

                props.setProperty(key + "id", s.getId());
                props.setProperty(
                        key + "name", s.getPlayerName());
                props.setProperty(
                        key + "points",
                        Integer.toString(s.getScore()));
                props.setProperty(
                        key + "mode", s.getGameMode().name());
                props.setProperty(
                        key + "difficulty",
                        s.getDifficulty().name());
            }

            try (OutputStream output = Files.newOutputStream(temp)) {
                props.store(
                        output, "SE Tetris Team14 Scoreboard");
            }

            try {
                Files.move(
                        temp,
                        scoreFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(
                        temp,
                        scoreFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            return true;

        } catch (IOException | RuntimeException ex) {
            return false;

        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException | SecurityException ignored) {
                    // 임시 파일 정리 실패 시 무시
                }
            }
        }
    }

    // 모드 및 난이도별 TOP 10 조회
    public synchronized List<ScoreDTO> getTopScores(
            GameMode mode, Difficulty difficulty) {

        if (mode == null || difficulty == null) {
            return List.of();
        }

        try {
            return topScores(readAll(), mode, difficulty);
        } catch (IOException | RuntimeException ex) {
            return List.of();
        }
    }

    // 새로운 점수가 TOP 10에 들어갈 수 있는지 확인
    public synchronized boolean qualifiesForTopTen(
            int score, GameMode mode, Difficulty difficulty) {

        if (score < 0 || mode == null || difficulty == null) {
            return false;
        }

        try {
            List<ScoreDTO> top = topScores(readAll(), mode, difficulty);

            return top.size() < TOP_LIMIT ||
                    score > top.get(TOP_LIMIT - 1).getScore();

        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    // 스코어보드 전체 초기화
    public synchronized boolean resetScoreboard() {

        try {
            Files.deleteIfExists(scoreFile);
            return true;
        } catch (IOException | SecurityException ex) {
            return false;
        }
    }

    // 점수 내림차순 정렬 후 최대 10개 반환
    private static List<ScoreDTO> topScores(
            List<ScoreDTO> all,
            GameMode mode,
            Difficulty difficulty) {

        return all.stream()
                .filter(s -> s.getGameMode() == mode &&
                        s.getDifficulty() == difficulty)
                .sorted(
                        Comparator.comparingInt(
                                ScoreDTO::getScore).reversed())
                .limit(TOP_LIMIT)
                .toList();
    }

    // 파일에서 저장된 모든 점수 읽기
    private List<ScoreDTO> readAll() throws IOException {

        if (!Files.exists(scoreFile)) {
            return new ArrayList<>();
        }

        Properties props = new Properties();

        try (InputStream input = Files.newInputStream(scoreFile)) {
            props.load(input);
        }

        try {
            if (!"1".equals(props.getProperty("version"))) {
                throw new IllegalArgumentException(
                        "Unsupported scoreboard version");
            }

            int count = Integer.parseInt(
                    required(props, "count"));

            if (count < 0 || count > 100000) {
                throw new IllegalArgumentException(
                        "Invalid scoreboard count");
            }

            List<ScoreDTO> scores = new ArrayList<>();

            for (int i = 0; i < count; i++) {

                String key = "score." + i + ".";

                scores.add(ScoreDTO.fromStoredRecord(
                        required(props, key + "id"),
                        required(props, key + "name"),
                        Integer.parseInt(
                                required(props, key + "points")),
                        GameMode.valueOf(
                                required(props, key + "mode")),
                        Difficulty.valueOf(
                                required(props, key + "difficulty"))));
            }

            return scores;

        } catch (RuntimeException ex) {
            throw new IOException(
                    "Invalid scoreboard file", ex);
        }
    }

    // 필수 저장값 확인
    private static String required(
            Properties props, String key) {

        String value = props.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing field: " + key);
        }

        return value;
    }
}