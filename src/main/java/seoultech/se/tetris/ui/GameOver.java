package seoultech.se.tetris.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;

public class GameOver {

    // 재시작과 메인 메뉴 전환은 호출한 화면에서 처리
    public static Parent create(Runnable onRetry, Runnable onMainMenu) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            GameOver.class.getResource("fxml/GameOver.fxml")
        );
        Parent root = loader.load();

        Button retryButton = (Button) loader.getNamespace().get("retryButton");
        Button mainMenuButton = (Button) loader.getNamespace().get("mainMenuButton");
        Button quitButton = (Button) loader.getNamespace().get("quitButton");

        // RETRY > 새 게임, MAIN > 메인 메뉴, QUIT > 프로그램 종료
        retryButton.setOnAction(e -> onRetry.run());
        mainMenuButton.setOnAction(e -> onMainMenu.run());
        quitButton.setOnAction(e -> Platform.exit());

        // 메인 메뉴와 같은 버튼 눌림 효과를 재사용
        root.lookupAll(".button").forEach(node -> {
            if (node instanceof Button button) {
                ButtonEffects.addPressEffect(button);
            }
        });

        return root;
    }
}
