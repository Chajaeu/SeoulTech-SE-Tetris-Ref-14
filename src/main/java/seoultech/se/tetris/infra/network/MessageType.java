package seoultech.se.tetris.infra.network;

/** 3차 P2P 요구사항: 앱 간 통신 메시지 유형. */
public enum MessageType {
    HELLO, MODE_SELECT, READY, START,
    GAME_STATE, ATTACK, GAME_OVER, REMATCH,
    PING, PONG, DISCONNECT, ERROR
}
