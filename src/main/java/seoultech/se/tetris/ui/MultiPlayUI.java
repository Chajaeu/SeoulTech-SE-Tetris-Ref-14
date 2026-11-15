package seoultech.se.tetris.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class MultiPlayUI {
    private MultiPlayUI() {}

    public static void showLocal(Stage stage, UIResolution.Size resolution,
            boolean itemMode, boolean timeMode) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            MultiPlayUI.class.getResource("fxml/MultiPlayUI.fxml"));
        Pane designRoot = loader.load();

        Label timerLabel = (Label) loader.getNamespace().get("timerLabel");
        timerLabel.setVisible(timeMode);
        timerLabel.setManaged(timeMode);

        Label networkQualityLabel =
            (Label) loader.getNamespace().get("networkQualityLabel");
        networkQualityLabel.setVisible(false);
        networkQualityLabel.setManaged(false);

        designRoot.getProperties().put("itemMode", itemMode);
        designRoot.getProperties().put("timeMode", timeMode);


        double scale = Math.min(resolution.getWidth() / designRoot.getPrefWidth(),
            resolution.getHeight() / designRoot.getPrefHeight());
        designRoot.setScaleX(scale);
        designRoot.setScaleY(scale);
        StackPane screen = new StackPane(designRoot);
        stage.setScene(new Scene(screen, resolution.getWidth(), resolution.getHeight()));
        stage.setResizable(false);
        stage.sizeToScene();
        stage.show();
    }
}
