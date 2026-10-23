package seoultech.se.tetris.infra.persistence;

import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.DisplaySize;
import seoultech.se.tetris.infra.dto.SettingsDTO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

// 설정 파일 저장, 불러오기, 초기화 담당
public class SettingsRepository {

    private final Path directory;
    private final Path settingsFile;

    // 기본 저장 위치
    public SettingsRepository() {
        this(Path.of(
                System.getProperty("user.home"),
                ".se-tetris-team14"));
    }

    // 테스트용 저장 경로 지정
    public SettingsRepository(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.settingsFile = directory.resolve("settings.properties");
    }

    // 설정값 저장
    public boolean saveSettings(SettingsDTO settings) {

        if (settings == null || !settings.isValid()) {
            return false;
        }

        Path temp = null;

        try {
            Files.createDirectories(directory);

            temp = Files.createTempFile(
                    directory, "settings-", ".tmp");

            Properties props = toProperties(settings);

            try (OutputStream output = Files.newOutputStream(temp)) {
                props.store(output, "SE Tetris Team14 Settings");
            }

            try {
                Files.move(
                        temp,
                        settingsFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(
                        temp,
                        settingsFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            return true;

        } catch (IOException | SecurityException ex) {
            return false;

        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException | SecurityException ignored) {
                    // 임시 파일 삭제 실패 시 무시
                }
            }
        }
    }

    // 저장된 설정값 불러오기
    public SettingsDTO loadSettings() {

        try (InputStream input = Files.newInputStream(settingsFile)) {

            Properties props = new Properties();
            props.load(input);

            SettingsDTO settings = fromProperties(props);

            if (!settings.isValid()) {
                return SettingsDTO.defaults();
            }

            return settings;

        } catch (IOException | RuntimeException ex) {
            return SettingsDTO.defaults();
        }
    }

    // 기본 설정으로 초기화
    public boolean resetSettings() {
        return saveSettings(SettingsDTO.defaults());
    }

    public Path getSettingsFile() {
        return settingsFile;
    }

    // SettingsDTO를 파일에 저장할 형태로 변환
    private static Properties toProperties(SettingsDTO settings) {

        Properties props = new Properties();

        props.setProperty(
                "displaySize",
                settings.getDisplaySize().name());

        props.setProperty(
                "difficulty",
                settings.getDifficulty().name());

        props.setProperty("keyLeft", settings.getKeyLeft());
        props.setProperty("keyRight", settings.getKeyRight());
        props.setProperty("keyDown", settings.getKeyDown());
        props.setProperty("keyRotate", settings.getKeyRotate());
        props.setProperty("keyDrop", settings.getKeyDrop());
        props.setProperty("keyPause", settings.getKeyPause());

        props.setProperty(
                "colorblindMode",
                Boolean.toString(settings.isColorblindMode()));

        return props;
    }

    // Properties 데이터를 SettingsDTO로 변환
    private static SettingsDTO fromProperties(Properties props) {

        SettingsDTO settings = new SettingsDTO();

        settings.setDisplaySize(
                DisplaySize.valueOf(required(props, "displaySize")));

        // 기존 설정 파일에 난이도가 없으면 NORMAL 사용
        String difficulty = props.getProperty(
                "difficulty", "NORMAL").trim();

        settings.setDifficulty(
                Difficulty.valueOf(difficulty));

        settings.setKeyLeft(
                parseKey(required(props, "keyLeft")));

        settings.setKeyRight(
                parseKey(required(props, "keyRight")));

        settings.setKeyDown(
                parseKey(required(props, "keyDown")));

        settings.setKeyRotate(
                parseKey(required(props, "keyRotate")));

        settings.setKeyDrop(
                parseKey(required(props, "keyDrop")));

        settings.setKeyPause(
                parseKey(required(props, "keyPause")));

        String colorblind = required(props, "colorblindMode");

        if (!"true".equals(colorblind) && !"false".equals(colorblind)) {
            throw new IllegalArgumentException(
                    "Invalid colorblindMode");
        }

        settings.setColorblindMode(
                Boolean.parseBoolean(colorblind));

        return settings;
    }

    // 기존 Swing 정수형 키 코드를 문자열 키로 변환
    private static String parseKey(String value) {

        if (!value.matches("[0-9]+")) {
            return value;
        }

        int code = Integer.parseInt(value);

        return switch (code) {
            case 37 -> "LEFT";
            case 38 -> "UP";
            case 39 -> "RIGHT";
            case 40 -> "DOWN";
            case 32 -> "SPACE";
            case 10 -> "ENTER";
            case 9 -> "TAB";
            case 8 -> "BACK_SPACE";

            default -> {
                if (code >= 65 && code <= 90) {
                    yield Character.toString((char) code);
                }

                if (code >= 48 && code <= 57) {
                    yield "DIGIT" + (code - 48);
                }

                if (code >= 96 && code <= 105) {
                    yield "NUMPAD" + (code - 96);
                }

                if (code >= 112 && code <= 123) {
                    yield "F" + (code - 111);
                }

                throw new IllegalArgumentException(
                        "Unsupported old key code: " + code);
            }
        };
    }

    // 필수 설정값 확인
    private static String required(Properties props, String name) {

        String value = props.getProperty(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing setting: " + name);
        }

        return value.trim();
    }
}