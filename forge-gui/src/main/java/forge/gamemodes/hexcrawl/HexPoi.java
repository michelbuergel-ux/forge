package forge.gamemodes.hexcrawl;

/** What is special about a hex field. */
public enum HexPoi {
    NONE,
    /** A town: no encounters, and a shop that trades gold for cards. */
    TOWN,
    /** The lair of the archmage of this field's colour (or of the final boss in the centre). */
    BOSS,
    /** A dungeon: three fights in a row, with a chest at the end (see {@link HexDungeon}). */
    DUNGEON
}
