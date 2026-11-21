import seoultech.se.tetris.infra.network.*;
import java.util.*;

/** UI 준비 전 두 PC 접속 검증용 콘솔. 실게임 구현과 별개의 테스트 도구. */
public class P2PConsoleDemo {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !(args[0].equals("host") || args[0].equals("join"))) {
            System.out.println("Usage: host <port> | join <host-ip> <port>");
            return;
        }
        NetworkListener logger = new NetworkListener() {
            public void onConnected() { System.out.println("[OK] connected"); }
            public void onDisconnected(String reason) { System.out.println("[DISCONNECTED] " + reason); }
            public void onLobbyChanged(LobbyState state) { System.out.println("[LOBBY] " + state); }
            public void onRemoteGameState(GameStateSnapshot state) {
                System.out.println("[STATE] score=" + state.score() + " / lines=" + state.lines());
            }
            public void onRemoteAttack(AttackPatternDTO attack) { System.out.println("[ATTACK] " + attack.rows()); }
            public void onError(String message, Throwable cause) {
                System.out.println("[ERROR] " + message + (cause == null ? "" : " : " + cause.getMessage()));
            }
            public void onMessage(NetworkMessage msg) {
                if (msg.type() == MessageType.GAME_OVER) System.out.println("[RESULT] " + msg.payload());
            }
        };
        try (P2PNetworkService service = new P2PNetworkService(logger);
             Scanner scanner = new Scanner(System.in)) {
            if (args[0].equals("host")) {
                int port = service.host(Integer.parseInt(args[1]));
                System.out.println("Host listening on port " + port + " / LAN IP: " + NetworkAddresses.lanIpv4Addresses());
            } else {
                if (args.length != 3) throw new IllegalArgumentException("join <host-ip> <port>");
                service.join(args[1], Integer.parseInt(args[2])).get();
            }
            System.out.println("Commands: mode NORMAL|ITEM|TIMED, ready, unready, state, attack, finish, status, quit");
            while (scanner.hasNextLine()) {
                String[] parts = scanner.nextLine().trim().split("\\s+");
                try {
                    switch (parts[0].toLowerCase()) {
                        case "mode" -> service.selectMode(VersusMode.valueOf(parts[1].toUpperCase()));
                        case "ready" -> service.setReady(true);
                        case "unready" -> service.setReady(false);
                        case "state" -> System.out.println("state sent="+service.sendGameState(
                                new GameStateSnapshot(Collections.nCopies(20,".........."), List.of("IIII"), 123,2,1,false)));
                        case "attack" -> System.out.println("attack sent="+service.sendAttack(
                                new AttackPatternDTO(List.of("###..#####","####..####"))));
                        case "finish" -> service.finishMatch("HOST_WON");
                        case "status" -> System.out.println(service.getLobbyState()+" / RTT="+service.getLastRttMillis()+"ms");
                        case "quit", "exit" -> { return; }
                        default -> System.out.println("unknown command");
                    }
                } catch (RuntimeException e) { System.out.println("[WARN] " + e.getMessage()); }
            }
        }
    }
}
