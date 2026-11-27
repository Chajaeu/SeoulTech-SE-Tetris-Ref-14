package seoultech.se.tetris.ui;

import java.io.IOException;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import seoultech.se.tetris.game.Game;

public final class Result {
    public enum Winner {
        PLAYER_1, PLAYER_2, DRAW
    }

    private Result() {}

    public static Optional<Winner> getResult(boolean finished, Game game1, Game game2) {
        if (!finished) {
            return Optional.empty();
        }

        int player1Score = game1.getScore();
        int player2Score = game2.getScore();
        if (player1Score > player2Score) {
            return Optional.of(Winner.PLAYER_1);
        }
        if (player2Score > player1Score) {
            return Optional.of(Winner.PLAYER_2);
        }
        return Optional.of(Winner.DRAW);
    }

    static Parent create(Winner winner, int score1, int score2, Runnable onRestart,
            Runnable onBack) throws IOException {
        FXMLLoader loader = new FXMLLoader(Result.class.getResource("fxml/Result.fxml"));
        Parent root = loader.load();
        Label firstPlace = (Label) loader.getNamespace().get("firstPlaceLabel");
        Label firstScore = (Label) loader.getNamespace().get("firstScoreLabel");
        Label secondPlace = (Label) loader.getNamespace().get("secondPlaceLabel");
        Label secondScore = (Label) loader.getNamespace().get("secondScoreLabel");

        if (winner == Winner.DRAW) {
            firstPlace.setText("DRAW : PLAYER 1");
            secondPlace.setText("DRAW : PLAYER 2");
            firstScore.setText("Score : " + score1);
            secondScore.setText("Score : " + score2);
        } else {
            boolean player1Won = winner == Winner.PLAYER_1;
            firstPlace.setText("1ST : PLAYER " + (player1Won ? 1 : 2));
            secondPlace.setText("2ND : PLAYER " + (player1Won ? 2 : 1));
            firstScore.setText("Score : " + (player1Won ? score1 : score2));
            secondScore.setText("Score : " + (player1Won ? score2 : score1));
        }

        Button restart = (Button) loader.getNamespace().get("startButton");
        Button back = (Button) loader.getNamespace().get("backButton");
        Button close = (Button) loader.getNamespace().get("closeButton");
        restart.setOnAction(e -> onRestart.run());
        back.setOnAction(e -> onBack.run());
        close.setOnAction(e -> onBack.run());
        ButtonEffects.addPressEffect(restart);
        ButtonEffects.addPressEffect(back);
        ButtonEffects.addPressEffect(close);
        UIInteraction.installNavigation(root, restart);
        return root;
    }
}
