package seoultech.se.tetris.ui;

import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Alert;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;
import seoultech.se.tetris.infra.persistence.ScoreRepository;
import seoultech.se.tetris.infra.persistence.SettingsRepository;
import seoultech.se.tetris.infra.dto.SettingsDTO;

public class Setting {

    // 선택된 옵션 표시
    private static final String SELECTED_STYLE =
        "-fx-background-color: linear-gradient(to bottom, " +
        "#FFF06A 0%, #FFD329 45%, #FF9D0A 100%);" +
        "-fx-text-fill: #10204A;" +
        "-fx-font-weight: bold;";


    public static Parent create(UIResolution.Size currentResolution)
            throws Exception {
        return create(currentResolution, selectedResolution -> {});
    }

    public static Parent create(UIResolution.Size currentResolution,
            Consumer<UIResolution.Size> onSaved) throws Exception {
        return create(currentResolution, onSaved, () -> {});
    }

    public static Parent create(UIResolution.Size currentResolution,
            Consumer<UIResolution.Size> onSaved, Runnable onResetSaved) throws Exception {

        FXMLLoader loader = new FXMLLoader(
            Setting.class.getResource("fxml/Setting.fxml")
        );

        Parent root = loader.load();


        // FXML 요소
        Button closeButton =
            (Button) loader.getNamespace().get("closeButton");

        ToggleButton smallResolutionButton =
            (ToggleButton) loader.getNamespace().get(
                "smallResolutionButton"
            );

        ToggleButton mediumResolutionButton =
            (ToggleButton) loader.getNamespace().get(
                "mediumResolutionButton"
            );

        ToggleButton largeResolutionButton =
            (ToggleButton) loader.getNamespace().get(
                "largeResolutionButton"
            );

        Button keySettingButton =
            (Button) loader.getNamespace().get(
                "keySettingButton"
            );

        Button scoreboardResetButton =
            (Button) loader.getNamespace().get(
                "scoreboardResetButton"
            );

        ToggleButton colorblindOnButton =
            (ToggleButton) loader.getNamespace().get(
                "colorblindOnButton"
            );

        ToggleButton colorblindOFFButton =
            (ToggleButton) loader.getNamespace().get(
                "colorblindOFFButton"
            );

        Button saveButton =
            (Button) loader.getNamespace().get(
                "saveButton"
            );

        Button resetButton =
            (Button) loader.getNamespace().get(
                "resetButton"
            );

        // 해상도 선택

        setupSelection(
            smallResolutionButton,
            mediumResolutionButton,
            largeResolutionButton
        );


        switch (currentResolution) {

            case SMALL:
                smallResolutionButton.setSelected(true);
                break;

            case MEDIUM:
                mediumResolutionButton.setSelected(true);
                break;

            case LARGE:
                largeResolutionButton.setSelected(true);
                break;
        }

        // 색약 모드 ON / OFF

        setupSelection(colorblindOnButton, colorblindOFFButton);

        // 마지막으로 저장한 색약 모드 표시
        SettingsRepository settingsRepository = new SettingsRepository();
        SettingsDTO[] draft = {settingsRepository.loadSettings()};
        boolean[] keysChanged = {false};
        boolean[] settingsResetPending = {false};
        if (draft[0].isColorblindMode()) {
            colorblindOnButton.setSelected(true);
        } else {
            colorblindOFFButton.setSelected(true);
        }


        // 메인 메뉴와 같은 버튼 눌림 효과
        root.lookupAll("*").forEach(node -> {
            if (node instanceof ButtonBase button) {
                ButtonEffects.addPressEffect(button);
            }
        });

        //닫기
        closeButton.setOnAction(e -> close(root));


        // 키 설정
        keySettingButton.setOnAction(e -> {
            try {
                openKeySettings((Pane) root, draft[0], keys -> {
                    // 키 설정창의 Save는 즉시 저장. 전체 초기화 예약 중일 때만 최종 Save까지 대기
                    if (!settingsResetPending[0]) {
                        SettingsDTO settings = settingsRepository.loadSettings();
                        KeySetting.copyKeys(keys, settings);
                        if (!settingsRepository.saveSettings(settings)) {
                            return false;
                        }
                    }
                    KeySetting.copyKeys(keys, draft[0]);
                    keysChanged[0] = settingsResetPending[0];
                    return true;
                }, settingsResetPending[0]);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });


        // 초기화는 예약만 표시하고, Save에서 실제 기록 삭제
        boolean[] scoreboardResetPending = {false};
        String scoreboardResetStyle = scoreboardResetButton.getStyle();
        scoreboardResetButton.setOnAction(e -> {
            scoreboardResetPending[0] = true;
            scoreboardResetButton.setStyle(scoreboardResetStyle + ";" + SELECTED_STYLE);
        });


        // 저장
        saveButton.setOnAction(e -> {

            UIResolution.Size selectedResolution =
                getSelectedResolution(
                    smallResolutionButton,
                    mediumResolutionButton,
                    largeResolutionButton,
                    currentResolution
                );

            boolean colorblindMode =
                colorblindOnButton.isSelected();

            // 전체 초기화도 Save 전까지 파일에 반영하지 않음
            SettingsDTO settings = settingsResetPending[0]
                ? SettingsDTO.defaults() : settingsRepository.loadSettings();
            if (keysChanged[0]) {
                KeySetting.copyKeys(draft[0], settings);
            }
            settings.setDisplaySize(selectedResolution.toDisplaySize());
            settings.setColorblindMode(colorblindMode);
            if (!settingsRepository.saveSettings(settings)) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.initOwner(root.getScene().getWindow());
                alert.setTitle("설정 저장 실패");
                alert.setHeaderText("설정을 저장하지 못했습니다.");
                alert.setContentText("설정 저장 위치를 확인한 뒤 다시 시도해주세요.");
                alert.showAndWait();
                return;
            }

            if (scoreboardResetPending[0]) {
                if (!new ScoreRepository().resetScoreboard()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.initOwner(root.getScene().getWindow());
                    alert.setTitle("스코어보드 초기화 실패");
                    alert.setHeaderText("기록을 초기화하지 못했습니다.");
                    alert.setContentText("기록 저장 위치를 확인한 뒤 다시 시도해주세요.");
                    alert.showAndWait();
                    return;
                }
                scoreboardResetPending[0] = false;
                scoreboardResetButton.setStyle(scoreboardResetStyle);
            }

            close(root);
            if (settingsResetPending[0]) {
                onResetSaved.run();
            }
            onSaved.accept(selectedResolution);
        });


        // 전체 설정의 기본값을 예약하고 Save에서 실제 저장
        String resetStyle = resetButton.getStyle();
        resetButton.setOnAction(e -> {
            draft[0] = SettingsDTO.defaults();
            keysChanged[0] = true;
            settingsResetPending[0] = true;
            mediumResolutionButton.setSelected(true);
            colorblindOFFButton.setSelected(true);
            resetButton.setStyle(resetStyle + ";" + SELECTED_STYLE);
        });


        UIInteraction.installNavigation(root, switch (currentResolution) {
            case SMALL -> smallResolutionButton;
            case MEDIUM -> mediumResolutionButton;
            case LARGE -> largeResolutionButton;
        });
        return root;
    }


    // 팝업 닫기
    private static void close(Parent root) {

        if (root.getParent() instanceof Pane parent) {
            parent.getChildren().remove(root);
        }
    }

    private static void openKeySettings(Pane settingsPane, SettingsDTO initialSettings,
            Predicate<SettingsDTO> onSave, boolean settingsResetPending) throws Exception {
        if (settingsPane.lookup("#keySettingPopup") != null) {
            return;
        }

        String savedMessage = settingsResetPending
            ? "키 설정을 반영했습니다. 전체 초기화는 설정 화면 SAVE로 적용됩니다."
            : "키 설정을 저장했습니다.";
        Pane keySettings = (Pane) KeySetting.create(initialSettings, onSave, savedMessage);
        keySettings.setId("keySettingPopup");
        keySettings.setLayoutX(
            (settingsPane.getPrefWidth() - keySettings.getPrefWidth()) / 2
        );
        keySettings.setLayoutY(
            (settingsPane.getPrefHeight() - keySettings.getPrefHeight()) / 2
        );
        settingsPane.getChildren().add(keySettings);
        keySettings.requestFocus();
    }


    // 선택된 해상도 반환
    private static UIResolution.Size getSelectedResolution(
        ToggleButton smallResolutionButton,
        ToggleButton mediumResolutionButton,
        ToggleButton largeResolutionButton,
        UIResolution.Size currentResolution) {

        if (smallResolutionButton.isSelected()) {
            return UIResolution.Size.SMALL;
        }
        if (mediumResolutionButton.isSelected()) {
            return UIResolution.Size.MEDIUM;
        }
        if (largeResolutionButton.isSelected()) {
            return UIResolution.Size.LARGE;
        }

        return currentResolution;
    }


    // 선택된 옵션 표시와 선택 유지
    private static void setupSelection(
            ToggleButton... buttons) {

        ToggleGroup group = new ToggleGroup();
        for (ToggleButton button : buttons) {

            button.setToggleGroup(group);
            String normalStyle = button.getStyle();

            button.selectedProperty().addListener(
                (observable, oldValue, selected) -> {

                    if (selected) {

                        button.setStyle(
                            normalStyle + ";" + SELECTED_STYLE
                        );

                    } else {

                        button.setStyle(normalStyle);
                    }
                }
            );


            // 처음부터 선택되어 있는 버튼도 표시
            if (button.isSelected()) {

                button.setStyle(
                    normalStyle + ";" + SELECTED_STYLE
                );
            }
        }

        // 이미 선택된 버튼을 다시 눌러도 선택과 색상 유지
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                group.selectToggle(oldToggle);
            }
        });
    }
}
