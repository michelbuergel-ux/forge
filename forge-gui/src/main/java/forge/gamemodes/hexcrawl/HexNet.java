package forge.gamemodes.hexcrawl;

import forge.gamemodes.net.IRemote;
import forge.gamemodes.net.event.HexActionEvent;
import forge.gamemodes.net.event.HexStateEvent;

/**
 * The meeting point between the network layer and the hex campaign: the server hands
 * guests' actions to the host's session here, a client hands the host's state to its screen.
 */
public final class HexNet {
    public interface HostListener {
        /** Called on a network thread. */
        void onAction(String userName, HexActionEvent action);
    }
    public interface GuestListener {
        /** Called on a network thread. */
        void onState(HexStateEvent state);
    }

    private static volatile HostListener hostListener;
    private static volatile GuestListener guestListener;
    private static volatile IRemote server; // set while this program is a guest
    private static volatile HexStateEvent lastState;

    private static volatile java.util.function.BooleanSupplier campaignFight;
    private static volatile Runnable resultClosed;

    /**
     * Changes whenever the campaign's network messages change; host and guests have to use
     * the same build. Shown as a warning when they differ.
     */
    public static final String BUILD_ID = "hex-2026-10-15";

    private static volatile String buildId;

    /**
     * What host and guests have to share: the campaign's build and Forge's own network protocol
     * (its list of messages). Programs that differ here cannot play a fight together: the first
     * message the other side does not know breaks the connection.
     */
    public static String buildId() {
        String id = buildId;
        if (id == null) {
            final StringBuilder methods = new StringBuilder();
            for (final forge.gamemodes.net.ProtocolMethod m : forge.gamemodes.net.ProtocolMethod.values()) {
                methods.append(m.name()).append(',');
            }
            id = BUILD_ID + "/" + Integer.toHexString(methods.toString().hashCode()) + System.getProperty("forge.hex.buildSuffix", ""); // suffix: tests
            buildId = id;
        }
        return id;
    }

    private HexNet() { }

    /** The connection to the host while this program is a guest, or null. */
    public static IRemote getServer() { return server; }

    /** Guest: how the connection to the host is doing ("CONNECTED", "RECONNECTING", "FAILED", ...), or null. */
    public static String connectionState() {
        final IRemote s = server;
        if (s instanceof forge.gamemodes.net.client.FGameClient client) {
            return client.getReconnectState().name();
        }
        return s == null ? null : "CONNECTED";
    }

    /** Guest: leaves the host. */
    public static void leave() {
        final IRemote s = server;
        setServer(null);
        if (s instanceof forge.gamemodes.net.client.FGameClient client) {
            client.close();
        }
    }

    /** The campaign screen tells how to find out whether the match on screen is a fight of the campaign. */
    public static void setFightHooks(final java.util.function.BooleanSupplier isCampaignFight, final Runnable onResultClosed) {
        campaignFight = isCampaignFight;
        resultClosed = onResultClosed;
    }

    /** Is the match that is on screen (or just ended) a fight of the hex campaign? */
    public static boolean isCampaignFight() {
        final java.util.function.BooleanSupplier check = campaignFight;
        return check != null && check.getAsBoolean();
    }

    /** The result screen of a match was closed. */
    public static void resultScreenClosed() {
        final Runnable r = resultClosed;
        if (r != null) {
            r.run();
        }
    }

    public static void setHostListener(final HostListener listener) { hostListener = listener; }

    public static void setGuestListener(final GuestListener listener) {
        guestListener = listener;
        final HexStateEvent pending = lastState;
        if (listener != null && pending != null) {
            listener.onState(pending); // a state that arrived before the screen existed
        }
    }

    /** Called when this program joins a host (null when the connection ends). */
    public static void setServer(final IRemote server0) {
        server = server0;
        if (server0 == null) {
            lastState = null;
        }
    }

    public static boolean isGuest() { return server != null; }

    public static void fromClient(final String userName, final HexActionEvent action) {
        final HostListener l = hostListener;
        if (l != null) {
            l.onAction(userName, action);
        }
    }

    /**
     * A state from the host. It is handed on from a thread of its own, never from the network
     * thread: that thread also carries the game, and reading a whole campaign there would hold up
     * what happens on the table. If several states arrive in the meantime, only the newest counts.
     */
    public static void fromServer(final HexStateEvent state) {
        lastState = state;
        if (pendingState.getAndSet(state) == null) {
            DELIVERY.execute(HexNet::deliverPending);
        }
    }

    private static final java.util.concurrent.atomic.AtomicReference<HexStateEvent> pendingState = new java.util.concurrent.atomic.AtomicReference<>();
    private static final java.util.concurrent.Executor DELIVERY = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "Hex campaign state");
        t.setDaemon(true);
        return t;
    });

    private static void deliverPending() {
        final HexStateEvent state = pendingState.getAndSet(null);
        final GuestListener l = guestListener;
        if (state != null && l != null) {
            try {
                l.onState(state);
            } catch (final RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    public static void sendToHost(final HexActionEvent action) {
        final IRemote s = server;
        if (s != null) {
            s.send(action);
        }
    }
}
