package forge.gamemodes.hexcrawl;

/** What one head may choose from after a won fight: the gold, or the card taken as ante from the enemy's deck. */
public class HexReward {
    public String playerName;
    public int gold;
    /** A random card from the enemy's deck; may be null if the deck offered nothing. */
    public String card;
}
