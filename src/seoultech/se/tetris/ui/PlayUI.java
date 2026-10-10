package seoultech.se.tetris.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

public class PlayUI {

    public static void show(Stage stage, UIResolution.Size resolution)
            throws Exception {

        Parent root = FXMLLoader.load(
            PlayUI.class.getResource("fxml/PlayUI.fxml")
        );

        Pane designRoot = (Pane) root;

        int boardWidth = resolution.getWidth();
        int boardHeight = resolution.getHeight();

        // FXML에 정의된 원본 크기를 기준으로 사용
        double designWidth = designRoot.getPrefWidth();
        double designHeight = designRoot.getPrefHeight();

        double scaleX = boardWidth / designWidth;
        double scaleY = boardHeight / designHeight;

        double scale = Math.min(scaleX, scaleY);

        designRoot.getTransforms().add(
            new Scale(scale, scale, 0, 0)
        );

        Scene scene = new Scene(root, boardWidth, boardHeight);

        stage.setTitle("Tetris");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }
}