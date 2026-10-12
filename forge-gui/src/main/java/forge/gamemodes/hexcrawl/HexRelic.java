package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.List;

/**
 * A relic: an item of the Adventure mode that a head carries and that changes how its fights
 * begin (more life, cards that start in play, a bigger hand, a handicap for the enemy).
 * A head can carry one relic per slot.
 */
public class HexRelic {
    public String name;
    /** Where it is worn: Left, Right, Neck, Body or Boots. */
    public String slot;
    public int price;
    public String description;
    /** Added to the shared life of the party. */
    public int life;
    /** Added to the starting hand of the head. */
    public int hand;
    /** Cards and tokens the head starts with on the battlefield. */
    public List<String> battlefield = new ArrayList<>();
    /** Added to the life of the enemy. */
    public int enemyLife;
    /** Cards and tokens the enemy starts with on the battlefield. */
    public List<String> enemyBattlefield = new ArrayList<>();
}
