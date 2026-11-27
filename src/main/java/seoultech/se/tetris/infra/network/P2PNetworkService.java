package seoultech.se.tetris.infra.network;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;

/**
 * 서버/클라이언트 로비 상태, 준비 확인, 상태·공격 전송, 지연/단절 검출을 담당한다.
 * Core와 UI에 의존하지 않으므로, 게임이 완성되기 전에도 단독 테스트할 수 있다.
 * NetworkListener 콜백은 백그라운드 스레드에서 올 수 있다.
 */
public final class P2PNetworkService implements AutoCloseable {
    public static final long LAG_THRESHOLD_MS = 500;
    public static final long DISCONNECT_THRESHOLD_MS = 3_000;
    private final Object lock = new Object();
    private final NetworkListener listener;
    private final RecentIpRepository recentIpRepository;
    private final ExecutorService connector = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tetris-p2p-connect");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "tetris-p2p-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong outboundSeq = new AtomicLong();

    private P2PServer server;
    private P2PClient client;
    private PeerRole role;
    private LobbyPhase phase = LobbyPhase.DISCONNECTED;
    private VersusMode mode;
    private int durationSeconds;
    private boolean localReady;
    private boolean remoteReady;
    private boolean lagging;
    private long lastReceivedSeq = -1;
    private long lastPingNanos;
    private volatile long lastRttMillis = -1;
    private volatile boolean closed;
    /** 이전 소켓의 지연 콜백이 재접속 세션을 종료하지 못하도록 구분한다. */
    private long sessionId;

    public P2PNetworkService(NetworkListener listener) {
        this(listener, new RecentIpRepository());
    }
    /** 테스트 또는 UI에서 IP 저장 위치를 지정할 수 있다. */
    public P2PNetworkService(NetworkListener listener, RecentIpRepository recentIpRepository) {
        this.listener = Objects.requireNonNull(listener);
        this.recentIpRepository = Objects.requireNonNull(recentIpRepository);
        watchdog.scheduleAtFixedRate(this::heartbeat, 250, 250, TimeUnit.MILLISECONDS);
    }
    public List<String> getRecentIps() { return recentIpRepository.loadRecentIps(); }
    /** Ping/Pong 왕복 지연(ms). 상대방 게임 화면 표시 지연과는 다르다. */
    public long getLastRttMillis() { return lastRttMillis; }

    /** 서버 리슨 시작. port=0이면 테스트용 임의 포트 할당. */
    public int host(int port) throws IOException {
        P2PServer candidate;
        synchronized (lock) {
            ensureAvailable();
            // 먼저 포트를 확보한다. 바인딩 실패 시 서비스는 DISCONNECTED로 남아 재시도할 수 있다.
            candidate = new P2PServer(port, new ServerEvents(sessionId + 1));
            sessionId++;
            server = candidate;
            role = PeerRole.HOST;
            phase = LobbyPhase.CONNECTING;
        }
        candidate.start();
        publishLobby();
        return candidate.port();
    }

    /** 비동기 접속. UI 스레드에서 직접 호출 가능(완료 콜백은 백그라운드). */
    public CompletableFuture<Void> join(String ip, int port) {
        if (!RecentIpRepository.isValidIp(ip)) throw new IllegalArgumentException("IP 주소를 입력해야 합니다.");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1-65535");
        synchronized (lock) {
            ensureAvailable();
            role = PeerRole.CLIENT;
            phase = LobbyPhase.CONNECTING;
            sessionId++;
            client = new P2PClient(new ClientEvents(sessionId));
        }
        publishLobby();
        return CompletableFuture.runAsync(() -> {
            try {
                P2PClient target;
                synchronized (lock) { target = client; }
                if (target == null) throw new IOException("Connection cancelled");
                target.connect(ip, port, 3_000);
                synchronized (lock) {
                    if (client != target || phase == LobbyPhase.DISCONNECTED) {
                        target.close();
                        throw new IOException("Connection cancelled");
                    }
                }
                // 접속에 성공한 IP만 보존한다. 저장 실패는 정상 게임 연결을 방해하지 않는다.
                recentIpRepository.saveRecentIp(ip);
            } catch (IOException | RuntimeException e) {
                reportError("서버 접속 실패", e);
                stop();
                throw new java.util.concurrent.CompletionException(e);
            }
        }, connector);
    }

    public LobbyState getLobbyState() {
        synchronized (lock) { return snapshot(); }
    }
    public boolean isConnected() {
        synchronized (lock) { return currentConnected(); }
    }

    /** 서버만 모드를 설정할 수 있으며 모드 변경 시 Ready는 초기화된다. */
    public void selectMode(VersusMode selectedMode) {
        selectMode(selectedMode, selectedMode == VersusMode.TIMED ? 180 : 0);
    }

    /** TIMED 대전은 30~3600초에서 서버가 제한시간을 지정할 수 있다. */
    public void selectMode(VersusMode selectedMode, int selectedDurationSeconds) {
        Objects.requireNonNull(selectedMode);
        if (selectedMode == VersusMode.TIMED && (selectedDurationSeconds < 30 || selectedDurationSeconds > 3600)) {
            throw new IllegalArgumentException("Timed match must last 30-3600 seconds");
        }
        if (selectedMode != VersusMode.TIMED && selectedDurationSeconds != 0) {
            throw new IllegalArgumentException("Non-timed match duration must be 0");
        }
        synchronized (lock) {
            if (role != PeerRole.HOST || phase == LobbyPhase.PLAYING
                    || phase == LobbyPhase.DISCONNECTED) {
                throw new IllegalStateException("Only host may select mode before playing");
            }
            mode = selectedMode;
            durationSeconds = selectedDurationSeconds;
            localReady = false;
            remoteReady = false;
        }
        send(MessageType.MODE_SELECT, selectedMode.name() + "|" + selectedDurationSeconds);
        publishLobby();
    }

    /** 게임 시작 버튼에 대응. 2명 모두 Ready인 경우 서버가 START 이벤트를 생성. */
    public void setReady(boolean ready) {
        synchronized (lock) {
            if (phase != LobbyPhase.WAITING || mode == null) {
                throw new IllegalStateException("Cannot ready before mode selection and connection");
            }
            localReady = ready;
        }
        send(MessageType.READY, Boolean.toString(ready));
        publishLobby();
        maybeStart();
    }

    /** 내 게임 상태와 공격은 Core에서 제공하는 불변 값만 전송한다. */
    public boolean sendGameState(GameStateSnapshot state) {
        Objects.requireNonNull(state);
        return getLobbyState().phase() == LobbyPhase.PLAYING
                && send(MessageType.GAME_STATE, GamePayloadCodec.encodeState(state));
    }

    public boolean sendAttack(AttackPatternDTO attack) {
        Objects.requireNonNull(attack);
        return getLobbyState().phase() == LobbyPhase.PLAYING
                && send(MessageType.ATTACK, GamePayloadCodec.encodeAttack(attack));
    }

    /** Core가 판정한 최종 결과를 양쪽에 전달하고 다음 경기 대기 상태로 되돌린다. */
    public void finishMatch(String result) {
        Objects.requireNonNull(result);
        if (result.isBlank() || result.length() > 100) throw new IllegalArgumentException("Invalid match result");
        synchronized (lock) {
            if (phase != LobbyPhase.PLAYING) throw new IllegalStateException("No running match");
            phase = LobbyPhase.WAITING;
            localReady = false;
            remoteReady = false;
        }
        send(MessageType.GAME_OVER, result);
        publishLobby();
        listener.onMatchFinished(result);
    }

    /** P2P 화면에서 뒤로 가기, 타임아웃, 통신 오류 발생 시 초기화한다. */
    public void stop() { stopSession(-1); }

    /** expectedId가 일치할 때만 종료: 이전 연결의 늦은 콜백은 무시한다. */
    private boolean stopSession(long expectedId) {
        P2PServer oldServer;
        P2PClient oldClient;
        boolean notify;
        synchronized (lock) {
            if (expectedId >= 0 && expectedId != sessionId) return false;
            notify = phase != LobbyPhase.DISCONNECTED;
            sessionId++; // socket.close()가 호출할 콜백을 먼저 무효화한다.
            phase = LobbyPhase.DISCONNECTED;
            oldServer = server;
            oldClient = client;
            server = null;
            client = null;
            role = null;
            mode = null;
            durationSeconds = 0;
            localReady = false;
            remoteReady = false;
            lagging = false;
            lastReceivedSeq = -1;
            lastRttMillis = -1;
        }
        if (oldServer != null) oldServer.close();
        if (oldClient != null) oldClient.close();
        if (notify) publishLobby();
        return notify;
    }

    private boolean send(MessageType type, String payload) {
        synchronized (lock) {
            final NetworkMessage message = new NetworkMessage(type, outboundSeq.incrementAndGet(), payload);
            return switch (role == null ? PeerRole.CLIENT : role) {
                case HOST -> server != null && server.send(message);
                case CLIENT -> client != null && client.send(message);
            };
        }
    }

    private void onConnected(long sourceId) {
        synchronized (lock) {
            if (sessionId != sourceId || phase != LobbyPhase.CONNECTING) return;
            phase = LobbyPhase.WAITING;
            localReady = false;
            remoteReady = false;
            lagging = false;
            lastReceivedSeq = -1;
            outboundSeq.set(0);
        }
        send(MessageType.HELLO, Integer.toString(MessageCodec.VERSION));
        if (getLobbyState().role() == PeerRole.HOST && getLobbyState().mode() != null) {
            send(MessageType.MODE_SELECT, getLobbyState().mode().name() + "|" + getLobbyState().durationSeconds());
        }
        listener.onConnected();
        publishLobby();
    }

    private void onMessage(long sourceId, NetworkMessage message) {
        synchronized (lock) {
            if (sessionId != sourceId || phase == LobbyPhase.DISCONNECTED || message.sequence() <= lastReceivedSeq) return;
            lastReceivedSeq = message.sequence();
            if (lagging) lagging = false;
        }
        try {
            switch (message.type()) {
                case HELLO -> {
                    if (!Integer.toString(MessageCodec.VERSION).equals(message.payload()))
                        throw new IOException("Protocol version mismatch");
                }
                case MODE_SELECT -> {
                    String[] config = message.payload().split("\\|", -1);
                    if (config.length != 2) throw new IOException("Invalid mode selection payload");
                    VersusMode selected = VersusMode.valueOf(config[0]);
                    int seconds = Integer.parseInt(config[1]);
                    if (selected == VersusMode.TIMED && (seconds < 30 || seconds > 3600)
                            || selected != VersusMode.TIMED && seconds != 0) {
                        throw new IOException("Invalid time limit");
                    }
                    synchronized (lock) {
                        if (role == PeerRole.CLIENT && phase == LobbyPhase.WAITING) {
                            mode = selected;
                            durationSeconds = seconds;
                            localReady = false;
                            remoteReady = false;
                        }
                    }
                    publishLobby();
                }
                case READY -> {
                    if (!"true".equals(message.payload()) && !"false".equals(message.payload())) {
                        throw new IOException("Invalid READY payload");
                    }
                    synchronized (lock) {
                        if (phase == LobbyPhase.WAITING) remoteReady = Boolean.parseBoolean(message.payload());
                    }
                    publishLobby();
                    maybeStart();
                }
                case START -> {
                    boolean started = false;
                    synchronized (lock) {
                        if (role == PeerRole.CLIENT && phase == LobbyPhase.WAITING && localReady && mode != null) {
                            phase = LobbyPhase.PLAYING;
                            started = true;
                        }
                    }
                    if (started) {
                        publishLobby();
                        LobbyState current = getLobbyState();
                        listener.onMatchStarted(current.mode(), current.durationSeconds());
                    }
                }
                case GAME_STATE -> {
                    if (getLobbyState().phase() == LobbyPhase.PLAYING) {
                        listener.onRemoteGameState(GamePayloadCodec.decodeState(message.payload()));
                    }
                }
                case ATTACK -> {
                    if (getLobbyState().phase() == LobbyPhase.PLAYING) {
                        listener.onRemoteAttack(GamePayloadCodec.decodeAttack(message.payload()));
                    }
                }
                case GAME_OVER -> {
                    if (message.payload().isBlank() || message.payload().length() > 100) {
                        throw new IOException("Invalid match result");
                    }
                    boolean finished = false;
                    synchronized (lock) {
                        if (phase == LobbyPhase.PLAYING) {
                            phase = LobbyPhase.WAITING;
                            localReady = false;
                            remoteReady = false;
                            finished = true;
                        }
                    }
                    if (finished) {
                        publishLobby();
                        listener.onMatchFinished(message.payload());
                    }
                }
                case REMATCH -> { /* 새 경기 시에는 양쪽 모두 setReady(true) 호출 */ }
                case PING -> send(MessageType.PONG, message.payload());
                case PONG -> {
                    try {
                        long nanos = Long.parseLong(message.payload());
                        long rtt = TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - nanos));
                        lastRttMillis = rtt;
                        boolean slow = rtt > LAG_THRESHOLD_MS;
                        synchronized (lock) { lagging = slow; }
                        publishLobby();
                    } catch (NumberFormatException ignored) { }
                }
                case DISCONNECT -> {
                    if (stopSession(sourceId)) listener.onDisconnected("상대방이 연결을 종료했습니다.");
                }
                case ERROR -> reportError(message.payload(), null);
            }
            listener.onMessage(message);
        } catch (IOException | IllegalArgumentException e) {
            if (stopSession(sourceId)) {
                reportError("P2P 메시지 오류", e);
                listener.onDisconnected("P2P 메시지 검증에 실패했습니다.");
            }
        }
    }

    private void maybeStart() {
        boolean start;
        synchronized (lock) {
            start = role == PeerRole.HOST && phase == LobbyPhase.WAITING
                    && mode != null && localReady && remoteReady;
            if (start) phase = LobbyPhase.PLAYING;
        }
        if (start) {
            send(MessageType.START, "");
            publishLobby();
            LobbyState current = getLobbyState();
            listener.onMatchStarted(current.mode(), current.durationSeconds());
        }
    }

    private void heartbeat() {
        try {
            if (closed || !isConnected()) return;
            long idle;
            long observedSessionId;
            synchronized (lock) {
                observedSessionId = sessionId;
                if (!currentConnected()) return;
                idle = role == PeerRole.HOST ? server.millisSinceIncoming() : client.millisSinceIncoming();
            }
            if (idle >= DISCONNECT_THRESHOLD_MS) {
                if (stopSession(observedSessionId)) {
                    reportError("상대방 응답이 없어 연결을 종료합니다.", null);
                    listener.onDisconnected("네트워크 연결 시간 초과");
                }
                return;
            }
            boolean changed;
            synchronized (lock) {
                changed = lagging != (idle >= LAG_THRESHOLD_MS);
                lagging = idle >= LAG_THRESHOLD_MS;
            }
            if (changed) publishLobby();
            long now = System.nanoTime();
            if (now - lastPingNanos >= TimeUnit.MILLISECONDS.toNanos(500)) {
                lastPingNanos = now;
                send(MessageType.PING, Long.toString(now));
            }
        } catch (RuntimeException e) { reportError("네트워크 상태 점검 오류", e); }
    }

    private void onDisconnected(long sourceId, String reason, Throwable cause) {
        if (!stopSession(sourceId)) return;
        if (cause != null) reportError(reason, cause);
        listener.onDisconnected(reason);
    }

    private boolean currentConnected() {
        return (role == PeerRole.HOST && server != null && server.isConnected())
                || (role == PeerRole.CLIENT && client != null && client.isConnected());
    }
    private LobbyState snapshot() {
        return new LobbyState(role, phase, mode, durationSeconds, localReady, remoteReady, lagging);
    }
    private void publishLobby() { listener.onLobbyChanged(getLobbyState()); }
    private void reportError(String message, Throwable cause) { listener.onError(message, cause); }
    private void ensureAvailable() {
        if (closed || phase != LobbyPhase.DISCONNECTED) throw new IllegalStateException("P2P service already in use");
    }
    @Override public void close() {
        closed = true;
        stop();
        watchdog.shutdownNow();
        connector.shutdownNow();
    }

    private final class ServerEvents implements P2PServer.Events {
        private final long id;
        ServerEvents(long id) { this.id = id; }
        public void onConnected() { P2PNetworkService.this.onConnected(id); }
        public void onMessage(NetworkMessage m) { P2PNetworkService.this.onMessage(id, m); }
        public void onDisconnected(String reason, Throwable error) { P2PNetworkService.this.onDisconnected(id, reason, error); }
    }
    private final class ClientEvents implements P2PClient.Events {
        private final long id;
        ClientEvents(long id) { this.id = id; }
        public void onConnected() { P2PNetworkService.this.onConnected(id); }
        public void onMessage(NetworkMessage m) { P2PNetworkService.this.onMessage(id, m); }
        public void onDisconnected(String reason, Throwable error) { P2PNetworkService.this.onDisconnected(id, reason, error); }
    }
}
