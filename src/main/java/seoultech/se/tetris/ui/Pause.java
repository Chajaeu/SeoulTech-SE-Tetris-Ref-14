package seoultech.se.tetris.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

public class Pause {

    // 게임 재개와 메인 메뉴 전환은 호출한 화면에서 처리
    public static Parent create(Runnable onResume, Runnable onMainMenu) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            Pause.class.getResource("fxml/Pause.fxml")
        );
        Parent root = loader.load();

        Button resumeButton = (Button) loader.getNamespace().get("resumeButton");
        Button mainMenuButton = (Button) loader.getNamespace().get("mainMenuButton");
        Button quitButton = (Button) loader.getNamespace().get("quitButton");
        Button closeButton = (Button) loader.getNamespace().get("closeButton");

        // RESUME/X → 팝업을 닫고 게임 재개
        resumeButton.setOnAction(e -> resume(root, onResume));
        closeButton.setOnAction(e -> resume(root, onResume));

        // MAIN → 메인 메뉴, QUIT → 프로그램 종료
        mainMenuButton.setOnAction(e -> onMainMenu.run());
        quitButton.setOnAction(e -> Platform.exit());

        // 메인 메뉴와 같은 버튼 눌림 효과를 재사용
        root.lookupAll(".button").forEach(node -> {
            if (node instanceof Button button) {
                ButtonEffects.addPressEffect(button);
            }
        });

        UIInteraction.installNavigation(root, resumeButton);
        return root;
    }

    private static void resume(Parent root, Runnable onResume) {
        if (root.getParent() instanceof Pane parent) {
            parent.getChildren().remove(root);
        }
        onResume.run();
    }
}
