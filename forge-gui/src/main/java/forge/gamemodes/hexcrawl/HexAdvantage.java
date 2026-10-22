package forge.gamemodes.hexcrawl;

/**
 * What the enemies get in New Game Plus, on top of the extra life every cycle brings. Every new
 * cycle rolls two of them (each adds one step); an advantage at its cap is not rolled again.
 */
public enum HexAdvantage {
    /** Basic lands of the enemy's colour in play at the start. */
    LANDS(4, 2, "lblHexAdvLands"),
    /** More cards in the opening hand. */
    HAND(4, 2, "lblHexAdvHand"),
    /** A card in play at the start: equipment, a creature or an enchantment of the enemy's colour. */
    BATTLEFIELD(4, 2, "lblHexAdvBattlefield"),
    /** An additional card in every draw step. The strongest, so not in the first New Game Plus. */
    DRAW(3, 3, "lblHexAdvDraw");

    private final int cap;
    private final int fromCycle;
    private final String labelKey;

    HexAdvantage(final int cap0, final int fromCycle0, final String labelKey0) {
        cap = cap0;
        fromCycle = fromCycle0;
        labelKey = labelKey0;
    }

    /** The most steps the enemies can get of it. */
    public int getCap() { return cap; }
    /** The first cycle (1 = the first run of the campaign) in which it can be rolled. */
    public int getFromCycle() { return fromCycle; }
    /** Text key with the number of steps as {0}. */
    public String getLabelKey() { return labelKey; }
}
