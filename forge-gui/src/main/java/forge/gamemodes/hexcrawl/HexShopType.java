package forge.gamemodes.hexcrawl;

/** The traders of a town: one shop per kind of card, and a dealer in relics. */
public enum HexShopType {
    CREATURE("lblHexShopCreatures"),
    ARTIFACT("lblHexShopArtifacts"),
    LAND("lblHexShopLands"),
    ENCHANTMENT("lblHexShopEnchantments"),
    SORCERY("lblHexShopSorceries"),
    INSTANT("lblHexShopInstants"),
    PLANESWALKER("lblHexShopPlaneswalkers"),
    /** Sells relics instead of cards. */
    RELIC("lblHexShopRelics");

    /** The shops that sell cards. */
    public static final HexShopType[] CARD_SHOPS = { CREATURE, ARTIFACT, LAND, ENCHANTMENT, SORCERY, INSTANT, PLANESWALKER };

    private final String labelKey;

    HexShopType(final String labelKey0) {
        labelKey = labelKey0;
    }

    public String getLabelKey() { return labelKey; }
}
