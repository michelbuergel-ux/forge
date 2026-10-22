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
    /** An abandoned camp: every head finds a card of the field's colour (into its sideboard). */
    CAMP,
    /** A blessing: every head draws one more card for its opening hand in the next fight. */
    BLESSING,
    /** A ley line: every head starts the next fight with a basic land in play. */
    LEY_LINE,
    /** A dark altar: the next enemy is stronger, but its gold reward is doubled. */
    ALTAR,
    /** A ruined shrine: every head may give up a card for a relic (a choice of each head). */
    RUINED_SHRINE,
    /** A merchant caravan: every head may trade a card for two cards of the field's colour (a choice of each head). */
    CARAVAN;

    public static final int SHRINE_LIFE = 5;
    public static final int CHEST_GOLD = 25;
    public static final int ALTAR_ENEMY_LIFE = 10;

    public String getLabel() {
        return Localizer.getInstance().getMessage("lblHexEvent_" + name());
    }

    public String getDescription() {
        return Localizer.getInstance().getMessage("lblHexEventDesc_" + name());
    }

    /** Does every head decide for itself (give a card, or leave it)? */
    public boolean isChoice() {
        return this == RUINED_SHRINE || this == CARAVAN;
    }

    public static HexEvent of(final String name) {
        try {
            return name == null ? null : valueOf(name);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
