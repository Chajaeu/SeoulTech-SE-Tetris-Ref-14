package seoultech.se.tetris.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import seoultech.se.tetris.infra.dto.DisplaySize;
import seoultech.se.tetris.infra.dto.SettingsDTO;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadDefaultsWhenFileDoesNotExist() {
        SettingsRepository repo = new SettingsRepository(tempDir);

        SettingsDTO settings = repo.loadSettings();

        assertNotNull(settings);
        assertEquals(DisplaySize.MEDIUM, settings.getDisplaySize());
        assertFalse(settings.isColorblindMode());
    }

    @Test
    void saveAndLoadSettings() {
        SettingsRepository repo = new SettingsRepository(tempDir);
        SettingsDTO settings = new SettingsDTO();

        settings.setDisplaySize(DisplaySize.LARGE);
        settings.setColorblindMode(true);

        assertTrue(repo.saveSettings(settings));
        assertTrue(Files.exists(repo.getSettingsFile()));

        SettingsRepository newRepo = new SettingsRepository(tempDir);
        SettingsDTO loaded = newRepo.loadSettings();

        assertEquals(DisplaySize.LARGE, loaded.getDisplaySize());
        assertTrue(loaded.isColorblindMode());
    }

    @Test
    void rejectDuplicateKeys() {
        SettingsRepository repo = new SettingsRepository(tempDir);
        SettingsDTO settings = new SettingsDTO();

        settings.setKeyRight(settings.getKeyLeft());

        assertFalse(repo.saveSettings(settings));
        assertFalse(Files.exists(repo.getSettingsFile()));
    }

    @Test
    void resetSettings() {
        SettingsRepository repo = new SettingsRepository(tempDir);
        SettingsDTO settings = new SettingsDTO();

        settings.setDisplaySize(DisplaySize.SMALL);
        settings.setColorblindMode(true);

        assertTrue(repo.saveSettings(settings));
        assertTrue(repo.resetSettings());

        SettingsDTO loaded = repo.loadSettings();

        assertEquals(DisplaySize.MEDIUM, loaded.getDisplaySize());
        assertFalse(loaded.isColorblindMode());
    }

    @Test
    void loadDefaultsWhenFileIsInvalid() throws IOException {
        SettingsRepository repo = new SettingsRepository(tempDir);

        Files.writeString(
                repo.getSettingsFile(),
                "displaySize=INVALID\n");

        SettingsDTO loaded = repo.loadSettings();

        assertNotNull(loaded);
        assertEquals(DisplaySize.MEDIUM, loaded.getDisplaySize());
    }

    @Test
    void rejectNullSettings() {
        SettingsRepository repo = new SettingsRepository(tempDir);

        assertFalse(repo.saveSettings(null));
    }
}