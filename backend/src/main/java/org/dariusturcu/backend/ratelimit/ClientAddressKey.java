package org.dariusturcu.backend.ratelimit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/**
 * Turns a client address into the key rate limits are counted against. An IPv6 client
 * usually controls its whole /64, so every address inside one shares a key instead of each
 * getting a fresh bucket.
 */
final class ClientAddressKey {

    private static final String IPV6_SEPARATOR = ":";
    private static final int IPV6_PREFIX_BYTES = 8;
    private static final String IPV6_PREFIX_SUFFIX = "/64";

    private ClientAddressKey() {
    }

    static String of(String remoteAddress) {
        if (remoteAddress == null || !remoteAddress.contains(IPV6_SEPARATOR)) {
            return remoteAddress;
        }
        try {
            if (InetAddress.getByName(remoteAddress) instanceof Inet6Address ipv6Address) {
                byte[] prefix = new byte[IPV6_PREFIX_BYTES];
                System.arraycopy(ipv6Address.getAddress(), 0, prefix, 0, IPV6_PREFIX_BYTES);
                return HexFormat.of().formatHex(prefix) + IPV6_PREFIX_SUFFIX;
            }
            return InetAddress.getByName(remoteAddress).getHostAddress();
        } catch (UnknownHostException unparseableAddress) {
            return remoteAddress;
        }
    }
}
