package seoultech.se.tetris.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import seoultech.se.tetris.infra.dto.SettingsDTO;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

public class KeySetting {

    private static final String WAITING_STYLE =
        "-fx-background-color: #FFD329;";

    // Save 전에는 팝업 안의 값만 변경하며 X로 닫으면 변경 취소
    private final Map<Button, KeyCode> keyBindings = new LinkedHashMap<>();
    private Button waitingButton;
    private String normalStyle;

    public static Parent create() throws Exception {
        SettingsRepository repository = new SettingsRepository();
        return create(repository.loadSettings(), keys -> {
            SettingsDTO settings = repository.loadSettings();
            copyKeys(keys, settings);
            return repository.saveSettings(settings);
        });
    }

    static Parent create(SettingsDTO initialSettings, Predicate<SettingsDTO> onSave) throws Exception {
        return create(initialSettings, onSave, "키 설정을 저장했습니다.");
    }

    static Parent create(SettingsDTO initialSettings, Predicate<SettingsDTO> onSave,
            String savedMessage) throws Exception {
        return new KeySetting().createPopup(initialSettings, onSave, savedMessage);
    }

    private Parent createPopup(SettingsDTO initialSettings, Predicate<SettingsDTO> onSave,
            String savedMessage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            KeySetting.class.getResource("fxml/KeySetting.fxml")
        );
        Parent root = loader.load();
        root.setFocusTraversable(true);
        Label status = (Label) loader.getNamespace().get("keySettingStatus");

        String[] keyButtonIds = {
            "leftKeyButton", "rightKeyButton", "downKeyButton",
            "rotateKeyButton", "dropKeyButton", "pauseKeyButton"
        };

        for (String id : keyButtonIds) {
            Button button = (Button) loader.getNamespace().get(id);
            keyBindings.put(button, KeyCode.UNDEFINED);
            button.setOnAction(e -> {
                stopWaiting();
                status.setText("");
                waitingButton = button;
                normalStyle = button.getStyle();
                button.setText("PRESS KEY");
                button.setStyle(normalStyle + ";" + WAITING_STYLE);
                button.requestFocus();
            });
        }
        displayKeys(initialSettings);

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
            SettingsDTO keys = SettingsDTO.defaults();
            var buttons = keyBindings.keySet().toArray(Button[]::new);
            keys.setKeyLeft(keyBindings.get(buttons[0]).name());
            keys.setKeyRight(keyBindings.get(buttons[1]).name());
            keys.setKeyDown(keyBindings.get(buttons[2]).name());
            keys.setKeyRotate(keyBindings.get(buttons[3]).name());
            keys.setKeyDrop(keyBindings.get(buttons[4]).name());
            keys.setKeyPause(keyBindings.get(buttons[5]).name());
            status.setStyle("-fx-text-fill: #B00020; -fx-font-size: 11px;");
            if (!keys.isValid()) {
                status.setText("키가 중복되었거나 지원하지 않는 키입니다. 다른 키를 선택해주세요.");
                return;
            }
            if (!onSave.test(keys)) {
                status.setText("키 설정을 저장하지 못했습니다. 저장 위치를 확인한 뒤 다시 시도해주세요.");
                return;
            }
            status.setStyle("-fx-text-fill: #10204A; -fx-font-size: 11px;");
            status.setText(savedMessage);
        });

        resetButton.setOnAction(e -> {
            stopWaiting();
            displayKeys(SettingsDTO.defaults());
            status.setStyle("-fx-text-fill: #10204A; -fx-font-size: 11px;");
            status.setText("기본 키를 선택했습니다. SAVE를 눌러 반영해주세요.");
        });

        closeButton.setOnAction(e -> {
            stopWaiting();
            if (root.getParent() instanceof Pane parent) {
                parent.getChildren().remove(root);
            }
        });

        // 설정 화면과 같은 버튼 눌림 효과
        root.lookupAll(".button").forEach(node -> {
            if (node instanceof Button button) {
                ButtonEffects.addPressEffect(button);
            }
        });

        UIInteraction.installNavigation(root, (Button) loader.getNamespace().get("leftKeyButton"),
            () -> waitingButton != null);
        return root;
    }

    private void displayKeys(SettingsDTO settings) {
        String[] keys = {settings.getKeyLeft(), settings.getKeyRight(), settings.getKeyDown(),
            settings.getKeyRotate(), settings.getKeyDrop(), settings.getKeyPause()};
        int index = 0;
        for (Button button : keyBindings.keySet()) {
            KeyCode code = KeyCode.valueOf(keys[index++]);
            keyBindings.put(button, code);
            button.setText(code.name());
        }
    }

    static void copyKeys(SettingsDTO source, SettingsDTO target) {
        target.setKeyLeft(source.getKeyLeft());
        target.setKeyRight(source.getKeyRight());
        target.setKeyDown(source.getKeyDown());
        target.setKeyRotate(source.getKeyRotate());
        target.setKeyDrop(source.getKeyDrop());
        target.setKeyPause(source.getKeyPause());
    }

    private void stopWaiting() {
        if (waitingButton != null) {
            waitingButton.setText(keyBindings.get(waitingButton).name());
            waitingButton.setStyle(normalStyle);
            waitingButton = null;
        }
    }
}
