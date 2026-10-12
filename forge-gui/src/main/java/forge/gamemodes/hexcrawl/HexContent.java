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
    }

    public static final int CARDS_PER_SHOP = 5;
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

    private static HexContent instance;

    private final Map<String, Enemy> enemies = new HashMap<>();
    private final Map<HexColor, List<Enemy>> enemiesByColor = new EnumMap<>(HexColor.class);
    private final Map<HexColor, Map<CardRarity, List<PaperCard>>> cardsByColor = new EnumMap<>(HexColor.class);
    private final Map<String, Integer> listedPrices = new HashMap<>();
    private final Map<HexShopType, Map<HexColor, Map<CardRarity, List<PaperCard>>>> cardsByShop = new EnumMap<>(HexShopType.class);
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
            final HexColor color = colorOf(rules);
            if (color == null) {
                continue;
            }
            cardsByColor.get(color).computeIfAbsent(rarity, k -> new ArrayList<>()).add(card);
        }
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
        // a land belongs to the colour of the mana it makes
        final HexColor color = shop == HexShopType.LAND ? colorOf(rules.getColorIdentity().getColor()) : colorOf(rules);
        if (shop == null || color == null) {
            return;
        }
        cardsByShop.computeIfAbsent(shop, k -> new EnumMap<>(HexColor.class))
                .computeIfAbsent(color, k -> new EnumMap<>(CardRarity.class))
                .computeIfAbsent(rarity, k -> new ArrayList<>()).add(card);
    }

    private static HexColor colorOf(final CardRules rules) {
        return colorOf(rules.getColor().getColor());
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
        return enc;
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

    /** A random card from the enemy's deck that is not a basic land, or null. */
    public String randomAnteCard(final HexEncounter enc, final Random rnd) {
        final List<String> names = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> entry : loadEnemyDeck(enc).getMain()) {
            if (!entry.getKey().getRules().getType().isBasicLand()) {
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
     * The stock of a town: every card shop offers a handful of cards of its kind, mostly in the
     * town's colour and a few colourless ones; the relic dealer offers a few relics.
     */
    public List<HexShopOffer> shopStock(final HexColor color, final Random rnd) {
        final List<HexShopOffer> offers = new ArrayList<>();
        for (final HexShopType shop : HexShopType.CARD_SHOPS) {
            final Map<HexColor, Map<CardRarity, List<PaperCard>>> shelves = cardsByShop.get(shop);
            if (shelves == null) {
                continue;
            }
            final Set<String> names = new HashSet<>();
            int guard = CARDS_PER_SHOP * 40;
            while (names.size() < CARDS_PER_SHOP && guard-- > 0) {
                final int roll = rnd.nextInt(100);
                final CardRarity rarity = roll < 45 ? CardRarity.Common : roll < 78 ? CardRarity.Uncommon : roll < 96 ? CardRarity.Rare : CardRarity.MythicRare;
                // artifacts are mostly colourless anyway; the other shops stock a colourless card now and then
                final boolean colorless = shop == HexShopType.ARTIFACT ? rnd.nextInt(3) != 0 : rnd.nextInt(6) == 0;
                final Map<CardRarity, List<PaperCard>> shelf = shelves.get(colorless ? HexColor.COLORLESS : color);
                final List<PaperCard> list = shelf == null ? null : shelf.get(rarity);
                if (list == null || list.isEmpty()) {
                    continue;
                }
                final PaperCard card = list.get(rnd.nextInt(list.size()));
                if (names.add(card.getName())) {
                    offers.add(new HexShopOffer(shop, card.getName(), price(card.getName())));
                }
            }
        }
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
