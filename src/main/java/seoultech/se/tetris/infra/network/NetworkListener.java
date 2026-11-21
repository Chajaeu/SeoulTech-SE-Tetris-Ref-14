package seoultech.se.tetris.infra.network;

/** 모든 콜백은 네트워크 백그라운드 스레드에서 발생할 수 있다. JavaFX UI 변경 시 Platform.runLater 사용. */
public interface NetworkListener {
    default void onConnected() {}
    default void onDisconnected(String reason) {}
    default void onMessage(NetworkMessage message) {}
    default void onLobbyChanged(LobbyState state) {}
    /** 로컬/원격 플레이어가 모두 Ready가 되었을 때 정확히 한 번 호출. */
    default void onMatchStarted(VersusMode mode, int durationSeconds) {}
    /** 로컬·상대측 경기 종료가 확정된 경우 호출. 양측 중복 종료는 Core에서 조정한다. */
    default void onMatchFinished(String result) {}
    default void onRemoteGameState(GameStateSnapshot state) {}
    default void onRemoteAttack(AttackPatternDTO attack) {}
    default void onError(String message, Throwable cause) {}
}
