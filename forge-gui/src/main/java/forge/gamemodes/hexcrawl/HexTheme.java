package forge.gamemodes.hexcrawl;

import forge.card.CardRules;
import forge.card.CardType;
import forge.card.DeckHints;
import forge.util.Localizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * A theme of the town shops: a mechanic (tokens, +1/+1 counters, the graveyard, ...), a kind of
 * card (artifacts, spells, ...) or a tribe (Elves, Goblins, ...). A card fits a theme if it does
 * that thing or wants it: the deck hints of the card scripts (DeckHas, DeckHints, DeckNeeds), its
 * types and its keywords tell.
 */
public final class HexTheme {
    /** Stable key, also used in saved campaigns and network actions. */
    public final String id;
    private final String labelKey;
    private final String labelArg;
    private final Predicate<CardRules> fits;

    private HexTheme(final String id0, final String labelKey0, final String labelArg0, final Predicate<CardRules> fits0) {
        id = id0;
        labelKey = labelKey0;
        labelArg = labelArg0;
        fits = fits0;
    }

    public boolean fits(final CardRules rules) {
        return rules != null && fits.test(rules);
    }

    public String getLabel() {
        return labelArg == null ? Localizer.getInstance().getMessage(labelKey) : Localizer.getInstance().getMessage(labelKey, labelArg);
    }

    /** The label without its kind ("Elf" instead of "Tribe: Elf"), for places that name the kind themselves. */
    public String getName() {
        return labelArg == null ? getLabel() : labelArg;
    }

    @Override
    public String toString() {
        return getLabel();
    }

    // ------------------------------------------------------------------ catalog

    private static final List<HexTheme> ALL;
    static {
        final List<HexTheme> all = new ArrayList<>();
        all.add(mechanic("tokens", r -> hints(r, DeckHints.Type.ABILITY, "Token")));
        all.add(mechanic("counters", r -> hints(r, DeckHints.Type.ABILITY, "Counters", "Proliferate")));
        all.add(mechanic("graveyard", r -> hints(r, DeckHints.Type.ABILITY, "Graveyard", "Mill", "Delirium", "Surveil")));
        all.add(mechanic("lifegain", r -> hints(r, DeckHints.Type.ABILITY, "LifeGain") || r.hasKeyword("Lifelink")));
        all.add(mechanic("sacrifice", r -> hints(r, DeckHints.Type.ABILITY, "Sacrifice")));
        all.add(mechanic("discard", r -> hints(r, DeckHints.Type.ABILITY, "Discard")));
        all.add(mechanic("ramp", r -> hints(r, DeckHints.Type.ABILITY, "Mana")));
        all.add(mechanic("treasure", r -> hints(r, DeckHints.Type.TYPE, "Treasure", "Food", "Clue")
                || hints(r, DeckHints.Type.ABILITY, "Investigate")));
        all.add(mechanic("artifacts", r -> r.getType().isArtifact() || hints(r, DeckHints.Type.TYPE, "Artifact", "Equipment")));
        all.add(mechanic("enchantments", r -> r.getType().isEnchantment() || hints(r, DeckHints.Type.TYPE, "Enchantment", "Aura")));
        all.add(mechanic("spells", r -> r.getType().isInstant() || r.getType().isSorcery()
                || hints(r, DeckHints.Type.TYPE, "Instant", "Sorcery")));
        // planeswalkers, and the cards that help them (loyalty, proliferate, protecting or finding them)
        all.add(mechanic("planeswalkers", r -> r.getType().isPlaneswalker()
                || hints(r, DeckHints.Type.TYPE, "Planeswalker")
                || mentionsPlaneswalkers(r)));
        all.add(mechanic("fliers", r -> (r.getType().isCreature() && r.hasKeyword("Flying"))
                || hints(r, DeckHints.Type.KEYWORD, "Flying")));
        for (final String tribe : new String[] { "Elf", "Goblin", "Zombie", "Vampire", "Dragon", "Human", "Spirit",
                "Knight", "Soldier", "Wizard", "Merfolk", "Angel", "Beast", "Dinosaur", "Cat", "Elemental", "Faerie",
                "Pirate", "Warrior", "Cleric", "Rogue" }) {
            all.add(new HexTheme("tribe." + tribe, "lblHexThemeTribe", tribe, r -> hasCreatureType(r.getType(), tribe)
                    || hints(r, DeckHints.Type.TYPE, tribe)));
        }
        ALL = Collections.unmodifiableList(all);
    }

    private static HexTheme mechanic(final String id, final Predicate<CardRules> fits) {
        return new HexTheme(id, "lblHexTheme_" + id, null, fits);
    }

    /** Rules text about planeswalkers you control or their loyalty (not "creature or planeswalker" removal). */
    private static boolean mentionsPlaneswalkers(final CardRules r) {
        final String oracle = r.getOracleText();
        if (oracle == null) {
            return false;
        }
        final String text = oracle.toLowerCase(java.util.Locale.ROOT);
        return text.contains("loyalty") || text.contains("planeswalker you control") || text.contains("planeswalkers you control")
                || text.contains("planeswalker card") || text.contains("planeswalker spell");
    }

    private static boolean hasCreatureType(final CardType type, final String tribe) {
        return type.isCreature() && type.hasCreatureType(tribe);
    }

    /** Does the card do (DeckHas) or want (DeckHints, DeckNeeds) any of these things? */
    private static boolean hints(final CardRules r, final DeckHints.Type type, final String... values) {
        for (final DeckHints h : new DeckHints[] { r.getAiHints().getDeckHas(), r.getAiHints().getDeckHints(), r.getAiHints().getDeckNeeds() }) {
            if (h == null || !h.isValid()) {
                continue;
            }
            for (final String value : values) {
                if (h.contains(type, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<HexTheme> all() {
        return ALL;
    }

    /** The theme with this key, or null (e.g. from a newer version). */
    public static HexTheme get(final String id) {
        if (id == null) {
            return null;
        }
        for (final HexTheme t : ALL) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** Position in the catalog (sent over the network); -1 for none. */
    public static int indexOf(final String id) {
        final HexTheme t = get(id);
        return t == null ? -1 : ALL.indexOf(t);
    }

    public static HexTheme byIndex(final int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
    }
}
