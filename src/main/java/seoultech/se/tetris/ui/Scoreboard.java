package seoultech.se.tetris.ui;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.GameMode;
import seoultech.se.tetris.infra.dto.ScoreDTO;
import seoultech.se.tetris.infra.persistence.ScoreRepository;

import java.util.List;
import java.util.Locale;

public class Scoreboard {

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
        return create(GameMode.NORMAL, Difficulty.NORMAL);
    }

    public static Parent create(GameMode currentMode, Difficulty currentDifficulty) throws Exception {
        return create(currentMode, currentDifficulty, null, () -> {});
    }

    public static Parent create(GameMode currentMode, Difficulty currentDifficulty,
            String highlightedId, Runnable onClose) throws Exception {
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
        ToggleButton normalModeButton =
            (ToggleButton) loader.getNamespace().get("normalModeButton");
        ToggleButton itemModeButton =
            (ToggleButton) loader.getNamespace().get("itemModeButton");
        ScoreRepository repository = new ScoreRepository();

        // 제목 행은 FXML의 Label을 그대로 사용하며 각 열 너비만큼 넓힘
        for (var node : scoreGrid.getChildren()) {
            if (node instanceof Label label) {
                configureCell(label);
                label.setStyle(label.getStyle()
                    + "-fx-background-color: #B8CBDD;-fx-font-weight: bold;");
            }
        }

        // 기록이 없는 순위는 빈칸으로 표시
        for (int rank = 1; rank <= MAX_SCORES; rank++) {
            scoreGrid.add(createCell(String.valueOf(rank), "rank" + rank), 0, rank);
            scoreGrid.add(createCell("—", "name" + rank), 1, rank);
            scoreGrid.add(createCell("—", "score" + rank), 2, rank);
        }

        ToggleGroup difficultyGroup = new ToggleGroup();
        configureChoice(easyButton, Difficulty.EASY, difficultyGroup);
        configureChoice(normalButton, Difficulty.NORMAL, difficultyGroup);
        configureChoice(hardButton, Difficulty.HARD, difficultyGroup);
        ToggleGroup modeGroup = new ToggleGroup();
        configureChoice(normalModeButton, GameMode.NORMAL, modeGroup);
        configureChoice(itemModeButton, GameMode.ITEM, modeGroup);

        Runnable refresh = () -> {
            if (modeGroup.getSelectedToggle() == null || difficultyGroup.getSelectedToggle() == null) {
                return;
            }
            GameMode mode = (GameMode) modeGroup.getSelectedToggle().getUserData();
            Difficulty difficulty = (Difficulty) difficultyGroup.getSelectedToggle().getUserData();
            List<ScoreDTO> scores = repository.getTopScores(mode, difficulty);
            for (int rank = 1; rank <= MAX_SCORES; rank++) {
                ScoreDTO record = rank <= scores.size() ? scores.get(rank - 1) : null;
                boolean highlighted = record != null && record.getId().equals(highlightedId);
                Label name = (Label) root.lookup("#name" + rank);
                Label score = (Label) root.lookup("#score" + rank);
                name.setText(record == null ? "—" : record.getPlayerName());
                name.setTooltip(record == null ? null : new Tooltip(record.getPlayerName()));
                score.setText(record == null ? "—" : formatScore(record.getScore()));
                String[] columns = {"rank", "name", "score"};
                for (int col = 0; col < columns.length; col++) {
                    Label cell = (Label) root.lookup("#" + columns[col] + rank);
                    cell.setUserData(record);
                    cell.setStyle(CELL_STYLE);
                    UIInteraction.markRecord(cell, highlighted, col);
                }
            }
        };
        setupRefresh(modeGroup, refresh);
        setupRefresh(difficultyGroup, refresh);

        switch (currentDifficulty) {
            case EASY -> easyButton.setSelected(true);
            case NORMAL -> normalButton.setSelected(true);
            case HARD -> hardButton.setSelected(true);
        }
        (currentMode == GameMode.ITEM ? itemModeButton : normalModeButton).setSelected(true);

        closeButton.setOnAction(e -> {
            if (root.getParent() instanceof Pane parent) {
                parent.getChildren().remove(root);
            }
            onClose.run();
        });
        ButtonEffects.addPressEffect(closeButton);
        UIInteraction.installNavigation(root, currentMode == GameMode.ITEM ? itemModeButton : normalModeButton);

        return root;
    }

    static String formatScore(int score) {
        return String.format(Locale.US, "%,d", score);
    }

    private static void setupRefresh(ToggleGroup group, Runnable refresh) {
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                group.selectToggle(oldToggle);
            } else if (newToggle != null) {
                refresh.run();
            }
        });
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

    private static void configureChoice(
            ToggleButton button, Object value, ToggleGroup group) {
        button.setToggleGroup(group);
        button.setUserData(value);
        String normalStyle = button.getStyle();
        button.selectedProperty().addListener((observable, oldValue, selected) -> {
            button.setStyle(selected
                ? normalStyle + ";" + SELECTED_STYLE
                : normalStyle);
        });
        ButtonEffects.addPressEffect(button);
    }
}
