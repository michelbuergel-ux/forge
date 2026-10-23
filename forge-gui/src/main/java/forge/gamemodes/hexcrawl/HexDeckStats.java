package forge.gamemodes.hexcrawl;

import forge.card.CardRules;
import forge.card.CardType;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.card.mana.ManaCostShard;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Numbers that help building a deck between fights: mana curve, card types and colours, what the spells
 * need of each colour and what the lands make, and advice on lands, colours and creatures.
 */
public final class HexDeckStats {
    /** Spells by mana value 0, 1, ..., 5 and 6 or more. */
    public final int[] curve = new int[7];
    /** The creatures among them. */
    public final int[] curveCreatures = new int[7];
    public int cards;
    public int lands;
    public int creatures;
    /** Everything else that isn't a land or a creature. */
    public int otherSpells;
    /** Spells of mana value 2 or less. */
    public int cheapSpells;
    /** Cards of each colour, in the order white, blue, black, red, green (a gold card counts for each). */
    public final int[] colors = new int[5];
    /** Mana symbols of each colour in the costs of the spells (a hybrid symbol counts for both colours). */
    public final int[] symbols = new int[5];
    /** Lands that make each colour (a land that makes two colours counts for both). */
    public final int[] sources = new int[5];
    private int manaValueSum;

    private static final byte[] COLOR_BITS = { MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK, MagicColor.RED, MagicColor.GREEN };
    /** A colour is one of the deck's main colours with at least this share of the symbols; less is a splash. */
    public static final double MAIN_SHARE = 0.1;

    public static HexDeckStats of(final List<String> deck, final HexContent content) {
        final HexDeckStats stats = new HexDeckStats();
        for (final String name : deck) {
            final PaperCard card = content.getCard(name);
            final CardRules rules = card == null ? null : card.getRules();
            if (rules == null) {
                continue;
            }
            stats.cards++;
            final CardType type = rules.getType();
            if (type.isLand()) {
                stats.lands++;
                final ColorSet makes = landColors(rules);
                for (int i = 0; i < COLOR_BITS.length; i++) {
                    if (makes.hasAnyColor(COLOR_BITS[i])) {
                        stats.sources[i]++;
                    }
                }
                continue;
            }
            final int mv = Math.max(0, rules.getManaCost().getCMC());
            if (type.isCreature()) {
                stats.creatures++;
                stats.curveCreatures[Math.min(mv, 6)]++;
            } else {
                stats.otherSpells++;
            }
            stats.curve[Math.min(mv, 6)]++;
            stats.manaValueSum += mv;
            if (mv <= 2) {
                stats.cheapSpells++;
            }
            for (int i = 0; i < COLOR_BITS.length; i++) {
                if (rules.getColor().hasAnyColor(COLOR_BITS[i])) {
                    stats.colors[i]++;
                }
            }
            for (final ManaCostShard shard : rules.getManaCost()) {
                for (int i = 0; i < COLOR_BITS.length; i++) {
                    if ((shard.getColorMask() & COLOR_BITS[i]) != 0) {
                        stats.symbols[i]++;
                    }
                }
            }
        }
        return stats;
    }

    /** The colours a land makes, read from its colour identity (basic land types and mana abilities). */
    static ColorSet landColors(final CardRules rules) {
        return rules.getColorIdentity();
    }

    public int spells() {
        return creatures + otherSpells;
    }

    /** Average mana value of the spells (lands left out). */
    public double averageManaValue() {
        return spells() == 0 ? 0 : (double) manaValueSum / spells();
    }

    public int totalSymbols() {
        int sum = 0;
        for (final int n : symbols) {
            sum += n;
        }
        return sum;
    }

    /** The share of the mana symbols that colour has (0 to 1). */
    public double share(final int color) {
        final int total = totalSymbols();
        return total == 0 ? 0 : (double) symbols[color] / total;
    }

    /** The colours the spells need, the most needed first (indices: white, blue, black, red, green). */
    public List<Integer> deckColors() {
        final List<Integer> result = new ArrayList<>();
        for (int i = 0; i < symbols.length; i++) {
            if (symbols[i] > 0) {
                result.add(i);
            }
        }
        result.sort(Comparator.comparingInt((Integer i) -> -symbols[i]));
        return result;
    }

    /** The deck's main colours (a tenth of the symbols or more), the most needed first; the others are splashes. */
    public List<Integer> mainColors() {
        final List<Integer> result = new ArrayList<>();
        for (final int i : deckColors()) {
            if (share(i) >= MAIN_SHARE) {
                result.add(i);
            }
        }
        return result;
    }

    /** About 16 lands at an average mana value of 3, two more for each point above, scaled to the deck's size. */
    public double recommendedLands() {
        return Math.max(15, Math.min(19, (16 + (averageManaValue() - 3) * 2) * Math.max(cards, HexCampaign.MIN_DECK_SIZE) / 40.0));
    }

    public int recommendedLow() {
        return (int) Math.floor(recommendedLands());
    }

    public int recommendedHigh() {
        return Math.max(recommendedLow() + 1, (int) Math.ceil(recommendedLands()));
    }

    /** How many lands should make a colour: its share of the symbols, at least 3 for a splash; 0 if the spells don't need it. */
    public int wantedSources(final int color) {
        return symbols[color] == 0 ? 0 : Math.max(3, (int) Math.round(share(color) * lands));
    }

    /** Whether a card suits the deck's colours: a spell of those colours (or colourless), a land that makes one of them. */
    public boolean fits(final CardRules rules) {
        final List<Integer> deckColors = deckColors();
        if (rules.getType().isLand()) {
            if (deckColors.isEmpty()) {
                return true;
            }
            final ColorSet makes = landColors(rules);
            for (final int i : deckColors) {
                if (makes.hasAnyColor(COLOR_BITS[i])) {
                    return true;
                }
            }
            return false;
        }
        for (int i = 0; i < COLOR_BITS.length; i++) {
            if (rules.getColor().hasAnyColor(COLOR_BITS[i]) && !deckColors.contains(i)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ advice

    /** One piece of advice, with a fix where the sideboard has one. */
    public static final class Advice {
        public enum Kind { NO_SOURCE, FEW_SOURCES, FEW_LANDS, MANY_LANDS, FEW_CREATURES, FEW_CHEAP, OVER_MINIMUM, SOLID }

        public final Kind kind;
        /** 0 a problem, 1 a warning, 2 a hint, 3 all is well. */
        public final int level;
        /** The colour it is about (white, blue, black, red, green), -1 if none. */
        public final int color;
        /** The numbers the text needs (see the kinds). */
        public final int[] numbers;
        /** The fix: move this many copies of landIn from the sideboard into the deck (and as many landOut out of it). */
        public final String landIn;
        public final String landOut;
        public final int count;

        Advice(final Kind kind0, final int level0, final int color0, final int[] numbers0, final String landIn0, final String landOut0, final int count0) {
            kind = kind0;
            level = level0;
            color = color0;
            numbers = numbers0;
            landIn = landIn0;
            landOut = landOut0;
            count = count0;
        }

        public boolean hasFix() {
            return landIn != null && count > 0;
        }
    }

    /**
     * Advice for this deck, the most pressing first. Numbers per kind: NO_SOURCE {cards of the colour};
     * FEW_SOURCES {sources, wanted, symbols}; FEW_LANDS {lands, low, high} with the average mana value apart;
     * MANY_LANDS {lands, low, high}; FEW_CREATURES {creatures, fitting creatures in the sideboard};
     * FEW_CHEAP {cheap spells}; OVER_MINIMUM {cards over the minimum}; SOLID {}.
     */
    public List<Advice> advice(final List<String> deck, final List<String> sideboard, final HexContent content) {
        final List<Advice> result = new ArrayList<>();
        final List<Integer> deckColors = deckColors();
        for (final int m : deckColors) {
            final int wanted = wantedSources(m);
            final int missing = wanted - sources[m];
            if (sources[m] > 0 && missing < 2) {
                continue;
            }
            // swap basics of the colour with the most spare sources for lands of this colour from the sideboard
            String spareBasic = null;
            int surplus = 0;
            for (final int o : deckColors) {
                final String basic = HexColor.COLORS[o].getBasicLand();
                final int extra = sources[o] - wantedSources(o);
                if (o != m && extra > surplus && Collections.frequency(deck, basic) > 0) {
                    spareBasic = basic;
                    surplus = extra;
                }
            }
            final String landIn = sideLandMaking(m, sideboard, content);
            int k = 0;
            if (landIn != null && spareBasic != null) {
                // the colour giving up lands may end one short: that is still fine (see above)
                k = Math.min(Math.min(missing, Collections.frequency(sideboard, landIn)),
                        Math.min(surplus + 1, Collections.frequency(deck, spareBasic)));
            }
            result.add(sources[m] == 0
                    ? new Advice(Advice.Kind.NO_SOURCE, 0, m, new int[] { colors[m] }, k > 0 ? landIn : null, spareBasic, k)
                    : new Advice(Advice.Kind.FEW_SOURCES, 1, m, new int[] { sources[m], wanted, symbols[m] }, k > 0 ? landIn : null, spareBasic, k));
        }
        final int low = recommendedLow();
        final int high = recommendedHigh();
        if (lands < low) {
            String land = null;
            for (final int m : deckColors) {
                land = land != null ? land : sideLandMaking(m, sideboard, content);
            }
            if (deckColors.isEmpty()) {
                land = anySideLand(sideboard, content);
            }
            result.add(new Advice(Advice.Kind.FEW_LANDS, 1, -1, new int[] { lands, low, high }, land, null, land == null ? 0 : 1));
        } else if (lands > high + 1) {
            result.add(new Advice(Advice.Kind.MANY_LANDS, 2, -1, new int[] { lands, low, high }, null, null, 0));
        }
        if (creatures < 12) {
            int spare = 0;
            for (final String name : sideboard) {
                final PaperCard card = content.getCard(name);
                if (card != null && card.getRules().getType().isCreature() && fits(card.getRules())) {
                    spare++;
                }
            }
            result.add(new Advice(Advice.Kind.FEW_CREATURES, 1, -1, new int[] { creatures, spare }, null, null, 0));
        }
        if (cheapSpells < 5) {
            result.add(new Advice(Advice.Kind.FEW_CHEAP, 2, -1, new int[] { cheapSpells }, null, null, 0));
        }
        if (cards > HexCampaign.MIN_DECK_SIZE + 1) {
            result.add(new Advice(Advice.Kind.OVER_MINIMUM, 2, -1, new int[] { cards - HexCampaign.MIN_DECK_SIZE }, null, null, 0));
        }
        boolean trouble = false;
        for (final Advice a : result) {
            trouble |= a.level < 2;
        }
        if (!trouble) {
            result.add(new Advice(Advice.Kind.SOLID, 3, -1, new int[0], null, null, 0));
        }
        result.sort(Comparator.comparingInt((Advice a) -> a.level));
        return result;
    }

    /** A land in the sideboard that makes the colour, one that makes few colours first (a basic before a tri-land). */
    private static String sideLandMaking(final int color, final List<String> sideboard, final HexContent content) {
        String best = null;
        int bestColors = Integer.MAX_VALUE;
        for (final String name : new LinkedHashSet<>(sideboard)) {
            final PaperCard card = content.getCard(name);
            if (card == null || !card.getRules().getType().isLand()) {
                continue;
            }
            final ColorSet makes = landColors(card.getRules());
            if (makes.hasAnyColor(COLOR_BITS[color]) && makes.countColors() < bestColors) {
                best = name;
                bestColors = makes.countColors();
            }
        }
        return best;
    }

    private static String anySideLand(final List<String> sideboard, final HexContent content) {
        final Set<String> names = new LinkedHashSet<>(sideboard);
        for (final String name : names) {
            final PaperCard card = content.getCard(name);
            if (card != null && card.getRules().getType().isLand()) {
                return name;
            }
        }
        return null;
    }
}
