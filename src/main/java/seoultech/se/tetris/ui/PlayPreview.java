package seoultech.se.tetris.ui;

import javafx.application.Application;
import javafx.stage.Stage;

// Game 확인용 테스트용임
public class PlayPreview extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        PlayUI.show(stage, UIResolution.Size.LARGE);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
