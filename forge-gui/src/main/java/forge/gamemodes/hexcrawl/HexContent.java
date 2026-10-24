package forge.gamemodes.hexcrawl;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardType;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.util.FileUtil;

/**
 * Everything the hex campaign takes from Forge's data: the enemies and bosses of the
 * Adventure mode (with their decks), the card pool for shops and starter decks, and prices.
 */
public final class HexContent {
    private static final class Enemy {
        String name;
        String deck;
        int life;
        String colors = "";
        String sprite;
        /** Items of the Adventure mode the enemy is equipped with. */
        List<String> equipment = new ArrayList<>();
        /** What a boss says before the fight and after winning it (Adventure data), or null. */
        String bossIntro;
        String bossInsult;
    }

    public static final int CARDS_PER_SHOP = 8;
    public static final int RELICS_PER_TOWN = 3;
    private static final Set<String> RELIC_SLOTS = new HashSet<>(java.util.Arrays.asList("Left", "Right", "Neck", "Body", "Boots"));

    private static final Map<HexColor, String> BOSS_NAMES = new EnumMap<>(HexColor.class);
    static {
        BOSS_NAMES.put(HexColor.WHITE, "Akroma");
        BOSS_NAMES.put(HexColor.BLUE, "Lorthos");
        BOSS_NAMES.put(HexColor.BLACK, "Griselbrand");
        BOSS_NAMES.put(HexColor.RED, "Lathliss");
        BOSS_NAMES.put(HexColor.GREEN, "Ghalta");
        BOSS_NAMES.put(HexColor.COLORLESS, "Emrakul");
    }

    /** What each archmage brings to its fight in the command zone, as in its Adventure castle. */
    private static final Map<HexColor, String> BOSS_PRESENCE = new EnumMap<>(HexColor.class);
    /** What each archmage does once its life is down to half (the campaign's own cards). */
    private static final Map<HexColor, String> BOSS_PHASE_TWO = new EnumMap<>(HexColor.class);
    static {
        BOSS_PRESENCE.put(HexColor.WHITE, "Akroma's Presence");
        BOSS_PRESENCE.put(HexColor.BLUE, "Lorthos' Presence");
        BOSS_PRESENCE.put(HexColor.BLACK, "Griselbrand's Presence");
        BOSS_PRESENCE.put(HexColor.RED, "Lathliss' Presence");
        BOSS_PRESENCE.put(HexColor.GREEN, "Ghalta's Presence");
        BOSS_PRESENCE.put(HexColor.COLORLESS, "Emrakul's Awakening");
        BOSS_PHASE_TWO.put(HexColor.WHITE, "Akroma Awakened");
        BOSS_PHASE_TWO.put(HexColor.BLUE, "Lorthos Awakened");
        BOSS_PHASE_TWO.put(HexColor.BLACK, "Griselbrand Awakened");
        BOSS_PHASE_TWO.put(HexColor.RED, "Lathliss Awakened");
        BOSS_PHASE_TWO.put(HexColor.GREEN, "Ghalta Awakened");
        BOSS_PHASE_TWO.put(HexColor.COLORLESS, "Emrakul Unbound");
    }
    /** Emrakul in New Game Plus: the presence of the harder Adventure difficulties. */
    private static final String EMRAKUL_HARD_PRESENCE = "Emrakul's Presence";
    /** Number of the Adventure mode's general boss intro lines (AdvBossIntro1..n) and insults. */
    private static final int BOSS_INTRO_LINES = 35;
    private static final int BOSS_INSULT_LINES = 44;

    private static HexContent instance;

    private final Map<String, Enemy> enemies = new HashMap<>();
    private final Map<HexColor, List<Enemy>> enemiesByColor = new EnumMap<>(HexColor.class);
    private final Map<HexColor, Map<CardRarity, List<PaperCard>>> cardsByColor = new EnumMap<>(HexColor.class);
    private final Map<String, Integer> listedPrices = new HashMap<>();
    private final Map<HexShopType, Map<HexColor, Map<CardRarity, List<PaperCard>>>> cardsByShop = new EnumMap<>(HexShopType.class);
    /** The multicoloured cards of each shop (lands by the colours of their mana), sold in the towns of their colours. */
    private final Map<HexShopType, List<PaperCard>> goldByShop = new EnumMap<>(HexShopType.class);
    private final List<Enemy> miniBosses = new ArrayList<>();
    private final Map<String, HexRelic> relics = new java.util.LinkedHashMap<>();
    /** What every item of the Adventure mode puts onto the battlefield at the start of a fight, by item name. */
    private final Map<String, List<String>> itemStartCards = new HashMap<>();

    public static synchronized HexContent get() {
        if (instance == null) {
            instance = new HexContent();
        }
        return instance;
    }

    private HexContent() {
        HexCustomCards.loadOnce(); // the bosses' presence and phase cards
        loadEnemies();
        loadCards();
        loadPrices();
        loadRelics();
    }

    private static String dir() {
        return ForgeConstants.ADVENTURE_COMMON_DIR;
    }

    private void loadEnemies() {
        try (Reader reader = new FileReader(dir() + "world" + File.separator + "enemies.json", java.nio.charset.StandardCharsets.UTF_8)) {
            final JsonArray all = JsonParser.parseReader(reader).getAsJsonArray();
            for (final JsonElement el : all) {
                final JsonObject o = el.getAsJsonObject();
                if (!o.has("name") || !o.has("deck") || !o.get("deck").isJsonArray() || o.getAsJsonArray("deck").size() == 0) {
                    continue;
                }
                final Enemy e = new Enemy();
                e.name = o.get("name").getAsString();
                // some enemies only have a recipe (.json) for a generated deck; we need a real deck file
                for (final JsonElement d : o.getAsJsonArray("deck")) {
                    final String path = d.getAsString();
                    if (path.endsWith(".dck") && new File(dir() + path).isFile()) {
                        e.deck = path;
                        break;
                    }
                }
                e.life = o.has("life") ? o.get("life").getAsInt() : 20;
                e.colors = o.has("colors") && o.get("colors").isJsonPrimitive() ? o.get("colors").getAsString() : "";
                e.sprite = o.has("sprite") && o.get("sprite").isJsonPrimitive() ? o.get("sprite").getAsString() : null;
                e.bossIntro = o.has("bossIntro") && o.get("bossIntro").isJsonPrimitive() ? cleanLine(o.get("bossIntro").getAsString()) : null;
                e.bossInsult = o.has("bossInsult") && o.get("bossInsult").isJsonPrimitive() ? cleanLine(o.get("bossInsult").getAsString()) : null;
                if (o.has("equipment") && o.get("equipment").isJsonArray()) {
                    for (final JsonElement item : o.getAsJsonArray("equipment")) {
                        e.equipment.add(item.getAsString());
                    }
                }
                if (e.deck != null) {
                    enemies.put(e.name, e);
                    if (e.deck.startsWith("decks/miniboss/")) {
                        miniBosses.add(e); // the named enemies the towns put a bounty on
                    }
                }
            }
        } catch (final Exception ex) {
            throw new IllegalStateException("Could not read the adventure enemies", ex);
        }

        for (final HexColor color : HexColor.values()) {
            final List<Enemy> list = new ArrayList<>();
            final File biome = new File(dir() + "world" + File.separator + "biomes" + File.separator + color.getBiome() + ".json");
            try (Reader reader = new FileReader(biome, java.nio.charset.StandardCharsets.UTF_8)) {
                final JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                final Set<String> seen = new HashSet<>();
                for (final JsonElement el : o.getAsJsonArray("enemies")) {
                    final Enemy e = enemies.get(el.getAsString());
                    // ordinary wanderers only: bosses and mini bosses have their own decks folder
                    if (e != null && e.deck.startsWith("decks/standard/") && seen.add(e.name)) {
                        list.add(e);
                    }
                }
            } catch (final Exception ex) {
                throw new IllegalStateException("Could not read biome " + biome, ex);
            }
            list.sort((a, b) -> a.life != b.life ? Integer.compare(a.life, b.life) : a.name.compareTo(b.name));
            enemiesByColor.put(color, list);
        }
        miniBosses.sort((a, b) -> a.name.compareTo(b.name));
    }

    private void loadCards() {
        for (final HexColor color : HexColor.values()) {
            cardsByColor.put(color, new EnumMap<>(CardRarity.class));
        }
        final List<PaperCard> all = new ArrayList<>(FModel.getMagicDb().getCommonCards().getUniqueCards());
        all.sort((a, b) -> a.getName().compareTo(b.getName())); // a stable order, so seeds are reproducible
        for (final PaperCard card : all) {
            final CardRules rules = card.getRules();
            if (rules == null) {
                continue;
            }
            if (needsCommander(rules)) {
                continue; // the campaign has no commanders: such cards would do nothing
            }
            final CardType type = rules.getType();
            sortIntoShop(card, rules);
            final boolean playable = type.isCreature() || type.isInstant() || type.isSorcery() || type.isEnchantment()
                    || type.isArtifact() || type.isPlaneswalker();
            if (!playable || type.isLand() || rules.getAiHints().getRemAIDecks()) {
                continue;
            }
            final CardRarity rarity = card.getRarity();
            if (rarity != CardRarity.Common && rarity != CardRarity.Uncommon && rarity != CardRarity.Rare && rarity != CardRarity.MythicRare) {
                continue;
            }
            final HexColor color = colorOf(rules.getColor().getColor());
            if (color == null) {
                continue;
            }
            cardsByColor.get(color).computeIfAbsent(rarity, k -> new ArrayList<>()).add(card);
        }
    }

    /**
     * Cards that only work with a commander: they speak of "commander" (lieutenants, Command Tower,
     * partners and the like), or they are or choose a Background. The campaign leaves them out of
     * its shops, starter decks and rewards.
     */
    public static boolean needsCommander(final CardRules rules) {
        if (rules == null) {
            return false;
        }
        final String oracle = rules.getOracleText();
        return (oracle != null && oracle.toLowerCase(java.util.Locale.ROOT).contains("commander"))
                || rules.canBeBackground() || rules.hasKeyword("Choose a Background");
    }

    /** Puts a card on the shelf of the shop that deals in its kind. */
    private void sortIntoShop(final PaperCard card, final CardRules rules) {
        final CardRarity rarity = card.getRarity();
        if (rarity != CardRarity.Common && rarity != CardRarity.Uncommon && rarity != CardRarity.Rare && rarity != CardRarity.MythicRare) {
            return;
        }
        if (rules.getAiHints().getRemAIDecks()) {
            return;
        }
        final CardType type = rules.getType();
        final HexShopType shop;
        if (type.isLand()) {
            shop = type.isBasicLand() ? null : HexShopType.LAND;
        } else if (type.isPlaneswalker()) {
            shop = HexShopType.PLANESWALKER; // also the rare planeswalker creatures and artifacts
        } else if (type.isCreature()) {
            shop = HexShopType.CREATURE;
        } else if (type.isArtifact()) {
            shop = HexShopType.ARTIFACT;
        } else if (type.isEnchantment()) {
            shop = HexShopType.ENCHANTMENT;
        } else if (type.isSorcery()) {
            shop = HexShopType.SORCERY;
        } else if (type.isInstant()) {
            shop = HexShopType.INSTANT;
        } else {
            shop = null;
        }
        if (shop == null) {
            return;
        }
        // a land belongs to the colour of the mana it makes
        final byte bits = colorBits(shop, rules);
        if (Integer.bitCount(bits) >= 2) {
            goldByShop.computeIfAbsent(shop, k -> new ArrayList<>()).add(card); // multicoloured: the towns of its colours
            return;
        }
        final HexColor color = colorOf(bits);
        if (color == null) {
            return;
        }
        cardsByShop.computeIfAbsent(shop, k -> new EnumMap<>(HexColor.class))
                .computeIfAbsent(color, k -> new EnumMap<>(CardRarity.class))
                .computeIfAbsent(rarity, k -> new ArrayList<>()).add(card);
    }

    /** The colours a card counts for in the shops: its colour, a land's the colours of its mana. */
    private static byte colorBits(final HexShopType shop, final CardRules rules) {
        return shop == HexShopType.LAND ? rules.getColorIdentity().getColor() : rules.getColor().getColor();
    }

    private static byte bitOf(final HexColor color) {
        switch (color) {
            case WHITE: return MagicColor.WHITE;
            case BLUE: return MagicColor.BLUE;
            case BLACK: return MagicColor.BLACK;
            case RED: return MagicColor.RED;
            case GREEN: return MagicColor.GREEN;
            default: return 0;
        }
    }

    private static HexColor colorOf(final byte c) {
        if (c == MagicColor.COLORLESS) { return HexColor.COLORLESS; }
        if (c == MagicColor.WHITE) { return HexColor.WHITE; }
        if (c == MagicColor.BLUE) { return HexColor.BLUE; }
        if (c == MagicColor.BLACK) { return HexColor.BLACK; }
        if (c == MagicColor.RED) { return HexColor.RED; }
        if (c == MagicColor.GREEN) { return HexColor.GREEN; }
        return null; // multicoloured cards are not sold in the single coloured towns
    }

    /** Reads the wearable items of the Adventure mode whose effects we can apply to a fight. */
    private void loadRelics() {
        final File f = new File(dir() + "world" + File.separator + "items.json");
        if (!f.isFile()) {
            return;
        }
        try (Reader reader = new FileReader(f, java.nio.charset.StandardCharsets.UTF_8)) {
            for (final JsonElement el : JsonParser.parseReader(reader).getAsJsonArray()) {
                final JsonObject o = el.getAsJsonObject();
                if (o.has("name") && o.has("effect") && o.getAsJsonObject("effect").has("startBattleWithCard")) {
                    // also items that are not for sale: enemies are equipped with them
                    final List<String> cards = new ArrayList<>();
                    for (final JsonElement c : o.getAsJsonObject("effect").getAsJsonArray("startBattleWithCard")) {
                        if (findStartCard(c.getAsString()) != null) {
                            cards.add(c.getAsString());
                        }
                    }
                    itemStartCards.put(o.get("name").getAsString(), cards);
                }
                if (!o.has("name") || !o.has("effect") || !o.has("cost") || !o.has("equipmentSlot")
                        || (o.has("questItem") && o.get("questItem").getAsBoolean())) {
                    continue;
                }
                final HexRelic relic = new HexRelic();
                relic.name = o.get("name").getAsString();
                relic.slot = o.get("equipmentSlot").getAsString();
                if (!RELIC_SLOTS.contains(relic.slot) || o.get("cost").getAsInt() <= 0) {
                    continue;
                }
                relic.price = Math.max(30, o.get("cost").getAsInt() / 50);
                final JsonObject effect = o.getAsJsonObject("effect");
                // items that need the Adventure mode's own cards (command zone effects) are left out
                if (effect.has("startBattleWithCardInCommandZone") || !readEffect(effect, relic, false)) {
                    continue;
                }
                if (effect.has("opponent") && !readEffect(effect.getAsJsonObject("opponent"), relic, true)) {
                    continue;
                }
                if (relic.life == 0 && relic.hand == 0 && relic.battlefield.isEmpty() && relic.enemyLife == 0 && relic.enemyBattlefield.isEmpty()) {
                    continue; // only useful on the Adventure map
                }
                relic.description = o.has("description") ? o.get("description").getAsString() : "";
                relics.put(relic.name, relic);
            }
        } catch (final Exception ex) {
            ex.printStackTrace(); // the campaign works without relics
        }
    }

    private boolean readEffect(final JsonObject effect, final HexRelic relic, final boolean enemy) {
        final int life = effect.has("lifeModifier") ? effect.get("lifeModifier").getAsInt() : 0;
        final List<String> cards = enemy ? relic.enemyBattlefield : relic.battlefield;
        if (effect.has("startBattleWithCard")) {
            for (final JsonElement c : effect.getAsJsonArray("startBattleWithCard")) {
                if (findStartCard(c.getAsString()) == null) {
                    return false;
                }
                cards.add(c.getAsString());
            }
        }
        if (enemy) {
            relic.enemyLife = life;
        } else {
            relic.life = life;
            relic.hand = effect.has("changeStartCards") ? effect.get("changeStartCards").getAsInt() : 0;
        }
        return true;
    }

    /** A card or a token an item puts onto the battlefield, by the name the Adventure data uses. */
    public forge.item.IPaperCard findStartCard(final String name) {
        try {
            final PaperCard card = FModel.getMagicDb().getCommonCards().getCard(name);
            if (card != null) {
                return card;
            }
            return FModel.getMagicDb().getAllTokens().getToken(name);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private void loadPrices() {
        final File f = new File(dir() + "world" + File.separator + "cardprices.txt");
        if (!f.isFile()) {
            return;
        }
        for (final String line : FileUtil.readFile(f)) {
            final int idx = line.lastIndexOf('=');
            if (idx <= 0) {
                continue;
            }
            try {
                listedPrices.put(line.substring(0, idx).trim(), Integer.parseInt(line.substring(idx + 1).trim()));
            } catch (final NumberFormatException ignored) {
                // a comment or a broken line
            }
        }
    }

    // ------------------------------------------------------------------ enemies

    public String getBossName(final HexColor color) {
        return BOSS_NAMES.get(color);
    }

    public HexEncounter bossEncounter(final HexColor color) {
        final Enemy e = enemies.get(BOSS_NAMES.get(color));
        if (e == null) {
            throw new IllegalStateException("Boss not found: " + BOSS_NAMES.get(color));
        }
        final HexEncounter enc = toEncounter(e, color);
        enc.boss = true;
        enc.gold = color == HexColor.COLORLESS ? 500 : 200;
        enc.intro = e.bossIntro;
        enc.insult = e.bossInsult;
        return enc;
    }

    /** A boss's words before the fight: its own line, or one of the Adventure mode's general ones. */
    public String bossIntro(final HexEncounter enc, final Random rnd) {
        if (enc.intro != null && !enc.intro.isEmpty()) {
            return enc.intro;
        }
        return forge.util.Localizer.getInstance().getMessage("AdvBossIntro" + (1 + rnd.nextInt(BOSS_INTRO_LINES)));
    }

    /** What a boss says after beating the party. */
    public String bossInsult(final HexEncounter enc, final Random rnd) {
        if (enc.insult != null && !enc.insult.isEmpty()) {
            return enc.insult;
        }
        return forge.util.Localizer.getInstance().getMessage("AdvBossInsult" + (1 + rnd.nextInt(BOSS_INSULT_LINES)));
    }

    /**
     * The cards an archmage (or Emrakul) has in the command zone: its presence from the Adventure
     * castle and the card that awakens it at half life. Empty for other enemies.
     */
    public List<forge.item.IPaperCard> bossCommandCards(final HexEncounter enc, final int cycle) {
        final List<forge.item.IPaperCard> result = new ArrayList<>();
        if (!enc.boss || enc.color == null) {
            return result;
        }
        String presence = BOSS_PRESENCE.get(enc.color);
        if (enc.color == HexColor.COLORLESS && cycle > 1) {
            presence = EMRAKUL_HARD_PRESENCE;
        }
        for (final String name : new String[] { presence, BOSS_PHASE_TWO.get(enc.color) }) {
            final PaperCard card = name == null ? null : FModel.getMagicDb().getCommonCards().getCard(name);
            if (card != null) {
                result.add(card);
            }
        }
        return result;
    }

    /** Adventure texts sometimes carry markup (italics) that a plain dialog would show as it is. */
    private static String cleanLine(final String s) {
        return s.replaceAll("<[^>]*>", "").trim();
    }

    /** The names of the cards an enemy starts with, for display. */
    public String startCardNames(final HexEncounter enc) {
        final List<String> names = new ArrayList<>();
        for (final String id : enc.startCards) {
            final forge.item.IPaperCard card = findStartCard(id);
            names.add(card == null ? id : card.getName());
        }
        return String.join(", ", names);
    }

    /**
     * A wandering enemy of the given colour.
     * @param danger 0 (close to the start) to 1 (far out): picks from the weaker or the tougher half
     */
    public HexEncounter randomEncounter(final HexColor color, final double danger, final Random rnd) {
        final List<Enemy> list = enemiesByColor.get(color);
        if (list == null || list.isEmpty()) {
            throw new IllegalStateException("No enemies for " + color);
        }
        final int window = Math.max(1, list.size() / 2);
        final int from = (int) Math.round(Math.max(0, Math.min(1, danger)) * (list.size() - window));
        final Enemy e = list.get(from + rnd.nextInt(window));
        final HexEncounter enc = toEncounter(e, color);
        enc.gold = 20 + e.life;
        return enc;
    }

    private HexEncounter toEncounter(final Enemy e, final HexColor color) {
        final HexEncounter enc = new HexEncounter();
        enc.enemyName = e.name;
        enc.deckPath = e.deck;
        enc.life = e.life;
        enc.color = color;
        for (final String item : e.equipment) {
            final List<String> cards = itemStartCards.get(item);
            if (cards != null) {
                enc.startCards.addAll(cards); // as in the Adventure mode: the enemy starts with its equipment in play
            }
        }
        return enc;
    }

    /**
     * The followers that fight at the side of an enemy when the party has several heads: other
     * wanderers of the same colour, none stronger than the enemy itself. The choice depends only
     * on the enemy and its field, so it is the same every time it is asked for.
     */
    public List<HexEncounter> followers(final HexEncounter enc, final int count) {
        final List<HexEncounter> result = new ArrayList<>();
        if (count <= 0) {
            return result;
        }
        List<Enemy> pool = enemiesByColor.get(enc.color);
        if (pool == null || pool.isEmpty()) {
            pool = enemiesByColor.get(HexColor.COLORLESS);
        }
        final List<Enemy> fitting = new ArrayList<>();
        for (final Enemy e : pool) {
            if (!e.name.equals(enc.enemyName) && e.life <= Math.max(enc.life, 14)) {
                fitting.add(e);
            }
        }
        if (fitting.isEmpty()) {
            fitting.addAll(pool);
        }
        final Random rnd = new Random(enc.enemyName.hashCode() * 31L + enc.q * 131L + enc.r * 17L);
        final Set<String> names = new HashSet<>();
        names.add(enc.enemyName);
        for (int i = 0; i < count; i++) {
            Enemy e = fitting.get(rnd.nextInt(fitting.size()));
            for (int tries = 0; tries < 20 && names.contains(e.name); tries++) {
                e = fitting.get(rnd.nextInt(fitting.size()));
            }
            final HexEncounter follower = new HexEncounter();
            String name = e.name;
            for (int n = 2; names.contains(name); n++) {
                name = e.name + " " + n; // every player in a game needs a name of its own
            }
            names.add(name);
            follower.enemyName = name;
            follower.deckPath = e.deck;
            follower.life = e.life;
            follower.color = enc.color;
            result.add(follower);
        }
        return result;
    }

    /**
     * A named enemy (a mini boss of the Adventure mode) for a task of a town, preferably one
     * that fits the colour of the field it will camp on.
     */
    public HexQuest questEnemy(final HexColor color, final Random rnd) {
        if (miniBosses.isEmpty()) {
            return null;
        }
        final List<Enemy> fitting = new ArrayList<>();
        for (final Enemy e : miniBosses) {
            if (e.colors.contains(color.getSymbol())) {
                fitting.add(e);
            }
        }
        final List<Enemy> pool = fitting.isEmpty() || rnd.nextInt(4) == 0 ? miniBosses : fitting;
        final Enemy e = pool.get(rnd.nextInt(pool.size()));
        final HexQuest quest = new HexQuest();
        quest.enemyName = e.name;
        quest.deckPath = e.deck;
        quest.life = Math.max(20, Math.min(40, e.life));
        quest.color = color;
        quest.gold = 60 + quest.life;
        // a wanted enemy carries a relic that helps it (never one that also helps the party)
        final List<HexRelic> helpful = new ArrayList<>();
        for (final HexRelic relic : relics.values()) {
            if (relic.life >= 0 && relic.hand >= 0 && relic.enemyLife == 0 && relic.enemyBattlefield.isEmpty()
                    && (relic.life > 0 || !relic.battlefield.isEmpty())) {
                helpful.add(relic);
            }
        }
        if (!helpful.isEmpty()) {
            quest.relic = helpful.get(rnd.nextInt(helpful.size())).name;
        }
        return quest;
    }

    public HexEncounter questEncounter(final HexQuest quest) {
        final HexEncounter enc = new HexEncounter();
        enc.enemyName = quest.enemyName;
        enc.deckPath = quest.deckPath;
        enc.life = quest.life;
        enc.color = quest.color;
        enc.gold = 20 + quest.life;
        enc.questId = quest.id;
        enc.q = quest.q;
        enc.r = quest.r;
        final HexRelic relic = quest.relic == null ? null : relics.get(quest.relic);
        if (relic != null) {
            enc.life += relic.life;
            enc.startCards.addAll(relic.battlefield);
        }
        return enc;
    }

    /**
     * The picture file of an enemy: the sprite sheet description (.atlas) of the Adventure mode,
     * or null if the enemy has none.
     */
    public File getSpriteAtlas(final String enemyName) {
        final Enemy e = enemies.get(enemyName);
        if (e == null || e.sprite == null) {
            return null;
        }
        final File f = new File(dir() + e.sprite);
        return f.isFile() ? f : null;
    }

    public int countEnemies(final HexColor color) {
        return enemiesByColor.get(color).size();
    }

    public Deck loadEnemyDeck(final HexEncounter enc) {
        final Deck deck = DeckSerializer.fromFile(new File(dir() + enc.deckPath));
        if (deck == null) {
            throw new IllegalStateException("Could not load deck " + enc.deckPath);
        }
        return deck;
    }

    /** What scouting tells about an enemy before the fight: the colours of its deck and its most valuable cards. */
    public static final class ScoutReport {
        public final List<HexColor> colors;
        public final List<String> keyCards;

        ScoutReport(final List<HexColor> colors, final List<String> keyCards) {
            this.colors = colors;
            this.keyCards = keyCards;
        }
    }

    private static final byte[] COLOR_BITS = { MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK, MagicColor.RED, MagicColor.GREEN };
    private final Map<String, ScoutReport> scoutReports = new HashMap<>();

    /** The scout report of an enemy (decks are read once and remembered). Null if its deck can't be read. */
    public synchronized ScoutReport scout(final HexEncounter enc) {
        if (enc == null || enc.deckPath == null) {
            return null;
        }
        final ScoutReport known = scoutReports.get(enc.deckPath);
        if (known != null) {
            return known;
        }
        final Deck deck;
        try {
            deck = loadEnemyDeck(enc);
        } catch (final RuntimeException e) {
            return null;
        }
        final int[] perColor = new int[COLOR_BITS.length];
        int colored = 0;
        final Map<String, Integer> values = new HashMap<>();
        for (final Map.Entry<PaperCard, Integer> entry : deck.getMain()) {
            final CardRules rules = entry.getKey().getRules();
            if (rules == null || rules.getType().isLand()) {
                continue;
            }
            for (int i = 0; i < COLOR_BITS.length; i++) {
                if (rules.getColor().hasAnyColor(COLOR_BITS[i])) {
                    perColor[i] += entry.getValue();
                    colored += entry.getValue();
                }
            }
            values.put(entry.getKey().getName(), price(entry.getKey().getName()));
        }
        // a colour counts if at least a sixth of the coloured cards have it (no splash of one card)
        final List<HexColor> colors = new ArrayList<>();
        for (int i = 0; i < COLOR_BITS.length; i++) {
            if (perColor[i] > 0 && perColor[i] * 6 >= colored) {
                colors.add(HexColor.COLORS[i]);
            }
        }
        final List<String> names = new ArrayList<>(values.keySet());
        names.sort((a, b) -> values.get(a).equals(values.get(b)) ? a.compareTo(b) : values.get(b) - values.get(a));
        final ScoutReport report = new ScoutReport(colors, names.subList(0, Math.min(3, names.size())));
        scoutReports.put(enc.deckPath, report);
        return report;
    }

    /** A random common or uncommon card of a colour (any colour if there is none), or null. */
    public String randomCardOfColor(final HexColor color, final Random rnd) {
        Map<CardRarity, List<PaperCard>> byRarity = cardsByColor.get(color);
        if (byRarity == null || byRarity.isEmpty()) {
            byRarity = cardsByColor.get(HexColor.COLORS[rnd.nextInt(HexColor.COLORS.length)]);
        }
        final List<PaperCard> pool = new ArrayList<>();
        for (final CardRarity rarity : new CardRarity[] { CardRarity.Common, CardRarity.Uncommon }) {
            final List<PaperCard> list = byRarity == null ? null : byRarity.get(rarity);
            if (list != null) {
                pool.addAll(list);
            }
        }
        return pool.isEmpty() ? null : pool.get(rnd.nextInt(pool.size())).getName();
    }

    /** The highest mana value of a creature or enchantment the enemies of New Game Plus start with in play. */
    public static final int ADVANTAGE_MAX_MANA_VALUE = 4;

    /**
     * A card the enemy starts a fight with in play (New Game Plus): equipment as the enemies of the
     * Adventure mode wear it, a creature, or an enchantment that is no Aura; colourless or of the enemy's colour.
     * Creatures and enchantments cost at most ADVANTAGE_MAX_MANA_VALUE. Null if none is found.
     */
    public forge.item.IPaperCard advantageCard(final HexColor color, final Random rnd) {
        final int kind = rnd.nextInt(3);
        if (kind == 0) {
            final List<String> equipment = new ArrayList<>();
            for (final List<String> cards : itemStartCards.values()) {
                for (final String name : cards) {
                    final forge.item.IPaperCard card = findStartCard(name);
                    final byte c = card == null ? 0 : card.getRules().getColor().getColor();
                    // colourless, or of the enemy's colour; creatures and the like no dearer than the others
                    if (card != null && !equipment.contains(name) && (c == MagicColor.COLORLESS || colorOf(c) == color)
                            && (card.getRules().getType().isArtifact() || card.getRules().getManaCost().getCMC() <= ADVANTAGE_MAX_MANA_VALUE)) {
                        equipment.add(name);
                    }
                }
            }
            Collections.sort(equipment); // a stable order, so seeds are reproducible
            if (!equipment.isEmpty()) {
                final forge.item.IPaperCard card = findStartCard(equipment.get(rnd.nextInt(equipment.size())));
                if (card != null) {
                    return card;
                }
            }
        }
        final HexShopType shop = kind == 2 ? HexShopType.ENCHANTMENT : HexShopType.CREATURE;
        final Map<HexColor, Map<CardRarity, List<PaperCard>>> shelves = cardsByShop.get(shop);
        final Map<CardRarity, List<PaperCard>> shelf = shelves == null ? null : shelves.get(color);
        final List<PaperCard> pool = new ArrayList<>();
        if (shelf != null) {
            for (final List<PaperCard> cards : shelf.values()) {
                for (final PaperCard card : cards) {
                    final CardRules rules = card.getRules();
                    if (!rules.getType().hasSubtype("Aura") && rules.getManaCost().getCMC() <= ADVANTAGE_MAX_MANA_VALUE) {
                        pool.add(card);
                    }
                }
            }
        }
        pool.sort((a, b) -> a.getName().compareTo(b.getName()));
        return pool.isEmpty() ? null : pool.get(rnd.nextInt(pool.size()));
    }

    /** A random card from the enemy's deck that is not a basic land, or null. */
    public String randomAnteCard(final HexEncounter enc, final Random rnd) {
        final List<String> names = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> entry : loadEnemyDeck(enc).getMain()) {
            if (!entry.getKey().getRules().getType().isBasicLand() && !needsCommander(entry.getKey().getRules())) {
                for (int i = 0; i < entry.getValue(); i++) {
                    names.add(entry.getKey().getName());
                }
            }
        }
        Collections.sort(names);
        return names.isEmpty() ? null : names.get(rnd.nextInt(names.size()));
    }

    // ------------------------------------------------------------------ cards

    public PaperCard getCard(final String name) {
        return FModel.getMagicDb().getCommonCards().getCard(name);
    }

    public boolean isBasicLand(final String name) {
        final PaperCard card = getCard(name);
        return card != null && card.getRules().getType().isBasicLand();
    }

    public int price(final String cardName) {
        final Integer listed = listedPrices.get(cardName);
        if (listed != null) {
            return listed;
        }
        final PaperCard card = getCard(cardName);
        if (card == null) {
            return 10;
        }
        if (card.getRules().getType().isBasicLand()) {
            return 5;
        }
        switch (card.getRarity()) {
            case Common: return 15;
            case Uncommon: return 40;
            case Rare: return 120;
            case MythicRare: return 300;
            default: return 40;
        }
    }

    /** What a shop pays for a card: a quarter of its price. */
    public int sellPrice(final String cardName) {
        return Math.max(1, price(cardName) / 4);
    }

    /**
     * The stock of a town for one theme: every card shop offers a handful of cards of its kind that
     * fit the theme, mostly in the town's colour and a few colourless ones. A theme with too few such
     * cards for a shop is topped up with ordinary cards of the town. Without a theme (null) the
     * shops sell any cards of the town.
     */
    public List<HexShopOffer> themeStock(final HexColor color, final HexTheme theme, final Random rnd) {
        return themeStock(color, Collections.emptySet(), theme, rnd);
    }

    /**
     * The same for a town near the regions of other colours (border): it sells multicoloured cards more often,
     * and mostly the pairs of its colour with those of its neighbours.
     */
    public List<HexShopOffer> themeStock(final HexColor color, final Set<HexColor> neighbours, final HexTheme theme, final Random rnd) {
        final List<HexShopOffer> offers = new ArrayList<>();
        for (final HexShopType shop : HexShopType.CARD_SHOPS) {
            final Set<String> names = new HashSet<>();
            stockShop(offers, names, shop, color, neighbours, theme, theme, rnd);
            if (theme != null && !theme.isStrict() && names.size() < CARDS_PER_SHOP) {
                stockShop(offers, names, shop, color, neighbours, null, theme, rnd); // a thin theme: fill up with what the town has
            }
        }
        return offers;
    }

    /** Out of GOLD_ROLLS draws for a shop: how many are multicoloured (in a border town more), and how many colourless. */
    static final int GOLD_ROLLS = 60;
    static final int GOLD_SHARE = 10;
    static final int GOLD_SHARE_BORDER = 15;
    static final int COLORLESS_SHARE = 10;
    static final int COLORLESS_SHARE_ARTIFACTS = 40;
    /** Of the multicoloured draws: 1 in MANY_COLOURS_ODDS is a card of three colours or more. */
    static final int MANY_COLOURS_ODDS = 5;
    /** Of the two-coloured draws in a border town: this many in 5 are a pair with a neighbour's colour. */
    static final int NEIGHBOUR_PAIR_IN_5 = 3;

    /** The shelf one draw of a shop takes its card from: the town's colour, colourless, or multicoloured. */
    private Map<CardRarity, List<PaperCard>> drawShelf(final HexShopType shop, final HexColor color, final Set<HexColor> neighbours,
            final HexTheme theme, final Random rnd) {
        final int roll = rnd.nextInt(GOLD_ROLLS);
        final int colorless = shop == HexShopType.ARTIFACT ? COLORLESS_SHARE_ARTIFACTS : COLORLESS_SHARE; // artifacts are mostly colourless
        final byte bit = bitOf(color);
        final int gold = bit == 0 ? 0 : neighbours.isEmpty() ? GOLD_SHARE : GOLD_SHARE_BORDER;
        if (roll < colorless) {
            return shelf(shop, HexColor.COLORLESS, theme);
        }
        if (roll < colorless + gold) {
            if (rnd.nextInt(MANY_COLOURS_ODDS) == 0) {
                return goldShelf(shop, GoldKind.MANY, bit, theme); // three colours or more: rare
            }
            if (!neighbours.isEmpty() && rnd.nextInt(5) < NEIGHBOUR_PAIR_IN_5) {
                final List<HexColor> list = new ArrayList<>(neighbours);
                return goldShelf(shop, GoldKind.PAIR, (byte) (bit | bitOf(list.get(rnd.nextInt(list.size())))), theme);
            }
            return goldShelf(shop, GoldKind.PAIRS, bit, theme);
        }
        return shelf(shop, color, theme);
    }

    /** Draws cards for one shop until it holds CARDS_PER_SHOP (or nothing more fits); offers are marked with the given tab theme. */
    private void stockShop(final List<HexShopOffer> offers, final Set<String> names, final HexShopType shop, final HexColor color,
            final Set<HexColor> neighbours, final HexTheme theme, final HexTheme tabTheme, final Random rnd) {
        int guard = CARDS_PER_SHOP * 40;
        while (names.size() < CARDS_PER_SHOP && guard-- > 0) {
            final int roll = rnd.nextInt(100);
            final CardRarity rarity = roll < 45 ? CardRarity.Common : roll < 78 ? CardRarity.Uncommon : roll < 96 ? CardRarity.Rare : CardRarity.MythicRare;
            final Map<CardRarity, List<PaperCard>> shelf = drawShelf(shop, color, neighbours, theme, rnd);
            List<PaperCard> list = shelf.get(rarity);
            if ((list == null || list.isEmpty()) && theme != null) {
                list = shelf.get(null); // a theme has few cards: any of its rarities will do
            }
            if (list == null || list.isEmpty()) {
                continue;
            }
            final PaperCard card = list.get(rnd.nextInt(list.size()));
            if (names.add(card.getName())) {
                final HexShopOffer offer = new HexShopOffer(shop, card.getName(), price(card.getName()));
                offer.theme = tabTheme == null ? null : tabTheme.id;
                offers.add(offer);
            }
        }
    }

    /** Theme shelves (cards of a shop and colour that fit a theme), by rarity; key null holds all rarities. */
    private final Map<String, Map<CardRarity, List<PaperCard>>> themeShelves = new HashMap<>();

    /** The cards of a shop in a colour, by rarity; only those that fit the theme if one is given. */
    private synchronized Map<CardRarity, List<PaperCard>> shelf(final HexShopType shop, final HexColor color, final HexTheme theme) {
        final Map<HexColor, Map<CardRarity, List<PaperCard>>> shelves = cardsByShop.get(shop);
        final Map<CardRarity, List<PaperCard>> plain = shelves == null ? null : shelves.get(color);
        if (plain == null) {
            return new HashMap<>();
        }
        if (theme == null) {
            return plain;
        }
        final String key = theme.id + "|" + shop + "|" + color;
        Map<CardRarity, List<PaperCard>> themed = themeShelves.get(key);
        if (themed == null) {
            themed = new HashMap<>();
            final List<PaperCard> any = new ArrayList<>();
            for (final Map.Entry<CardRarity, List<PaperCard>> e : plain.entrySet()) {
                final List<PaperCard> fitting = new ArrayList<>();
                for (final PaperCard card : e.getValue()) {
                    if (theme.fits(card.getRules())) {
                        fitting.add(card);
                    }
                }
                themed.put(e.getKey(), fitting);
                any.addAll(fitting);
            }
            themed.put(null, any);
            themeShelves.put(key, themed);
        }
        return themed;
    }

    /** Which multicoloured cards a shelf holds: one pair, any pair with a colour, or three colours or more with it. */
    enum GoldKind { PAIR, PAIRS, MANY }

    private final Map<String, Map<CardRarity, List<PaperCard>>> goldShelves = new HashMap<>();

    /** Multicoloured cards of a shop by rarity (key null: all), that have the given colours and fit the theme if one is given. */
    private synchronized Map<CardRarity, List<PaperCard>> goldShelf(final HexShopType shop, final GoldKind kind, final byte bits,
            final HexTheme theme) {
        final String key = kind + "|" + bits + "|" + shop + "|" + (theme == null ? "" : theme.id);
        Map<CardRarity, List<PaperCard>> result = goldShelves.get(key);
        if (result == null) {
            result = new HashMap<>();
            final List<PaperCard> any = new ArrayList<>();
            for (final PaperCard card : goldByShop.getOrDefault(shop, Collections.emptyList())) {
                final byte cardBits = colorBits(shop, card.getRules());
                final int colours = Integer.bitCount(cardBits);
                final boolean fits;
                switch (kind) {
                    case PAIR: fits = cardBits == bits; break;
                    case PAIRS: fits = colours == 2 && (cardBits & bits) == bits; break;
                    default: fits = colours >= 3 && (cardBits & bits) == bits; break;
                }
                if (fits && (theme == null || theme.fits(card.getRules()))) {
                    result.computeIfAbsent(card.getRarity(), k -> new ArrayList<>()).add(card);
                    any.add(card);
                }
            }
            result.put(null, any);
            goldShelves.put(key, result);
        }
        return result;
    }

    /** A theme needs at least this many cards in a town's colour to be offered there. */
    public static final int THEME_MIN_CARDS = 15;
    private final Map<HexColor, List<HexTheme>> themesByColor = new EnumMap<>(HexColor.class);

    /** The themes with enough cards in this colour to stock a town's shops (all themes if none has). */
    public synchronized List<HexTheme> themesFor(final HexColor color) {
        List<HexTheme> result = themesByColor.get(color);
        if (result == null) {
            result = new ArrayList<>();
            for (final HexTheme theme : HexTheme.all()) {
                int cards = 0;
                for (final HexShopType shop : HexShopType.CARD_SHOPS) {
                    final List<PaperCard> any = shelf(shop, color, theme).get(null);
                    cards += any == null ? 0 : any.size();
                    if (theme.isStrict() && color != HexColor.COLORLESS) { // the shops sell colourless cards too
                        final List<PaperCard> colourless = shelf(shop, HexColor.COLORLESS, theme).get(null);
                        cards += colourless == null ? 0 : colourless.size();
                    }
                    if (color != HexColor.COLORLESS) { // and multicoloured cards with the town's colour
                        for (final GoldKind kind : new GoldKind[] { GoldKind.PAIRS, GoldKind.MANY }) {
                            final List<PaperCard> gold = goldShelf(shop, kind, bitOf(color), theme).get(null);
                            cards += gold == null ? 0 : gold.size();
                        }
                    }
                }
                if (cards >= THEME_MIN_CARDS) {
                    result.add(theme);
                }
            }
            if (result.isEmpty()) {
                result.addAll(HexTheme.all());
            }
            result = java.util.Collections.unmodifiableList(result);
            themesByColor.put(color, result);
        }
        return result;
    }

    /** The relics a town's dealer offers. */
    public List<HexShopOffer> relicStock(final Random rnd) {
        final List<HexShopOffer> offers = new ArrayList<>();
        final List<HexRelic> all = new ArrayList<>(relics.values());
        final Set<String> relicNames = new HashSet<>();
        int guard = 200;
        while (relicNames.size() < Math.min(RELICS_PER_TOWN, all.size()) && guard-- > 0) {
            final HexRelic relic = all.get(rnd.nextInt(all.size()));
            if (relicNames.add(relic.name)) {
                offers.add(new HexShopOffer(HexShopType.RELIC, relic.name, relic.price));
            }
        }
        return offers;
    }

    // ------------------------------------------------------------------ relics

    /** All relics, in a stable order. */
    public List<HexRelic> allRelics() {
        final List<HexRelic> result = new ArrayList<>(relics.values());
        result.sort((a, b) -> a.name.compareTo(b.name));
        return result;
    }

    public HexRelic getRelic(final String name) {
        return relics.get(name);
    }

    public int countRelics() {
        return relics.size();
    }

    /** What a dealer pays for a relic: half of its price. */
    public int relicSellPrice(final String name) {
        final HexRelic relic = relics.get(name);
        return relic == null ? 0 : relic.price / 2;
    }

    /**
     * A weak, almost random starter deck in two colours: 17 basic lands and 23 commons
     * that cost five mana or less, creatures first.
     */
    public List<String> starterDeck(final Random rnd) {
        final HexColor first = HexColor.COLORS[rnd.nextInt(5)];
        HexColor second = HexColor.COLORS[rnd.nextInt(5)];
        while (second == first) {
            second = HexColor.COLORS[rnd.nextInt(5)];
        }
        final List<String> deck = new ArrayList<>();
        for (int i = 0; i < 9; i++) { deck.add(first.getBasicLand()); }
        for (int i = 0; i < 8; i++) { deck.add(second.getBasicLand()); }
        final Set<String> used = new HashSet<>();
        addStarterCards(deck, used, first, 8, true, rnd);
        addStarterCards(deck, used, second, 7, true, rnd);
        addStarterCards(deck, used, first, 4, false, rnd);
        addStarterCards(deck, used, second, 4, false, rnd);
        return deck;
    }

    private void addStarterCards(final List<String> deck, final Set<String> used, final HexColor color, final int count,
            final boolean creatures, final Random rnd) {
        final List<PaperCard> commons = cardsByColor.get(color).get(CardRarity.Common);
        int added = 0;
        int guard = 5000;
        while (added < count && guard-- > 0) {
            final PaperCard card = commons.get(rnd.nextInt(commons.size()));
            final CardRules rules = card.getRules();
            if (rules.getType().isCreature() != creatures || rules.getManaCost().getCMC() > 5 || !used.add(card.getName())) {
                continue;
            }
            deck.add(card.getName());
            added++;
        }
    }

    /** Builds a Forge deck from card names. */
    public Deck toDeck(final String deckName, final List<String> cardNames) {
        final Deck deck = new Deck(deckName);
        for (final String name : cardNames) {
            final PaperCard card = getCard(name);
            if (card != null) {
                deck.getMain().add(card);
            }
        }
        return deck;
    }
}
