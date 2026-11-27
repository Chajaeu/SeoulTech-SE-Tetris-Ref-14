package seoultech.se.tetris.ui;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;

/** 로비 표시와 버튼 이벤트만 담당한다. 확정 상태는 Infra에서 전달받는다. */
public final class P2PLobby {
    private static final String GOLD = ";-fx-background-color: linear-gradient(to bottom, #FFF06A 0%, #FFD329 45%, #FF9D0A 100%);";
    private final Pane root;
    private final ToggleButton serverReady;
    private final ToggleButton clientReady;
    private final ToggleButton itemOn;
    private final ToggleButton itemOff;
    private final ToggleButton timeOn;
    private final ToggleButton timeOff;
    private final Button start;
    private final boolean server;

    public P2PLobby(boolean server, Consumer<Boolean> onReady,
            BiConsumer<Boolean, Boolean> onMode, Runnable onStart, Runnable onBack) throws IOException {
        this.server = server;
        FXMLLoader loader = new FXMLLoader(P2PLobby.class.getResource("fxml/P2PLobby.fxml"));
        root = loader.load();
        // 기존 FXML ID를 그대로 사용한다.
        serverReady = (ToggleButton) loader.getNamespace().get("itemModeButton1");
        clientReady = (ToggleButton) loader.getNamespace().get("timeModeOnButton1");
        itemOn = (ToggleButton) loader.getNamespace().get("itemModeButton");
        itemOff = (ToggleButton) loader.getNamespace().get("normalModeButton");
        timeOn = (ToggleButton) loader.getNamespace().get("timeModeOnButton");
        timeOff = (ToggleButton) loader.getNamespace().get("timeModeOffButton");
        start = (Button) loader.getNamespace().get("startButton");
        Button back = (Button) loader.getNamespace().get("backButton");
        Button close = (Button) loader.getNamespace().get("closeButton");
        gold(serverReady); gold(clientReady);
        pair(itemOff, itemOn); pair(timeOff, timeOn);
        ToggleButton ownReady = server ? serverReady : clientReady;
        ownReady.setOnAction(e -> onReady.accept(ownReady.isSelected()));
        for (ToggleButton button : new ToggleButton[] {itemOn, itemOff, timeOn, timeOff}) {
            button.setDisable(!server);
            button.setOnAction(e -> onMode.accept(itemOn.isSelected(), timeOn.isSelected()));
        }
        // 현재 Infra의 시작 확정 통지가 우선이다. 이 버튼이 게임을 직접 실행하지 않는다.
        start.setVisible(server); start.setManaged(server);
        start.setOnAction(e -> onStart.run());
        back.setOnAction(e -> onBack.run()); close.setOnAction(e -> onBack.run());
        root.lookupAll("*").forEach(node -> {
            if (node instanceof ButtonBase button) ButtonEffects.addPressEffect(button);
        });
        updateState(false, false, false, false, false);
        UIInteraction.installNavigation(root, back);
    }

    public Pane getRoot() { return root; }

    /** JavaFX 스레드에서 호출한다. setSelected는 송신 콜백을 발생시키지 않는다. */
    public void updateState(boolean connected, boolean hostReady, boolean guestReady,
            boolean itemMode, boolean timeMode) {
        serverReady.setSelected(hostReady); clientReady.setSelected(guestReady);
        serverReady.setDisable(!connected || !server); clientReady.setDisable(!connected || server);
        (itemMode ? itemOn : itemOff).setSelected(true);
        (timeMode ? timeOn : timeOff).setSelected(true);
        start.setDisable(!server || !connected || !hostReady || !guestReady);
    }

    private static void pair(ToggleButton off, ToggleButton on) {
        ToggleGroup group = new ToggleGroup();
        off.setToggleGroup(group); on.setToggleGroup(group);
        gold(off); gold(on);
        group.selectedToggleProperty().addListener((o, old, selected) -> {
            if (selected == null && old != null) group.selectToggle(old);
        });
        off.setSelected(true);
    }

    private static void gold(ToggleButton button) {
        String original = button.getStyle();
        button.selectedProperty().addListener((o, old, selected) ->
            button.setStyle(selected ? original + GOLD : original));
        if (button.isSelected()) button.setStyle(original + GOLD);
    }
}
