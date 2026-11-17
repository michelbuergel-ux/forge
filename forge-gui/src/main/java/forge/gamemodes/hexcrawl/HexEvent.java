package forge.gamemodes.hexcrawl;

import forge.util.Localizer;

/**
 * Something the party finds on a field instead of an enemy: no fight, but an advantage (or a
 * gamble) right away or for the next fight. Events are seen on the map like enemies and vanish
 * when the land changes (after a fight or a town visit).
 */
public enum HexEvent {
    /** A shrine: the party's life is higher in the next fight. */
    SHRINE,
    /** A treasure chest: gold for every head. */
    CHEST,
    /** An abandoned camp: every head chooses one of three rare cards that fit its deck, or gold (a choice of each head). */
    CAMP,
    /** A blessing: every head draws one more card for its opening hand in the next fight. */
    BLESSING,
    /** A ley line: every head chooses a card more in its opening hand, or a land of its deck in play, for the next fight. */
    LEY_LINE,
    /** A dark altar: the next enemy is stronger, but its gold reward is doubled. */
    ALTAR,
    /** A ruined shrine: every head may give up a card for one of the relics it is shown (a choice of each head). */
    RUINED_SHRINE,
    /** A merchant caravan: every head may trade a card for one of the cards of its stall, paying the difference (a choice of each head). */
    CARAVAN,
    /**
     * A magic mirror, rare: every head may copy one of its cards (it keeps the card, the copy goes into the
     * sideboard; a choice of each head). See {@link #MIRROR_ODDS}.
     */
    MIRROR,
    /**
     * A wandering archivist, rare: every head may buy one card of its choice, any card the shops know, at the
     * archivist's price (HexContent#archivistPrice; a choice of each head). As rare as the mirror.
     */
    ARCHIVIST;

    /** Of the fields a rare event (the mirror, the archivist) would stand on, only one in this many gets it; the others get another event. */
    public static final int MIRROR_ODDS = 4;
    /** The rare events, the last ones of the list. */
    private static final int RARE_EVENTS = 2;

    public static final int SHRINE_LIFE = 5;
    public static final int CHEST_GOLD = 25;
    public static final int ALTAR_ENEMY_LIFE = 10;
    /** The cards a camp offers a head, a caravan's stall, the relics a ruined shrine shows. */
    public static final int CAMP_CARDS = 3;
    public static final int STALL_CARDS = 5;
    public static final int SHRINE_RELICS = 3;
    /** The choice of a camp's gold (instead of a card), and a ley line's two choices. */
    public static final int PICK_GOLD = -1;
    public static final int PICK_HAND = 1;
    public static final int PICK_LAND = 2;

    public String getLabel() {
        return Localizer.getInstance().getMessage("lblHexEvent_" + name());
    }

    public String getDescription() {
        return Localizer.getInstance().getMessage("lblHexEventDesc_" + name());
    }

    /** Does every head decide for itself (pick a card, or leave it)? */
    public boolean isChoice() {
        return this == RUINED_SHRINE || this == CARAVAN || this == MIRROR || this == CAMP || this == LEY_LINE || this == ARCHIVIST;
    }

    /** Is it one of the rare events (see MIRROR_ODDS)? */
    public boolean isRare() {
        return this == MIRROR || this == ARCHIVIST;
    }

    /** Does the head pick one of its own cards (to give it up, or to copy it)? */
    public boolean needsCard() {
        return this == RUINED_SHRINE || this == CARAVAN || this == MIRROR;
    }

    /** Does the head give up the card it picks (a shrine, a caravan), or keep it (the mirror copies it)? */
    public boolean takesCard() {
        return this == RUINED_SHRINE || this == CARAVAN;
    }

    /** The event of a new field: any of them alike, but the mirror and the archivist are rare. */
    public static HexEvent random(final java.util.Random rnd) {
        final HexEvent[] all = values();
        final HexEvent event = all[rnd.nextInt(all.length)];
        if (event.isRare() && rnd.nextInt(MIRROR_ODDS) != 0) {
            return all[rnd.nextInt(all.length - RARE_EVENTS)]; // one of the others (the rare ones are the last)
        }
        return event;
    }

    public static HexEvent of(final String name) {
        try {
            return name == null ? null : valueOf(name);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
