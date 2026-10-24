package seoultech.se.tetris.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;

public class Setting {

    // 선택된 옵션 표시
    private static final String SELECTED_STYLE =
        "-fx-background-color: linear-gradient(to bottom, " +
        "#FFF06A 0%, #FFD329 45%, #FF9D0A 100%);" +
        "-fx-text-fill: #10204A;" +
        "-fx-font-weight: bold;";


    public static Parent create(UIResolution.Size currentResolution)
            throws Exception {

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

        ToggleButton saveButton =
            (ToggleButton) loader.getNamespace().get(
                "saveButton"
            );

        ToggleButton resetButton =
            (ToggleButton) loader.getNamespace().get(
                "resetButton"
            );

        // 해상도 선택

        ToggleGroup resolutionGroup = new ToggleGroup();

        smallResolutionButton.setToggleGroup(resolutionGroup);
        mediumResolutionButton.setToggleGroup(resolutionGroup);
        largeResolutionButton.setToggleGroup(resolutionGroup);


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

        ToggleGroup colorblindGroup = new ToggleGroup();

        colorblindOnButton.setToggleGroup(colorblindGroup);
        colorblindOFFButton.setToggleGroup(colorblindGroup);

        // bd 연결전 기본값 추후 수정필요
        colorblindOFFButton.setSelected(true);


        // 선택된 옵션 표시
        setupSelectionStyle(
            smallResolutionButton,
            mediumResolutionButton,
            largeResolutionButton
        );

        setupSelectionStyle(
            colorblindOnButton,
            colorblindOFFButton
        );

        //닫기
        closeButton.setOnAction(e -> close(root));


        // 키 설정
        keySettingButton.setOnAction(e -> {
            try {
                openKeySettings((Pane) root);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });


        // 스코어보드 초기화
        scoreboardResetButton.setOnAction(e -> {
            System.out.println("스코어보드 초기화");
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

            System.out.println(
                "선택된 해상도: " + selectedResolution
            );

            System.out.println(
                "색약 모드: " + colorblindMode
            );

            // 추후 Infra 연결 필요

            close(root);
        });


        // 설정 초기화
        resetButton.setOnAction(e -> {

            mediumResolutionButton.setSelected(true);
            colorblindOFFButton.setSelected(true);
        });


        return root;
    }


    // 팝업 닫기
    private static void close(Parent root) {

        if (root.getParent() instanceof Pane parent) {
            parent.getChildren().remove(root);
        }
    }

    private static void openKeySettings(Pane settingsPane) throws Exception {
        if (settingsPane.lookup("#keySettingPopup") != null) {
            return;
        }

        Pane keySettings = (Pane) KeySetting.create();
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


    // 선택된 해상도 반환(bd로 반환 연결 필요)
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


    // 선택된 ToggleButton 표시
    private static void setupSelectionStyle(
            ToggleButton... buttons) {

        for (ToggleButton button : buttons) {

            String normalStyle = button.getStyle();

            button.selectedProperty().addListener(
                (observable, oldValue, selected) -> {

                    if (selected) {

                        button.setStyle(
                            normalStyle + SELECTED_STYLE
                        );

                    } else {

                        button.setStyle(normalStyle);
                    }
                }
            );


            // 처음부터 선택되어 있는 버튼도 표시
            if (button.isSelected()) {

                button.setStyle(
                    normalStyle + SELECTED_STYLE
                );
            }
        }
    }
}
