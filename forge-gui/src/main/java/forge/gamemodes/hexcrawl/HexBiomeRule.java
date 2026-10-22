package forge.gamemodes.hexcrawl;

/**
 * New Game Plus: every colour region has one small rule for the fights on its fields. Some help
 * the enemies, some the party. The first run of a campaign has none.
 */
public enum HexBiomeRule {
    /** Swamp: every enemy head starts with a 2/2 black Zombie. */
    SWAMP(HexColor.BLACK, "b_2_2_zombie", 0, 0, 0),
    /** Forest: every head of the party starts with one more basic land in play. */
    FOREST(HexColor.GREEN, null, 1, 0, 0),
    /** Plains: every enemy head starts with a 1/1 white Soldier. */
    PLAINS(HexColor.WHITE, "w_1_1_soldier", 0, 0, 0),
    /** Island: every head of the party draws one more card for its opening hand. */
    ISLAND(HexColor.BLUE, null, 0, 1, 0),
    /** Mountain: every enemy head starts with a 1/1 red Goblin with haste. */
    MOUNTAIN(HexColor.RED, "r_1_1_goblin_haste", 0, 0, 0),
    /** Wastes (the centre): the enemies have more life. */
    WASTES(HexColor.COLORLESS, null, 0, 0, 5);

    private final HexColor color;
    /** A token every enemy head starts with, or null. */
    public final String enemyToken;
    /** More basic lands in play for every head of the party. */
    public final int partyLands;
    /** More cards in every party head's opening hand. */
    public final int partyHand;
    /** More life for the enemy side. */
    public final int enemyLife;

    HexBiomeRule(final HexColor color0, final String enemyToken0, final int partyLands0, final int partyHand0, final int enemyLife0) {
        color = color0;
        enemyToken = enemyToken0;
        partyLands = partyLands0;
        partyHand = partyHand0;
        enemyLife = enemyLife0;
    }

    public HexColor getColor() { return color; }

    public String getLabelKey() { return "lblHexBiomeRule_" + name(); }

    public static HexBiomeRule of(final HexColor color) {
        for (final HexBiomeRule r : values()) {
            if (r.color == color) {
                return r;
            }
        }
        return null;
    }

    /** The rule for a fight of the campaign (by the colour of its field), or null in the first run. */
    public static HexBiomeRule forFight(final HexCampaign campaign, final HexEncounter enc) {
        if (campaign == null || enc == null || campaign.cycle < 2) {
            return null;
        }
        return of(HexMusic.fieldColor(campaign, enc));
    }

    /** The rule of a field (for the map), or null in the first run. */
    public static HexBiomeRule forField(final HexCampaign campaign, final HexTile t) {
        return campaign == null || t == null || campaign.cycle < 2 ? null : of(t.color);
    }
}
