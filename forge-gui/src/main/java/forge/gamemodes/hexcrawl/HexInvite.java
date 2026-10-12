package forge.gamemodes.hexcrawl;

import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Invitations to a hosted campaign. The host does not need to know which of its addresses a
 * friend can reach: the invitation code carries all of them (VPN, internet, IPv6, home network),
 * and the friend's program tries them all and takes the first one that answers.
 */
public final class HexInvite {
    private static final String PREFIX = "HEX-";

    /** One way to reach the host. */
    public static final class Address {
        /** What kind of address it is: VPN, WAN, IPV6 or LAN. */
        public final String kind;
        /** Host and port, as the join code of Forge's lobby expects them. */
        public final String url;

        Address(final String kind0, final String url0) {
            kind = kind0;
            url = url0;
        }
    }

    /** What trying one address gave. */
    public enum Probe { OK, REFUSED, TIMEOUT, UNKNOWN_HOST, ERROR }

    /** The result of trying one address. */
    public static final class ProbeResult {
        public final Address address;
        public final Probe probe;

        ProbeResult(final Address address0, final Probe probe0) {
            address = address0;
            probe = probe0;
        }
    }

    private HexInvite() { }

    /**
     * All addresses under which this computer might be reachable on the given port, the most
     * promising first: VPN networks (they work through every router), the internet address,
     * global IPv6 addresses, and the home network.
     * @param externalAddress the internet address as seen from outside, or null if unknown
     */
    public static List<Address> collectAddresses(final String externalAddress, final int port) {
        final List<Address> vpn = new ArrayList<>();
        final List<Address> v6 = new ArrayList<>();
        final List<Address> lan = new ArrayList<>();
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (final NetworkInterface iface : interfaces == null ? Collections.<NetworkInterface>emptyList() : Collections.list(interfaces)) {
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                final String names = (iface.getName() + " " + iface.getDisplayName()).toLowerCase();
                for (final InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                        continue;
                    }
                    final String ip = addr.getHostAddress().replaceAll("%.*$", "");
                    if (addr instanceof Inet4Address) {
                        if (isVpn(ip, names)) {
                            vpn.add(new Address("VPN", ip + ":" + port));
                        } else if (addr.isSiteLocalAddress()) {
                            lan.add(new Address("LAN", ip + ":" + port));
                        }
                    } else if (addr instanceof Inet6Address && isGlobalV6(ip)) {
                        v6.add(new Address(names.contains("tailscale") || names.contains("zerotier") ? "VPN" : "IPV6", "[" + ip + "]:" + port));
                    }
                }
            }
        } catch (final Exception e) {
            // without the list of interfaces only the internet address is offered
        }
        final List<Address> all = new ArrayList<>(vpn);
        if (externalAddress != null && !externalAddress.trim().isEmpty()) {
            all.add(new Address("WAN", externalAddress.trim() + ":" + port));
        }
        all.addAll(v6);
        all.addAll(lan);
        final String extra = System.getProperty("forge.hex.extraAddress"); // for tests on a single machine
        if (extra != null && !extra.isEmpty()) {
            all.add(new Address("LAN", extra));
        }
        return all;
    }

    /** Tailscale (100.64.0.0/10), Radmin (26.x), Hamachi (25.x), ZeroTier and other tunnels. */
    static boolean isVpn(final String ip, final String names) {
        if (names.contains("tailscale") || names.contains("zerotier") || names.contains("radmin") || names.contains("hamachi")
                || names.contains("wireguard") || names.startsWith("zt")) {
            return true;
        }
        final String[] p = ip.split("\\.");
        if (p.length != 4) {
            return false;
        }
        try {
            final int a = Integer.parseInt(p[0]);
            final int b = Integer.parseInt(p[1]);
            return (a == 100 && b >= 64 && b <= 127) || a == 25 || a == 26;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /** For tests: is this an address of a VPN? */
    public static boolean isVpnForTest(final String ip) {
        return isVpn(ip, "");
    }

    /** Addresses from 2000::/3 are reachable on the internet; fc00::/7 and fe80::/10 are not. */
    static boolean isGlobalV6(final String ip) {
        final String lower = ip.toLowerCase();
        return !lower.isEmpty() && (lower.charAt(0) == '2' || lower.charAt(0) == '3') && lower.contains(":");
    }

    /** The invitation code for a list of addresses: "HEX-" followed by the addresses, base64 encoded. */
    public static String encode(final List<Address> addresses) {
        final StringBuilder sb = new StringBuilder("1");
        for (final Address a : addresses) {
            sb.append('|').append(a.kind).append('=').append(a.url);
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads an invitation code. Plain addresses ("1.2.3.4:36743", "name.example.org") are
     * accepted as well, so that nobody has to know which of the two they got.
     * @return the addresses, empty if the text is neither
     */
    public static List<Address> decode(final String text) {
        final List<Address> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        final String code = text.trim().replaceAll("\\s+", "");
        if (code.isEmpty()) {
            return result;
        }
        if (code.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            try {
                final String plain = new String(Base64.getUrlDecoder().decode(code.substring(PREFIX.length())), StandardCharsets.UTF_8);
                final String[] parts = plain.split("\\|");
                for (int i = 1; i < parts.length; i++) {
                    final int eq = parts[i].indexOf('=');
                    if (eq > 0 && eq < parts[i].length() - 1) {
                        result.add(new Address(parts[i].substring(0, eq), parts[i].substring(eq + 1)));
                    }
                }
            } catch (final IllegalArgumentException e) {
                // not a code after all
            }
            return result;
        }
        result.add(new Address("DIRECT", code));
        return result;
    }

    /** Splits "host:port", "[v6]:port" or "host" into host and port. */
    static String[] hostAndPort(final String url, final int defaultPort) {
        String host = url;
        String port = String.valueOf(defaultPort);
        if (url.startsWith("[")) {
            final int end = url.indexOf(']');
            host = end > 0 ? url.substring(1, end) : url;
            if (end > 0 && url.length() > end + 2 && url.charAt(end + 1) == ':') {
                port = url.substring(end + 2);
            }
        } else if (url.indexOf(':') == url.lastIndexOf(':') && url.indexOf(':') > 0) {
            host = url.substring(0, url.indexOf(':'));
            port = url.substring(url.indexOf(':') + 1);
        }
        return new String[] { host, port };
    }

    /** Tries to open a connection to one address. */
    public static Probe probe(final String url, final int defaultPort, final int timeoutMs) {
        final String[] hp = hostAndPort(url, defaultPort);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName(hp[0]), Integer.parseInt(hp[1])), timeoutMs);
            return Probe.OK;
        } catch (final UnknownHostException e) {
            return Probe.UNKNOWN_HOST;
        } catch (final ConnectException e) {
            return e.getMessage() != null && e.getMessage().toLowerCase().contains("timed out") ? Probe.TIMEOUT : Probe.REFUSED;
        } catch (final SocketTimeoutException e) {
            return Probe.TIMEOUT;
        } catch (final Exception e) {
            return Probe.ERROR;
        }
    }

    /** Tries all addresses at the same time and reports how each one went, in the given order. */
    public static List<ProbeResult> probeAll(final List<Address> addresses, final int defaultPort, final int timeoutMs) {
        final List<ProbeResult> results = new ArrayList<>();
        if (addresses.isEmpty()) {
            return results;
        }
        final ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, addresses.size()));
        try {
            final List<Future<Probe>> futures = new ArrayList<>();
            for (final Address a : addresses) {
                futures.add(pool.submit(() -> probe(a.url, defaultPort, timeoutMs)));
            }
            for (int i = 0; i < addresses.size(); i++) {
                Probe p;
                try {
                    p = futures.get(i).get(timeoutMs + 2000L, TimeUnit.MILLISECONDS);
                } catch (final Exception e) {
                    p = Probe.TIMEOUT;
                }
                results.add(new ProbeResult(addresses.get(i), p));
            }
        } finally {
            pool.shutdownNow();
        }
        return results;
    }

    /** The first address that answered, or null. */
    public static Address firstReachable(final List<ProbeResult> results) {
        for (final ProbeResult r : results) {
            if (r.probe == Probe.OK) {
                return r.address;
            }
        }
        return null;
    }
}
