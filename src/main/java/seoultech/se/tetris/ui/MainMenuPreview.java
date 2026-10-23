package seoultech.se.tetris.ui;

import javafx.application.Application;
import javafx.stage.Stage;

public class MainMenuPreview extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        MainMenuUI.show(stage, UIResolution.Size.LARGE);
    }

    public static void main(String[] args) {
        launch(args);
    }
}