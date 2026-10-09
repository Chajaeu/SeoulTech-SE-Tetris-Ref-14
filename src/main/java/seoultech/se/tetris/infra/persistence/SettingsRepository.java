package seoultech.se.tetris.infra.persistence;

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

/** settings.properties 읽기/쓰기. Java 표준 라이브러리만 사용한다. */
public class SettingsRepository {
    private final Path directory;
    private final Path settingsFile;

    /** 기본 저장 경로: 사용자 홈/.se-tetris-team14/settings.properties */
    public SettingsRepository() {
        this(Path.of(System.getProperty("user.home"), ".se-tetris-team14"));
    }

    /** 테스트에서 임시 디렉터리를 넣을 수 있다. */
    public SettingsRepository(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.settingsFile = directory.resolve("settings.properties");
    }

    public boolean saveSettings(SettingsDTO settings) {
        if (settings == null || !settings.isValid()) {
            return false;
        }
        Path temp = null;
        try {
            Files.createDirectories(directory);
            temp = Files.createTempFile(directory, "settings-", ".tmp");
            Properties props = toProperties(settings);
            try (OutputStream output = Files.newOutputStream(temp)) {
                props.store(output, "SE Tetris Team14 Settings");
            }
            try {
                Files.move(temp, settingsFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, settingsFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | SecurityException ex) {
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException | SecurityException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    /** 파일이 없거나 파일 내용이 유효하지 않으면 기본 설정을 반환한다. null 반환하지 않음. */
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

    /** 파일을 삭제하지 않고 기본 설정으로 덮어써 초기화한다. */
    public boolean resetSettings() {
        return saveSettings(SettingsDTO.defaults());
    }

    public Path getSettingsFile() {
        return settingsFile;
    }

    private static Properties toProperties(SettingsDTO settings) {
        Properties props = new Properties();
        props.setProperty("displaySize", settings.getDisplaySize().name());
        props.setProperty("keyLeft", Integer.toString(settings.getKeyLeft()));
        props.setProperty("keyRight", Integer.toString(settings.getKeyRight()));
        props.setProperty("keyDown", Integer.toString(settings.getKeyDown()));
        props.setProperty("keyRotate", Integer.toString(settings.getKeyRotate()));
        props.setProperty("keyDrop", Integer.toString(settings.getKeyDrop()));
        props.setProperty("keyPause", Integer.toString(settings.getKeyPause()));
        props.setProperty("colorblindMode", Boolean.toString(settings.isColorblindMode()));
        return props;
    }

    private static SettingsDTO fromProperties(Properties props) {
        SettingsDTO settings = new SettingsDTO();
        settings.setDisplaySize(DisplaySize.valueOf(required(props, "displaySize")));
        settings.setKeyLeft(Integer.parseInt(required(props, "keyLeft")));
        settings.setKeyRight(Integer.parseInt(required(props, "keyRight")));
        settings.setKeyDown(Integer.parseInt(required(props, "keyDown")));
        settings.setKeyRotate(Integer.parseInt(required(props, "keyRotate")));
        settings.setKeyDrop(Integer.parseInt(required(props, "keyDrop")));
        settings.setKeyPause(Integer.parseInt(required(props, "keyPause")));
        String colorblind = required(props, "colorblindMode");
        if (!"true".equals(colorblind) && !"false".equals(colorblind)) {
            throw new IllegalArgumentException("Invalid colorblindMode");
        }
        settings.setColorblindMode(Boolean.parseBoolean(colorblind));
        return settings;
    }

    private static String required(Properties props, String name) {
        String value = props.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing setting: " + name);
        }
        return value.trim();
    }
}
