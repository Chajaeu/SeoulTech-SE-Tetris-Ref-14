package seoultech.se.tetris.infra.persistence;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 최근 접속 IP 저장. 파일 손상/누락 시 빈 목록, 저장은 원자적 교체를 시도한다. */
public final class RecentIpRepository {
    private static final int MAX_RECENT = 8;
    private final Path file;

    public RecentIpRepository() {
        this(Path.of(System.getProperty("user.home"), ".se-tetris-team14"));
    }
    public RecentIpRepository(Path directory) {
        file = Objects.requireNonNull(directory).resolve("recent-ips.txt");
    }
    public Path getFile() { return file; }

    public synchronized List<String> loadRecentIps() {
        try {
            if (!Files.exists(file)) return List.of();
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String ip = line.trim();
                if (isValidIp(ip)) result.add(ip);
                if (result.size() >= MAX_RECENT) break;
            }
            return List.copyOf(result);
        } catch (IOException | SecurityException e) { return List.of(); }
    }

    public synchronized boolean saveRecentIp(String ip) {
        if (!isValidIp(ip)) return false;
        Path temp = null;
        try {
            Files.createDirectories(file.getParent());
            LinkedHashSet<String> values = new LinkedHashSet<>();
            values.add(ip);
            values.addAll(loadRecentIps());
            List<String> recent = new ArrayList<>(values);
            if (recent.size() > MAX_RECENT) recent = recent.subList(0, MAX_RECENT);
            temp = Files.createTempFile(file.getParent(), "recent-ips-", ".tmp");
            Files.write(temp, recent, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | SecurityException e) { return false; }
        finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    /** 호스트명은 저장하지 않는다. DNS 조회 없는 IPv4 또는 IPv6 리터럴만 허용한다. */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45 || !ip.equals(ip.trim())) return false;
        if (ip.contains(":")) {
            if (!ip.matches("[0-9a-fA-F:.]+")) return false;
            try { return InetAddress.getByName(ip).getHostAddress().contains(":"); }
            catch (UnknownHostException e) { return false; }
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.matches("[0-9]+")) return false;
            if (part.length() > 1 && part.charAt(0) == '0') return false;
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }
}
