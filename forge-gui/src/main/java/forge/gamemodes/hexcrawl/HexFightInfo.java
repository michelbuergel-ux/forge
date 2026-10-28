package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.List;

/**
 * What the match screen shows of a campaign fight in its banner: who the enemy is and what kind, the life it
 * started with (and, for a boss, the card that wakes it at half of it), and what the party knows about it.
 */
public class HexFightInfo {
    public String enemyName;
    /** What kind of fight it is, as the banner says it ("Archmage", "Dungeon, fight 2 of 3", ...). */
    public String kind;
    /** The enemy team's life at the start of the fight. */
    public int startLife;
    /** A boss's card that wakes it at half its life (its phase two), or null. */
    public String phaseCard;
    /** Short facts shown as pills: the region's rule, the enemies' advantages of the cycle, a dungeon's life. */
    public List<String> pills = new ArrayList<>();
    /** The whole scout report (tooltip), or null. */
    public String scout;
}
