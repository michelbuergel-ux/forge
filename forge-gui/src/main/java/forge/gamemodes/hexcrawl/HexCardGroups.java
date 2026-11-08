package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import forge.StaticData;
import forge.card.CardEdition;
import forge.card.CardRules;
import forge.card.CardType;
import forge.card.ColorSet;
import forge.card.ICardFace;
import forge.card.MagicColor;
import forge.card.mana.ManaCostShard;

/**
 * Groups of cards: what kind of card a card is (a creature, an instant, a land, ...: the deck window's groups), and
 * the shop themes of whole worlds and mechanics a card belongs to, each with its factions (Ravnica's guilds, Tarkir's
 * clans and dragonlords, Alara's shards, Strixhaven's colleges, ...).
 *
 * <p>The themes of a world take the cards printed in its sets. Forge knows no watermarks, so a card's faction is told
 * by its name (an Azorius Guildgate, a Bant Charm), the mechanic of the faction (bloodrush is Gruul's, exalted is
 * Bant's) or its colours (a red and white card of Strixhaven is Lorehold's). A card can belong to more than one
 * faction, and a card of the sets that shows none of them belongs to none.
 */
public final class HexCardGroups {
    private HexCardGroups() { }

    /** The kinds of cards, in the order the deck window shows them; a card is the first kind that fits it. */
    public enum Kind {
        CREATURE, PLANESWALKER, BATTLE, INSTANT, SORCERY, ARTIFACT, ENCHANTMENT, LAND, OTHER;

        /** The key of its name (a heading of the deck, an entry of the filter). */
        public String labelKey() {
            return "lblHexKind_" + name();
        }
    }

    /** The kind of a card (OTHER for none). */
    public static Kind kindOf(final CardRules rules) {
        if (rules == null) {
            return Kind.OTHER;
        }
        final CardType type = rules.getType();
        if (type.isCreature()) {
            return Kind.CREATURE;
        } else if (type.isPlaneswalker()) {
            return Kind.PLANESWALKER;
        } else if (type.isBattle()) {
            return Kind.BATTLE;
        } else if (type.isLand()) {
            return Kind.LAND;
        } else if (type.isInstant()) {
            return Kind.INSTANT;
        } else if (type.isSorcery()) {
            return Kind.SORCERY;
        } else if (type.isArtifact()) {
            return Kind.ARTIFACT;
        } else if (type.isEnchantment()) {
            return Kind.ENCHANTMENT;
        }
        return Kind.OTHER;
    }

    /** The themes of the shops (see {@link HexTheme}), each with its factions (group ids). */
    public enum Theme {
        TEAMWORK("PARTNER", "ASSIST", "DEMONSTRATE", "SUPPORT", "SURGE", "JOIN_FORCES", "COUNCIL"),
        RAVNICA("AZORIUS", "DIMIR", "RAKDOS", "GRUUL", "SELESNYA", "ORZHOV", "IZZET", "GOLGARI", "BOROS", "SIMIC"),
        TARKIR("ABZAN", "JESKAI", "SULTAI", "MARDU", "TEMUR", "DROMOKA", "OJUTAI", "SILUMGAR", "KOLAGHAN", "ATARKA"),
        ALARA("BANT", "ESPER", "GRIXIS", "JUND", "NAYA"),
        STRIXHAVEN("SILVERQUILL", "PRISMARI", "WITHERBLOOM", "LOREHOLD", "QUANDRIX"),
        PHYREXIANS("PHYREXIAN", "PHYREXIAN_MANA", "POISON");

        private final List<String> groups;

        Theme(final String... groups0) {
            groups = List.of(groups0);
        }

        public List<String> groups() {
            return groups;
        }
    }

    /** Does the card belong to the theme (to any of its factions)? */
    public static boolean belongsTo(final Theme theme, final CardRules rules) {
        return !groupsOf(theme, rules).isEmpty();
    }

    // ------------------------------------------------------------------ the worlds' sets

    /** Ravnica's sets; those of the guild blocks (whose mechanics are a guild's) first. */
    private static final List<String> RAVNICA_GUILD_SETS = List.of("RAV", "GPT", "DIS", "RTR", "GTC", "DGM", "GRN", "RNA", "GK1", "GK2", "RVR");
    private static final List<String> RAVNICA_OTHER_SETS = List.of("WAR", "MKM", "MKC", "CLU");
    private static final List<String> KHANS_SETS = List.of("KTK", "FRF", "TDM", "TDC");
    /** Tarkir's expansions without its Commander decks (whose many reprints are told by name, mechanic or three colours only). */
    private static final List<String> KHANS_EXPANSIONS = List.of("KTK", "FRF", "TDM");
    private static final List<String> DRAGONS_SETS = List.of("DTK");
    private static final List<String> ALARA_SETS = List.of("ALA", "CFX", "ARB");
    private static final List<String> STRIXHAVEN_SETS = List.of("STX", "C21", "SOS", "SOC");

    private static final Map<String, Set<String>> SET_NAMES = new ConcurrentHashMap<>();

    /** The names of the cards printed in these sets (a set Forge does not know adds none). */
    private static Set<String> namesIn(final List<String> codes) {
        return SET_NAMES.computeIfAbsent(String.join(",", codes), k -> {
            final Set<String> names = new HashSet<>();
            final StaticData data = StaticData.instance();
            if (data == null) {
                return names;
            }
            for (final String code : codes) {
                final CardEdition edition = data.getEditions().get(code);
                if (edition != null) {
                    for (final CardEdition.EditionEntry e : edition.getAllCardsInSet()) {
                        names.add(e.name());
                    }
                }
            }
            return names;
        });
    }

    // ------------------------------------------------------------------ the factions

    private static final byte W = MagicColor.WHITE;
    private static final byte U = MagicColor.BLUE;
    private static final byte B = MagicColor.BLACK;
    private static final byte R = MagicColor.RED;
    private static final byte G = MagicColor.GREEN;

    private record Faction(String group, byte colors, Pattern mechanic) { }

    private static Faction faction(final String group, final int colors, final String mechanic) {
        return new Faction(group, (byte) colors, mechanic == null ? null : Pattern.compile("(?i)\\b(?:" + mechanic + ")\\b"));
    }

    private static final List<Faction> GUILDS = List.of(
            faction("AZORIUS", W | U, "forecast|detain|addendum"),
            faction("DIMIR", U | B, "transmute|cipher|surveil"),
            faction("RAKDOS", B | R, "hellbent|unleash|spectacle"),
            faction("GRUUL", R | G, "bloodthirst|bloodrush|riot"),
            faction("SELESNYA", G | W, "convoke|populate"),
            faction("ORZHOV", W | B, "haunt|extort|afterlife"),
            faction("IZZET", U | R, "replicate|overload|jump-start"),
            faction("GOLGARI", B | G, "dredge|scavenge|undergrowth"),
            faction("BOROS", R | W, "radiance|battalion|mentor"),
            faction("SIMIC", G | U, "graft|evolve|adapt"));

    private static final List<Faction> CLANS = List.of(
            faction("ABZAN", W | B | G, "outlast|bolster|endure"),
            faction("JESKAI", U | R | W, "prowess|flurry"),
            faction("SULTAI", B | G | U, "delve|renew"),
            faction("MARDU", R | W | B, "raid|dash|mobilize"),
            faction("TEMUR", G | U | R, "ferocious|harmonize"));

    private static final List<Faction> DRAGONLORDS = List.of(
            faction("DROMOKA", G | W, "bolster"),
            faction("OJUTAI", W | U, "rebound"),
            faction("SILUMGAR", U | B, "exploit"),
            faction("KOLAGHAN", B | R, "dash"),
            faction("ATARKA", R | G, "formidable"));

    private static final List<Faction> SHARDS = List.of(
            faction("BANT", G | W | U, "exalted"),
            faction("ESPER", W | U | B, null),
            faction("GRIXIS", U | B | R, "unearth"),
            faction("JUND", B | R | G, "devour"),
            faction("NAYA", R | G | W, null));

    private static final List<Faction> COLLEGES = List.of(
            faction("SILVERQUILL", W | B, "inkling"),
            faction("PRISMARI", U | R, "4/4 blue and red elemental"),
            faction("WITHERBLOOM", B | G, "pest"),
            faction("LOREHOLD", R | W, "3/2 red and white spirit"),
            faction("QUANDRIX", G | U, "fractal"));

    private static final Pattern REMINDER = Pattern.compile("\\([^)]*\\)");

    private static final Map<String, String> TEAMWORK = new java.util.LinkedHashMap<>();
    static {
        // keywords at the start of a line or after a comma; the others anywhere in the text
        TEAMWORK.put("PARTNER", "(?im)(?:^|,\\s*)(?:partner|friends forever|doctor's companion)\\b");
        TEAMWORK.put("ASSIST", "(?im)(?:^|,\\s*)assist\\b");
        TEAMWORK.put("DEMONSTRATE", "(?im)(?:^|,\\s*)demonstrate\\b");
        TEAMWORK.put("SUPPORT", "(?i)\\bsupport \\d+|\\bsupport x\\b");
        TEAMWORK.put("SURGE", "(?im)(?:^|,\\s*)surge\\b");
        TEAMWORK.put("JOIN_FORCES", "(?i)\\bjoin forces\\b");
        TEAMWORK.put("COUNCIL", "(?i)\\bwill of the council\\b|\\bcouncil's dilemma\\b");
    }
    private static final Map<String, Pattern> TEAMWORK_PATTERNS = new java.util.LinkedHashMap<>();
    static {
        for (final Map.Entry<String, String> e : TEAMWORK.entrySet()) {
            TEAMWORK_PATTERNS.put(e.getKey(), Pattern.compile(e.getValue()));
        }
    }
    private static final Pattern POISON = Pattern.compile("(?i)\\b(?:infect|toxic \\d+|poisonous \\d+|compleated|corrupted|poison counters?|oil counters?)\\b");

    private static final Map<Theme, Map<String, List<String>>> CACHE = new EnumMap<>(Theme.class);

    /** The factions of the theme a card belongs to, in the theme's order (empty: none, or no such card). */
    public static List<String> groupsOf(final Theme theme, final CardRules rules) {
        if (theme == null || rules == null) {
            return Collections.emptyList();
        }
        final Map<String, List<String>> cache;
        synchronized (CACHE) {
            cache = CACHE.computeIfAbsent(theme, k -> new ConcurrentHashMap<>());
        }
        return cache.computeIfAbsent(rules.getName(), k -> Collections.unmodifiableList(compute(theme, rules)));
    }

    private static List<String> compute(final Theme theme, final CardRules rules) {
        final String name = rules.getName();
        final String text = textOf(rules);
        final byte colors = colorsOf(rules);
        final Set<String> found = new LinkedHashSet<>();
        switch (theme) {
            case TEAMWORK -> {
                for (final Map.Entry<String, Pattern> e : TEAMWORK_PATTERNS.entrySet()) {
                    if (e.getValue().matcher(text).find()) {
                        found.add(e.getKey());
                    }
                }
            }
            case RAVNICA -> {
                final boolean guildBlock = namesIn(RAVNICA_GUILD_SETS).contains(name);
                if (guildBlock || namesIn(RAVNICA_OTHER_SETS).contains(name)) {
                    match(GUILDS, name, text, colors, guildBlock, found);
                }
            }
            case TARKIR -> {
                final boolean khans = namesIn(KHANS_SETS).contains(name);
                final boolean dragons = namesIn(DRAGONS_SETS).contains(name);
                if (khans || dragons) {
                    // the dragonlords by their names anywhere in Tarkir; by colour and mechanic in their own set
                    named(DRAGONLORDS, name, found);
                    if (dragons) {
                        match(DRAGONLORDS, name, text, colors, true, found);
                    }
                    if (khans) {
                        match(CLANS, name, text, colors, true, found);
                        if (found.isEmpty() && namesIn(KHANS_EXPANSIONS).contains(name)) {
                            twoOfThree(CLANS, colors, found); // a two-colour card: the clans whose colours hold it
                        }
                    }
                }
            }
            case ALARA -> {
                if (namesIn(ALARA_SETS).contains(name)) {
                    match(SHARDS, name, text, colors, true, found);
                    if (found.isEmpty() && rules.getType().isArtifact() && colors != 0 && (colors & ~(W | U | B)) == 0) {
                        found.add("ESPER"); // Esper's coloured artifacts
                    }
                    if (found.isEmpty() && rules.getType().isCreature() && rules.getMainPart().getIntPower() >= 5
                            && colors != 0 && (colors & ~(R | G | W)) == 0) {
                        found.add("NAYA"); // Naya's big creatures
                    }
                    if (found.isEmpty()) {
                        twoOfThree(SHARDS, colors, found);
                    }
                }
            }
            case STRIXHAVEN -> {
                if (namesIn(STRIXHAVEN_SETS).contains(name)) {
                    match(COLLEGES, name, text, colors, true, found);
                }
            }
            case PHYREXIANS -> {
                for (final ICardFace face : rules.getAllFaces()) {
                    if (face != null && face.getType().hasCreatureType("Phyrexian")) {
                        found.add("PHYREXIAN");
                    }
                }
                if (name.contains("Phyrexian")) {
                    found.add("PHYREXIAN");
                }
                for (final ManaCostShard shard : rules.getManaCost()) {
                    if (shard.isPhyrexian()) {
                        found.add("PHYREXIAN_MANA");
                    }
                }
                if (POISON.matcher(text).find()) {
                    found.add("POISON");
                }
            }
            default -> { }
        }
        final List<String> ordered = new ArrayList<>();
        for (final String group : theme.groups()) {
            if (found.contains(group)) {
                ordered.add(group);
            }
        }
        return ordered;
    }

    /** Factions by name, then (if allowed) by mechanic, then by exactly the faction's colours. */
    private static void match(final List<Faction> factions, final String name, final String text, final byte colors,
            final boolean byMechanic, final Set<String> found) {
        named(factions, name, found);
        if (!found.isEmpty()) {
            return;
        }
        if (byMechanic) {
            for (final Faction f : factions) {
                if (f.mechanic() != null && f.mechanic().matcher(text).find()) {
                    found.add(f.group());
                }
            }
            if (!found.isEmpty()) {
                return;
            }
        }
        for (final Faction f : factions) {
            if (colors == f.colors()) {
                found.add(f.group());
            }
        }
    }

    /** Factions whose name the card's name holds (an Azorius Guildgate, Dromoka's Command). */
    private static void named(final List<Faction> factions, final String name, final Set<String> found) {
        final String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (final Faction f : factions) {
            if (lower.contains(f.group().toLowerCase(java.util.Locale.ROOT))) {
                found.add(f.group());
            }
        }
    }

    /** A card of two colours: every three-colour faction whose colours hold both. */
    private static void twoOfThree(final List<Faction> factions, final byte colors, final Set<String> found) {
        if (Integer.bitCount(colors & 0xff) != 2) {
            return;
        }
        for (final Faction f : factions) {
            if ((colors & ~f.colors()) == 0) {
                found.add(f.group());
            }
        }
    }

    /** The card's rules text without reminder text, all faces. */
    private static String textOf(final CardRules rules) {
        final StringBuilder sb = new StringBuilder();
        for (final ICardFace face : rules.getAllFaces()) {
            if (face != null && face.getOracleText() != null) {
                sb.append(face.getOracleText().replace("\\n", "\n")).append('\n');
            }
        }
        return REMINDER.matcher(sb).replaceAll("");
    }

    /** A card's colours; a land's are those of its colour identity (the mana it makes). */
    private static byte colorsOf(final CardRules rules) {
        final ColorSet set = rules.getType().isLand() ? rules.getColorIdentity() : rules.getColor();
        return (byte) (set.getColor() & (W | U | B | R | G));
    }
}
