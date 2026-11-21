package seoultech.se.tetris.infra.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.infra.persistence.RecentIpRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RecentIpRepositoryTest {
    @TempDir Path dir;
    @Test void savesRestoresAndDeduplicates() {
        RecentIpRepository repo = new RecentIpRepository(dir);
        assertEquals(List.of(), repo.loadRecentIps());
        assertTrue(repo.saveRecentIp("192.168.0.2"));
        assertTrue(repo.saveRecentIp("10.0.0.1"));
        assertTrue(repo.saveRecentIp("192.168.0.2"));
        assertEquals(List.of("192.168.0.2", "10.0.0.1"), new RecentIpRepository(dir).loadRecentIps());
    }
    @Test void rejectsInvalidIpAndIgnoresCorruption() throws IOException {
        RecentIpRepository repo = new RecentIpRepository(dir);
        assertFalse(repo.saveRecentIp("256.256.1.1"));
        assertFalse(repo.saveRecentIp("example.org"));
        Files.writeString(repo.getFile(), "NOT-IP\n192.168.1.3\n");
        assertEquals(List.of("192.168.1.3"), repo.loadRecentIps());
        assertTrue(repo.saveRecentIp("127.0.0.1"));
        assertEquals(List.of("127.0.0.1", "192.168.1.3"), repo.loadRecentIps());
    }
    @Test void keepsAtMostEight() {
        RecentIpRepository repo = new RecentIpRepository(dir);
        for (int i=1;i<=12;i++) assertTrue(repo.saveRecentIp("10.0.0."+i));
        assertEquals(8, repo.loadRecentIps().size());
        assertEquals("10.0.0.12", repo.loadRecentIps().getFirst());
    }
}
