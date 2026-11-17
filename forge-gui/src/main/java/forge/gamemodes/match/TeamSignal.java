package forge.gamemodes.match;

/**
 * Quick messages to the team mates in a team game, faster than the chat: they show up on the
 * team mates' screens for a moment.
 */
public enum TeamSignal {
    HOLD("lblSignalHold"),
    GO_AHEAD("lblSignalGoAhead"),
    BLOCK("lblSignalBlock");

    private final String labelKey;

    TeamSignal(final String labelKey0) {
        labelKey = labelKey0;
    }

    public String getLabelKey() { return labelKey; }

    /** The signals a player can send with a button. */
    public static final TeamSignal[] BUTTONS = values();
}
