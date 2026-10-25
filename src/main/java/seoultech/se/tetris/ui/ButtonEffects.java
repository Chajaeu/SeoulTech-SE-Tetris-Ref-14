package seoultech.se.tetris.ui;

import javafx.scene.control.ButtonBase;

final class ButtonEffects {

    private ButtonEffects() {}

    static void addPressEffect(ButtonBase button) {
        UIInteraction.installButton(button);
        button.setOnMousePressed(e -> {
            button.setTranslateY(3);
            button.setScaleX(0.98);
            button.setScaleY(0.98);
        });

        button.setOnMouseReleased(e -> {
            button.setTranslateY(0);
            button.setScaleX(1.0);
            button.setScaleY(1.0);
        });
    }
}
