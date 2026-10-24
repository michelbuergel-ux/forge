package forge.gamemodes.hexcrawl;

/** A task from a town's notice board: defeat a named enemy that camps on a field of the map. */
public class HexQuest {
    public int id;
    public String enemyName;
    /** Deck file below the adventure "common" folder. */
    public String deckPath;
    public int life;
    public HexColor color;
    /** Field where the enemy waits. */
    public int q;
    public int r;
    /** Gold every head gets when the enemy is beaten (on top of the usual reward). */
    public int gold;
    /** Name of the town that offered the task. */
    public String town;
    /** The relic the enemy carries into the fight, or null. */
    public String relic;
    /** An archenemy: it fights alone (no heads or followers), with a deck of schemes. */
    public boolean archenemy;
}
