package seoultech.se.tetris.ui;

import javafx.application.Application;
import javafx.stage.Stage;

public class MainMenuPreview {

    // 배포 실행기에서도 별도 JavaFX 옵션 없이 시작하도록 진입점을 분리
    public static void main(String[] args) {
        Application.launch(MainMenuApplication.class, args);
    }

    public static class MainMenuApplication extends Application {
        @Override
        public void start(Stage stage) throws Exception {
            MainMenuUI.show(stage, UIResolution.Size.LARGE);
        }
    }
}
