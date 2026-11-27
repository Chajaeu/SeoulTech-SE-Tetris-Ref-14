package seoultech.se.tetris.ui;

import java.io.IOException;
import java.util.Map;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;

/** P2P 화면 연결과 통신 연결 지점. 소켓·공격·승패 규칙은 재구현하지 않는다. */
public final class P2PGame implements AutoCloseable {
    private enum Phase { JOIN, CONNECTING, LOBBY, PLAYING, RESULT, ERROR, CLOSED }
    private final Stage stage;
    private final UIResolution.Size resolution;
    private final Pane menuRoot;
    private final Scene menuScene;
    private final ToggleButton serverButton;
    private final ToggleButton clientButton;
    private final TextField ipField;
    private final CheckBox recentIpButton;
    private final Button connectButton;
    private final Label loadingLabel = new Label();
    private final Timeline loading = new Timeline();
    private final EventHandler<WindowEvent> onHidden = e -> close();
    private Pane overlay;
    private P2PLobby lobby;
    private P2PError error;
    private MultiPlayUI gameUi;
    private Phase phase = Phase.JOIN;
    private boolean server;
    private boolean connected;
    private boolean serverReady;
    private boolean clientReady;
    private boolean itemMode;
    private boolean timeMode;
    private String recentIp;
    private String beforeRecent = "";
    private int dots;

    private P2PGame(Stage stage, UIResolution.Size resolution, Pane menuRoot,
            Pane overlay, Pane joinRoot, Map<String, Object> nodes) {
        this.stage = stage; this.resolution = resolution; this.menuRoot = menuRoot;
        this.menuScene = stage.getScene(); this.overlay = overlay;
        serverButton = (ToggleButton) nodes.get("serverModeButton");
        clientButton = (ToggleButton) nodes.get("clientModeButton");
        ipField = (TextField) nodes.get("serverIpField");
        recentIpButton = (CheckBox) nodes.get("recentIpCheckBox");
        connectButton = (Button) nodes.get("startButton");
        loadingLabel.setId("p2pLoadingLabel");
        loadingLabel.setLayoutX(83); loadingLabel.setLayoutY(278); loadingLabel.setPrefWidth(434);
        loadingLabel.setStyle("-fx-text-fill: #10204A; -fx-font-size: 12px;");
        joinRoot.getChildren().add(loadingLabel);
        loading.getKeyFrames().add(new KeyFrame(Duration.seconds(1), e -> {
            dots = dots % 3 + 1;
            loadingLabel.setText("Loading " + ".".repeat(dots));
        }));
        loading.setCycleCount(Animation.INDEFINITE);
        connectButton.setDisable(false);
        connectButton.setOnAction(e -> requestConnect());
        ((Button) nodes.get("backButton")).setOnAction(e -> returnToMain());
        ((Button) nodes.get("closeButton")).setOnAction(e -> returnToMain());
        recentIpButton.setOnAction(e -> {
            if (recentIpButton.isSelected()) {
                beforeRecent = ipField.getText(); ipField.setText(recentIp);
            } else ipField.setText(beforeRecent);
        });
        ipField.textProperty().addListener((o, old, value) -> {
            if (recentIpButton.isSelected() && !value.equals(recentIp)) recentIpButton.setSelected(false);
        });
        updateRecentIp(null);
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
        // todo : infra 와 연동 필요
        // 최근 접속 IP를 조회하여 onRecentIpLoaded()로 전달한다.
    }

    public static P2PGame bindJoin(Stage stage, UIResolution.Size resolution,
            Pane menuRoot, Pane overlay, Pane joinRoot, Map<String, Object> nodes) {
        return new P2PGame(stage, resolution, menuRoot, overlay, joinRoot, nodes);
    }

    private void requestConnect() {
        if (phase != Phase.JOIN) return;
        server = serverButton.isSelected();
        String ip = ipField.getText().trim();
        if (!server && ip.isBlank()) {
            loadingLabel.setText("서버 IP를 입력해 주세요."); ipField.requestFocus(); return;
        }
        phase = Phase.CONNECTING;
        setConnecting(true);
        // todo : infra 와 연동 필요
        // server이면 host(), 아니면 ip로 join()을 요청한다.
        // 주소 검증·실제 연결 판단은 기존 Infra를 사용한다.
        // 성공 시 onConnected(), 실패 시 onConnectionFailed()를 호출한다.
    }

    public void onRecentIpLoaded(String ip) { dispatch(() -> updateRecentIp(ip)); }
    private void updateRecentIp(String ip) {
        recentIp = ip == null || ip.isBlank() ? null : ip;
        recentIpButton.setSelected(false);
        recentIpButton.setText("Recent IP : " + (recentIp == null ? "없음" : recentIp));
        recentIpButton.setDisable(recentIp == null || phase == Phase.CONNECTING);
    }
    private void setConnecting(boolean value) {
        serverButton.setDisable(value); clientButton.setDisable(value); ipField.setDisable(value);
        recentIpButton.setDisable(value || recentIp == null); connectButton.setDisable(value);
        loading.stop();
        if (value) {
            dots = 1; loadingLabel.setText("Loading ."); loading.playFromStart();
        } else loadingLabel.setText("");
    }

    public void onConnected() {
        dispatch(() -> {
            if (phase != Phase.CONNECTING) return;
            connected = true; setConnecting(false); showLobby();
        });
    }
    public void onConnectionFailed(String reason) {
        dispatch(() -> {
            if (phase != Phase.CONNECTING) return;
            connected = false; phase = Phase.JOIN; setConnecting(false);
            loadingLabel.setText(reason);
        });
    }
    public void onLobbyState(boolean peerConnected, boolean hostReady, boolean guestReady,
            boolean itemMode, boolean timeMode) {
        dispatch(() -> {
            connected = peerConnected; serverReady = hostReady; clientReady = guestReady;
            this.itemMode = itemMode; this.timeMode = timeMode;
            if (phase == Phase.LOBBY && lobby != null)
                lobby.updateState(connected, serverReady, clientReady, itemMode, timeMode);
        });
    }
    private void showLobby() throws IOException {
        if (!connected) { returnToMain(); return; }
        stopGameView();
        lobby = new P2PLobby(server, this::requestReady, this::requestMode,
            this::requestStart, this::returnToMain);
        lobby.updateState(connected, serverReady, clientReady, itemMode, timeMode);
        showPopup(menuRoot, lobby.getRoot());
        stage.setScene(menuScene); stage.sizeToScene(); phase = Phase.LOBBY;
    }
    private void requestReady(boolean ready) {
        if (!connected || phase != Phase.LOBBY) return;
        // todo : infra 와 연동 필요
        // setReady(ready)를 요청한다. 상대 상태를 UI에서 임의로 바꾸지 않는다.
    }
    private void requestMode(boolean selectedItem, boolean selectedTime) {
        if (!server || !connected || phase != Phase.LOBBY) return;
        // todo : infra 와 연동 필요
        // 기존 VersusMode 규격으로 변환하여 서버 모드 변경을 요청한다.
        // 모드 확정과 Ready 초기화는 Infra의 onLobbyState 통지를 따른다.
    }
    private void requestStart() {
        if (!server || !connected || phase != Phase.LOBBY || !serverReady || !clientReady) return;
        // todo : infra 와 연동 필요
        // 기존 Infra의 양측 Ready 후 시작 확정 처리를 우선 사용한다.
        // 여기서 onMatchStarted()나 로컬 게임을 직접 호출하지 않는다.
    }

    public void onMatchStarted(boolean selectedItem, boolean selectedTime, int durationSeconds) {
        dispatch(() -> {
            if (!connected || phase != Phase.LOBBY) return;
            removePopup(); itemMode = selectedItem; timeMode = selectedTime; phase = Phase.PLAYING;
            gameUi = MultiPlayUI.showP2P(stage, resolution, server, itemMode, timeMode,
                durationSeconds, this);
        });
    }
    public void onOpponentState(Cell[][] board, Cell[][] next, int score, Cell[][] pendingAttack) {
        // Cell은 불변 객체다. runLater 전에 행 배열을 복사한다.
        Cell[][] boardCopy = copy(board); Cell[][] nextCopy = copy(next); Cell[][] pendingCopy = copy(pendingAttack);
        dispatch(() -> {
            if (phase == Phase.PLAYING && gameUi != null)
                gameUi.updateOpponent(boardCopy, nextCopy, score, pendingCopy);
        });
    }
    public void onMatchStatus(int remainingSeconds, long rttMillis) {
        dispatch(() -> {
            if (phase == Phase.PLAYING && gameUi != null)
                gameUi.updateP2PStatus(remainingSeconds, rttMillis);
        });
    }

    // 아래 세 메서드는 기존 PlayUI의 게임 업데이트 스레드에서 호출된다.
    void beforeGameUpdate(Game game) {
        if (phase != Phase.PLAYING || !connected) return;
        // todo : infra 와 연동 필요
        // 기존 브리지의 applyReceivedAttacks(game)를 호출한다.
    }
    void afterGameUpdate(Game game) {
        if (phase != Phase.PLAYING) return;
        // todo : infra 와 연동 필요
        // captureLocalGame(game) 및 전송 대기 공격 재시도를 연결한다.
    }
    void afterLinesCleared(Game game) {
        if (phase != Phase.PLAYING) return;
        // todo : infra 와 연동 필요
        // 기존 브리지의 afterDeleteCells(game)를 연결한다.
        // game.attack()을 여기서 중복 호출하지 않는다.
    }
    void onLocalGameOver(Game game) {
        // todo : infra 와 연동 필요
        // Core가 판정한 내 종료를 전달한다. 양측 승패 확정은 기존 처리와 합의한다.
        // 최종 결과는 onMatchFinished()로 전달받는다.
    }
    public void onMatchFinished(Result.Winner winner, int score1, int score2) {
        dispatch(() -> {
            if (phase != Phase.PLAYING || gameUi == null) return;
            phase = Phase.RESULT;
            gameUi.showP2PResult(winner, score1, score2, this::requestRematch, this::returnToMain);
        });
    }
    private void requestRematch() {
        if (!connected) { returnToMain(); return; }
        // todo : infra 와 연동 필요
        // 경기 종료 후 WAITING 상태와 Ready 초기화는 Infra의 기존 처리로 받는다.
        dispatch(this::showLobby);
    }
    public void onDisconnected(String reason) {
        dispatch(() -> { connected = false; showNetworkError(reason); });
    }
    public void onNetworkError(String reason) { dispatch(() -> showNetworkError(reason)); }
    private void showNetworkError(String reason) throws IOException {
        if (phase == Phase.ERROR) return;
        loading.stop(); phase = Phase.ERROR;
        Pane parent = menuRoot;
        if (gameUi != null && gameUi.designRoot.getScene() == stage.getScene()) {
            gameUi.suspendForNetworkIssue(); parent = gameUi.designRoot;
        }
        P2PError popup = new P2PError(reason, this::isPeerConnected,
            () -> dispatch(this::showLobby), this::returnToMain);
        showPopup(parent, popup.getRoot()); error = popup;
    }
    private boolean isPeerConnected() {
        // todo : infra 와 연동 필요
        // OK 확인 시점에 network.isConnected()를 우선 조회하도록 연결한다.
        // 현재 값은 외부의 연결/단절 통지에서만 변경된다.
        return connected;
    }

    private void showPopup(Pane parent, Parent content) {
        removePopup();
        StackPane layer = new StackPane(content);
        layer.setId("gameSetupPopup");
        layer.setPrefSize(parent.getPrefWidth(), parent.getPrefHeight());
        layer.resize(parent.getPrefWidth(), parent.getPrefHeight()); layer.setPickOnBounds(true);
        overlay = layer; parent.getChildren().add(layer);
    }
    private void removePopup() {
        if (error != null) { error.close(); error = null; }
        if (overlay != null && overlay.getParent() instanceof Pane parent) parent.getChildren().remove(overlay);
        overlay = null;
    }
    private void stopGameView() {
        if (gameUi != null) { gameUi.stopP2PView(); gameUi = null; }
    }
    private void returnToMain() {
        close(); stage.setScene(menuScene); stage.sizeToScene();
    }
    private void dispatch(UiAction action) {
        Runnable task = () -> {
            if (phase == Phase.CLOSED) return;
            try { action.run(); }
            catch (Exception ex) { throw new IllegalStateException("P2P 화면 처리 실패", ex); }
        };
        if (Platform.isFxApplicationThread()) task.run(); else Platform.runLater(task);
    }
    private static Cell[][] copy(Cell[][] cells) {
        Cell[][] result = new Cell[cells.length][];
        for (int row = 0; row < cells.length; row++) result[row] = cells[row].clone();
        return result;
    }
    @FunctionalInterface private interface UiAction { void run() throws Exception; }
    @Override public void close() {
        if (phase == Phase.CLOSED) return;
        phase = Phase.CLOSED; connected = false; loading.stop(); stopGameView(); removePopup();
        stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
        // todo : infra 와 연동 필요
        // 대기 중 접속 요청 취소 및 기존 브리지/연결 종료를 호출한다.
    }
}
