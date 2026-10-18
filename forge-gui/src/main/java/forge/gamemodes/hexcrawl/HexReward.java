package forge.gamemodes.hexcrawl;

/** What one head may choose from after a won fight: the gold, or the card taken as ante from the enemy's deck. */
public class HexReward {
    public String playerName;
    public int gold;
    /** A random card from the enemy's deck; may be null if the deck offered nothing. Older saves only. */
    public String card;
    /** The cards from the enemy's deck the head may choose one of (up to three). */
    public java.util.List<String> cards = new java.util.ArrayList<>();

    /** The cards on offer: the three to choose from, or the single card of an older save. */
    public java.util.List<String> options() {
        if (cards != null && !cards.isEmpty()) {
            return cards;
        }
        final java.util.List<String> single = new java.util.ArrayList<>();
        if (card != null) {
            single.add(card);
        }
        return single;
    }
}
