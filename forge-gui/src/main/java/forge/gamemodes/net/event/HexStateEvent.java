package forge.gamemodes.net.event;

import forge.gamemodes.net.server.RemoteClient;

/** Server -> client: the current state of the hex campaign the host is running. */
public final class HexStateEvent implements NetEvent {
    private static final long serialVersionUID = 1L;

    private final String stateJson;
    private String yourHead;

    public HexStateEvent(final String stateJson0) {
        stateJson = stateJson0;
    }

    public String getStateJson() { return stateJson; }
    /** The head of the party the receiving client plays (its user name). */
    public String getYourHead() { return yourHead; }

    @Override
    public void updateForClient(final RemoteClient client) {
        yourHead = client.getUsername();
    }
}
