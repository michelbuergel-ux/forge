package forge.gamemodes.hexcrawl;

/** One field of the hex map, in axial coordinates (q, r). */
public class HexTile {
    public int q;
    public int r;
    public HexColor color = HexColor.COLORLESS;
    public HexPoi poi = HexPoi.NONE;
    /** Name of the town, or of the boss living here. */
    public String name;
    /** The party has been here. */
    public boolean explored;
    /** Boss fields only: the boss is beaten. */
    public boolean cleared;
    /** The party has seen this field from nearby; it has been decided whether an enemy roams here. */
    public boolean scouted;
    /** The enemy that roams this field in plain sight, or null. */
    public HexEncounter enemy;
    /** Towns only: the theme the town's shops always offer (a HexTheme id). */
    public String theme;
    /** Something the party finds here instead of an enemy (a HexEvent name), or null. */
    public String event;

    public HexTile() { }
    public HexTile(final int q0, final int r0) { q = q0; r = r0; }

    public String key() { return q + "," + r; }
}
