package seoultech.se.tetris.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainMenuUI {

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

        // 버튼 눌림 효과
        designRoot.lookupAll(".button").forEach(node -> {
            if (node instanceof Button button) {
                addPressEffect(button);
            }
        });

        stage.setTitle("Tetris");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private static void addPressEffect(Button button) {

        button.setOnMousePressed(e -> {
            button.setTranslateY(3);
            button.setScaleX(0.98);
            button.setScaleY(0.98);
        });

        button.setOnMouseReleased(e -> {
            button.setTranslateY(0);
            button.setScaleX(1.0);
            button.setScaleY(1.0);
        });
    }
}