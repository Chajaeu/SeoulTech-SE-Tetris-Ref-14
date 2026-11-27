package seoultech.se.tetris.ui;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import seoultech.se.tetris.infra.dto.Difficulty;
import seoultech.se.tetris.infra.dto.GameMode;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

public class MainMenuUI {
    private static final String SELECTED_STYLE =
        "-fx-background-color: linear-gradient(to bottom, " +
        "#FFF06A 0%, #FFD329 45%, #FF9D0A 100%);";

    public static void show(Stage stage, UIResolution.Size resolution) throws Exception {
        Pane designRoot = FXMLLoader.load(
            MainMenuUI.class.getResource("fxml/MainMenuUI.fxml"));
        ObjectProperty<UIResolution.Size> currentResolution =
            new SimpleObjectProperty<>(resolution);
        StackPane screen = new StackPane(designRoot);

        Button normalGameButton = (Button) designRoot.lookup("#normalGameButton");
        Button localGameButton = (Button) designRoot.lookup("#localGameButton");
        Button p2pGameButton = (Button) designRoot.lookup("#p2pGameButton");

        // 각 버튼은 해당 게임의 시작 옵션 창을 연다.
        normalGameButton.setOnAction(e ->
            openGameSetup(stage, designRoot, currentResolution.get(), "NormalGame.fxml"));
        localGameButton.setOnAction(e ->
            openGameSetup(stage, designRoot, currentResolution.get(), "LocalGame.fxml"));
        p2pGameButton.setOnAction(e ->
            openGameSetup(stage, designRoot, currentResolution.get(), "P2PGame.fxml"));

        Button settingButton = (Button) designRoot.lookup("#settingButton");
        settingButton.setOnAction(e -> {
            try {
                openSettings(designRoot, currentResolution.get(), selectedResolution -> {
                    if (selectedResolution != currentResolution.get()) {
                        currentResolution.set(selectedResolution);
                        applyResolution(stage, designRoot, screen, selectedResolution);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        Button scoreboardButton = (Button) designRoot.lookup("#scoreboardButton");
        scoreboardButton.setOnAction(e -> {
            try {
                Difficulty difficulty = new SettingsRepository().loadSettings().getDifficulty();
                openScoreboard(designRoot, GameMode.NORMAL, difficulty);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        ((Button) designRoot.lookup("#exitButton")).setOnAction(e -> Platform.exit());
        installButtonEffects(designRoot);
        UIInteraction.installNavigation(designRoot, normalGameButton);
        stage.setTitle("Tetris");
        stage.setResizable(false);
        applyResolution(stage, designRoot, screen, resolution);
        stage.show();
    }

    private static void openGameSetup(Stage stage, Pane designRoot,
            UIResolution.Size resolution, String fxml) {
        if (hasPopup(designRoot)) return;
        try {
            FXMLLoader loader = new FXMLLoader(MainMenuUI.class.getResource("fxml/" + fxml));
            Pane popup = loader.load();
            Button startButton = (Button) loader.getNamespace().get("startButton");
            Button backButton = (Button) loader.getNamespace().get("backButton");
            Button closeButton = (Button) loader.getNamespace().get("closeButton");

            StackPane overlay = new StackPane(popup);
            overlay.setId("gameSetupPopup");
            overlay.setPrefSize(designRoot.getPrefWidth(), designRoot.getPrefHeight());
            overlay.resize(designRoot.getPrefWidth(), designRoot.getPrefHeight());
            overlay.setPickOnBounds(true);
            Runnable close = () -> designRoot.getChildren().remove(overlay);
            backButton.setOnAction(e -> close.run());
            closeButton.setOnAction(e -> close.run());

            switch (fxml) {
                case "NormalGame.fxml" -> {
                    ToggleButton easy = (ToggleButton) loader.getNamespace().get("easyDifficultyButton");
                    ToggleButton normal = (ToggleButton) loader.getNamespace().get("normalDifficultyButton");
                    ToggleButton hard = (ToggleButton) loader.getNamespace().get("hardDifficultyButton");
                    setupSelection(easy, normal, hard);
                    switch (new SettingsRepository().loadSettings().getDifficulty()) {
                        case EASY -> easy.setSelected(true);
                        case NORMAL -> normal.setSelected(true);
                        case HARD -> hard.setSelected(true);
                    }
                    ToggleButton itemOn = setupItemMode(loader);
                    startButton.setOnAction(e -> {
                        Difficulty difficulty = easy.isSelected() ? Difficulty.EASY
                            : hard.isSelected() ? Difficulty.HARD : Difficulty.NORMAL;
                        try {
                            Scene menuScene = stage.getScene();
                            PlayUI.show(stage, resolution, difficulty, itemOn.isSelected());
                            // 게임 시작에 실패해 메뉴에 남으면 옵션 창 유지
                            if (stage.getScene() != menuScene) close.run();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
                case "LocalGame.fxml" -> {
                    ToggleButton itemOn = setupItemMode(loader);
                    ToggleButton timeOn = (ToggleButton) loader.getNamespace().get("timeModeOnButton");
                    ToggleButton timeOff = (ToggleButton) loader.getNamespace().get("timeModeOffButton");
                    setupSelection(timeOn, timeOff);
                    timeOff.setSelected(true);
                    startButton.setOnAction(e -> {
                        try {
                            MultiPlayUI.showLocal(stage, resolution,
                                itemOn.isSelected(), timeOn.isSelected());
                            close.run();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
                case "P2PGame.fxml" -> {
                    ToggleButton server = (ToggleButton) loader.getNamespace().get("serverModeButton");
                    ToggleButton client = (ToggleButton) loader.getNamespace().get("clientModeButton");
                    Pane serverIpPane = (Pane) loader.getNamespace().get("serverIpPane");
                    Pane clientIpPane = (Pane) loader.getNamespace().get("clientIpPane");
                    setupSelection(server, client);
                    serverIpPane.visibleProperty().bind(server.selectedProperty());
                    serverIpPane.managedProperty().bind(server.selectedProperty());
                    clientIpPane.visibleProperty().bind(client.selectedProperty());
                    clientIpPane.managedProperty().bind(client.selectedProperty());
                    ((Label) loader.getNamespace().get("serverIpLabel")).setText(getLocalIpAddress());
                    server.setSelected(true);
                    P2PGame.bindJoin(stage, resolution, designRoot, overlay,
                        popup, loader.getNamespace());
                }
                default -> throw new IllegalArgumentException("Unknown game setup: " + fxml);
            }

            installButtonEffects(popup);
            UIInteraction.installNavigation(popup, startButton.isDisabled() ? backButton : startButton);
            designRoot.getChildren().add(overlay);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // 네트워크의 IPv4 주소를 조회
    private static String getLocalIpAddress() {
        String fallback = null;
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                var addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    var address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()
                            || address.isLinkLocalAddress() || address.isAnyLocalAddress()) continue;
                    // 같은 네트워크에서 접속할 수 있는 사설 IP를 우선 표시
                    if (address.isSiteLocalAddress()) return address.getHostAddress();
                    if (fallback == null) fallback = address.getHostAddress();
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return fallback != null ? fallback : "IP 확인 불가";
    }

    private static ToggleButton setupItemMode(FXMLLoader loader) {
        ToggleButton itemOn = (ToggleButton) loader.getNamespace().get("itemModeButton");
        ToggleButton itemOff = (ToggleButton) loader.getNamespace().get("normalModeButton");
        setupSelection(itemOn, itemOff);
        itemOff.setSelected(true);
        return itemOn;
    }

    private static boolean hasPopup(Pane designRoot) {
        return designRoot.lookup("#gameSetupPopup") != null
            || designRoot.lookup("#settingPopup") != null
            || designRoot.lookup("#scoreboardPopup") != null;
    }

    private static void applyResolution(Stage stage, Pane designRoot, StackPane screen,
            UIResolution.Size resolution) {
        double scale = Math.min(resolution.getWidth() / designRoot.getPrefWidth(),
            resolution.getHeight() / designRoot.getPrefHeight());
        designRoot.setScaleX(scale);
        designRoot.setScaleY(scale);
        screen.setPrefSize(resolution.getWidth(), resolution.getHeight());
        if (stage.getScene() != null && stage.getScene().getRoot() == screen) {
            stage.getScene().setRoot(new Pane());
        }
        stage.setScene(new Scene(screen, resolution.getWidth(), resolution.getHeight()));
        stage.sizeToScene();
    }

    private static void openSettings(Pane designRoot, UIResolution.Size resolution,
            Consumer<UIResolution.Size> onSaved) throws Exception {
        if (hasPopup(designRoot)) return;
        Pane settings = (Pane) Setting.create(resolution, onSaved);
        settings.setId("settingPopup");
        addCenteredPopup(designRoot, settings);
    }

    private static void openScoreboard(Pane designRoot, GameMode mode,
            Difficulty difficulty) throws Exception {
        if (hasPopup(designRoot)) return;
        Pane scoreboard = (Pane) Scoreboard.create(mode, difficulty);
        scoreboard.setId("scoreboardPopup");
        addCenteredPopup(designRoot, scoreboard);
    }

    private static void addCenteredPopup(Pane designRoot, Pane popup) {
        popup.setLayoutX((designRoot.getPrefWidth() - popup.getPrefWidth()) / 2);
        popup.setLayoutY((designRoot.getPrefHeight() - popup.getPrefHeight()) / 2);
        designRoot.getChildren().add(popup);
    }

    private static void installButtonEffects(Parent root) {
        root.lookupAll("*").forEach(node -> {
            if (node instanceof ButtonBase button) ButtonEffects.addPressEffect(button);
        });
    }

    private static void setupSelection(ToggleButton... buttons) {
        ToggleGroup group = new ToggleGroup();
        for (ToggleButton button : buttons) {
            button.setToggleGroup(group);
            String normalStyle = button.getStyle();
            button.selectedProperty().addListener((observable, oldValue, selected) ->
                button.setStyle(selected ? normalStyle + ";" + SELECTED_STYLE : normalStyle));
            if (button.isSelected()) {
                button.setStyle(normalStyle + ";" + SELECTED_STYLE);
            }
        }
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) group.selectToggle(oldToggle);
        });
    }
}
