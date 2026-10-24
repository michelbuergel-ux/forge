package forge.gamemodes.hexcrawl;

import java.util.HashMap;
import java.util.Map;

/** What one head did, over a cycle of the campaign or the whole of it: fights, cards, life and gold. */
public class HexHeadStats {
    public int fights;
    /** Damage dealt to the enemies (their heads and creatures). */
    public int damage;
    /** Cards drawn in fights (opening hands not counted). */
    public int cardsDrawn;
    public int lifeGained;
    public int spells;
    public int lands;
    /** Fights in which this head dealt the most damage of the party. */
    public int fightsMvp;
    /** The most damage dealt in one fight. */
    public int bestFight;
    public String bestFightEnemy;
    /** Gold from rewards, bounties, chests, shrines and sales (not gifts of other heads). */
    public int goldEarned;
    /** Gold for cards, relics, restocks, lands, fast travel and fleeing. */
    public int goldSpent;
    public int cardsBought;
    public int cardsSold;
    public int relicsBought;
    /** Damage of the head's best card of every fight, summed by card: the favourite card is the top one. */
    public Map<String, Integer> cardDamage = new HashMap<>();

    /** Adds a head's part in one fight. */
    public void add(final HexFightSummary.Head h, final String enemyName, final boolean mvp) {
        fights++;
        damage += h.damage;
        cardsDrawn += h.cardsDrawn;
        lifeGained += h.lifeGained;
        spells += h.spells;
        lands += h.lands;
        if (mvp) {
            fightsMvp++;
        }
        if (h.damage > bestFight) {
            bestFight = h.damage;
            bestFightEnemy = enemyName;
        }
        if (h.topCard != null && h.topCardDamage > 0) {
            cardDamage.merge(h.topCard, h.topCardDamage, Integer::sum);
        }
    }

    /** The card that dealt the most damage over all fights, or null. */
    public String favouriteCard() {
        String best = null;
        int most = 0;
        for (final Map.Entry<String, Integer> e : cardDamage.entrySet()) {
            if (e.getValue() > most || e.getValue() == most && best != null && e.getKey().compareTo(best) < 0) {
                best = e.getKey();
                most = e.getValue();
            }
        }
        return best;
    }

    public int favouriteCardDamage() {
        final String card = favouriteCard();
        return card == null ? 0 : cardDamage.get(card);
    }
}
