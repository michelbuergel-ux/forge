package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.List;

/** What every head of the party did in the last fight; kept in the campaign so guests see it too. */
public class HexFightSummary {
    /** One head's part in the fight. */
    public static class Head {
        public String name;
        /** Damage dealt to the enemy side (its heads and creatures). */
        public int damage;
        public int spells;
        public int lands;
        /** Cards drawn (the opening hand not counted). */
        public int cardsDrawn;
        public int lifeGained;
        /** The card of this head that dealt the most damage, or null. */
        public String topCard;
        public int topCardDamage;
    }

    public String enemyName;
    /** WON, LOST or FLED (HexBattle.Result). */
    public String result;
    public int turns;
    public List<Head> heads = new ArrayList<>();
    /** The head that dealt the most damage (spells cast break a tie), or null if nobody did anything. */
    public String mvp;
    /** Counts up with every fight, so every screen shows each summary once. */
    public int serial;

    public Head head(final String name) {
        for (final Head h : heads) {
            if (h.name.equals(name)) {
                return h;
            }
        }
        return null;
    }
}
