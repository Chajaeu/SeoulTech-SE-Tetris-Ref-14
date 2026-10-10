package seoultech.se.tetris.ui;

import javafx.application.Application;
import javafx.stage.Stage;

public class MainMenuPreview extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        PlayUI.show(stage, UIResolution.Size.DEFAULT);
    }

    public static void main(String[] args) {
        launch(args);
    }
}