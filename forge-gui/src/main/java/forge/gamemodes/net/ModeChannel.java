package forge.gamemodes.net;

import forge.gamemodes.net.event.NetEvent;

/**
 * Messages of a game mode that runs between the matches of a hosted lobby (for example a campaign the host plays
 * with its guests). The client and the server only pass them on; the mode registers one handler that knows its own
 * message types.
 */
public final class ModeChannel {
    /** A message of a game mode. */
    public interface Message extends NetEvent { }

    public interface Handler {
        /** This program joined a host (null when the connection ends). */
        default void connected(final IRemote server) { }

        /** On the host: a message from a guest. */
        default void fromClient(final String userName, final Message message) { }

        /** On a guest: a message from the host. */
        default void fromServer(final Message message) { }
    }

    private static volatile Handler handler;

    private ModeChannel() { }

    public static void setHandler(final Handler handler0) {
        handler = handler0;
    }

    public static void connected(final IRemote server) {
        final Handler h = handler;
        if (h != null) {
            h.connected(server);
        }
    }

    public static void fromClient(final String userName, final Message message) {
        final Handler h = handler;
        if (h != null) {
            h.fromClient(userName, message);
        }
    }

    public static void fromServer(final Message message) {
        final Handler h = handler;
        if (h != null) {
            h.fromServer(message);
        }
    }
}
