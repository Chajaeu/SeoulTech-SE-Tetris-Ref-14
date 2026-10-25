package seoultech.se.tetris.ui;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.DisplaySize;
import seoultech.se.tetris.infra.dto.GameMode;
import seoultech.se.tetris.infra.dto.ScoreDTO;
import seoultech.se.tetris.infra.dto.SettingsDTO;
import seoultech.se.tetris.infra.integration.GameSessionService;
import seoultech.se.tetris.infra.persistence.ScoreRepository;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JavaFX 화면과 저장소·Core 연동을 함께 확인하는 테스트.
 * 화면/버튼 접근은 반드시 FX Application Thread에서 진행한다.
 * 저장소는 @TempDir의 별도 홈을 사용하므로 사용자의 실제 점수·설정을 건드리지 않는다.
 * 이 테스트는 화면을 사용할 수 있는 macOS/Windows 데스크톱에서 실행한다.
 */
class UIIntegrationTest {
    @TempDir
    static Path tempHome;

    private static String previousHome;
    private Stage stage;

    @BeforeAll
    static void startJavaFx() throws Exception {
        previousHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.toString());

        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            // 다른 테스트에서 JavaFX Toolkit을 이미 시작한 경우
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(20, TimeUnit.SECONDS), "JavaFX Toolkit 시작 실패");
        Platform.setImplicitExit(false);
    }

    @AfterAll
    static void restoreUserHome() {
        if (previousHome != null) {
            System.setProperty("user.home", previousHome);
        }
        // JavaFX 런타임이 Gradle 테스트 프로세스를 붙잡지 않도록 마지막에 종료한다.
        Platform.exit();
    }

    @BeforeEach
    void cleanTestData() {
        assertTrue(new SettingsRepository().resetSettings());
        assertTrue(new ScoreRepository().resetScoreboard());
    }

    @AfterEach
    void closeWindow() throws Exception {
        onFx(() -> {
            if (stage != null) {
                stage.close();
                stage = null;
            }
            return null;
        });
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception e)
                throw e;
            if (cause instanceof Error error)
                throw error;
            throw new RuntimeException(cause);
        }
    }

    private static <T extends javafx.scene.Node> T node(Parent root, String id, Class<T> type) {
        javafx.scene.Node found = root.lookup("#" + id);
        assertNotNull(found, "UI 요소를 찾을 수 없습니다: " + id);
        return type.cast(found);
    }

    private static KeyEvent pressed(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    @Test
    void resolutionSizeAndEnumConversionMatchSettings() {
        assertEquals(960, UIResolution.Size.SMALL.getWidth());
        assertEquals(540, UIResolution.Size.SMALL.getHeight());
        assertEquals(1024, UIResolution.Size.MEDIUM.getWidth());
        assertEquals(576, UIResolution.Size.MEDIUM.getHeight());
        assertEquals(1280, UIResolution.Size.LARGE.getWidth());
        assertEquals(720, UIResolution.Size.LARGE.getHeight());
        for (UIResolution.Size size : UIResolution.Size.values()) {
            assertSame(size, UIResolution.Size.fromDisplaySize(size.toDisplaySize()));
        }
        assertSame(UIResolution.Size.MEDIUM, UIResolution.loadSavedSize());
        SettingsDTO settings = new SettingsRepository().loadSettings();
        settings.setDisplaySize(DisplaySize.LARGE);
        assertTrue(new SettingsRepository().saveSettings(settings));
        assertSame(UIResolution.Size.LARGE, UIResolution.loadSavedSize());
    }

    @Test
    void scoreFormattingAndKeyCopyPreserveExpectedFields() {
        assertEquals("0", Scoreboard.formatScore(0));
        assertEquals("1,234,567", Scoreboard.formatScore(1_234_567));
        SettingsDTO source = SettingsDTO.defaults();
        source.setKeyLeft("A");
        source.setKeyRight("D");
        source.setKeyDown("S");
        source.setKeyRotate("W");
        source.setKeyDrop("SPACE");
        source.setKeyPause("P");
        SettingsDTO destination = SettingsDTO.defaults();
        destination.setDifficulty(Difficulty.HARD);
        KeySetting.copyKeys(source, destination);
        assertEquals("A", destination.getKeyLeft());
        assertEquals("D", destination.getKeyRight());
        assertEquals("S", destination.getKeyDown());
        assertEquals("W", destination.getKeyRotate());
        assertEquals("SPACE", destination.getKeyDrop());
        assertEquals("P", destination.getKeyPause());
        assertEquals(Difficulty.HARD, destination.getDifficulty());
        assertTrue(destination.isValid());
    }

    @Test
    void rendererDistinguishesEmptyStandardAndItemCells() throws Exception {
        onFx(() -> {
            Cell empty = new Cell();
            Cell normal = new Cell('I');
            Cell item = new Cell('L', true, 'I');
            StackPane emptyNode = BlockRenderer.createCell(empty, 24, false);
            StackPane normalNode = BlockRenderer.createCell(normal, 24, false);
            StackPane itemNode = BlockRenderer.createCell(item, 24, true);
            assertSame(empty, emptyNode.getUserData());
            assertEquals(1, emptyNode.getChildren().size());
            assertEquals(1, normalNode.getChildren().size());
            assertEquals(2, itemNode.getChildren().size());
            assertEquals("L", ((Label) itemNode.getChildren().get(1)).getText());
            assertEquals(Color.web("#081423"), BlockRenderer.colorFor(empty, false));
            assertEquals(Color.web("#FFD329"), BlockRenderer.colorFor(item, false));
            assertEquals(Color.web("#F7F7F2"), BlockRenderer.colorFor(item, true));
            assertEquals(Color.WHITE, ((Rectangle) normalNode.getChildren().get(0)).getStroke());
            for (char type : "IJLOSTZ".toCharArray()) {
                Cell block = new Cell(type);
                assertNotEquals(BlockRenderer.colorFor(block, false), BlockRenderer.colorFor(block, true));
            }
            return null;
        });
    }

    @Test
    void recordHighlightStylesCanBeAppliedAndCleared() throws Exception {
        onFx(() -> {
            for (int column = 0; column < 3; column++) {
                Label cell = new Label("Rank");
                UIInteraction.markRecord(cell, true, column);
                assertTrue(cell.getStyleClass().contains("record-cell"));
                assertTrue(cell.getPseudoClassStates().contains(PseudoClass.getPseudoClass("new-record")));
                if (column == 0)
                    assertTrue(cell.getStyleClass().contains("record-start"));
                if (column == 2)
                    assertTrue(cell.getStyleClass().contains("record-end"));
                UIInteraction.markRecord(cell, false, column);
                assertFalse(cell.getPseudoClassStates().contains(PseudoClass.getPseudoClass("new-record")));
            }
            return null;
        });
    }

    @Test
    void scoreboardFiltersByModeAndDifficultyAndMarksNewRecord() throws Exception {
        ScoreRepository repository = new ScoreRepository();
        ScoreDTO normalEasy = new ScoreDTO("EASY_PLAYER", 12345, GameMode.NORMAL, Difficulty.EASY);
        assertTrue(repository.saveScore(normalEasy));
        assertTrue(repository.saveScore("ITEM_PLAYER", 900, GameMode.ITEM, Difficulty.EASY));
        assertTrue(repository.saveScore("HARD_PLAYER", 500, GameMode.NORMAL, Difficulty.HARD));
        AtomicInteger closed = new AtomicInteger();
        onFx(() -> {
            Parent root = Scoreboard.create(GameMode.NORMAL, Difficulty.EASY, normalEasy.getId(),
                    closed::incrementAndGet);
            assertTrue(node(root, "easyDifficultyButton", ToggleButton.class).isSelected());
            assertTrue(node(root, "normalModeButton", ToggleButton.class).isSelected());
            Label topName = node(root, "name1", Label.class);
            assertEquals("EASY_PLAYER", topName.getText());
            assertEquals("12,345", node(root, "score1", Label.class).getText());
            assertTrue(topName.getPseudoClassStates().contains(PseudoClass.getPseudoClass("new-record")));
            node(root, "itemModeButton", ToggleButton.class).fire();
            assertEquals("ITEM_PLAYER", topName.getText());
            assertFalse(topName.getPseudoClassStates().contains(PseudoClass.getPseudoClass("new-record")));
            node(root, "normalModeButton", ToggleButton.class).fire();
            node(root, "hardDifficultyButton", ToggleButton.class).fire();
            assertEquals("HARD_PLAYER", topName.getText());
            assertEquals("—", node(root, "name2", Label.class).getText());
            node(root, "closeButton", Button.class).fire();
            assertEquals(1, closed.get());
            return null;
        });
    }

    @Test
    void keySettingSavesChangedKeyAndResetRestoresDefaults() throws Exception {
        AtomicReference<SettingsDTO> submitted = new AtomicReference<>();
        onFx(() -> {
            Parent root = KeySetting.create(SettingsDTO.defaults(), keys -> {
                submitted.set(keys);
                return true;
            });
            node(root, "leftKeyButton", Button.class).fire();
            assertEquals("PRESS KEY", node(root, "leftKeyButton", Button.class).getText());
            Event.fireEvent(root, pressed(KeyCode.A));
            node(root, "saveButton", Button.class).fire();
            assertNotNull(submitted.get());
            assertEquals("A", submitted.get().getKeyLeft());
            assertEquals("RIGHT", submitted.get().getKeyRight());
            assertTrue(node(root, "keySettingStatus", Label.class).getText().contains("저장"));
            node(root, "resetButton", Button.class).fire();
            assertEquals("LEFT", node(root, "leftKeyButton", Button.class).getText());
            node(root, "saveButton", Button.class).fire();
            assertEquals("LEFT", submitted.get().getKeyLeft());
            return null;
        });
    }

    @Test
    void keySettingRejectsDuplicateKeysAndDoesNotSaveWhenCancelled() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        onFx(() -> {
            Parent root = KeySetting.create(SettingsDTO.defaults(), keys -> {
                saves.incrementAndGet();
                return true;
            });
            node(root, "leftKeyButton", Button.class).fire();
            Event.fireEvent(root, pressed(KeyCode.RIGHT));
            node(root, "saveButton", Button.class).fire();
            assertEquals(0, saves.get());
            assertTrue(node(root, "keySettingStatus", Label.class).getText().contains("중복"));
            node(root, "closeButton", Button.class).fire();
            assertEquals(0, saves.get());
            return null;
        });
    }

    @Test
    void settingsSaveChangesResolutionAndColorblindMode() throws Exception {
        AtomicReference<UIResolution.Size> saved = new AtomicReference<>();
        onFx(() -> {
            Parent root = Setting.create(UIResolution.Size.SMALL, saved::set);
            assertTrue(node(root, "smallResolutionButton", ToggleButton.class).isSelected());
            node(root, "largeResolutionButton", ToggleButton.class).fire();
            node(root, "colorblindOnButton", ToggleButton.class).fire();
            node(root, "saveButton", Button.class).fire();
            assertEquals(UIResolution.Size.LARGE, saved.get());
            return null;
        });
        SettingsDTO stored = new SettingsRepository().loadSettings();
        assertEquals(DisplaySize.LARGE, stored.getDisplaySize());
        assertTrue(stored.isColorblindMode());
    }

    @Test
    void settingsResetAndScoreboardResetAreDeferredUntilSave() throws Exception {
        ScoreRepository scores = new ScoreRepository();
        assertTrue(scores.saveScore("BEFORE_RESET", 1000, GameMode.NORMAL, Difficulty.NORMAL));
        SettingsDTO settings = SettingsDTO.defaults();
        settings.setDisplaySize(DisplaySize.LARGE);
        settings.setColorblindMode(true);
        assertTrue(new SettingsRepository().saveSettings(settings));
        AtomicInteger resetCallbacks = new AtomicInteger();
        onFx(() -> {
            Parent root = Setting.create(UIResolution.Size.LARGE, size -> {
            }, resetCallbacks::incrementAndGet);
            node(root, "scoreboardResetButton", Button.class).fire();
            node(root, "resetButton", Button.class).fire();
            assertTrue(node(root, "mediumResolutionButton", ToggleButton.class).isSelected());
            assertFalse(node(root, "colorblindOnButton", ToggleButton.class).isSelected());
            assertEquals(1, scores.getTopScores(GameMode.NORMAL, Difficulty.NORMAL).size());
            assertEquals(DisplaySize.LARGE, new SettingsRepository().loadSettings().getDisplaySize());
            node(root, "saveButton", Button.class).fire();
            assertEquals(1, resetCallbacks.get());
            return null;
        });
        assertTrue(scores.getTopScores(GameMode.NORMAL, Difficulty.NORMAL).isEmpty());
        assertEquals(DisplaySize.MEDIUM, new SettingsRepository().loadSettings().getDisplaySize());
        assertFalse(new SettingsRepository().loadSettings().isColorblindMode());
    }

    @Test
    void settingsOpensKeyPopupAndDoesNotOpenDuplicate() throws Exception {
        onFx(() -> {
            Parent root = Setting.create(UIResolution.Size.MEDIUM);
            Button keys = node(root, "keySettingButton", Button.class);
            keys.fire();
            Parent popup = node(root, "keySettingPopup", Parent.class);
            keys.fire();
            assertSame(popup, node(root, "keySettingPopup", Parent.class));
            node(popup, "closeButton", Button.class).fire();
            assertNull(root.lookup("#keySettingPopup"));
            return null;
        });
    }

    @Test
    void pauseAndGameOverButtonsInvokeCallbacks() throws Exception {
        AtomicInteger resumed = new AtomicInteger();
        AtomicInteger wentMain = new AtomicInteger();
        AtomicInteger retried = new AtomicInteger();
        onFx(() -> {
            Pane container = new Pane();
            Parent pause = Pause.create(resumed::incrementAndGet, wentMain::incrementAndGet);
            container.getChildren().add(pause);
            node(pause, "resumeButton", Button.class).fire();
            assertEquals(1, resumed.get());
            assertFalse(container.getChildren().contains(pause));

            Parent otherPause = Pause.create(resumed::incrementAndGet, wentMain::incrementAndGet);
            node(otherPause, "mainMenuButton", Button.class).fire();
            assertEquals(1, wentMain.get());

            Parent gameOver = GameOver.create(retried::incrementAndGet, wentMain::incrementAndGet);
            node(gameOver, "retryButton", Button.class).fire();
            node(gameOver, "mainMenuButton", Button.class).fire();
            assertEquals(1, retried.get());
            assertEquals(2, wentMain.get());
            return null;
        });
    }

    @Test
    void scoreSubmissionValidatesNameAndRejectsSubmissionBeforeGameOver() throws Exception {
        GameSessionService session = new GameSessionService(new SettingsRepository(), new ScoreRepository());
        session.startGame(GameMode.NORMAL);
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger saved = new AtomicInteger();
        onFx(() -> {
            Parent root = ScoreSubmit.create(session, closed::incrementAndGet, id -> saved.incrementAndGet());
            TextField input = node(root, "nameField", TextField.class);
            node(root, "submitButton", Button.class).fire();
            assertTrue(node(root, "errorLabel", Label.class).getText().contains("1~20"));
            input.setText("TEST_PLAYER");
            node(root, "submitButton", Button.class).fire();
            assertTrue(node(root, "errorLabel", Label.class).getText().contains("저장하지 못"));
            assertEquals(0, saved.get());
            node(root, "closeButton", Button.class).fire();
            assertEquals(1, closed.get());
            return null;
        });
    }

    @Test
    void mainMenuDisplaysSettingsAndScoreboardPopups() throws Exception {
        onFx(() -> {
            stage = new Stage();
            MainMenuUI.show(stage, UIResolution.Size.SMALL);
            Parent root = stage.getScene().getRoot();
            assertNotNull(node(root, "startButton", Button.class));
            assertTrue(node(root, "normalModeButton", ToggleButton.class).isSelected());
            node(root, "settingButton", Button.class).fire();
            Parent popup = node(root, "settingPopup", Parent.class);
            node(root, "settingButton", Button.class).fire();
            assertSame(popup, node(root, "settingPopup", Parent.class));
            node(popup, "closeButton", Button.class).fire();
            assertNull(root.lookup("#settingPopup"));
            node(root, "scoreboardButton", Button.class).fire();
            Parent boardPopup = node(root, "scoreboardPopup", Parent.class);
            node(boardPopup, "closeButton", Button.class).fire();
            assertNull(root.lookup("#scoreboardPopup"));
            return null;
        });
    }

    @Test
    void playScreenCreatesBoardAndRespondsToPauseAndResume() throws Exception {
        onFx(() -> {
            stage = new Stage();
            PlayUI.show(stage, UIResolution.Size.SMALL, Difficulty.NORMAL, false);
            Scene scene = stage.getScene();
            Parent root = scene.getRoot();
            assertNotNull(root.lookup("#boardCell-0-0"));
            assertNotNull(root.lookup("#boardCell-19-9"));
            assertNotNull(node(root, "currentScore", Label.class));
            assertNotNull(node(root, "gameScoreGrid", javafx.scene.layout.GridPane.class));
            Event.fireEvent(root, pressed(KeyCode.P));
            Parent pause = node(root, "pauseOverlay", Parent.class);
            node(pause, "resumeButton", Button.class).fire();
            assertNull(root.lookup("#pauseOverlay"));
            // Stage.close는 PlayUI의 WindowEvent를 호출해 Timeline을 정리한다.
            return null;
        });
    }

    @Test
    void menuStartsItemGameWithChosenDifficulty() throws Exception {
        onFx(() -> {
            stage = new Stage();
            MainMenuUI.show(stage, UIResolution.Size.SMALL);
            Parent menu = stage.getScene().getRoot();
            node(menu, "hardDifficultyButton", ToggleButton.class).fire();
            node(menu, "itemModeButton", ToggleButton.class).fire();
            node(menu, "startButton", Button.class).fire();
            Parent gameScreen = stage.getScene().getRoot();
            assertNotNull(node(gameScreen, "currentScore", Label.class));
            assertEquals(Difficulty.HARD, new SettingsRepository().loadSettings().getDifficulty());
            assertNotNull(gameScreen.lookup("#boardCell-0-0"));
            return null;
        });
    }

    @Test
    void pauseMenuNavigatesBackToMainMenu() throws Exception {
        onFx(() -> {
            stage = new Stage();
            PlayUI.show(stage, UIResolution.Size.SMALL, Difficulty.NORMAL, false);
            Parent gameScreen = stage.getScene().getRoot();
            Event.fireEvent(gameScreen, pressed(KeyCode.P));
            Parent overlay = node(gameScreen, "pauseOverlay", Parent.class);
            node(overlay, "mainMenuButton", Button.class).fire();
            assertNotNull(stage.getScene().getRoot().lookup("#startButton"));
            assertNotSame(gameScreen, stage.getScene().getRoot());
            return null;
        });
    }

}
