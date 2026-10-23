package seoultech.se.tetris.infra;

import seoultech.se.tetris.infra.dto.DisplaySize;
import seoultech.se.tetris.infra.dto.SettingsDTO;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

import java.nio.file.Files;
import java.nio.file.Path;

/** Gradle/JUnit 없이 javac/java만으로 실행하는 1주차 임시 검증. */
public class SettingsRepositorySmokeTest {
    public static void main(String[] args) throws Exception {
        Path tempDirectory = Files.createTempDirectory("tetris-infra-smoke-");
        SettingsRepository repo = new SettingsRepository(tempDirectory);
        require(!Files.exists(repo.getSettingsFile()), "initial file should not exist");

        // 파일이 없으면 기본값을 반환
        SettingsDTO defaults = repo.loadSettings();
        require(defaults.getDisplaySize() == DisplaySize.MEDIUM, "default display size");
        require(!defaults.isColorblindMode(), "default colorblind mode");

        // 저장 후 새로운 Repository 객체에서 다시 읽어도 유지
        SettingsDTO changed = new SettingsDTO();
        changed.setDisplaySize(DisplaySize.LARGE);
        changed.setColorblindMode(true);
        require(repo.saveSettings(changed), "saveSettings");
        require(Files.isRegularFile(repo.getSettingsFile()), "file created");
        SettingsDTO loaded = new SettingsRepository(tempDirectory).loadSettings();
        require(loaded.getDisplaySize() == DisplaySize.LARGE, "display size persisted");
        require(loaded.isColorblindMode(), "colorblind mode persisted");

        // 중복 키 설정 저장 거부
        changed.setKeyRight(changed.getKeyLeft());
        require(!repo.saveSettings(changed), "duplicate key rejected");

        // 초기화 파일 덮어쓰기
        require(repo.resetSettings(), "resetSettings");
        require(repo.loadSettings().getDisplaySize() == DisplaySize.MEDIUM,
                "reset to default display size");
        require(!repo.loadSettings().isColorblindMode(), "reset colorblind mode");

        // 잘못된 파일을 읽을 때 안전하게 기본값 반환
        Files.writeString(repo.getSettingsFile(), "displaySize=INVALID\n");
        require(repo.loadSettings().getDisplaySize() == DisplaySize.MEDIUM,
                "corrupt settings fall back to defaults");

        System.out.println("PASS: 6 settings checks (temporary directory: " + tempDirectory + ")");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAIL: " + message);
        }
    }
}
