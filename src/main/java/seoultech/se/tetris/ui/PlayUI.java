package seoultech.se.tetris.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.Tooltip;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import java.util.EnumMap;
import java.util.Map;
import seoultech.se.tetris.game.Cell;
import seoultech.se.tetris.game.Game;
import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.GameMode;
import seoultech.se.tetris.infra.dto.SettingsDTO;
import seoultech.se.tetris.infra.dto.ScoreDTO;
import seoultech.se.tetris.infra.integration.GameSessionService;
import seoultech.se.tetris.infra.persistence.ScoreRepository;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

public class PlayUI {

    // 게임 상태는 Core가 관리하고 클래스는 입력과 화면 갱신을 담당
    private Game game;
    private GameSessionService gameSession;
    private Stage stage;
    private UIResolution.Size resolution;
    private Difficulty difficulty;
    private boolean itemMode;
    private boolean colorblindMode;
    private KeyCode pauseKey;
    private final Map<KeyCode, Runnable> gameActions = new EnumMap<>(KeyCode.class);
    private Pane designRoot;
    private Pane playboardPane;
    private Pane nextPane;
    private Pane myScorePane;
    private Pane scoreboardPane;
    private StackPane pauseOverlay;
    private StackPane gameOverOverlay;
    private Timeline fallTimer;
    private Timeline deleteAnimation;
    private boolean disposed;
    private int fallInterval;
    private Label scoreLabel;

    public static void show(Stage stage, UIResolution.Size resolution)
            throws Exception {
        Difficulty difficulty = new SettingsRepository().loadSettings().getDifficulty();
        show(stage, resolution, difficulty, false);
    }

    public static void show(Stage stage, UIResolution.Size resolution,
            Difficulty difficulty, boolean itemMode) throws Exception {
        new PlayUI().open(stage, resolution, difficulty, itemMode);
    }

    private void open(Stage stage, UIResolution.Size resolution,
            Difficulty difficulty, boolean itemMode) throws Exception {
        this.stage = stage;
        this.resolution = resolution;
        this.difficulty = difficulty;
        this.itemMode = itemMode;
        FXMLLoader loader = new FXMLLoader(
            PlayUI.class.getResource("fxml/PlayUI.fxml")
        );
        Parent root = loader.load();

        designRoot = (Pane) root;
        UIInteraction.installStyles(designRoot);
        designRoot.setFocusTraversable(true);
        playboardPane = (Pane) loader.getNamespace().get("playboard_pane");
        nextPane = (Pane) loader.getNamespace().get("next_pane");
        myScorePane = (Pane) loader.getNamespace().get("myscore_pane");
        scoreboardPane = (Pane) loader.getNamespace().get("scoreboard_pane");
        scoreLabel = new Label("0");
        scoreLabel.setId("currentScore");
        scoreLabel.setAlignment(Pos.CENTER);
        scoreLabel.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");
        scoreLabel.prefWidthProperty().bind(myScorePane.widthProperty());
        scoreLabel.prefHeightProperty().bind(myScorePane.heightProperty());
        myScorePane.getChildren().add(scoreLabel);

        int boardWidth = resolution.getWidth();
        int boardHeight = resolution.getHeight();

        // FXML에 정의된 원본 크기를 기준으로 사용
        double designWidth = designRoot.getPrefWidth();
        double designHeight = designRoot.getPrefHeight();

        // 실제 해상도에 맞게 전체 UI 크기 조절
        double scaleX = boardWidth / designWidth;
        double scaleY = boardHeight / designHeight;

        double scale = Math.min(scaleX, scaleY);

        designRoot.getTransforms().add(
            new Scale(scale, scale, 0, 0)
        );

        if (!initializeGame()) {
            return;
        }

        Scene scene = new Scene(root, boardWidth, boardHeight);
        setupKeyboard(scene);
        setupCleanup(stage, scene);

        stage.setTitle("Tetris");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        designRoot.applyCss();
        designRoot.layout();
        startGameLoop(game.getTickInterval());
        refreshGameView();
        designRoot.requestFocus();
    }

    private void setupKeyboard(Scene scene) {
        UIInteraction.trackInput(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            // 팝업 버튼이 처리한 Enter/Space를 게임 입력으로 다시 사용하지 않음
            if (e.isConsumed()) {
                return;
            }
            // 팝업의 버튼 조작은 허용하고, Core에는 입력을 전달 X
            if (pauseOverlay != null || gameOverOverlay != null) {
                return;
            }

            if (e.getCode() == pauseKey) {
                e.consume();
                if (!game.getIsGameover()) {
                    try {
                        openPause();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                return;
            }

            if (handleGameKey(e.getCode())) {
                e.consume();
            }
        });
    }

    private void openPause() throws Exception {
        Parent popup = Pause.create(this::resumeGame, this::returnToMainMenu);
        if (fallTimer != null) {
            fallTimer.pause();
        }
        pauseGame();

        pauseOverlay = showOverlay(popup, "pauseOverlay");
    }

    private void openGameOver() throws Exception {
        if (gameOverOverlay != null) {
            return;
        }
        // 등록 대상이면 이름 입력 후 점수판, 그 외에는 게임오버 메뉴 표시
        Parent popup = gameSession.canSubmitScore()
            ? ScoreSubmit.create(gameSession, this::showGameOverActions, this::showSavedScoreboard)
            : GameOver.create(this::retryGame, this::returnToMainMenu);
        gameOverOverlay = showOverlay(popup, "gameOverOverlay");
    }

    private void showGameOverActions() {
        try {
            gameOverOverlay.getChildren().setAll(GameOver.create(this::retryGame, this::returnToMainMenu));
            gameOverOverlay.requestFocus();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void showSavedScoreboard(String savedId) {
        refreshScoreboard(savedId);
        try {
            Parent popup = Scoreboard.create(gameSession.getGameMode(), gameSession.getDifficulty(),
                savedId, this::showGameOverActions);
            gameOverOverlay.getChildren().setAll(popup);
            gameOverOverlay.requestFocus();
        } catch (Exception ex) {
            ex.printStackTrace();
            showGameOverActions();
        }
    }

    // 화면 전체를 덮어 팝업 뒤쪽 클릭을 차단
    private StackPane showOverlay(Parent popup, String id) {
        StackPane overlay = new StackPane(popup);
        overlay.setId(id);
        overlay.setPrefSize(designRoot.getPrefWidth(), designRoot.getPrefHeight());
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.35);");
        overlay.setPickOnBounds(true);
        overlay.setFocusTraversable(true);
        designRoot.getChildren().add(overlay);
        overlay.requestFocus();
        return overlay;
    }

    // RETRY → 같은 해상도, 난이도, 모드로 새 게임 시작
    private void retryGame() {
        try {
            PlayUI.show(stage, resolution, difficulty, itemMode);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // MAIN → 메인 메뉴로 전환, 기존 타이머는 화면 변경 시 정리
    private void returnToMainMenu() {
        try {
            MainMenuUI.show(stage, resolution);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void resumeGame() {
        designRoot.getChildren().remove(pauseOverlay);
        pauseOverlay = null;

        game.resume();
        restartFallTimer();
        designRoot.requestFocus();
    }

    private void pauseGame() {
        game.pause();
    }

    private boolean initializeGame() {
        SettingsRepository settingsRepository = new SettingsRepository();
        // 기존 키, 해상도, 색약 설정은 보존하고 선택한 난이도만 저장
        SettingsDTO settings = settingsRepository.loadSettings();
        settings.setDifficulty(difficulty);
        colorblindMode = settings.isColorblindMode();
        if (!settingsRepository.saveSettings(settings)) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("게임 시작 실패");
            alert.setHeaderText("난이도를 저장하지 못했습니다.");
            alert.setContentText("설정 저장 위치를 확인한 뒤 다시 시도해주세요.");
            alert.showAndWait();
            return false;
        }

        // Infra가 저장된 난이도를 Core 값으로 변환해 게임 생성
        gameSession = new GameSessionService(settingsRepository, new ScoreRepository());
        game = gameSession.startGame(itemMode ? GameMode.ITEM : GameMode.NORMAL);
        pauseKey = KeyCode.valueOf(settings.getKeyPause());
        gameActions.put(KeyCode.valueOf(settings.getKeyLeft()), game::moveLeft);
        gameActions.put(KeyCode.valueOf(settings.getKeyRight()), game::moveRight);
        gameActions.put(KeyCode.valueOf(settings.getKeyDown()), () -> {
            game.moveDown();
            restartFallTimer();
        });
        gameActions.put(KeyCode.valueOf(settings.getKeyRotate()), game::rotateCW);
        gameActions.put(KeyCode.valueOf(settings.getKeyDrop()), game::harddrop);
        refreshScoreboard(null);
        return true;
    }

    // 게임 시작과 기록 저장 뒤에만 조회하여, 낙하마다 파일을 다시 읽지 않음
    private void refreshScoreboard(String highlightedId) {
        var records = gameSession.getTopScores(gameSession.getGameMode(), gameSession.getDifficulty());
        GridPane grid = new GridPane();
        grid.setId("gameScoreGrid");
        grid.prefWidthProperty().bind(scoreboardPane.widthProperty());
        grid.prefHeightProperty().bind(scoreboardPane.heightProperty());
        for (double width : new double[] {15, 48, 37}) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(width);
            grid.getColumnConstraints().add(column);
        }
        for (int row = 0; row <= 10; row++) {
            RowConstraints constraint = new RowConstraints();
            constraint.setPercentHeight(100.0 / 11);
            grid.getRowConstraints().add(constraint);
        }
        String[] headers = {"#", "NAME", "SCORE"};
        for (int col = 0; col < headers.length; col++) {
            grid.add(scoreboardCell(headers[col], "sideHeader" + col, true, false, col), col, 0);
        }
        for (int rank = 1; rank <= 10; rank++) {
            ScoreDTO record = rank <= records.size() ? records.get(rank - 1) : null;
            boolean highlighted = record != null && record.getId().equals(highlightedId);
            grid.add(scoreboardCell(Integer.toString(rank), "sideRank" + rank, false, highlighted, 0), 0, rank);
            grid.add(scoreboardCell(record == null ? "—" : record.getPlayerName(),
                "sideName" + rank, false, highlighted, 1), 1, rank);
            grid.add(scoreboardCell(record == null ? "—" : Scoreboard.formatScore(record.getScore()),
                "sideScore" + rank, false, highlighted, 2), 2, rank);
        }
        scoreboardPane.getChildren().setAll(grid);
    }

    private Label scoreboardCell(String text, String id, boolean header, boolean highlighted, int column) {
        Label label = new Label(text);
        label.setId(id);
        label.setMinSize(0, 0);
        label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.setTooltip(new Tooltip(text));
        label.setStyle("-fx-font-size: 11px;-fx-padding: 0 2;"
            + "-fx-text-fill: white;"
            + (header ? "-fx-font-weight: bold;" : ""));
        UIInteraction.markRecord(label, highlighted, column);
        return label;
    }

    private boolean handleGameKey(KeyCode code) {
        if (disposed || game.getIsGameover()) {
            return false;
        }
        if (deleteAnimation != null) {
            return true;
        }
        Runnable action = gameActions.get(code);
        if (action == null) {
            return false;
        }
        action.run();
        refreshGameView();
        return true;
    }

    private void startGameLoop(int intervalMillis) {
        stopGameLoop();
        fallInterval = intervalMillis;
        fallTimer = new Timeline(new KeyFrame(
            Duration.millis(intervalMillis), e -> tickGame()
        ));
        fallTimer.setCycleCount(Animation.INDEFINITE);
        restartFallTimer();
    }

    private void restartFallTimer() {
        if (!disposed && fallTimer != null && deleteAnimation == null
                && !game.getIsPaused() && !game.getIsGameover()) {
            // 수동 낙하와 재개 뒤에는 다음 자동 낙하까지 한 주기를 온전히 기다림
            fallTimer.playFromStart();
        }
    }

    private void tickGame() {
        if (disposed || game.getIsPaused() || deleteAnimation != null) {
            return;
        }
        game.tick();
        refreshGameView();
    }

    private void refreshGameView() {
        if (disposed || deleteAnimation != null) {
            return;
        }

        drawCells(playboardPane, game.getDisplayBoard(), true);
        drawCells(nextPane, game.getNextMino(), false);
        scoreLabel.setText(Integer.toString(game.getScore()));

        if (game.getIsGameover()) {
            stopGameLoop();
            try {
                openGameOver();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else if (game.getIsWaitingDelete()) {
            startDeleteAnimation();
        } else if (fallInterval != game.getTickInterval()) {
            startGameLoop(game.getTickInterval());
        }
    }

    private void startDeleteAnimation() {
        stopGameLoop();
        boolean[][] cells = game.getDeleteWaitingCells();
        // 삭제 대상 칸만 하얗게 한 번 반짝인 뒤 삭제한다.
        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                if (cells[row][col]) {
                    StackPane square = (StackPane) playboardPane.lookup(
                        "#boardCell-" + row + "-" + col
                    );
                    Rectangle tile = (Rectangle) square.getChildren().get(0);
                    tile.setFill(Color.WHITE);
                }
            }
        }

        deleteAnimation = new Timeline(new KeyFrame(Duration.millis(200)));
        deleteAnimation.setOnFinished(e -> {
            deleteAnimation = null;
            if (disposed) {
                return;
            }
            // 일시정지 중에도 삭제는 완료하고 Core의 정지 상태 유지
            game.deleteCells();
            startGameLoop(game.getTickInterval());
            refreshGameView();
        });
        deleteAnimation.play();
    }

    private void drawCells(Pane pane, Cell[][] cells, boolean showGrid) {
        pane.getChildren().clear();
        Insets insets = pane.getInsets();
        double width = pane.getWidth() > 0 ? pane.getWidth() : pane.getPrefWidth();
        double height = pane.getHeight() > 0 ? pane.getHeight() : pane.getPrefHeight();
        double cellSize = Math.min(
            (width - insets.getLeft() - insets.getRight()) / cells[0].length,
            (height - insets.getTop() - insets.getBottom()) / cells.length
        );
        // NEXT는 모양에 관계없이 보드와 비슷한 크기로 표시
        if (!showGrid) {
            cellSize = Math.min(cellSize, 32);
        }
        double left = (width - cellSize * cells[0].length) / 2;
        double top = (height - cellSize * cells.length) / 2;

        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                Cell cell = cells[row][col];
                if (cell.isEmpty() && !showGrid) {
                    continue;
                }
                StackPane square = BlockRenderer.createCell(cell, cellSize, colorblindMode);
                square.setId((showGrid ? "boardCell-" : "nextCell-") + row + "-" + col);
                square.setLayoutX(left + col * cellSize);
                square.setLayoutY(top + row * cellSize);
                square.setPrefSize(cellSize, cellSize);
                pane.getChildren().add(square);
            }
        }
    }

    private void stopGameLoop() {
        if (fallTimer != null) {
            fallTimer.stop();
        }
    }

    private void disposeGame() {
        disposed = true;
        stopGameLoop();
        if (deleteAnimation != null) {
            deleteAnimation.stop();
            deleteAnimation = null;
        }
    }

    private void setupCleanup(Stage stage, Scene scene) {
        EventHandler<WindowEvent> onHidden = e -> disposeGame();
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
        stage.sceneProperty().addListener(new ChangeListener<Scene>() {
            @Override
            public void changed(
                    ObservableValue<? extends Scene> observable,
                    Scene oldScene, Scene newScene) {
                if (oldScene == scene) {
                    disposeGame();
                    stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
                    stage.sceneProperty().removeListener(this);
                }
            }
        });
    }
}
