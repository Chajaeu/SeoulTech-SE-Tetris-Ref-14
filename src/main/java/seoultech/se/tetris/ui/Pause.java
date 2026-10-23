package seoultech.se.tetris.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

public class Pause {

    public static Parent create() throws Exception {
        return create(() -> {});
    }

    // RESUME/X로 닫은 뒤, 호출한 화면에서 게임 재개와 포커스 복귀를 처리한다.
    public static Parent create(Runnable onClose) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            Pause.class.getResource("fxml/Pause.fxml")
        );
        Parent root = loader.load();

        Button resumeButton = (Button) loader.getNamespace().get("resumeButton");
        Button quitButton = (Button) loader.getNamespace().get("quitButton");
        Button closeButton = (Button) loader.getNamespace().get("closeButton");

        resumeButton.setOnAction(e -> {
            close(root);
            onClose.run();
        });

        quitButton.setOnAction(e -> Platform.exit());

        closeButton.setOnAction(e -> {
            close(root);
            onClose.run();
        });

        // 메인 메뉴와 같은 버튼 눌림 효과를 재사용한다.
        root.lookupAll(".button").forEach(node -> {
            if (node instanceof Button button) {
                ButtonEffects.addPressEffect(button);
            }
        });

        return root;
    }

    private static void close(Parent root) {
        if (root.getParent() instanceof Pane parent) {
            parent.getChildren().remove(root);
        }
    }
}
