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
    /** Only cards that fit, never topped up with ordinary ones: a shop of this theme may hold fewer cards. */
    private final boolean strict;
    /** Most of its cards are multicoloured (a world's factions): its shops draw multicoloured cards much more often. */
    private final boolean multicoloured;

    /** Cards of at least this price fit the theme of expensive cards. */
    public static final int VALUABLE_PRICE = 500;

    private HexTheme(final String id0, final String labelKey0, final String labelArg0, final Predicate<CardRules> fits0) {
        this(id0, labelKey0, labelArg0, fits0, false);
    }

    private HexTheme(final String id0, final String labelKey0, final String labelArg0, final Predicate<CardRules> fits0, final boolean strict0) {
        this(id0, labelKey0, labelArg0, fits0, strict0, false);
    }

    private HexTheme(final String id0, final String labelKey0, final String labelArg0, final Predicate<CardRules> fits0, final boolean strict0,
            final boolean multicoloured0) {
        id = id0;
        labelKey = labelKey0;
        labelArg = labelArg0;
        fits = fits0;
        strict = strict0;
        multicoloured = multicoloured0;
    }

    /** The groups the theme picker sorts the themes into. */
    public enum Group {
        WORLDS, PLAY, KINDS, TRIBES;

        public String labelKey() {
            return "lblHexThemeGroup_" + name();
        }
    }

    private static final java.util.Set<String> WORLDS = java.util.Set.of("ravnica", "tarkir", "alara", "strixhaven");
    private static final java.util.Set<String> KINDS = java.util.Set.of("artifacts", "enchantments", "spells", "planeswalkers", "equipment",
            "colorless", "valuable");

    /** The worlds and their peoples, the ways to play (mechanics), the kinds of cards, or the tribes. */
    public Group group() {
        if (id.startsWith("tribe.") || "critters".equals(id) || "phyrexians".equals(id)) {
            return Group.TRIBES;
        } else if (WORLDS.contains(id)) {
            return Group.WORLDS;
        } else if (KINDS.contains(id)) {
            return Group.KINDS;
        }
        return Group.PLAY;
    }

    /** Are most of the theme's cards multicoloured (see {@link #multicoloured})? */
    public boolean isMulticoloured() {
        return multicoloured;
    }

    /**
     * Whether only fitting cards are sold in this theme's tab. Such a theme also counts the colourless cards
     * when it decides whether a town can offer it (most expensive cards are colourless).
     */
    public boolean isStrict() {
        return strict;
    }

    public boolean fits(final CardRules rules) {
        return rules != null && fits.test(rules);
    }

    public String getLabel() {
        return labelArg == null ? Localizer.getInstance().getMessage(labelKey) : Localizer.getInstance().getMessage(labelKey, labelArg);
    }

    /** The theme's name, as the shops and the town's banner show it (the same as its label). */
    public String getName() {
        return getLabel();
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
                "Pirate", "Warrior", "Cleric", "Rogue", "Sliver" }) {
            all.add(new HexTheme("tribe." + tribe, "lblHexTribe_" + tribe, null, r -> hasCreatureType(r.getType(), tribe)
                    || hints(r, DeckHints.Type.TYPE, tribe)));
        }
        // expensive cards: what costs 500 gold or more (the price list's treasures); last, so the indices above stay
        all.add(new HexTheme("valuable", "lblHexTheme_valuable", null,
                r -> HexContent.get().price(r.getName()) >= VALUABLE_PRICE, true));
        // added later, so after the others: the indices above stay
        for (final String tribe : new String[] { "Dwarf", "Giant" }) {
            all.add(new HexTheme("tribe." + tribe, "lblHexTribe_" + tribe, null, r -> hasCreatureType(r.getType(), tribe)
                    || hints(r, DeckHints.Type.TYPE, tribe)));
        }
        // colourless cards only (artifacts, Eldrazi, ...), no lands
        all.add(new HexTheme("colorless", "lblHexTheme_colorless", null,
                r -> r.getColor().isColorless() && !r.getType().isLand(), true));
        // the small furry folk
        all.add(mechanic("critters", r -> {
            for (final String critter : HexTheme.CRITTERS) {
                if (hasCreatureType(r.getType(), critter) || hints(r, DeckHints.Type.TYPE, critter)) {
                    return true;
                }
            }
            return false;
        }));
        // Equipment, and the cards that want it (equipped creatures, Equipment you control, finding or moving it);
        // the artifact shop's Equipment is mostly colourless, the creature shop's helpers are of the town's colour
        all.add(mechanic("equipment", r -> r.getType().hasSubtype("Equipment")
                || hints(r, DeckHints.Type.TYPE, "Equipment")
                || mentionsEquipment(r)));
        // clones and copies: creatures that enter as a copy, tokens that are copies, spells and abilities copied
        all.add(mechanic("copy", HexTheme::copies));
        // the cards for playing together (partner, assist, ...), the factions of four worlds (Ravnica's guilds,
        // Tarkir's clans and dragonlords, Alara's shards, Strixhaven's colleges) and the Phyrexians: only their own
        // cards; the worlds' cards are mostly multicoloured. See HexCardGroups.
        for (final HexCardGroups.Theme group : HexCardGroups.Theme.values()) {
            final String id = group.name().toLowerCase(java.util.Locale.ROOT);
            final boolean world = group != HexCardGroups.Theme.TEAMWORK && group != HexCardGroups.Theme.PHYREXIANS;
            all.add(new HexTheme(id, "lblHexTheme_" + id, null, r -> HexCardGroups.belongsTo(group, r), true, world));
        }
        ALL = Collections.unmodifiableList(all);
    }

    /** The creature types of the critters theme. */
    private static final String[] CRITTERS = { "Squirrel", "Rabbit", "Otter", "Raccoon", "Weasel", "Rat", "Mouse", "Hamster",
            "Badger", "Possum", "Mole" };

    private static HexTheme mechanic(final String id, final Predicate<CardRules> fits) {
        return new HexTheme(id, "lblHexTheme_" + id, null, fits);
    }

    private static final java.util.regex.Pattern COPY = java.util.regex.Pattern.compile("\\bcop(y|ies|ied|ying)\\b");

    /** Rules text that copies something: a permanent (as a clone or a token), a spell or an ability. */
    private static boolean copies(final CardRules r) {
        final String oracle = r.getOracleText();
        return oracle != null && COPY.matcher(oracle.toLowerCase(java.util.Locale.ROOT)).find();
    }

    /** Rules text about Equipment or equipped creatures. */
    private static boolean mentionsEquipment(final CardRules r) {
        final String oracle = r.getOracleText();
        if (oracle == null) {
            return false;
        }
        final String text = oracle.toLowerCase(java.util.Locale.ROOT);
        return text.contains("equipment") || text.contains("equipped creature");
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
