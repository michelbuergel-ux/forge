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
        } else if (WORLDS.contains(id) || HexWorlds.isWorld(id)) {
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

    /** Does the card give its player experience counters or count them? */
    static boolean caresAboutExperience(final CardRules r) {
        final String oracle = r.getOracleText();
        if (oracle != null && oracle.toLowerCase(java.util.Locale.ROOT).contains("experience counter")) {
            return true;
        }
        final String other = r.getOtherPart() == null ? null : r.getOtherPart().getOracleText();
        return other != null && other.toLowerCase(java.util.Locale.ROOT).contains("experience counter");
    }

    // ------------------------------------------------------------------ catalog

    private static final List<HexTheme> ALL;
    static {
        final List<HexTheme> all = new ArrayList<>();
        all.add(mechanic("tokens", r -> hints(r, DeckHints.Type.ABILITY, "Token")));
        // counters of every kind, also the experience counters a player gets (few cards, and proliferate adds them too)
        all.add(mechanic("counters", r -> hints(r, DeckHints.Type.ABILITY, "Counters", "Proliferate") || caresAboutExperience(r)));
        // the graveyard: the cards hinted so (delirium, surveil, ...) and those that bring cards back (reanimating creatures,
        // returning cards to the hand, casting from the graveyard)
        all.add(mechanic("graveyard", r -> hints(r, DeckHints.Type.ABILITY, "Graveyard", "Mill", "Delirium", "Surveil")
                || mentionsAnyFace(r, HexTheme.FROM_GRAVEYARD)));
        // life gain: cards that give life, lifelink (having or giving it), doubling life gained ("if you would gain life") and
        // the payoffs (whenever you gain life, life gained this turn, a high life total); not paying life, not Food (a theme
        // of its own), not the cards that merely have the AI's LifeGain hint
        all.add(mechanic("lifegain", r -> r.hasKeyword("Lifelink") || mentionsAnyFace(r, HexTheme.LIFEGAIN)));
        // sacrifice: cards that make you (or others) sacrifice something, outlets, edicts, payoffs, paying life (not the
        // lands that may cost life) and the "aristocrats" (whenever a creature dies, Teysa Karlov's dying causes a trigger);
        // not the cards that only sacrifice themselves or make Food, Treasure, Clues
        all.add(mechanic("sacrifice", r -> mentionsAnyFace(r, HexTheme.SACRIFICE) || mentionsAnyFace(r, HexTheme.DIES)
                || !r.getType().isLand() && mentionsAnyFace(r, HexTheme.PAY_LIFE)));
        // discard: cards that discard (yours or an opponent's), madness, connive, hellbent, Blood; not discarding only themselves
        all.add(mechanic("discard", r -> mentionsAnyFace(r, HexTheme.DISCARD)));
        // ramp: nonland cards that make mana, double it, fetch lands onto the battlefield, give extra land drops, untap lands
        // or make spells cheaper (the AI's "Mana" hint that this used is on no card)
        all.add(mechanic("ramp", r -> !r.getType().isLand() && mentionsAnyFace(r, HexTheme.RAMP)));
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
        // the worlds beyond Magic's planes (Marvel, Fallout, ...): the cards of their sets and Secret Lair drops. See HexWorlds.
        for (final String world : HexWorlds.IDS) {
            all.add(new HexTheme(world, "lblHexTheme_" + world, null, r -> HexWorlds.belongsTo(world, r.getName()), true));
        }
        // haste, deathtouch (creatures that have it, cards that give it) and direct damage
        all.add(mechanic("haste", r -> mentions(r, HexTheme.HASTE) || hints(r, DeckHints.Type.KEYWORD, "Haste")));
        all.add(mechanic("deathtouch", r -> mentions(r, HexTheme.DEATHTOUCH) || hints(r, DeckHints.Type.KEYWORD, "Deathtouch")));
        all.add(mechanic("burn", r -> mentions(r, HexTheme.BURN)));
        // drawing cards; milling (a library into its graveyard); Alchemy, the digital cards of MTG Arena
        all.add(mechanic("carddraw", r -> mentionsAnyFace(r, HexTheme.DRAW)));
        // milling only: the graveyard's payoffs are the graveyard theme
        all.add(mechanic("mill", r -> mentionsAnyFace(r, HexTheme.MILL)));
        all.add(new HexTheme("alchemy", "lblHexTheme_alchemy", null, HexTheme::alchemy, true));
        // poison: cards that give poison counters (infect, toxic, poisonous), add more (proliferate) or care about them (corrupted)
        all.add(mechanic("poison", r -> mentions(r, HexTheme.POISON) || hints(r, DeckHints.Type.KEYWORD, "Infect", "Toxic", "Poisonous")));
        // energy: cards that give energy counters ({E}) or pay them
        all.add(mechanic("energy", r -> mentionsAnyFace(r, HexTheme.ENERGY)));
        ALL = Collections.unmodifiableList(all);
    }

    /** The creature types of the critters theme. */
    private static final String[] CRITTERS = { "Squirrel", "Rabbit", "Otter", "Raccoon", "Weasel", "Rat", "Mouse", "Hamster",
            "Badger", "Possum", "Mole" };

    private static HexTheme mechanic(final String id, final Predicate<CardRules> fits) {
        return new HexTheme(id, "lblHexTheme_" + id, null, fits);
    }

    private static final java.util.regex.Pattern POISON = java.util.regex.Pattern.compile(
            "\\b(?:poison counters?|infect|toxic|poisonous|proliferate|corrupted)\\b");
    private static final java.util.regex.Pattern LIFEGAIN = java.util.regex.Pattern.compile(
            "\\bgains? (?:(?:\\d+|x|one|two|three|four|five|six|seven|eight|nine|ten|that much|twice that much|half that much"
            + "|that many|an equal amount of|an amount of) life|life equal to)|\\blifelink\\b|\\bif you would gain life\\b"
            + "|\\bextort\\b"
            // and the cards that reward gaining life: whenever you gain life, life gained this turn, a high life total
            + "|\\bwhenever you gain life\\b|\\byou(?:'ve)? gained (?:\\d+ or more )?life this turn\\b|\\blife you(?:'ve)? gained this turn\\b"
            + "|\\bamount of life you gained\\b|\\bat least \\d+ life\\b|\\b\\d+ or more life\\b|\\blife total is (?:at least|greater)"
            + "|\\bmore life than your starting\\b|\\bequal to your life total\\b");
    private static final java.util.regex.Pattern SACRIFICE = java.util.regex.Pattern.compile(
            "\\bsacrifices? (?:a|an|another|one or more|one|two|three|four|five|x|any number of|half|all|that many|\\d+)\\b"
            + "|\\b(?:exploit|casualty|bargain|emerge|devour)\\b");
    private static final java.util.regex.Pattern PAY_LIFE = java.util.regex.Pattern.compile(
            "\\bpays? (?:(?:\\d+|x|one|two|three|four|five|six|seven|eight|nine|ten|any amount of) life|life equal to"
            + "|half (?:your|their|his or her) life)");
    private static final java.util.regex.Pattern ENERGY = java.util.regex.Pattern.compile("\\{e\\}|\\benergy counters?\\b");
    private static final java.util.regex.Pattern HASTE = java.util.regex.Pattern.compile("\\b(?:haste|dash|blitz)\\b");
    private static final java.util.regex.Pattern DEATHTOUCH = java.util.regex.Pattern.compile("\\bdeathtouch\\b");
    /** Damage dealt to any target, players, creatures, planeswalkers (not "deals combat damage", not "to you"). */
    private static final java.util.regex.Pattern BURN = java.util.regex.Pattern.compile(
            "\\bdeals? (?:\\d+|x|that much|twice that much|three times that much|damage equal to [^.]*?) (?:damage )?to "
            + "(?:any|up to|another target|target|each|that player|defending player|its controller|that creature's controller"
            + "|that permanent's controller)\\b");

    private static final java.util.regex.Pattern DRAW = java.util.regex.Pattern.compile(
            "(?<!opponent )\\bdraws? (?:a|an|one|two|three|four|five|six|seven|x|that many|a number of)(?: additional)? cards?\\b"
            + "|(?<!opponent )\\bdraws? cards equal to\\b");
    private static final java.util.regex.Pattern MILL = java.util.regex.Pattern.compile("\\bmill(?:s|ed)?\\b");
    private static final java.util.regex.Pattern DISCARD = java.util.regex.Pattern.compile(
            "\\bdiscard(?:s|ed)?\\b(?! this card)|\\bmadness\\b|\\bconnives?\\b|\\bhellbent\\b|\\bblood tokens?\\b");
    private static final java.util.regex.Pattern RAMP = java.util.regex.Pattern.compile(
            "\\badd (?:\\{[wubrgcs0-9x]\\}|one mana|two mana|three mana|x mana|an amount of mana|that much mana|mana)"
            + "|\\badds? (?:an )?additional\\b[^.]*mana|\\bproduces? (?:twice|three times) as much\\b"
            + "|\\bsearch your library for [^.]*?\\blands? cards?[^.]*?onto the battlefield"
            + "|\\bput (?:a|up to \\w+|that|those|x|two|three) (?:basic )?lands? cards? [^.]*?onto the battlefield"
            + "|\\bplay (?:an|two|three) additional lands?\\b|\\buntap (?:target|all|up to \\w+|x|two|three) lands?\\b"
            + "|\\bspells you cast cost \\{\\d\\} less\\b");
    private static final java.util.regex.Pattern FROM_GRAVEYARD = java.util.regex.Pattern.compile(
            "\\breturn [^.]*?from (?:your|a|an opponent's|their|its owner's|any|each player's|all) graveyards? (?:to|onto) "
            + "|\\bput [^.]*?from (?:your|a|an opponent's|their|any) graveyards? onto the battlefield"
            + "|\\bcast [^.]*?from (?:your|a|an opponent's|their|any) graveyards?"
            + "|\\breturn enchanted creature card to the battlefield\\b");
    private static final java.util.regex.Pattern DIES = java.util.regex.Pattern.compile(
            "\\bwhenever [^.]*?\\b(?:a|an|another|one or more|nontoken|each)\\b[^.]*?\\b(?:creatures?|permanents?|tokens?)\\b[^.]*? dies?\\b"
            + "|\\bdying causes a triggered ability\\b");
    /** The mechanics only MTG Arena's cards have. */
    private static final java.util.regex.Pattern DIGITAL = java.util.regex.Pattern.compile(
            "\\bperpetually\\b|\\bseeks? (?:a|an|two|three|x)\\b|\\bconjures? (?:a|an|two|three|x)\\b|\\bspellbook\\b"
            + "|\\bspecialize\\b|\\bdouble team\\b|\\bintensity\\b|\\bincorporate\\b");
    private static final java.util.Map<String, Boolean> ALCHEMY = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Alchemy, MTG Arena's digital cards: those first printed in an Alchemy set (Alchemy: Innistrad, Alchemy Horizons:
     * Baldur's Gate, ...), the rebalanced "A-" cards, and any with a mechanic only Arena has (perpetually, seek, conjure, ...).
     */
    private static boolean alchemy(final CardRules r) {
        return ALCHEMY.computeIfAbsent(r.getName(), name -> {
            if (name.startsWith("A-") || mentions(r, DIGITAL)) {
                return true;
            }
            final forge.StaticData data = forge.StaticData.instance();
            if (data == null) {
                return false;
            }
            forge.card.CardEdition first = null;
            for (final forge.item.PaperCard card : data.getCommonCards().getAllCards(name)) {
                final forge.card.CardEdition ed = data.getEditions().get(card.getEdition());
                if (ed != null && ed.getDate() != null && (first == null || ed.getDate().before(first.getDate()))) {
                    first = ed;
                }
            }
            return first != null && first.getName().startsWith("Alchemy");
        });
    }

    private static final java.util.regex.Pattern REMINDER = java.util.regex.Pattern.compile("\\([^)]*\\)");

    /**
     * The rules text as the themes read it: lower case, the lines apart (the card scripts keep a literal "\\n" between
     * them, which glued a line's first word to the line before), and without reminder text: "(... Draw a card.)" of
     * cycling, Clues or Blood is not what the card does itself.
     */
    static String rulesText(final String oracle) {
        if (oracle == null) {
            return null;
        }
        return REMINDER.matcher(oracle.replace("\\n", "\n")).replaceAll("").toLowerCase(java.util.Locale.ROOT);
    }

    /** Rules text (all faces, lower case) that matches. */
    private static boolean mentions(final CardRules r, final java.util.regex.Pattern pattern) {
        final String text = rulesText(r.getOracleText());
        return text != null && pattern.matcher(text).find();
    }

    /** As {@link #mentions}, also on the card's other face. */
    private static boolean mentionsAnyFace(final CardRules r, final java.util.regex.Pattern pattern) {
        if (mentions(r, pattern)) {
            return true;
        }
        final String other = r.getOtherPart() == null ? null : rulesText(r.getOtherPart().getOracleText());
        return other != null && pattern.matcher(other).find();
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
