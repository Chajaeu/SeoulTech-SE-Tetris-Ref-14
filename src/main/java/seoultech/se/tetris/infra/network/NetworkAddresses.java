package seoultech.se.tetris.infra.network;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 서버 선택 화면에 표시할 로컬 네트워크 IPv4 후보. */
public final class NetworkAddresses {
    private NetworkAddresses() {}
    public static List<String> lanIpv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                for (var address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address ipv4 && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) addresses.add(ipv4.getHostAddress());
                }
            }
        } catch (SocketException e) { /* 환경에 따라 조회 불가: 아래 루프백 주소로 안내 */ }
        if (addresses.isEmpty()) addresses.add("127.0.0.1");
        return List.copyOf(addresses);
    }
}
