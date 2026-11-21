package seoultech.se.tetris.infra.network;

/** 모든 콜백은 네트워크 백그라운드 스레드에서 발생할 수 있다. JavaFX UI 변경 시 Platform.runLater 사용. */
public interface NetworkListener {
    default void onConnected() {}
    default void onDisconnected(String reason) {}
    default void onMessage(NetworkMessage message) {}
    default void onLobbyChanged(LobbyState state) {}
    default void onRemoteGameState(GameStateSnapshot state) {}
    default void onRemoteAttack(AttackPatternDTO attack) {}
    default void onError(String message, Throwable cause) {}
}
