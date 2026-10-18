package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import forge.util.Localizer;

/**
 * The state and the rules of a hex campaign: a party of one or more heads travels a hex map,
 * fights random encounters as one Multi-Headed Giant team, trades in towns, and has to beat
 * the five archmages before the final boss in the centre.
 *
 * This class only knows the campaign; it does not start matches or show anything.
 */
public class HexCampaign {
    public static final int MIN_DECK_SIZE = 40;
    public static final int MAX_HEADS = 4;
    public static final int BASIC_LAND_PRICE = 5;
    public static final int START_GOLD = 60;
    /** Every head of the party after the first adds this much to its shared life. */
    public static final int LIFE_PER_EXTRA_HEAD = 10;
    /** How many heads the enemy fights with, by the number of heads of the party (1 to 4). */
    public static final int[] ENEMY_HEADS = { 1, 2, 2, 3 };
    /** What the enemy side gets on top of the enemy's own life, by the number of heads of the party (1 to 4). */
    public static final int[] ENEMY_EXTRA_LIFE = { 0, 5, 30, 40 };
    public static final int QUESTS_PER_TOWN = 2;
    public static final int MAX_ACTIVE_QUESTS = 3;
    /** A town gets new goods and tasks after this many won fights. */
    public static final int RESTOCK_AFTER_WINS = 4;
    /** Shop themes every head picks for itself, besides the town's theme and the random one. */
    public static final int PICKED_THEMES = 2;

    public enum MoveResult { NOTHING, TOWN, ENCOUNTER, NOT_ADJACENT, LOCKED, BUSY }

    public String name;
    public long seed;
    public HexMap map;
    public int partyQ;
    public int partyR;
    public int prevQ;
    public int prevR;
    public List<HexPlayer> players = new ArrayList<>();
    public Set<HexColor> defeatedBosses = EnumSet.noneOf(HexColor.class);
    public boolean won;
    public int battlesWon;
    public int battlesLost;
    public int steps;
    /** Shared life total of the party at the start of a fight. */
    public int teamLife = 20;
    /** How far the party sees: enemies on fields up to this many steps away are shown on the map. */
    public static final int SIGHT = 2;
    /** Chance in percent that an enemy roams a field when the party sees it for the first time. */
    public int encounterChance = 30;
    /** Chance in percent, per step, that a new enemy turns up on an empty field in sight (0 = never). */
    public int encounterChanceExplored = 0;

    /** The fight the party is facing right now, if any. */
    public HexEncounter pendingEncounter;
    /** Rewards of the last won fight that have not been chosen yet. */
    public List<HexReward> pendingRewards = new ArrayList<>();
    /** Town stock by field key. */
    public Map<String, List<HexShopOffer>> shops = new HashMap<>();
    /** Number of won fights at the time a town was stocked, by field key. */
    public Map<String, Integer> stockedAt = new HashMap<>();
    /** The random shop theme of a town, by field key; it changes after every fight. */
    public Map<String, String> randomThemes = new HashMap<>();
    /** Number of fights fought when the random theme of a town was rolled, by field key. */
    public Map<String, Integer> randomThemeAt = new HashMap<>();
    /** Number of fights fought when a theme's shelves of a town were stocked, by "field key|theme id". */
    public Map<String, Integer> themeStockedAt = new HashMap<>();
    /** Tasks on the notice boards of the towns, by field key. */
    public Map<String, List<HexQuest>> questOffers = new HashMap<>();
    /** Tasks the party has taken on. */
    public List<HexQuest> quests = new ArrayList<>();
    public int questsDone;
    private int nextQuestId = 1;
    public List<String> journal = new ArrayList<>();
    private long rngCounter;

    public HexCampaign() { }

    public static HexCampaign create(final String name, final long seed, final int radius, final List<HexPlayer> players) {
        final HexCampaign c = new HexCampaign();
        c.name = name;
        c.seed = seed;
        c.map = HexMap.generate(seed, radius);
        c.partyQ = c.prevQ = c.map.startQ;
        c.partyR = c.prevR = c.map.startR;
        final HexContent content = HexContent.get();
        final Set<String> townNames = new java.util.HashSet<>();
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.BOSS) {
                t.name = content.getBossName(t.color);
            } else if (t.poi == HexPoi.TOWN) {
                String townName = townName(t, c.random());
                for (int i = 0; i < 50 && !townNames.add(townName); i++) {
                    townName = townName(t, c.random()); // every town its own name
                }
                t.name = townName;
                t.theme = c.rollTheme(t, null).id; // every town is known for something
            }
        }
        for (final HexPlayer p : players) {
            p.gold = START_GOLD;
            p.deck = content.starterDeck(c.random());
            c.players.add(p);
        }
        c.tile().explored = true;
        c.scout();
        c.log(msg("lblHexJournalStart", c.tile().name));
        return c;
    }

    private static final String[] TOWN_A = { "Alt", "Neu", "Hoch", "Nieder", "Grau", "Stein", "Wind", "Nebel", "Dorn", "Eichen", "Raben", "Silber" };
    private static final String[] TOWN_B = { "furt", "hafen", "burg", "tal", "wacht", "heim", "brück", "feld", "hain", "stein", "markt", "quell" };

    private static String townName(final HexTile t, final Random rnd) {
        return TOWN_A[rnd.nextInt(TOWN_A.length)] + TOWN_B[rnd.nextInt(TOWN_B.length)];
    }

    /** A new head joins the party (a friend connecting to a running campaign) with a starter deck. */
    public HexPlayer addPlayer(final String playerName) {
        final HexPlayer p = new HexPlayer(playerName, false);
        p.gold = START_GOLD;
        p.deck = HexContent.get().starterDeck(random());
        players.add(p);
        log(msg("lblHexJournalJoined", playerName));
        return p;
    }

    /** Can the party step onto this field right now? */
    public boolean canEnter(final HexTile target) {
        if (target == null || isBusy() || won || !isAdjacent(target)) {
            return false;
        }
        return !(target.poi == HexPoi.BOSS && target.color == HexColor.COLORLESS && !allArchmagesDefeated());
    }

    /** A fresh random generator; the sequence is fixed by the campaign seed, so a campaign replays the same way. */
    Random random() {
        return new Random(seed * 31 + (++rngCounter) * 7919);
    }

    private static String msg(final String key, final Object... args) {
        return Localizer.getInstance().getMessage(key, args);
    }

    private void log(final String text) {
        journal.add(text);
        while (journal.size() > 200) {
            journal.remove(0);
        }
    }

    public HexTile tile() {
        return map.get(partyQ, partyR);
    }

    public HexPlayer player(final String playerName) {
        for (final HexPlayer p : players) {
            if (p.name.equals(playerName)) {
                return p;
            }
        }
        return null;
    }

    public boolean allArchmagesDefeated() {
        for (final HexColor c : HexColor.COLORS) {
            if (!defeatedBosses.contains(c)) {
                return false;
            }
        }
        return true;
    }

    /** Fights and unclaimed rewards have to be dealt with before the party can travel or trade. */
    public boolean isBusy() {
        return pendingEncounter != null || !pendingRewards.isEmpty();
    }

    public boolean isAdjacent(final HexTile target) {
        return target != null && HexMap.distance(partyQ, partyR, target.q, target.r) == 1;
    }

    /** How dangerous a field is: 0 at the start, 1 at the rim of the map. */
    public double danger(final HexTile t) {
        final int fromStart = HexMap.distance(t.q, t.r, map.startQ, map.startR);
        return Math.min(1.0, fromStart / (double) Math.max(1, map.radius + 2));
    }

    /** The enemy the party would have to fight on this field (boss, wanted enemy or roaming enemy), or null. */
    public HexEncounter enemyAt(final HexTile t) {
        if (t == null) {
            return null;
        }
        if (t.poi == HexPoi.BOSS) {
            return t.cleared ? null : HexContent.get().bossEncounter(t.color);
        }
        final HexQuest quest = questAt(t.q, t.r);
        if (quest != null) {
            return HexContent.get().questEncounter(quest);
        }
        return t.enemy;
    }

    /**
     * Looks around: for every ordinary field in sight it is decided, once, whether an enemy
     * roams there; the enemy then stays on the map until the next fight or town visit
     * (see {@link #reshuffleEnemies()}).
     */
    public void scout() {
        final List<HexTile> inSight = new ArrayList<>();
        for (final HexTile t : map.tiles.values()) {
            final int far = HexMap.distance(t.q, t.r, partyQ, partyR);
            if (far >= 1 && far <= SIGHT) {
                inSight.add(t);
            }
        }
        inSight.sort((a, b) -> a.key().compareTo(b.key())); // a stable order, so seeds are reproducible
        final Random rnd = random();
        for (final HexTile t : inSight) {
            final boolean first = !t.scouted;
            t.scouted = true;
            if (t.poi != HexPoi.NONE || t.enemy != null || questAt(t.q, t.r) != null) {
                continue;
            }
            if (rnd.nextInt(100) < (first ? encounterChance : encounterChanceExplored)) {
                t.enemy = HexContent.get().randomEncounter(t.color, danger(t), rnd);
                t.enemy.q = t.q;
                t.enemy.r = t.r;
            }
        }
    }

    /**
     * All roaming enemies leave the map and what the party sees is rolled anew. Happens after
     * every fight and whenever the party enters a town. Bosses and wanted enemies stay.
     */
    public void reshuffleEnemies() {
        for (final HexTile t : map.tiles.values()) {
            t.enemy = null;
            t.scouted = false;
        }
        scout();
    }

    /** Moves the party one field. */
    public MoveResult moveTo(final int q, final int r) {
        final HexTile target = map.get(q, r);
        if (isBusy() || won) {
            return MoveResult.BUSY;
        }
        if (!isAdjacent(target)) {
            return MoveResult.NOT_ADJACENT;
        }
        final boolean finalBoss = target.poi == HexPoi.BOSS && target.color == HexColor.COLORLESS;
        if (finalBoss && !allArchmagesDefeated()) {
            return MoveResult.LOCKED;
        }
        prevQ = partyQ;
        prevR = partyR;
        partyQ = q;
        partyR = r;
        steps++;
        target.explored = true;
        if (target.poi == HexPoi.TOWN) {
            reshuffleEnemies(); // while the party rests in town, the land around changes
        } else {
            scout();
        }

        if (target.poi == HexPoi.TOWN) {
            log(msg("lblHexJournalTown", target.name));
            shop(); // stock the shelves now, so everybody at the table sees the same offers
            return MoveResult.TOWN;
        }
        final HexContent content = HexContent.get();
        final HexQuest quest = questAt(q, r);
        if (target.poi == HexPoi.BOSS && !target.cleared) {
            pendingEncounter = content.bossEncounter(target.color);
        } else if (quest != null) {
            pendingEncounter = content.questEncounter(quest); // the enemy the party was sent for
        } else if (target.enemy != null) {
            pendingEncounter = target.enemy; // the enemy everybody could see on the map
        }
        if (pendingEncounter == null) {
            return MoveResult.NOTHING;
        }
        pendingEncounter.q = q;
        pendingEncounter.r = r;
        log(msg(pendingEncounter.boss ? "lblHexJournalBoss" : "lblHexJournalEncounter", pendingEncounter.enemyName));
        return MoveResult.ENCOUNTER;
    }

    /**
     * Applies the outcome of the pending fight. A win offers every head gold or a card of the
     * enemy's deck; a loss costs every head a random card and sends the party back.
     */
    public void resolveBattle(final boolean partyWon) {
        final HexEncounter enc = pendingEncounter;
        if (enc == null) {
            return;
        }
        pendingEncounter = null;
        final HexContent content = HexContent.get();
        if (partyWon) {
            battlesWon++;
            log(msg("lblHexJournalWon", enc.enemyName));
            for (final HexPlayer p : players) {
                final HexReward reward = new HexReward();
                reward.playerName = p.name;
                reward.gold = enc.gold;
                reward.card = content.randomAnteCard(enc, random());
                pendingRewards.add(reward);
            }
            final HexQuest quest = quest(enc.questId);
            if (quest != null) {
                quests.remove(quest);
                questsDone++;
                for (final HexPlayer p : players) {
                    p.gold += quest.gold; // the bounty: the same for every head
                }
                log(msg("lblHexJournalQuestDone", quest.enemyName, String.valueOf(quest.gold)));
            }
            if (enc.boss) {
                final HexTile t = map.get(enc.q, enc.r);
                if (t != null) {
                    t.cleared = true;
                }
                defeatedBosses.add(enc.color);
                if (enc.color == HexColor.COLORLESS) {
                    won = true;
                    log(msg("lblHexJournalVictory"));
                } else if (allArchmagesDefeated()) {
                    log(msg("lblHexJournalCenterOpen"));
                }
            }
        } else {
            battlesLost++;
            log(msg("lblHexJournalLost", enc.enemyName));
            for (final HexPlayer p : players) {
                final String lost = loseRandomCard(p);
                if (lost != null) {
                    log(msg("lblHexJournalAnteLost", p.name, lost));
                }
            }
            partyQ = prevQ;
            partyR = prevR;
        }
        reshuffleEnemies();
    }

    /** Removes a random card that is not a basic land; a deck at minimum size gets a basic land in return. */
    private String loseRandomCard(final HexPlayer p) {
        final HexContent content = HexContent.get();
        final List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < p.deck.size(); i++) {
            if (!content.isBasicLand(p.deck.get(i))) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        final Random rnd = random();
        final String lost = p.deck.remove((int) candidates.get(rnd.nextInt(candidates.size())));
        if (p.deck.size() < MIN_DECK_SIZE) {
            final List<String> basics = new ArrayList<>();
            for (final String card : p.deck) {
                if (content.isBasicLand(card)) {
                    basics.add(card);
                }
            }
            p.deck.add(basics.isEmpty() ? HexColor.COLORLESS.getBasicLand() : basics.get(rnd.nextInt(basics.size())));
        }
        return lost;
    }

    public HexReward pendingReward(final String playerName) {
        for (final HexReward r : pendingRewards) {
            if (r.playerName.equals(playerName)) {
                return r;
            }
        }
        return null;
    }

    /** A head takes its reward: the card (if there is one and it wants it), otherwise the gold. */
    public void chooseReward(final String playerName, final boolean takeCard) {
        final HexReward reward = pendingReward(playerName);
        final HexPlayer p = player(playerName);
        if (reward == null || p == null) {
            return;
        }
        pendingRewards.remove(reward);
        if (takeCard && reward.card != null) {
            p.deck.add(reward.card);
            log(msg("lblHexJournalRewardCard", p.name, reward.card));
        } else {
            p.gold += reward.gold;
            log(msg("lblHexJournalRewardGold", p.name, String.valueOf(reward.gold)));
        }
    }

    // ------------------------------------------------------------------ towns

    public boolean inTown() {
        return tile().poi == HexPoi.TOWN && !isBusy();
    }

    /**
     * The stock of the town the party is in (all shops and theme tabs); created at the first visit.
     * The town's own theme and the relics are renewed now and then, the random theme and the
     * themes the heads picked after every fight.
     */
    public List<HexShopOffer> shop() {
        if (tile().poi != HexPoi.TOWN) {
            return new ArrayList<>();
        }
        final String key = tile().key();
        List<HexShopOffer> stock = shops.get(key);
        final Integer since = stockedAt.get(key);
        boolean stale = stock == null || since == null || battlesWon - since >= RESTOCK_AFTER_WINS;
        if (stock != null) {
            for (final HexShopOffer o : stock) {
                // a save from before the towns had several shops, or before the shops had themes
                stale |= o.shop == null || (o.shop != HexShopType.RELIC && o.theme == null);
            }
        }
        final HexContent content = HexContent.get();
        if (stale) {
            final HexTheme townTheme = townTheme();
            stock = content.relicStock(random());
            stock.addAll(content.themeStock(tile().color, townTheme, random()));
            shops.put(key, stock);
            stockedAt.put(key, battlesWon);
            themeStockedAt.keySet().removeIf(k -> k.startsWith(key + "|"));
            themeStockedAt.put(key + "|" + townTheme.id, fights());
            questOffers.put(key, newQuests(tile()));
        }
        // the random theme and the picked ones get fresh shelves after every fight
        final Set<String> wanted = new java.util.LinkedHashSet<>();
        wanted.add(randomTheme().id);
        for (final HexPlayer p : players) {
            for (final String id : p.themes) {
                if (HexTheme.get(id) != null) {
                    wanted.add(id);
                }
            }
        }
        wanted.remove(townTheme().id);
        for (final String id : wanted) {
            final Integer at = themeStockedAt.get(key + "|" + id);
            if (at == null || at != fights()) {
                stock.removeIf(o -> id.equals(o.theme));
                stock.addAll(content.themeStock(tile().color, HexTheme.get(id), random()));
                themeStockedAt.put(key + "|" + id, fights());
            }
        }
        return stock;
    }

    /** What one shop of the town the party is in has on offer in a theme tab. */
    public List<HexShopOffer> shop(final HexShopType type, final String theme) {
        final List<HexShopOffer> result = new ArrayList<>();
        for (final HexShopOffer o : shop()) {
            if (o.shop == type && (type == HexShopType.RELIC || java.util.Objects.equals(o.theme, theme))) {
                result.add(o);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ shop themes

    /** All fights fought so far, won or lost. */
    public int fights() {
        return battlesWon + battlesLost;
    }

    /** A theme that suits the town's colour, other than the given one. */
    private HexTheme rollTheme(final HexTile town, final String except) {
        final List<HexTheme> choices = new ArrayList<>(HexContent.get().themesFor(town.color));
        if (choices.size() > 1) {
            choices.removeIf(t -> t.id.equals(except));
        }
        return choices.get(random().nextInt(choices.size()));
    }

    /** The theme the shops of the town the party is in always offer. */
    public HexTheme townTheme() {
        final HexTile t = tile();
        HexTheme theme = HexTheme.get(t.theme);
        if (theme == null) {
            theme = rollTheme(t, null); // a save from before the shops had themes
            t.theme = theme.id;
        }
        return theme;
    }

    /** The random theme of the town the party is in; a new one after every fight. */
    public HexTheme randomTheme() {
        final String key = tile().key();
        HexTheme theme = HexTheme.get(randomThemes.get(key));
        final Integer at = randomThemeAt.get(key);
        if (theme == null || at == null || at != fights()) {
            theme = rollTheme(tile(), townTheme().id);
            randomThemes.put(key, theme.id);
            randomThemeAt.put(key, fights());
        }
        return theme;
    }

    /** The themes a head may pick in the town the party is in: those that suit the town's colour. */
    public List<HexTheme> themeChoices() {
        return HexContent.get().themesFor(tile().color);
    }

    /** May the head pick a (new) theme for this slot? Once per fight: after picking, at the next fight again. */
    public boolean mayPickTheme(final String playerName, final int slot) {
        final HexPlayer p = player(playerName);
        return p != null && slot >= 0 && slot < PICKED_THEMES && p.themesPickedAt[slot] != fights();
    }

    /** A head picks a theme for one of its two theme tabs; its shelves are stocked right away. */
    public boolean pickTheme(final String playerName, final int slot, final String themeId) {
        final HexPlayer p = player(playerName);
        final HexTheme theme = HexTheme.get(themeId);
        if (!inTown() || p == null || theme == null || !mayPickTheme(playerName, slot) || !themeChoices().contains(theme)) {
            return false;
        }
        p.themes[slot] = theme.id;
        p.themesPickedAt[slot] = fights();
        themeStockedAt.remove(tile().key() + "|" + theme.id); // a fresh assortment
        shop();
        log(msg("lblHexJournalTheme", p.name, theme.getLabel()));
        return true;
    }

    public boolean buy(final String playerName, final HexShopOffer offer) {
        final HexPlayer p = player(playerName);
        final List<HexShopOffer> stock = shop();
        if (!inTown() || p == null || !stock.contains(offer)) {
            return false;
        }
        if (offer.shop == HexShopType.RELIC) {
            return buyRelic(p, offer, stock);
        }
        if (p.gold < offer.price) {
            return false;
        }
        p.gold -= offer.price;
        p.deck.add(offer.card);
        stock.remove(offer);
        log(msg("lblHexJournalBought", p.name, offer.card, String.valueOf(offer.price)));
        return true;
    }

    /** Buys the offer for the named card (or relic) in the given shop and theme tab, if the town has one. */
    public boolean buy(final String playerName, final HexShopType type, final String theme, final String card) {
        for (final HexShopOffer offer : shop()) {
            if (offer.card.equals(card) && (type == null || offer.shop == type)
                    && (theme == null || offer.shop == HexShopType.RELIC || theme.equals(offer.theme))) {
                return buy(playerName, offer);
            }
        }
        return false;
    }

    /** Buys the offer for the named card, whichever shop sells it. */
    public boolean buy(final String playerName, final String card) {
        return buy(playerName, null, null, card);
    }

    // ------------------------------------------------------------------ relics

    /** The relic a head wears in the given slot, or null. */
    public HexRelic relicInSlot(final HexPlayer p, final String slot) {
        for (final String name : p.relics) {
            final HexRelic relic = HexContent.get().getRelic(name);
            if (relic != null && relic.slot.equals(slot)) {
                return relic;
            }
        }
        return null;
    }

    /** A relic replaces the one worn in the same slot; the dealer takes the old one in part exchange. */
    private boolean buyRelic(final HexPlayer p, final HexShopOffer offer, final List<HexShopOffer> stock) {
        final HexContent content = HexContent.get();
        final HexRelic relic = content.getRelic(offer.card);
        if (relic == null || p.relics.contains(relic.name)) {
            return false;
        }
        final HexRelic old = relicInSlot(p, relic.slot);
        final int refund = old == null ? 0 : content.relicSellPrice(old.name);
        if (p.gold + refund < offer.price) {
            return false;
        }
        if (old != null) {
            p.relics.remove(old.name);
            log(msg("lblHexJournalSold", p.name, old.name, String.valueOf(refund)));
        }
        p.gold += refund - offer.price;
        p.relics.add(relic.name);
        stock.remove(offer);
        log(msg("lblHexJournalBought", p.name, relic.name, String.valueOf(offer.price)));
        return true;
    }

    public boolean sellRelic(final String playerName, final String relicName) {
        final HexPlayer p = player(playerName);
        if (!inTown() || p == null || !p.relics.remove(relicName)) {
            return false;
        }
        final int price = HexContent.get().relicSellPrice(relicName);
        p.gold += price;
        log(msg("lblHexJournalSold", p.name, relicName, String.valueOf(price)));
        return true;
    }

    // ------------------------------------------------------------------ tasks

    public HexQuest quest(final int id) {
        for (final HexQuest quest : quests) {
            if (quest.id == id && id != 0) {
                return quest;
            }
        }
        return null;
    }

    /** The task whose enemy waits on this field, or null. */
    public HexQuest questAt(final int q, final int r) {
        for (final HexQuest quest : quests) {
            if (quest.q == q && quest.r == r) {
                return quest;
            }
        }
        return null;
    }

    /** The tasks on the notice board of the town the party is in. */
    public List<HexQuest> questOffers() {
        if (tile().poi != HexPoi.TOWN) {
            return new ArrayList<>();
        }
        shop(); // fills the board together with the shops
        final List<HexQuest> offers = questOffers.get(tile().key());
        return offers == null ? new ArrayList<>() : offers;
    }

    /** New tasks for a town: named enemies camping on ordinary fields a few steps away. */
    private List<HexQuest> newQuests(final HexTile town) {
        final List<HexQuest> result = new ArrayList<>();
        final Random rnd = random();
        final List<HexTile> fields = new ArrayList<>();
        for (final HexTile t : map.tiles.values()) {
            final int far = HexMap.distance(t.q, t.r, town.q, town.r);
            if (t.poi == HexPoi.NONE && t.enemy == null && far >= 2 && far <= 4 && questAt(t.q, t.r) == null && !isOffered(t)) {
                fields.add(t);
            }
        }
        fields.sort((a, b) -> a.key().compareTo(b.key())); // a stable order, so seeds are reproducible
        final java.util.Set<String> names = new java.util.HashSet<>();
        for (final HexQuest active : quests) {
            names.add(active.enemyName);
        }
        int guard = 50;
        while (result.size() < QUESTS_PER_TOWN && !fields.isEmpty() && guard-- > 0) {
            final HexTile t = fields.remove(rnd.nextInt(fields.size()));
            final HexQuest quest = HexContent.get().questEnemy(t.color, rnd);
            if (quest == null || !names.add(quest.enemyName)) {
                continue;
            }
            quest.id = nextQuestId++;
            quest.q = t.q;
            quest.r = t.r;
            quest.town = town.name;
            quest.gold += 10 * HexMap.distance(t.q, t.r, town.q, town.r);
            result.add(quest);
        }
        return result;
    }

    private boolean isOffered(final HexTile t) {
        for (final List<HexQuest> offers : questOffers.values()) {
            for (final HexQuest quest : offers) {
                if (quest.q == t.q && quest.r == t.r) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The party takes on a task from the board of the town it is in. */
    public boolean acceptQuest(final int id) {
        if (!inTown() || quests.size() >= MAX_ACTIVE_QUESTS) {
            return false;
        }
        final List<HexQuest> offers = questOffers();
        for (final HexQuest quest : offers) {
            if (quest.id == id) {
                offers.remove(quest);
                quests.add(quest);
                final HexTile camp = map.get(quest.q, quest.r);
                if (camp != null) {
                    camp.enemy = null; // the wanted enemy has driven off whoever roamed there
                }
                log(msg("lblHexJournalQuestTaken", quest.enemyName));
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ fights

    /**
     * Shared life of the party at the start of a fight: the base life, ten more for every head
     * after the first, and what the relics of all heads add.
     */
    public int fightLife() {
        int life = teamLife + LIFE_PER_EXTRA_HEAD * Math.max(0, players.size() - 1);
        for (final HexPlayer p : players) {
            for (final String name : p.relics) {
                final HexRelic relic = HexContent.get().getRelic(name);
                life += relic == null ? 0 : relic.life;
            }
        }
        return Math.max(1, life);
    }

    /** The number of heads an enemy brings against the party as it is now. */
    public int enemyHeads() {
        return ENEMY_HEADS[Math.max(1, Math.min(ENEMY_HEADS.length, players.size())) - 1];
    }

    /**
     * Shared life of the enemy side of a fight: the life of the enemy, ten more for every
     * follower, and what the relics of the party do to it.
     */
    public int enemyLife(final HexEncounter enc) {
        int life = enc.life + ENEMY_EXTRA_LIFE[Math.max(1, Math.min(ENEMY_EXTRA_LIFE.length, players.size())) - 1];
        for (final HexPlayer p : players) {
            for (final String name : p.relics) {
                final HexRelic relic = HexContent.get().getRelic(name);
                life += relic == null ? 0 : relic.enemyLife;
            }
        }
        return Math.max(1, life);
    }

    /**
     * Moves one copy of a card between the deck and the sideboard of a head; a deck may not
     * fall below the minimum size. Whether a fight is running is for the caller to check.
     */
    public boolean moveCard(final String playerName, final String card, final boolean toSideboard) {
        final HexPlayer p = player(playerName);
        if (p == null) {
            return false;
        }
        if (toSideboard) {
            if (p.deck.size() <= MIN_DECK_SIZE || !p.deck.remove(card)) {
                return false;
            }
            p.sideboard.add(card);
        } else {
            if (!p.sideboard.remove(card)) {
                return false;
            }
            p.deck.add(card);
        }
        return true;
    }

    public boolean buyBasicLand(final String playerName, final HexColor color) {
        final HexPlayer p = player(playerName);
        if (!inTown() || p == null || p.gold < BASIC_LAND_PRICE) {
            return false;
        }
        p.gold -= BASIC_LAND_PRICE;
        p.deck.add(color.getBasicLand());
        return true;
    }

    /** Sells one copy of a card of the deck; a deck may not fall below the minimum size. */
    public boolean sell(final String playerName, final String card) {
        return sell(playerName, card, false);
    }

    /** Sells one copy of a card of the deck or the sideboard; a deck may not fall below the minimum size. */
    public boolean sell(final String playerName, final String card, final boolean fromSideboard) {
        final HexPlayer p = player(playerName);
        if (!inTown() || p == null) {
            return false;
        }
        if (fromSideboard ? !p.sideboard.remove(card) : p.deck.size() <= MIN_DECK_SIZE || !p.deck.remove(card)) {
            return false;
        }
        final int price = HexContent.get().sellPrice(card);
        p.gold += price;
        log(msg("lblHexJournalSold", p.name, card, String.valueOf(price)));
        return true;
    }
}
