package seoultech.se.tetris.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;

public class KeySetting {

    private static final String WAITING_STYLE =
        "-fx-background-color: #FFD329;";

    // 저장 연동 전에는 팝업 안에서만 작동
    private final Map<Button, KeyCode> keyBindings = new LinkedHashMap<>();
    private Button waitingButton;
    private String normalStyle;

    public static Parent create() throws Exception {
        return new KeySetting().createPopup();
    }

    private Parent createPopup() throws Exception {
        FXMLLoader loader = new FXMLLoader(
            KeySetting.class.getResource("fxml/KeySetting.fxml")
        );
        Parent root = loader.load();
        root.setFocusTraversable(true);

        String[] keyButtonIds = {
            "leftKeyButton", "rightKeyButton", "downKeyButton",
            "rotateKeyButton", "dropKeyButton", "pauseKeyButton"
        };

        for (String id : keyButtonIds) {
            Button button = (Button) loader.getNamespace().get(id);
            keyBindings.put(button, KeyCode.valueOf(button.getText()));
            button.setOnAction(e -> {
                stopWaiting();
                waitingButton = button;
                normalStyle = button.getStyle();
                button.setText("PRESS KEY");
                button.setStyle(normalStyle + ";" + WAITING_STYLE);
                root.requestFocus();
            });
        }

        // 버튼의 기본 키 동작보다 먼저 받아 Enter/Space도 새 키로 입력한다.
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (waitingButton == null) {
                return;
            }
            if (e.getCode() != KeyCode.UNDEFINED) {
                keyBindings.put(waitingButton, e.getCode());
                stopWaiting();
            }
            e.consume();
        });

        Button saveButton = (Button) loader.getNamespace().get("saveButton");
        Button resetButton = (Button) loader.getNamespace().get("resetButton");
        Button closeButton = (Button) loader.getNamespace().get("closeButton");

        saveButton.setOnAction(e -> {
            stopWaiting();
            // 저장 연결.
            System.out.println("키 설정 저장 버튼 클릭");
        });

        resetButton.setOnAction(e -> {
            stopWaiting();
            // 초기화 연결.
            System.out.println("키 설정 초기화 버튼 클릭");
        });

        closeButton.setOnAction(e -> {
            stopWaiting();
            if (root.getParent() instanceof Pane parent) {
                parent.getChildren().remove(root);
            }
        });

        return root;
    }

    private void stopWaiting() {
        if (waitingButton != null) {
            waitingButton.setText(keyBindings.get(waitingButton).name());
            waitingButton.setStyle(normalStyle);
            waitingButton = null;
        }
    }
}
