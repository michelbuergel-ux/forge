package forge.gamemodes.hexcrawl;

import forge.card.CardRules;
import forge.card.CardType;
import forge.card.MagicColor;
import forge.item.PaperCard;

import java.util.List;

/** Numbers that help building a deck between fights: mana curve, card types and colours. */
public final class HexDeckStats {
    /** Spells by mana value 0, 1, ..., 5 and 6 or more. */
    public final int[] curve = new int[7];
    public int lands;
    public int creatures;
    /** Everything else that isn't a land or a creature. */
    public int otherSpells;
    /** Cards of each colour, in the order white, blue, black, red, green (a gold card counts for each). */
    public final int[] colors = new int[5];
    private int manaValueSum;

    private static final byte[] COLOR_BITS = { MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK, MagicColor.RED, MagicColor.GREEN };

    public static HexDeckStats of(final List<String> deck, final HexContent content) {
        final HexDeckStats stats = new HexDeckStats();
        for (final String name : deck) {
            final PaperCard card = content.getCard(name);
            final CardRules rules = card == null ? null : card.getRules();
            if (rules == null) {
                continue;
            }
            final CardType type = rules.getType();
            if (type.isLand()) {
                stats.lands++;
                continue;
            }
            if (type.isCreature()) {
                stats.creatures++;
            } else {
                stats.otherSpells++;
            }
            final int mv = Math.max(0, rules.getManaCost().getCMC());
            stats.curve[Math.min(mv, 6)]++;
            stats.manaValueSum += mv;
            for (int i = 0; i < COLOR_BITS.length; i++) {
                if (rules.getColor().hasAnyColor(COLOR_BITS[i])) {
                    stats.colors[i]++;
                }
            }
        }
        return stats;
    }

    public int spells() {
        return creatures + otherSpells;
    }

    /** Average mana value of the spells (lands left out). */
    public double averageManaValue() {
        return spells() == 0 ? 0 : (double) manaValueSum / spells();
    }
}
