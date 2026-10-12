package forge.gamemodes.hexcrawl;

/** The colour of a hex field: the five colours of Magic plus colourless (the centre of the map). */
public enum HexColor {
    WHITE("W", "white", "Plains", 0xF4EDC4),
    BLUE("U", "blue", "Island", 0x4F86C6),
    BLACK("B", "black", "Swamp", 0x5B5560),
    RED("R", "red", "Mountain", 0xC65A46),
    GREEN("G", "green", "Forest", 0x5E9B5A),
    COLORLESS("C", "colorless", "Wastes", 0xA9A39B);

    /** The five real colours, in the order they sit around the map. */
    public static final HexColor[] COLORS = { WHITE, BLUE, BLACK, RED, GREEN };

    private final String symbol;
    private final String biome;
    private final String basicLand;
    private final int rgb;

    HexColor(final String symbol0, final String biome0, final String basicLand0, final int rgb0) {
        symbol = symbol0;
        biome = biome0;
        basicLand = basicLand0;
        rgb = rgb0;
    }

    public String getSymbol() { return symbol; }
    /** File name (without extension) of the adventure biome that lists this colour's enemies. */
    public String getBiome() { return biome; }
    public String getBasicLand() { return basicLand; }
    public int getRgb() { return rgb; }
    public String getLabelKey() { return "lblHexColor" + symbol; }
}
