package forge.gamemodes.match;

/**
 * Quick messages to the team mates in a team game, faster than the chat: they show up on the
 * team mates' screens for a moment. FLEE_VOTE carries the state of a Hex Campaign flee vote.
 */
public enum TeamSignal {
    HOLD("lblSignalHold"),
    GO_AHEAD("lblSignalGoAhead"),
    BLOCK("lblSignalBlock"),
    FLEE_VOTE("lblSignalFleeVote");

    private final String labelKey;

    TeamSignal(final String labelKey0) {
        labelKey = labelKey0;
    }

    public String getLabelKey() { return labelKey; }

    /** The signals a player can send with a button (the flee vote has its own controls). */
    public static final TeamSignal[] BUTTONS = { HOLD, GO_AHEAD, BLOCK };
}
