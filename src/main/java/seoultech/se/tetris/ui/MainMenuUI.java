package seoultech.se.tetris.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import java.util.function.Consumer;
import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.GameMode;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

public class MainMenuUI {

    private static final String SELECTED_STYLE =
        "-fx-background-color: linear-gradient(to bottom, " +
        "#FFF06A 0%, #FFD329 45%, #FF9D0A 100%);";

    public static void show(Stage stage, UIResolution.Size resolution)
            throws Exception {

        Parent root = FXMLLoader.load(
            MainMenuUI.class.getResource("fxml/MainMenuUI.fxml")
        );

        Pane designRoot = (Pane) root;
        ObjectProperty<UIResolution.Size> currentResolution = new SimpleObjectProperty<>(resolution);
        StackPane screen = new StackPane(designRoot);

        // 저장된 난이도를 불러오고 하나만 선택
        ToggleButton easyButton =
            (ToggleButton) designRoot.lookup("#easyDifficultyButton");
        ToggleButton normalButton =
            (ToggleButton) designRoot.lookup("#normalDifficultyButton");
        ToggleButton hardButton =
            (ToggleButton) designRoot.lookup("#hardDifficultyButton");

        setupSelection(easyButton, normalButton, hardButton);
        Difficulty savedDifficulty = new SettingsRepository().loadSettings().getDifficulty();
        switch (savedDifficulty) {
            case EASY -> easyButton.setSelected(true);
            case NORMAL -> normalButton.setSelected(true);
            case HARD -> hardButton.setSelected(true);
        }

        // 게임 모드는 하나만 선택하며 처음에는 일반 모드를 선택
        ToggleButton normalModeButton =
            (ToggleButton) designRoot.lookup("#normalModeButton");
        ToggleButton itemModeButton =
            (ToggleButton) designRoot.lookup("#itemModeButton");

        setupSelection(normalModeButton, itemModeButton);
        normalModeButton.setSelected(true);

        // SETTING 버튼
        Button settingButton =
            (Button) designRoot.lookup("#settingButton");

        settingButton.setOnAction(e -> {

            try {
                openSettings(designRoot, currentResolution.get(), selectedResolution -> {
                    if (selectedResolution != currentResolution.get()) {
                        currentResolution.set(selectedResolution);
                        applyResolution(stage, designRoot, screen, selectedResolution);
                    }
                }, () -> normalButton.setSelected(true));

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // 메인 메뉴에서 선택한 모드와 난이도의 점수판 표시
        Button scoreboardButton =
            (Button) designRoot.lookup("#scoreboardButton");

        scoreboardButton.setOnAction(e -> {
            Difficulty difficulty = easyButton.isSelected()
                ? Difficulty.EASY
                : hardButton.isSelected()
                    ? Difficulty.HARD
                    : Difficulty.NORMAL;
            GameMode mode = itemModeButton.isSelected() ? GameMode.ITEM : GameMode.NORMAL;
            try {
                openScoreboard(designRoot, mode, difficulty);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // GAME START → 게임 화면으로 전환
        Button startButton =
        (Button) designRoot.lookup("#startButton");

        startButton.setOnAction(e -> {
            try {
                Difficulty difficulty = easyButton.isSelected() ? Difficulty.EASY
                    : hardButton.isSelected() ? Difficulty.HARD : Difficulty.NORMAL;
                PlayUI.show(stage, currentResolution.get(), difficulty, itemModeButton.isSelected());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // EXIT → 프로그램 종료
        Button exitButton =
        (Button) designRoot.lookup("#exitButton");

        exitButton.setOnAction(e -> Platform.exit());


        // 버튼 눌림 효과
        designRoot.lookupAll(".button").forEach(node -> {
            if (node instanceof Button button) {
                ButtonEffects.addPressEffect(button);
            }
        });

        UIInteraction.installNavigation(designRoot, startButton);

        stage.setTitle("Tetris");
        stage.setResizable(false);
        applyResolution(stage, designRoot, screen, resolution);
        stage.show();
    }

    // 메뉴를 새로 만들지 않고 선택한 난이도와 모드를 유지하며 크기만 변경
    private static void applyResolution(Stage stage, Pane designRoot, StackPane screen,
            UIResolution.Size resolution) {
        double scale = Math.min(resolution.getWidth() / designRoot.getPrefWidth(),
            resolution.getHeight() / designRoot.getPrefHeight());
        designRoot.setScaleX(scale);
        designRoot.setScaleY(scale);
        screen.setPrefSize(resolution.getWidth(), resolution.getHeight());

        // 기존 Scene에서 분리한 뒤 선택한 화면 크기로 다시 연결
        if (stage.getScene() != null && stage.getScene().getRoot() == screen) {
            stage.getScene().setRoot(new Pane());
        }
        stage.setScene(new Scene(screen, resolution.getWidth(), resolution.getHeight()));
        stage.sizeToScene();
    }


    // SettingsUI를 MainMenu 위에 팝업으로 표시
    private static void openSettings(
            Pane designRoot,
            UIResolution.Size resolution,
            Consumer<UIResolution.Size> onSaved,
            Runnable onResetSaved)
            throws Exception {

        // 이미 설정창이 열려 있으면 또 만들지 않음
        if (designRoot.lookup("#settingPopup") != null
                || designRoot.lookup("#scoreboardPopup") != null) {
            return;
        }

        Parent settings = Setting.create(resolution, onSaved, onResetSaved);

        // MainMenu에서 팝업을 식별하기 위한 ID
        settings.setId("settingPopup");

        Pane settingsPane = (Pane) settings;

        // MainMenuUI.fxml 기준으로 가운데 정렬
        double x =
            (designRoot.getPrefWidth()
                - settingsPane.getPrefWidth()) / 2;

        double y =
            (designRoot.getPrefHeight()
                - settingsPane.getPrefHeight()) / 2;

        settingsPane.setLayoutX(x);
        settingsPane.setLayoutY(y);

        // MainMenu 위에 추가
        designRoot.getChildren().add(settingsPane);
    }


    private static void openScoreboard(
            Pane designRoot, GameMode mode, Difficulty difficulty) throws Exception {
        if (designRoot.lookup("#scoreboardPopup") != null
                || designRoot.lookup("#settingPopup") != null) {
            return;
        }

        Pane scoreboard = (Pane) Scoreboard.create(mode, difficulty);
        scoreboard.setId("scoreboardPopup");
        scoreboard.setLayoutX(
            (designRoot.getPrefWidth() - scoreboard.getPrefWidth()) / 2
        );
        scoreboard.setLayoutY(
            (designRoot.getPrefHeight() - scoreboard.getPrefHeight()) / 2
        );
        designRoot.getChildren().add(scoreboard);
    }


    private static void setupSelection(ToggleButton... buttons) {
        ToggleGroup selectionGroup = new ToggleGroup();

        for (ToggleButton button : buttons) {
            button.setToggleGroup(selectionGroup);

            // Scene Builder에서 지정한 기본 스타일을 유지한다.
            String normalStyle = button.getStyle();
            button.selectedProperty().addListener((observable, oldValue, selected) -> {
                button.setStyle(selected
                    ? normalStyle + ";" + SELECTED_STYLE
                    : normalStyle);
            });

            ButtonEffects.addPressEffect(button);
        }

        // 선택된 버튼을 다시 눌러도 선택이 사라지지 않도록 함
        selectionGroup.selectedToggleProperty().addListener(
            (observable, oldToggle, newToggle) -> {
                if (newToggle == null && oldToggle != null) {
                    selectionGroup.selectToggle(oldToggle);
                }
            }
        );
    }
}
