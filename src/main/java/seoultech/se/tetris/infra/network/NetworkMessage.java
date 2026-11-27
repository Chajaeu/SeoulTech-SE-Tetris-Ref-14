package seoultech.se.tetris.infra.network;

import java.util.Objects;

/** 전송 불변 값. sequence는 같은 연결에서 순서 판별 및 중복 방지에 사용한다. */
public record NetworkMessage(MessageType type, long sequence, String payload) {
    public NetworkMessage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be >= 0");
    }
}
