package seoultech.se.tetris.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import seoultech.se.tetris.game.Cell;

// 보드와 NEXT에서 같은 팔레트를 사용하고 색약 모드에서는 색상만 변경
final class BlockRenderer {
    private BlockRenderer() {}

    static StackPane createCell(Cell cell, double size, boolean colorblindMode) {
        Color fill = colorFor(cell, colorblindMode);
        Rectangle tile = new Rectangle(size, size);
        tile.setFill(fill);
        tile.setStroke(cell.isEmpty() ? Color.web("#1C2B3D") : Color.WHITE);
        tile.setStrokeWidth(cell.isEmpty() ? 0.5 : 1);
        tile.setArcWidth(3);
        tile.setArcHeight(3);
        StackPane square = new StackPane(tile);
        square.setUserData(cell);
        square.setPrefSize(size, size);
        if (cell.isEmpty()) return square;

        if (cell.isItem()) {
            Label mark = new Label(String.valueOf(cell.getMark()));
            mark.getStyleClass().add("block-mark");
            mark.setStyle("-fx-text-fill: #10204A;-fx-font-weight: bold;-fx-font-size: 16px;");
            mark.setMouseTransparent(true);
            square.getChildren().add(mark);
        }
        return square;
    }

    static Color colorFor(Cell cell, boolean colorblindMode) {
        if (cell.isEmpty()) return Color.web("#081423");
        if (cell.isItem()) return Color.web(colorblindMode ? "#F7F7F2" : "#FFD329");
        if (colorblindMode) {
            return Color.web(switch (cell.getType()) {
                case 'I' -> "#56B4E9";
                case 'J' -> "#0072B2";
                case 'L' -> "#E69F00";
                case 'O' -> "#F0E442";
                case 'S' -> "#009E73";
                case 'T' -> "#CC79A7";
                case 'Z' -> "#D55E00";
                default -> "#FFFFFF";
            });
        }
        return Color.web(switch (cell.getType()) {
            case 'I' -> "#39CFE8";
            case 'J' -> "#4775E8";
            case 'L' -> "#F49A38";
            case 'O' -> "#F4DA43";
            case 'S' -> "#63C76B";
            case 'T' -> "#B478DD";
            case 'Z' -> "#E96773";
            default -> "#FFFFFF";
        });
    }

}
