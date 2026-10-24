package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.List;

/**
 * A relic: an item a head carries that changes how its fights begin (more life, cards that start in
 * play, a bigger hand, a handicap for the enemy), or a Vanguard avatar worn in its own slot. A head
 * can carry one relic per slot. The relics are the Hex Campaign's own (res/hexcrawl/relics.json).
 */
public class HexRelic {
    public String name;
    /** Where it is worn: Head, Left, Right, Neck, Body, Boots or Avatar. */
    public String slot;
    /** 1 for an ordinary relic; 2 and 3 for the stronger ones of New Game Plus (offered from that cycle on). */
    public int tier = 1;
    /** For tiers 2 and 3: the name of the ordinary relic it is a stronger version of. */
    public String base;
    /** For the Avatar slot: the Vanguard card the head wears in the command zone. */
    public String avatar;
    /** False for an avatar the AI cannot play well: not offered to AI heads. */
    public boolean aiPlayable = true;
    /** The first cycle of the campaign in which the relic is offered (New Game Plus relics: 2). */
    public int fromCycle = 1;
    /** Cards of which some, drawn at random for every fight, start in play with the head. */
    public List<String> randomCards = new ArrayList<>();
    /** How many of the random cards start in play per cycle of the campaign (cycle 3, 1 per cycle: 3 cards). */
    public int randomPerCycle;
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
