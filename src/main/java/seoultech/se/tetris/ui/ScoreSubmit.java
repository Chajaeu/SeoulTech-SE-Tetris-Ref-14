package seoultech.se.tetris.ui;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import seoultech.se.tetris.infra.dto.ScoreDTO;
import seoultech.se.tetris.infra.integration.GameSessionService;

public class ScoreSubmit {

    private boolean finished;

    public static Parent create(GameSessionService session, Runnable onClose,
            Consumer<String> onSaved) throws Exception {
        return new ScoreSubmit().createPopup(session, onClose, onSaved);
    }

    private Parent createPopup(GameSessionService session, Runnable onClose,
            Consumer<String> onSaved) throws Exception {
        FXMLLoader loader = new FXMLLoader(ScoreSubmit.class.getResource("fxml/ScoreSubmit.fxml"));
        Parent root = loader.load();
        Label rankLabel = (Label) loader.getNamespace().get("rankLabel");
        Label modeLabel = (Label) loader.getNamespace().get("modeLabel");
        Label scoreLabel = (Label) loader.getNamespace().get("scoreLabel");
        Label errorLabel = (Label) loader.getNamespace().get("errorLabel");
        TextField nameField = (TextField) loader.getNamespace().get("nameField");
        Button submitButton = (Button) loader.getNamespace().get("submitButton");
        Button closeButton = (Button) loader.getNamespace().get("closeButton");

        int score = session.getGame().getScore();
        // 동점은 먼저 저장된 기록을 우선
        long rank = session.getTopScores(session.getGameMode(), session.getDifficulty()).stream()
            .filter(record -> record.getScore() >= score).count() + 1;
        rankLabel.setText("RANK " + rank);
        modeLabel.setText(session.getGameMode().name() + " / " + session.getDifficulty().name());
        scoreLabel.setText(Scoreboard.formatScore(score));

        submitButton.setOnAction(e -> {
            if (finished) {
                return;
            }
            String name = nameField.getText();
            try {
                // 이름 검증은 Infra DTO의 기준을 사용
                new ScoreDTO(name, score, session.getGameMode(), session.getDifficulty());
            } catch (IllegalArgumentException ex) {
                errorLabel.setText("이름은 1~20자로 입력해주세요. (제어 문자 제외)");
                nameField.requestFocus();
                return;
            }

            Set<String> previousIds = session.getTopScores(session.getGameMode(), session.getDifficulty())
                .stream().map(ScoreDTO::getId).collect(Collectors.toSet());
            if (!session.saveFinalScore(name)) {
                errorLabel.setText("기록을 저장하지 못했습니다. TOP 10 대상과 저장 위치를 확인해주세요.");
                return;
            }

            finished = true;
            submitButton.setDisable(true);
            // 같은 이름과 점수의 기존 기록 대신, 새로 저장된 ID로 강조
            String savedId = session.getTopScores(session.getGameMode(), session.getDifficulty()).stream()
                .filter(record -> !previousIds.contains(record.getId()))
                .filter(record -> record.getScore() == score && record.getPlayerName().equals(name.trim()))
                .map(ScoreDTO::getId).findFirst().orElse(null);
            onSaved.accept(savedId);
        });
        nameField.setOnAction(e -> {
            submitButton.fire();
            e.consume();
        });
        closeButton.setOnAction(e -> {
            if (!finished) {
                finished = true;
                onClose.run();
            }
        });
        ButtonEffects.addPressEffect(submitButton);
        ButtonEffects.addPressEffect(closeButton);
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> {
                    if (nameField.getScene() == newScene) {
                        nameField.requestFocus();
                    }
                });
            }
        });
        UIInteraction.installNavigation(root, nameField);
        return root;
    }
}
