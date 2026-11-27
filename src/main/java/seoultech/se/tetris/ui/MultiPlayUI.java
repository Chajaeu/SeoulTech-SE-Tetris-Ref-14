package seoultech.se.tetris.ui;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.animation.PauseTransition;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

public final class MultiPlayUI extends PlayUI {
    // 임시로 넣어둔 Player2 키
    static final List<KeyCode> PLAYER_2_KEYS = List.of(
        KeyCode.A, KeyCode.D, KeyCode.S, KeyCode.W, KeyCode.CONTROL);
    private static final Color ATTACK_COLOR = Color.web("#85909D");

    // 같은 클래스로 두 플레이어를 실행한다. 1P가 공용 화면과 경기 타이머를 관리한다.
    private MultiPlayUI host = this;
    private MultiPlayUI opponent;
    private Game battleGame;
    private Pane statusPane;
    private boolean itemMode;
    private boolean timeMode;
    private boolean finished;
    private KeyCode pauseKey;
    private StackPane pauseOverlay;
    private PauseTransition matchClock;
    private P2PGame p2pGame;
    private boolean p2pMode;
    private boolean networkPaused;
    private boolean viewDisposed;
    private boolean localGameOverReported;
    private Pane opponentBoardPane;
    private Pane opponentNextPane;
    private Pane opponentStatusPane;
    private Label opponentScoreLabel;
    private Label networkLabel;
    private Label p2pTimerLabel;

    public static void showLocal(Stage stage, UIResolution.Size resolution,
            boolean itemMode, boolean timeMode) throws Exception {
        new MultiPlayUI().openLocal(stage, resolution, itemMode, timeMode);
    }

    public static MultiPlayUI showP2P(Stage stage, UIResolution.Size resolution,
            boolean server, boolean itemMode, boolean timeMode, int durationSeconds,
            P2PGame p2pGame) throws Exception {
        MultiPlayUI ui = new MultiPlayUI();
        ui.openP2P(stage, resolution, server, itemMode, timeMode, durationSeconds, p2pGame);
        return ui;
    }

    private void openP2P(Stage stage, UIResolution.Size resolution, boolean server,
            boolean itemMode, boolean timeMode, int durationSeconds, P2PGame p2pGame) throws Exception {
        this.stage = stage; this.resolution = resolution; this.itemMode = itemMode;
        this.timeMode = timeMode; this.p2pGame = p2pGame; p2pMode = true;
        var settings = new SettingsRepository().loadSettings();
        List<KeyCode> keys = List.of(KeyCode.valueOf(settings.getKeyLeft()),
            KeyCode.valueOf(settings.getKeyRight()), KeyCode.valueOf(settings.getKeyDown()),
            KeyCode.valueOf(settings.getKeyRotate()), KeyCode.valueOf(settings.getKeyDrop()));
        FXMLLoader loader = new FXMLLoader(MultiPlayUI.class.getResource("fxml/MultiPlayUI.fxml"));
        designRoot = loader.load();
        Map<String, Object> nodes = loader.getNamespace();
        String mine = server ? "player1" : "player2";
        String other = server ? "player2" : "player1";
        // Core의 기존 대전 규칙을 사용한다. 상대 Core는 만들지 않는다.
        connectPlayer(nodes, mine, keys, settings.isColorblindMode());
        opponentBoardPane = (Pane) nodes.get(other + "BoardPane");
        opponentNextPane = (Pane) nodes.get(other + "NextPane");
        opponentStatusPane = (Pane) nodes.get(other + "StatusPane");
        Pane scorePane = (Pane) nodes.get(other + "ScorePane");
        opponentScoreLabel = new Label("—");
        opponentScoreLabel.setAlignment(javafx.geometry.Pos.CENTER);
        opponentScoreLabel.setStyle("-fx-text-fill: white; -fx-font-size: 24px;");
        opponentScoreLabel.prefWidthProperty().bind(scorePane.widthProperty());
        opponentScoreLabel.prefHeightProperty().bind(scorePane.heightProperty());
        scorePane.getChildren().setAll(opponentScoreLabel);
        networkLabel = (Label) nodes.get("networkQualityLabel");
        networkLabel.setVisible(true); networkLabel.setManaged(true);
        p2pTimerLabel = (Label) nodes.get("timerLabel");
        p2pTimerLabel.setVisible(timeMode); p2pTimerLabel.setManaged(timeMode);
        updateP2PStatus(durationSeconds, -1);
        double scale = Math.min(resolution.getWidth() / designRoot.getPrefWidth(),
            resolution.getHeight() / designRoot.getPrefHeight());
        designRoot.setScaleX(scale); designRoot.setScaleY(scale); designRoot.setFocusTraversable(true);
        Scene scene = new Scene(new StackPane(designRoot), resolution.getWidth(), resolution.getHeight());
        setupKeyboard(scene, keys);
        stage.setScene(scene); stage.setResizable(false); stage.sizeToScene(); setupCleanup(stage, scene);
        stage.show(); scene.getRoot().applyCss(); scene.getRoot().layout(); designRoot.requestFocus();
        startPlayer();
        // P2P의 남은 시간은 외부 확정 값으로 갱신한다. 로컬 타이머를 따로 시작하지 않는다.
    }

    private void openLocal(Stage stage, UIResolution.Size resolution,
            boolean itemMode, boolean timeMode) throws Exception {
        var settings = new SettingsRepository().loadSettings();
        List<KeyCode> firstKeys = List.of(
            KeyCode.valueOf(settings.getKeyLeft()), KeyCode.valueOf(settings.getKeyRight()),
            KeyCode.valueOf(settings.getKeyDown()), KeyCode.valueOf(settings.getKeyRotate()),
            KeyCode.valueOf(settings.getKeyDrop()));
        pauseKey = KeyCode.valueOf(settings.getKeyPause());

        this.stage = stage;
        this.resolution = resolution;
        this.itemMode = itemMode;
        this.timeMode = timeMode;
        FXMLLoader loader = new FXMLLoader(MultiPlayUI.class.getResource("fxml/MultiPlayUI.fxml"));
        designRoot = loader.load();

        opponent = new MultiPlayUI();
        opponent.host = this;
        opponent.opponent = this;
        connectPlayer(loader.getNamespace(), "player1", firstKeys, settings.isColorblindMode());
        opponent.connectPlayer(loader.getNamespace(), "player2", PLAYER_2_KEYS, settings.isColorblindMode());

        double scale = Math.min(resolution.getWidth() / designRoot.getPrefWidth(),
            resolution.getHeight() / designRoot.getPrefHeight());
        designRoot.setScaleX(scale);
        designRoot.setScaleY(scale);
        designRoot.setFocusTraversable(true);

        Scene scene = new Scene(new StackPane(designRoot), resolution.getWidth(), resolution.getHeight());
        setupKeyboard(scene, firstKeys);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.sizeToScene();
        setupCleanup(stage, scene);
        stage.show();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        designRoot.requestFocus();

        startPlayer();
        if (!finished) {
            opponent.startPlayer();
        }
        if (timeMode && !finished) {
            startMatchClock((Label) loader.getNamespace().get("timerLabel"));
        }
    }

    private void startMatchClock(Label timerLabel) {
        int totalSeconds = 120;
        matchClock = new PauseTransition(Duration.seconds(totalSeconds));
        timerLabel.setText(formatTime(totalSeconds));
        timerLabel.setManaged(true);
        timerLabel.setVisible(true);
        matchClock.currentTimeProperty().addListener((observable, previous, elapsed) -> {
            int remaining = (int) Math.ceil(totalSeconds - elapsed.toSeconds());
            if (!finished && remaining != Math.ceil(totalSeconds - previous.toSeconds())) {
                timerLabel.setText(formatTime(remaining));
            }
        });
        matchClock.setOnFinished(event -> runUiAction(() -> finishMatch(null)));
        matchClock.play();
    }

    private static String formatTime(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void connectPlayer(Map<String, Object> nodes, String prefix,
            List<KeyCode> keys, boolean colorblindMode) {
        battleGame = new Game(Game.NORMAL, host.itemMode, true);
        statusPane = (Pane) nodes.get(prefix + "StatusPane");
        configurePlayer(battleGame,
            (Pane) nodes.get(prefix + "BoardPane"), (Pane) nodes.get(prefix + "NextPane"),
            (Pane) nodes.get(prefix + "ScorePane"), prefix + "ScoreValue", 24, colorblindMode);
        bindGameKeys(keys.get(0), keys.get(1), keys.get(2), keys.get(3), keys.get(4));
    }

    private void startPlayer() {
        startGameLoop(battleGame.getTickInterval());
        refreshGameView();
    }

    private void setupKeyboard(Scene scene, List<KeyCode> firstKeys) {
        UIInteraction.trackInput(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isConsumed() || pauseOverlay != null || finished || viewDisposed || networkPaused) {
                return;
            }
            KeyCode key = event.getCode();
            if (p2pMode) {
                if (firstKeys.contains(key) && handleGameKey(key)) event.consume();
                return;
            }
            if (key == pauseKey) {
                event.consume();
                runUiAction(this::openPause);
            } else if ((firstKeys.contains(key) && handleGameKey(key))
                    || (PLAYER_2_KEYS.contains(key) && opponent.handleGameKey(key))) {
                event.consume();
            }
        });
    }

    @Override
    protected void refreshGameView() {
        if (viewDisposed) return;
        super.refreshGameView();
        refreshAttackStatus();
        if (p2pMode) p2pGame.afterGameUpdate(battleGame);
    }

    @Override
    protected void beforeGameUpdate() {
        if (p2pMode) p2pGame.beforeGameUpdate(battleGame);
    }

    private void refreshAttackStatus() {
        drawPendingAttack(statusPane, battleGame.getAttackedLines());
    }

    private void drawPendingAttack(Pane pane, Cell[][] pending) {
        Insets insets = pane.getInsets();
        double width = pane.getWidth() - insets.getLeft() - insets.getRight();
        double height = pane.getHeight() - insets.getTop() - insets.getBottom();
        double size = Math.min(width / Game.COLS, height / Game.ROWS);
        if (size > 0) {
            double left = insets.getLeft() + (width - Game.COLS * size) / 2;
            double top = pane.getHeight() - insets.getBottom() - pending.length * size;
            placeCells(pane, pending, false, size, left, top, null);
        }
    }

    @Override
    protected void afterLinesCleared() {
        if (p2pMode) {
            p2pGame.afterLinesCleared(battleGame);
            return;
        }
        if (battleGame.getIsGameover()) {
            return;
        }
        boolean[][] attack = battleGame.attack();
        if (attack.length > 0) {
            opponent.battleGame.attacked(attack);
            opponent.refreshAttackStatus();
        }
    }

    @Override
    protected StackPane createTile(Cell cell, double size) {
        StackPane square = super.createTile(cell, size);
        if (cell.getType() == Game.ATTACK_CELL) {
            ((Rectangle) square.getChildren().getFirst()).setFill(ATTACK_COLOR);
        }
        return square;
    }

    @Override
    protected void openGameOver() throws IOException {
        if (p2pMode) {
            if (!localGameOverReported) {
                localGameOverReported = true;
                p2pGame.onLocalGameOver(battleGame);
            }
            return;
        }
        host.finishMatch(host == this ? Result.Winner.PLAYER_2 : Result.Winner.PLAYER_1);
    }

    private void openPause() throws Exception {
        Parent popup = Pause.create(this::resumeMatch, this::returnToMainMenu);
        pauseGame();
        opponent.pauseGame();
        if (matchClock != null) {
            matchClock.pause();
        }
        pauseOverlay = showOverlay(popup, "pauseOverlay");
    }

    private void resumeMatch() {
        designRoot.getChildren().remove(pauseOverlay);
        pauseOverlay = null;
        resumePlay();
        opponent.resumePlay();
        if (matchClock != null) {
            matchClock.play();
        }
        designRoot.requestFocus();
    }

    private void finishMatch(Result.Winner winner) throws IOException {
        if (finished) {
            return;
        }
        finished = true;
        disposeGame();
        if (winner == null) {
            winner = Result.getResult(finished, battleGame, opponent.battleGame).orElseThrow();
        }
        Runnable restart = () -> runUiAction(() -> showLocal(stage, resolution, itemMode, timeMode));
        Parent result = Result.create(winner, battleGame.getScore(), opponent.battleGame.getScore(),
            restart, this::returnToMainMenu);
        showOverlay(result, "resultOverlay");
    }

    private static void runUiAction(UiAction action) {
        try {
            action.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FunctionalInterface
    private interface UiAction {
        void run() throws Exception;
    }

    @Override
    protected void disposeGame() {
        if (viewDisposed) return;
        viewDisposed = true;
        super.disposeGame();
        if (host == this) {
            if (opponent != null) opponent.disposeGame();
            if (matchClock != null) {
                matchClock.stop();
            }
        }
    }

    void updateOpponent(Cell[][] board, Cell[][] next, int score, Cell[][] pendingAttack) {
        if (!p2pMode || viewDisposed || finished || networkPaused) return;
        drawCells(opponentBoardPane, board, true);
        drawCells(opponentNextPane, next, false);
        opponentScoreLabel.setText(Integer.toString(score));
        drawPendingAttack(opponentStatusPane, pendingAttack);
    }

    void updateP2PStatus(int remainingSeconds, long rttMillis) {
        if (!p2pMode || viewDisposed) return;
        p2pTimerLabel.setText(formatTime(Math.max(0, remainingSeconds)));
        networkLabel.setText(rttMillis < 0 ? "— ms" : rttMillis + " ms");
    }

    void showP2PResult(Result.Winner winner, int score1, int score2,
            Runnable onRestart, Runnable onBack) throws IOException {
        if (!p2pMode || finished) return;
        finished = true; disposeGame();
        showOverlay(Result.create(winner, score1, score2, onRestart, onBack), "resultOverlay");
    }

    void suspendForNetworkIssue() {
        if (!p2pMode || viewDisposed) return;
        networkPaused = true; pauseGameUpdates();
    }

    void stopP2PView() { disposeGame(); }
}
