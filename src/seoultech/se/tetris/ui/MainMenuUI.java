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

public class MainMenuUI {

    private static final String SELECTED_DIFFICULTY_STYLE =
        "-fx-background-color: linear-gradient(to bottom, " +
        "#FFF06A 0%, #FFD329 45%, #FF9D0A 100%);";

    public static void show(Stage stage, UIResolution.Size resolution)
            throws Exception {

        Parent root = FXMLLoader.load(
            MainMenuUI.class.getResource("fxml/MainMenuUI.fxml")
        );

        Pane designRoot = (Pane) root;

        // 설정에서 선택된 실제 화면 크기
        int boardWidth = resolution.getWidth();
        int boardHeight = resolution.getHeight();

        // FXML에 설정된 원본 설계 크기
        double designWidth = designRoot.getPrefWidth();
        double designHeight = designRoot.getPrefHeight();

        // 실제 해상도에 맞게 전체 UI 크기 조절
        double scaleX = boardWidth / designWidth;
        double scaleY = boardHeight / designHeight;

        double scale = Math.min(scaleX, scaleY);

        designRoot.setScaleX(scale);
        designRoot.setScaleY(scale);

        // 실제 창을 담당하는 바깥쪽 Pane
        StackPane screen = new StackPane(designRoot);
        screen.setPrefSize(boardWidth, boardHeight);

        Scene scene = new Scene(
            screen,
            boardWidth,
            boardHeight
        );

        // 난이도는 하나만 선택하며, 처음에는 NORMAL을 선택한다.
        ToggleButton easyButton =
            (ToggleButton) designRoot.lookup("#easyDifficultyButton");
        ToggleButton normalButton =
            (ToggleButton) designRoot.lookup("#normalDifficultyButton");
        ToggleButton hardButton =
            (ToggleButton) designRoot.lookup("#hardDifficultyButton");

        setupDifficultySelection(easyButton, normalButton, hardButton);
        normalButton.setSelected(true);

        // SETTING 버튼
        Button settingButton =
            (Button) designRoot.lookup("#settingButton");

        settingButton.setOnAction(e -> {

            try {
                openSettings(designRoot, resolution);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // SCORE BOARD → 메인 메뉴에서 선택한 난이도의 점수판 표시
        Button scoreboardButton =
            (Button) designRoot.lookup("#scoreboardButton");

        scoreboardButton.setOnAction(e -> {
            Scoreboard.Difficulty difficulty = easyButton.isSelected()
                ? Scoreboard.Difficulty.EASY
                : hardButton.isSelected()
                    ? Scoreboard.Difficulty.HARD
                    : Scoreboard.Difficulty.NORMAL;
            try {
                openScoreboard(designRoot, difficulty);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // GAME START → 게임 화면으로 전환
        Button startButton =
        (Button) designRoot.lookup("#startButton");

        startButton.setOnAction(e -> {
            try {
                PlayUI.show(stage, resolution);
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

        stage.setTitle("Tetris");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }


    // SettingsUI를 MainMenu 위에 팝업으로 표시
    private static void openSettings(
            Pane designRoot,
            UIResolution.Size resolution)
            throws Exception {

        // 이미 설정창이 열려 있으면 또 만들지 않음
        if (designRoot.lookup("#settingPopup") != null
                || designRoot.lookup("#scoreboardPopup") != null) {
            return;
        }

        Parent settings = Setting.create(resolution);

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
            Pane designRoot, Scoreboard.Difficulty difficulty) throws Exception {
        if (designRoot.lookup("#scoreboardPopup") != null
                || designRoot.lookup("#settingPopup") != null) {
            return;
        }

        Pane scoreboard = (Pane) Scoreboard.create(difficulty);
        scoreboard.setId("scoreboardPopup");
        scoreboard.setLayoutX(
            (designRoot.getPrefWidth() - scoreboard.getPrefWidth()) / 2
        );
        scoreboard.setLayoutY(
            (designRoot.getPrefHeight() - scoreboard.getPrefHeight()) / 2
        );
        designRoot.getChildren().add(scoreboard);
    }


    private static void setupDifficultySelection(ToggleButton... buttons) {
        ToggleGroup difficultyGroup = new ToggleGroup();

        for (ToggleButton button : buttons) {
            button.setToggleGroup(difficultyGroup);

            // Scene Builder에서 지정한 기본 스타일을 유지한다.
            String normalStyle = button.getStyle();
            button.selectedProperty().addListener((observable, oldValue, selected) -> {
                button.setStyle(selected
                    ? normalStyle + ";" + SELECTED_DIFFICULTY_STYLE
                    : normalStyle);
            });

            ButtonEffects.addPressEffect(button);
        }

        // 선택된 버튼을 다시 눌러도 난이도 선택이 사라지지 않도록 한다.
        difficultyGroup.selectedToggleProperty().addListener(
            (observable, oldToggle, newToggle) -> {
                if (newToggle == null && oldToggle != null) {
                    difficultyGroup.selectToggle(oldToggle);
                }
            }
        );
    }
}
