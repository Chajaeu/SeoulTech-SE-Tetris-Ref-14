import seoultech.se.tetris.infra.network.*;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Gradle 없이도 java 21 표준 라이브러리만으로 실행 가능한 통신 스모크 검증. */
public class P2PStandaloneSmoke {
    static void waitFor(String name, java.util.function.BooleanSupplier f) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline && !f.getAsBoolean()) Thread.sleep(10);
        if(!f.getAsBoolean()) throw new AssertionError("Timed out: "+name);
    }
    static void check(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    static class Events implements NetworkListener {
        AtomicReference<GameStateSnapshot> state=new AtomicReference<>();
        AtomicReference<AttackPatternDTO> attack=new AtomicReference<>();
        AtomicReference<Throwable> error=new AtomicReference<>();
        public void onRemoteGameState(GameStateSnapshot s){state.set(s);}
        public void onRemoteAttack(AttackPatternDTO a){attack.set(a);}
        public void onError(String message,Throwable e){if(e!=null)error.set(e);}
    }
    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("team14-p2p-smoke-");
        RecentIpRepository repository=new RecentIpRepository(temp);
        check(repository.saveRecentIp("127.0.0.1"),"IP save");
        check(repository.loadRecentIps().equals(List.of("127.0.0.1")),"IP load");
        check(!repository.saveRecentIp("999.999.999.999"),"invalid IP");
        Events a=new Events(), b=new Events();
        try(P2PNetworkService host=new P2PNetworkService(a,new RecentIpRepository(temp.resolve("host"))); P2PNetworkService client=new P2PNetworkService(b,new RecentIpRepository(temp.resolve("client")))) {
            int port=host.host(0);
            client.join("127.0.0.1",port).get(3,TimeUnit.SECONDS);
            waitFor("connected",()->host.isConnected() && client.isConnected());
            waitFor("IP auto-save",()->client.getRecentIps().equals(List.of("127.0.0.1")));
            host.selectMode(VersusMode.TIMED);
            waitFor("mode",()->client.getLobbyState().mode()==VersusMode.TIMED);
            host.setReady(true);client.setReady(true);
            waitFor("playing",()->host.getLobbyState().phase()==LobbyPhase.PLAYING && client.getLobbyState().phase()==LobbyPhase.PLAYING);
            GameStateSnapshot snapshot=new GameStateSnapshot(Collections.nCopies(20,".........."),List.of("IIII"),25,1,1,false);
            AttackPatternDTO attack=new AttackPatternDTO(List.of("##..######","####..####"));
            check(host.sendGameState(snapshot),"state send");
            check(client.sendAttack(attack),"attack send");
            waitFor("state received",()->snapshot.equals(b.state.get()));
            waitFor("attack received",()->attack.equals(a.attack.get()));
            host.finishMatch("HOST_WON");
            waitFor("lobby",()->client.getLobbyState().phase()==LobbyPhase.WAITING);
            host.setReady(true);client.setReady(true);
            waitFor("rematch",()->client.getLobbyState().phase()==LobbyPhase.PLAYING);
            check(a.error.get()==null,"host error: "+a.error.get());
            check(b.error.get()==null,"client error: "+b.error.get());
            client.stop();
            waitFor("disconnect",()->host.getLobbyState().phase()==LobbyPhase.DISCONNECTED);
        }
        System.out.println("PASS: IP persistence, TCP connect, mode, Ready, state, attack, rematch, disconnect");
    }
}
