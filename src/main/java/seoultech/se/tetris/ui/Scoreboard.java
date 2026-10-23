package seoultech.se.tetris.ui;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

public class Scoreboard {

    // UI에서 사용하는 난이도. Core/Infra 연동은 Marge후 예정
    public enum Difficulty {
        EASY, NORMAL, HARD
    }

    private static final int MAX_SCORES = 10;

    private static final String SELECTED_STYLE =
        "-fx-background-color: linear-gradient(to bottom, " +
        "#FFF06A 0%, #FFD329 45%, #FF9D0A 100%);";

    private static final String CELL_STYLE =
        "-fx-text-fill: #10204A;" +
        "-fx-font-size: 14px;" +
        "-fx-padding: 0 8 0 8;" +
        "-fx-border-color: #C5D5E3;" +
        "-fx-border-width: 0 0 1 0;";

    public static Parent create() throws Exception {
        return create(Difficulty.NORMAL);
    }

    public static Parent create(Difficulty currentDifficulty) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            Scoreboard.class.getResource("fxml/Scoreboard.fxml")
        );
        Parent root = loader.load();

        Button closeButton = (Button) loader.getNamespace().get("closeButton");
        GridPane scoreGrid = (GridPane) loader.getNamespace().get("scoreGrid");
        ToggleButton easyButton =
            (ToggleButton) loader.getNamespace().get("easyDifficultyButton");
        ToggleButton normalButton =
            (ToggleButton) loader.getNamespace().get("normalDifficultyButton");
        ToggleButton hardButton =
            (ToggleButton) loader.getNamespace().get("hardDifficultyButton");

        // 제목 행은 FXML의 Label을 그대로 사용하며 각 열 너비만큼 넓힘
        for (var node : scoreGrid.getChildren()) {
            if (node instanceof Label label) {
                configureCell(label);
                label.setStyle(label.getStyle()
                    + "-fx-background-color: #B8CBDD;-fx-font-weight: bold;");
            }
        }

        // API 연결 전이므로 실제 기록 대신 상위 10개 기록을 표시할 자리만 제작
        for (int rank = 1; rank <= MAX_SCORES; rank++) {
            scoreGrid.add(createCell(String.valueOf(rank), "rank" + rank), 0, rank);
            scoreGrid.add(createCell("—", "name" + rank), 1, rank);
            scoreGrid.add(createCell("—", "score" + rank), 2, rank);
        }

        ToggleGroup difficultyGroup = new ToggleGroup();
        configureDifficulty(easyButton, Difficulty.EASY, difficultyGroup);
        configureDifficulty(normalButton, Difficulty.NORMAL, difficultyGroup);
        configureDifficulty(hardButton, Difficulty.HARD, difficultyGroup);

        difficultyGroup.selectedToggleProperty().addListener(
            (observable, oldToggle, newToggle) -> {
                if (newToggle == null) {
                    // 선택된 버튼을 다시 눌러도 선택을 해제하지 않는다.
                    if (oldToggle != null) {
                        difficultyGroup.selectToggle(oldToggle);
                    }
                    return;
                }

                /*
                 * API 연동 필요
                 * - newToggle.getUserData()의 Difficulty를 Infra 난이도에 매핑
                 * - 일반/아이템 모드와 선택 난이도에 해당하는 상위 10개 조회
                 * - 기존 name1~name10, score1~score10 Label을 먼저 비운 뒤 갱신
                 * - 점수는 천 단위 쉼표로 표시하고 기록이 없는 칸은 "—" 유지
                 * - 게임 종료 후 새 기록을 보여주는 경우 해당 행을 강조
                 */
            }
        );

        switch (currentDifficulty) {
            case EASY -> easyButton.setSelected(true);
            case NORMAL -> normalButton.setSelected(true);
            case HARD -> hardButton.setSelected(true);
        }

        closeButton.setOnAction(e -> {
            if (root.getParent() instanceof Pane parent) {
                parent.getChildren().remove(root);
            }
        });

        return root;
    }

    private static Label createCell(String text, String id) {
        Label label = new Label(text);
        label.setId(id);
        configureCell(label);
        return label;
    }

    private static void configureCell(Label label) {
        label.setMinSize(0, 0);
        label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.setStyle(label.getStyle() + ";" + CELL_STYLE);
        GridPane.setFillWidth(label, true);
        GridPane.setFillHeight(label, true);
    }

    private static void configureDifficulty(
            ToggleButton button, Difficulty difficulty, ToggleGroup group) {
        button.setToggleGroup(group);
        button.setUserData(difficulty);
        String normalStyle = button.getStyle();
        button.selectedProperty().addListener((observable, oldValue, selected) -> {
            button.setStyle(selected
                ? normalStyle + ";" + SELECTED_STYLE
                : normalStyle);
        });
    }
}
