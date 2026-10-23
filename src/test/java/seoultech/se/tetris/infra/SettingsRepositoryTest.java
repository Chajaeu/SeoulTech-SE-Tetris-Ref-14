package seoultech.se.tetris.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.DisplaySize;
import seoultech.se.tetris.infra.dto.SettingsDTO;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SettingsRepositoryTest {

    @TempDir
    Path tempDir;

    // 설정 파일이 없으면 기본값 반환
    @Test
    void loadDefaultsWhenFileDoesNotExist() {

        SettingsDTO settings = new SettingsRepository(tempDir).loadSettings();

        assertNotNull(settings);
        assertEquals(DisplaySize.MEDIUM, settings.getDisplaySize());
        assertEquals(Difficulty.NORMAL, settings.getDifficulty());
        assertEquals("LEFT", settings.getKeyLeft());
        assertEquals("SPACE", settings.getKeyDrop());
        assertFalse(settings.isColorblindMode());
    }

    // 설정값 저장 후 다시 불러오기
    @Test
    void saveAndLoadAllSettings() {

        SettingsRepository repo = new SettingsRepository(tempDir);
        SettingsDTO settings = new SettingsDTO();

        settings.setDisplaySize(DisplaySize.LARGE);
        settings.setDifficulty(Difficulty.HARD);
        settings.setKeyLeft("A");
        settings.setKeyPause("Q");
        settings.setColorblindMode(true);

        assertTrue(repo.saveSettings(settings));
        assertTrue(Files.exists(repo.getSettingsFile()));

        SettingsDTO loaded = new SettingsRepository(tempDir).loadSettings();

        assertEquals(DisplaySize.LARGE, loaded.getDisplaySize());
        assertEquals(Difficulty.HARD, loaded.getDifficulty());
        assertEquals("A", loaded.getKeyLeft());
        assertEquals("Q", loaded.getKeyPause());
        assertTrue(loaded.isColorblindMode());
    }

    // 중복 키 저장 방지
    @Test
    void rejectDuplicateKeys() {

        SettingsRepository repo = new SettingsRepository(tempDir);
        SettingsDTO settings = new SettingsDTO();

        settings.setKeyRight(settings.getKeyLeft());

        assertFalse(repo.saveSettings(settings));
        assertFalse(Files.exists(repo.getSettingsFile()));
    }

    // ESCAPE 키 사용 방지
    @Test
    void rejectReservedEscapeKey() {

        SettingsRepository repo = new SettingsRepository(tempDir);
        SettingsDTO settings = new SettingsDTO();

        settings.setKeyPause("ESCAPE");

        assertFalse(repo.saveSettings(settings));
    }

    // 잘못된 키 이름 저장 방지
    @Test
    void rejectInvalidKeyNames() {

        SettingsRepository repo = new SettingsRepository(tempDir);
        SettingsDTO settings = new SettingsDTO();

        settings.setKeyRotate("NOT_A_KEY");
        assertFalse(repo.saveSettings(settings));

        settings.setKeyRotate("up");
        assertFalse(repo.saveSettings(settings));
    }

    // 설정 초기화
    @Test
    void resetSettings() {

        SettingsRepository repo = new SettingsRepository(tempDir);
        SettingsDTO settings = new SettingsDTO();

        settings.setDisplaySize(DisplaySize.SMALL);
        settings.setDifficulty(Difficulty.EASY);
        settings.setColorblindMode(true);

        assertTrue(repo.saveSettings(settings));
        assertTrue(repo.resetSettings());

        SettingsDTO loaded = repo.loadSettings();

        assertEquals(DisplaySize.MEDIUM, loaded.getDisplaySize());
        assertEquals(Difficulty.NORMAL, loaded.getDifficulty());
        assertFalse(loaded.isColorblindMode());
    }

    // 손상된 설정 파일 처리
    @Test
    void loadDefaultsWhenFileIsInvalid() throws IOException {

        SettingsRepository repo = new SettingsRepository(tempDir);

        Files.writeString(
                repo.getSettingsFile(),
                "displaySize=INVALID\n");

        SettingsDTO loaded = repo.loadSettings();

        assertEquals(DisplaySize.MEDIUM, loaded.getDisplaySize());
        assertEquals(Difficulty.NORMAL, loaded.getDifficulty());
    }

    // 잘못된 난이도 처리
    @Test
    void loadDefaultsWhenDifficultyIsInvalid() throws IOException {

        SettingsRepository repo = new SettingsRepository(tempDir);

        assertTrue(repo.saveSettings(new SettingsDTO()));

        String contents = Files.readString(repo.getSettingsFile());

        Files.writeString(
                repo.getSettingsFile(),
                contents.replace(
                        "difficulty=NORMAL",
                        "difficulty=IMPOSSIBLE"));

        assertEquals(
                Difficulty.NORMAL,
                repo.loadSettings().getDifficulty());
    }

    // 기존 Swing 정수형 키 설정 파일 불러오기
    @Test
    void loadOldFileWithIntegerAwtKeys() throws IOException {

        SettingsRepository repo = new SettingsRepository(tempDir);

        String oldFile = """
                displaySize=SMALL
                keyLeft=37
                keyRight=39
                keyDown=40
                keyRotate=38
                keyDrop=32
                keyPause=80
                colorblindMode=true
                """;

        Files.writeString(repo.getSettingsFile(), oldFile);

        SettingsDTO loaded = repo.loadSettings();

        assertEquals(DisplaySize.SMALL, loaded.getDisplaySize());
        assertEquals("LEFT", loaded.getKeyLeft());
        assertEquals("P", loaded.getKeyPause());
        assertEquals(Difficulty.NORMAL, loaded.getDifficulty());
        assertTrue(loaded.isColorblindMode());
    }

    // 난이도 항목이 없는 기존 파일 처리
    @Test
    void loadOldStringSettingsWithoutDifficulty() throws IOException {

        SettingsRepository repo = new SettingsRepository(tempDir);

        assertTrue(repo.saveSettings(new SettingsDTO()));

        Properties props = new Properties();

        try (var in = Files.newInputStream(repo.getSettingsFile())) {
            props.load(in);
        }

        props.remove("difficulty");

        try (var out = Files.newOutputStream(repo.getSettingsFile())) {
            props.store(out, "Previous version");
        }

        assertEquals(
                Difficulty.NORMAL,
                repo.loadSettings().getDifficulty());
    }

    // null 설정값 저장 방지
    @Test
    void rejectNullSettings() {

        SettingsRepository repo = new SettingsRepository(tempDir);

        assertFalse(repo.saveSettings(null));
    }
}