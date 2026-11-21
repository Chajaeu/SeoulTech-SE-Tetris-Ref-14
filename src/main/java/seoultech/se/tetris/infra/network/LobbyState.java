package seoultech.se.tetris.infra.network;

/** UI에서 조회 가능한 읽기 전용 로비 상태. */
public record LobbyState(PeerRole role, LobbyPhase phase, VersusMode mode,
                         int durationSeconds, boolean localReady, boolean remoteReady, boolean lagging) {}
