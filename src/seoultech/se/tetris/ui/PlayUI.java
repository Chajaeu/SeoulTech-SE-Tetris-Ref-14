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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public class PlayUI {

    // 한 게임 화면의 상태를 보관한다. Core 통합 후 Game 필드도 여기에 둔다.
    private Pane designRoot;
    private Pane playboardPane;
    private Pane nextPane;
    private Pane myScorePane;
    private Pane scoreboardPane;
    private StackPane pauseOverlay;
    private Timeline fallTimer;
    private boolean resumeTimer;

    public static void show(Stage stage, UIResolution.Size resolution)
            throws Exception {
        new PlayUI().open(stage, resolution);
    }

    private void open(Stage stage, UIResolution.Size resolution) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            PlayUI.class.getResource("fxml/PlayUI.fxml")
        );
        Parent root = loader.load();

        designRoot = (Pane) root;
        designRoot.setFocusTraversable(true);
        playboardPane = (Pane) loader.getNamespace().get("playboard_pane");
        nextPane = (Pane) loader.getNamespace().get("next_pane");
        myScorePane = (Pane) loader.getNamespace().get("myscore_pane");
        scoreboardPane = (Pane) loader.getNamespace().get("scoreboard_pane");

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

        Scene scene = new Scene(root, boardWidth, boardHeight);
        setupKeyboard(scene);
        setupCleanup(stage, scene);

        initializeGame();
        refreshGameView();

        stage.setTitle("Tetris");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        designRoot.requestFocus();
    }

    private void setupKeyboard(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                if (pauseOverlay == null) {
                    try {
                        openPause();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                return;
            }

            // 팝업의 버튼 조작은 허용하고, Core에는 입력을 전달하지 않는다.
            if (pauseOverlay != null) {
                return;
            }

            if (handleGameKey(e.getCode())) {
                e.consume();
            }
        });
    }

    private void openPause() throws Exception {
        Parent popup = Pause.create(this::resumeGame);
        pauseOverlay = new StackPane(popup);
        pauseOverlay.setId("pauseOverlay");
        pauseOverlay.setPrefSize(
            designRoot.getPrefWidth(), designRoot.getPrefHeight()
        );
        pauseOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.35);");
        pauseOverlay.setPickOnBounds(true);
        pauseOverlay.setFocusTraversable(true);

        resumeTimer = fallTimer != null
            && fallTimer.getStatus() == Animation.Status.RUNNING;
        if (fallTimer != null) {
            fallTimer.pause();
        }
        pauseGame();

        // 화면 전체를 덮어 뒤쪽 클릭을 막는다. 배경 클릭으로는 닫지 않는다.
        designRoot.getChildren().add(pauseOverlay);
        pauseOverlay.requestFocus();
    }

    private void resumeGame() {
        designRoot.getChildren().remove(pauseOverlay);
        pauseOverlay = null;

        // TODO Core: game.resume();
        if (resumeTimer && fallTimer != null) {
            fallTimer.play();
        }
        resumeTimer = false;
        designRoot.requestFocus();
    }

    private void pauseGame() {
        // TODO Core: game.pause();
    }

    private void initializeGame() {
        // TODO Core: 선택한 난이도와 아이템 모드로 Game을 한 번만 생성.
        // game = new Game(difficulty, itemMode);
        // startGameLoop(game.getTickInterval());
        // ScoreRepository의 기록은 scoreboardPane에 별도로 표시한다.
    }

    private boolean handleGameKey(KeyCode code) {
        // TODO Infra: 저장된 키 설정으로 분기. 아래는 기본 키의 연결 위치.
        switch (code) {
            case LEFT:
                // TODO Core: game.moveLeft();
                break;
            case RIGHT:
                // TODO Core: game.moveRight();
                break;
            case DOWN:
                // TODO Core: game.moveDown();
                break;
            case UP:
                // TODO Core: game.rotateCW();
                break;
            case SPACE:
                // TODO Core: game.harddrop();
                break;
            default:
                return false;
        }
        refreshGameView();
        return true;
    }

    private void startGameLoop(int intervalMillis) {
        stopGameLoop();
        fallTimer = new Timeline(new KeyFrame(
            Duration.millis(intervalMillis), e -> tickGame()
        ));
        fallTimer.setCycleCount(Animation.INDEFINITE);
        if (pauseOverlay == null) {
            fallTimer.play();
        } else {
            resumeTimer = true;
        }
    }

    private void tickGame() {
        if (pauseOverlay != null) {
            return;
        }
        // TODO Core: game.tick();
        refreshGameView();
    }

    private void refreshGameView() {
        // TODO Core: FXML을 다시 로드하지 않고, 가져온 Pane 내부만 갱신한다.
        // playboardPane <- game.getDisplayBoard()
        // nextPane      <- game.getNextMino()
        // myScorePane   <- game.getScore()
        // 줄 삭제 연출 후 game.deleteCells(), 속도 변경 시 타이머를 갱신한다.
        // game.getIsGameover()가 true이면 타이머를 멈추고 종료 화면을 연다.
    }

    private void stopGameLoop() {
        if (fallTimer != null) {
            fallTimer.stop();
        }
        resumeTimer = false;
    }

    private void setupCleanup(Stage stage, Scene scene) {
        EventHandler<WindowEvent> onHidden = e -> stopGameLoop();
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
        stage.sceneProperty().addListener(new ChangeListener<Scene>() {
            @Override
            public void changed(
                    ObservableValue<? extends Scene> observable,
                    Scene oldScene, Scene newScene) {
                if (oldScene == scene) {
                    stopGameLoop();
                    stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
                    stage.sceneProperty().removeListener(this);
                }
            }
        });
    }
}
