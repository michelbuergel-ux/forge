package forge.gamemodes.hexcrawl;

/** A card (or, at the relic dealer, a relic) on sale in a town. */
public class HexShopOffer {
    /** Name of the card or relic. */
    public String card;
    public int price;
    /** The shop that sells it. */
    public HexShopType shop;
    /** The theme tab it is offered in (a HexTheme id); null for relics. */
    public String theme;

    public HexShopOffer() { }

    public HexShopOffer(final HexShopType shop0, final String card0, final int price0) { shop = shop0; card = card0; price = price0; }
}
