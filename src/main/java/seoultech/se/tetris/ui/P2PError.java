package seoultech.se.tetris.ui;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

/** 5초 로딩 표시와 확인 버튼만 담당한다. 연결 판정은 외부에서 받는다. */
public final class P2PError implements AutoCloseable {
    private static final int WAIT_SECONDS = 5;
    private final Pane root;
    private final Timeline timer = new Timeline();
    private final Label reasonLabel;
    private final Button ok;
    private final Button exit;
    private final String reason;
    private int remaining = WAIT_SECONDS;
    private int dots = 1;
    private boolean closed;
    private boolean accepted;

    public P2PError(String reason, BooleanSupplier peerConnected,
            Runnable onLobby, Runnable onMain) throws IOException {
        this.reason = reason;
        FXMLLoader loader = new FXMLLoader(P2PError.class.getResource("fxml/P2PError.fxml"));
        root = loader.load();
        reasonLabel = (Label) loader.getNamespace().get("errorreasonLabel");
        ok = (Button) loader.getNamespace().get("okButton");
        exit = (Button) loader.getNamespace().get("closeButton");
        reasonLabel.setWrapText(true);
        timer.getKeyFrames().add(new KeyFrame(Duration.seconds(1), e -> {
            remaining--; dots = dots % 3 + 1;
            update();
            if (remaining <= 0) { timer.stop(); ok.requestFocus(); }
        }));
        timer.setCycleCount(WAIT_SECONDS);
        Runnable confirm = () -> {
            if (closed || accepted || remaining > 0) return;
            accepted = true; close();
            // 외부에서 받은 연결 상태로 복귀 위치를 결정한다.
            if (peerConnected.getAsBoolean()) onLobby.run();
            else onMain.run();
        };
        ok.setOnAction(e -> confirm.run()); exit.setOnAction(e -> confirm.run());
        ButtonEffects.addPressEffect(ok); ButtonEffects.addPressEffect(exit);
        update();
        UIInteraction.installNavigation(root, null);
        root.sceneProperty().addListener((o, old, scene) -> {
            if (scene != null && !closed && remaining > 0) timer.play();
            else if (old != null && scene == null) close();
        });
    }

    public Pane getRoot() { return root; }
    private void update() {
        boolean ready = remaining <= 0;
        ok.setVisible(ready); ok.setManaged(ready); ok.setDisable(!ready);
        exit.setVisible(ready); exit.setManaged(ready); exit.setDisable(!ready);
        reasonLabel.setText(reason + (ready ? "" : "\nLoading " + ".".repeat(dots)));
    }
    @Override public void close() { closed = true; timer.stop(); }
}
