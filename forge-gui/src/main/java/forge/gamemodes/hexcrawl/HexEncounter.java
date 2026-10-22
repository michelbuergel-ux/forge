package forge.gamemodes.hexcrawl;

/** An enemy the party has to fight. */
public class HexEncounter {
    public String enemyName;
    /** Deck file below the adventure "common" folder, e.g. decks/standard/bandit.dck. */
    public String deckPath;
    public int life;
    public boolean boss;
    public HexColor color;
    /** Gold each head may take as its reward. */
    public int gold;
    /** Field the fight is about. */
    public int q;
    public int r;
    /** The task this fight belongs to, or 0. */
    public int questId;
    /** Cards and tokens the enemy starts the fight with on the battlefield (its equipment). */
    public java.util.List<String> startCards = new java.util.ArrayList<>();

    /**
     * Bosses and the enemies of tasks fight with copies of themselves as their other heads (the
     * same deck and equipment); every other enemy brings followers of its colour.
     */
    public boolean headsAlike() {
        return boss || questId != 0;
    }
}
