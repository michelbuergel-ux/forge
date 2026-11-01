package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

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
    /** One in this many new tasks is an archenemy (at most one per notice board). */
    public static final int ARCHENEMY_ODDS = 3;
    /** An archenemy's bounty, in percent of an ordinary task's. */
    public static final int ARCHENEMY_BOUNTY_PERCENT = 150;
    /** An archenemy fights alone: this much life for every head it does without. */
    public static final int ARCHENEMY_LIFE_PER_MISSING_HEAD = 10;
    public static final int MAX_ACTIVE_QUESTS = 3;
    /** A town gets new goods and tasks after this many won fights. */
    public static final int RESTOCK_AFTER_WINS = 4;
    /** What a head pays to have all shops of a town restocked at once. */
    public static final int RESTOCK_PRICE = 50;
    /** Shop themes every head picks for itself, besides the town's theme and the random one. */
    public static final int PICKED_THEMES = 2;
    /** The gold multipliers a new campaign can be started with (starting gold, fight rewards, bounties). */
    public static final int[] GOLD_MULTIPLIERS = { 1, 2, 3, 5, 10, 20 };
    /** Every head picks a perk after this many won fights. */
    public static final int PERK_EVERY_WINS = 3;
    /** Perks offered to choose from. */
    public static final int PERK_CHOICES = 3;
    /** Cards from the enemy's deck a head may choose its reward from. */
    public static final int REWARD_CHOICES = 3;
    /** Chance in percent that an empty field holds an event when the party sees it for the first time. */
    public static final int EVENT_CHANCE = 12;
    /** Chance in percent that a new enemy far enough from the start roams toward the party. */
    public static final int ROAMING_CHANCE = 15;
    /** At most this many roaming enemies on the map. */
    public static final int MAX_ROAMERS = 3;
    /** Roaming enemies only hunt a party this close. */
    public static final int ROAM_RANGE = 4;
    /** Only the tougher enemies roam: those on fields at least this dangerous (0 to 1). */
    public static final double ROAMING_DANGER = 0.35;
    /** What every head pays to travel to another town it has been to. */
    public static final int FAST_TRAVEL_GOLD = 10;
    /** New Game Plus: the enemies' extra life per cycle after the first. */
    public static final int LIFE_PER_CYCLE = 10;
    /** New Game Plus: the advantages every new cycle rolls for the enemies. */
    public static final int ADVANTAGES_PER_CYCLE = 2;
    /** New Game Plus: more gold per cycle after the first, in percent. */
    public static final int GOLD_PERCENT_PER_CYCLE = 25;
    /** A freed town's shops sell this much cheaper for the rest of the cycle, in percent. */
    public static final int SIEGE_DISCOUNT_PERCENT = 25;
    /** The slot the bosses' trophies are worn in. */
    public static final String TROPHY_SLOT = "Head";
    /** A besieger has this much more life for every head of the party. */
    public static final int SIEGE_LIFE_PER_HEAD = 5;
    /** Chance in percent, after every won fight, that an army besieges one more town. */
    public static final int SIEGE_CHANCE_PERCENT = 17;
    /** Besieged towns a map starts with. */
    public static final int START_SIEGES = 1;
    /** A dungeon's boss is tougher by this much life per head. */
    public static final int DUNGEON_GUARDIAN_LIFE_PER_HEAD = 5;
    /** A treasure room's gold, for every head. */
    public static final int DUNGEON_TREASURE_GOLD = 100;
    /** Treasure rooms all dungeons together give in a cycle, per head, so they cannot be farmed (the bosses' chests apart). */
    public static final int DUNGEON_TREASURES_PER_HEAD = 2;
    /** A shrine's life (for the rest of the visit, even above full life), and what weakens the enemies. */
    public static final int DUNGEON_SHRINE_LIFE = 5;
    public static final int DUNGEON_WEAKEN_LIFE = 5;
    /** Between two fights of a dungeon the party catches its breath: this much life back (up to its full life). */
    public static final int DUNGEON_HEAL = 5;
    /** The gold in a dungeon's chest, for every head. */
    public static final int DUNGEON_CHEST_GOLD = 150;

    public enum MoveResult { NOTHING, TOWN, ENCOUNTER, NOT_ADJACENT, LOCKED, BUSY, DUNGEON }

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
    /** Fights the party fled from in the first turns. */
    public int battlesFled;
    /** The head whose vote decides where the party goes (the others' votes are suggestions), or null. */
    public String leader;
    /** What every head did in the last fight (shown once on every screen), or null. */
    public HexFightSummary lastFight;
    public int steps;
    /** Shared life total of the party at the start of a fight. */
    public int teamLife = 20;
    /** Multiplies the starting gold and all gold rewards (fights and bounties); 1 = normal. */
    public int goldMultiplier = 1;
    /** New Game Plus: the run of the campaign, 1 for the first; every new one makes the enemies stronger. */
    public int cycle = 1;
    /** New Game Plus: the steps of every advantage the enemies have (HexAdvantage names). */
    public Map<String, Integer> enemyAdvantages = new HashMap<>();
    /** New Game Plus: what the start of the current cycle added (HexAdvantage names), for the screen. */
    public List<String> newAdvantages = new ArrayList<>();

    // what events grant for the next fight (cleared when it is over)
    /** Extra life of the party. */
    public int boostLife;
    /** Extra cards in every head's opening hand. */
    public int boostHand;
    /** Every head starts with a basic land in play. */
    public boolean boostLand;
    /** Extra life of the enemy side. */
    public int boostEnemyLife;
    /** The gold reward is doubled. */
    public boolean boostDoubleGold;
    /** The last event the party came across (shown to everybody once), and a counter for it. */
    public String lastEvent;
    public int eventSerial;
    /** The cards the last event gave, and the head that got each (side by side). */
    public List<String> lastEventCards = new ArrayList<>();
    public List<String> lastEventOwners = new ArrayList<>();
    /** Ready check: the heads that looked at their deck since the pending fight came up. */
    public List<String> deckChecked = new ArrayList<>();
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
    /** Number of fights fought when a theme's shelves of a town were stocked, by "field key|theme id". */
    public Map<String, Integer> themeStockedAt = new HashMap<>();
    /** Tasks on the notice boards of the towns, by field key. */
    public Map<String, List<HexQuest>> questOffers = new HashMap<>();
    /** Tasks the party has taken on. */
    public List<HexQuest> quests = new ArrayList<>();
    public int questsDone;
    /** The enemies the party has beaten in this cycle, in order. */
    public List<HexDefeat> defeated = new ArrayList<>();
    /** Enemies beaten in the whole campaign. */
    public int defeatedTotal;
    /** The numbers of every finished cycle (New Game Plus). */
    public List<HexCycleRecord> cycleHistory = new ArrayList<>();
    /** The towns of this map got their sieges (false in saves from before there were sieges). */
    public boolean siegesPlaced;
    /** The dungeons of this map are placed (false in saves from before there were dungeons). */
    public boolean dungeonsPlaced;
    /** Treasure rooms opened in this cycle, in all dungeons together (at most {@link #treasureCap()}). */
    public int treasuresTaken;
    /** The last town the party freed (thanked once on every screen), and a counter for it. */
    public String freedTown;
    public String freedFrom;
    public int freedSerial;
    private int nextQuestId = 1;
    public List<String> journal = new ArrayList<>();
    private long rngCounter;

    public HexCampaign() { }

    public static HexCampaign create(final String name, final long seed, final int radius, final List<HexPlayer> players) {
        return create(name, seed, radius, players, 1);
    }

    public static HexCampaign create(final String name, final long seed, final int radius, final List<HexPlayer> players, final int goldMultiplier) {
        final HexCampaign c = new HexCampaign();
        c.name = name;
        c.goldMultiplier = Math.max(1, goldMultiplier);
        c.seed = seed;
        c.newMap(seed, radius);
        final HexContent content = HexContent.get();
        for (final HexPlayer p : players) {
            p.gold = c.gold(START_GOLD);
            p.deck = content.starterDeck(c.random());
            c.players.add(p);
        }
        c.tile().explored = true;
        c.scout();
        c.log(msg("lblHexJournalStart", c.tile().name));
        return c;
    }

    /** A new map with the party at its start: towns get names and themes, the strongholds their archmages. */
    private void newMap(final long mapSeed, final int radius) {
        map = HexMap.generate(mapSeed, radius);
        partyQ = prevQ = map.startQ;
        partyR = prevR = map.startR;
        final HexContent content = HexContent.get();
        final Set<String> townNames = new java.util.HashSet<>();
        final java.util.Map<String, Integer> partsUsed = new java.util.HashMap<>();
        for (final HexTile t : map.tiles.values()) {
            if (t.poi == HexPoi.BOSS) {
                t.name = content.getBossName(t.color);
            } else if (t.poi == HexPoi.TOWN) {
                t.name = townName(random(), townNames, partsUsed);
                t.theme = rollTheme(t, null).id; // every town is known for something
            }
        }
        siegesPlaced = false;
        placeSieges(new Random(mapSeed * 131L + 17L)); // its own random: the map's other rolls stay as they were
        dungeonsPlaced = false;
        placeDungeons(new Random(mapSeed * 131L + 29L));
    }

    // ------------------------------------------------------------------ dungeons

    /**
     * Every colour gets a dungeon, once per map: on an empty field half way out or further, never next to a
     * town or a stronghold, and on none the party has been to (saves from before there were dungeons get
     * theirs the first time they are played on). A small region without such a field takes one nearer
     * the centre, and at last one next to its stronghold (never next to a town).
     */
    void placeDungeons(final Random rnd) {
        if (dungeonsPlaced) {
            return;
        }
        dungeonsPlaced = true;
        for (final HexColor color : HexColor.COLORS) {
            final List<HexTile> fields = new ArrayList<>();
            for (int pass = 0; pass < 3 && fields.isEmpty(); pass++) {
                for (final HexTile t : map.region(color)) {
                    final int out = HexMap.distance(t.q, t.r, 0, 0);
                    final boolean far = pass == 0 ? out >= map.radius / 2 && out <= map.radius - 1 : out >= 2;
                    if (t.poi == HexPoi.NONE && !t.explored && t != tile() && questAt(t.q, t.r) == null
                            && far && farFromPlaces(t, pass < 2)) {
                        fields.add(t);
                    }
                }
            }
            fields.sort((a, b) -> a.key().compareTo(b.key())); // a stable order, so seeds are reproducible
            if (fields.isEmpty()) {
                continue;
            }
            final HexTile t = fields.get(rnd.nextInt(fields.size()));
            final HexDungeon dungeon = new HexDungeon();
            dungeon.color = color;
            HexLabyrinth.build(dungeon, t.q, t.r, rnd, cycle);
            t.poi = HexPoi.DUNGEON;
            t.dungeon = dungeon;
            t.enemy = null;
            t.event = null;
        }
    }

    /** No town or other dungeon next to this field (nor a stronghold, if asked). */
    private boolean farFromPlaces(final HexTile t, final boolean strongholds) {
        for (final HexTile other : map.tiles.values()) {
            if (other.poi != HexPoi.NONE && (strongholds || other.poi != HexPoi.BOSS) && HexMap.distance(t.q, t.r, other.q, other.r) < 2) {
                return false;
            }
        }
        return true;
    }

    /** The dungeons of the map, in a stable order. */
    public List<HexTile> dungeons() {
        final List<HexTile> result = new ArrayList<>(map.tilesWith(HexPoi.DUNGEON));
        result.sort((a, b) -> a.key().compareTo(b.key()));
        return result;
    }

    /** The dungeon of the party's field, or null. */
    public HexDungeon dungeonHere() {
        final HexTile t = tile();
        return t == null ? null : t.dungeon;
    }

    /** The party is inside the labyrinth of the dungeon on its field. */
    public boolean inLabyrinth() {
        final HexDungeon d = dungeonHere();
        return d != null && d.inside;
    }

    /** The party stands on a dungeon's field, outside, and may go in. */
    public boolean canEnterLabyrinth() {
        final HexDungeon d = dungeonHere();
        return d != null && !d.cleared && !d.inside && !isBusy() && !won;
    }

    /**
     * The party goes into the dungeon: to its entrance, with its full life; from now on its decks stay as they
     * are. A dungeon of a save from before there were labyrinths gets its labyrinth now.
     */
    public MoveResult enterLabyrinth() {
        if (!canEnterLabyrinth()) {
            return MoveResult.BUSY;
        }
        final HexDungeon d = dungeonHere();
        if (d.rooms.isEmpty()) {
            HexLabyrinth.build(d, partyQ, partyR, dungeonRandom(), cycle);
        }
        d.reset();
        d.inside = true;
        d.lifeLeft = fightLife();
        d.see(d.entrance());
        final forge.item.IPaperCard rule = d.ruleCard == null ? null : HexContent.get().findStartCard(d.ruleCard);
        log(msg("lblHexJournalDungeonEnter", rule == null ? "-" : rule.getName()));
        return MoveResult.DUNGEON;
    }

    /** May the party step into this room of its dungeon (next to its own, through an open door)? */
    public boolean canStepRoom(final int x, final int y) {
        final HexDungeon d = dungeonHere();
        return d != null && d.inside && !isBusy() && !won && d.nextToParty(d.room(x, y));
    }

    /** Treasure rooms all dungeons together give in a cycle: two for every head. */
    public int treasureCap() {
        return DUNGEON_TREASURES_PER_HEAD * Math.max(1, players.size());
    }

    /** Treasure rooms the dungeons still give in this cycle; once none are left, their chests are empty. */
    public int treasuresLeft() {
        return Math.max(0, treasureCap() - treasuresTaken);
    }

    /** The room holds a treasure the party may still take in this cycle. */
    public boolean treasureWaits(final HexRoom room) {
        return room != null && room.kind == HexRoom.Kind.TREASURE && room.full() && treasuresLeft() > 0;
    }

    /**
     * The party steps into a room of the labyrinth: it sees down the ways behind its doors; an enemy in it
     * fights the party, a treasure or a shrine without one is the party's at once.
     */
    public MoveResult stepRoom(final int x, final int y) {
        if (!canStepRoom(x, y)) {
            return MoveResult.NOT_ADJACENT;
        }
        final HexDungeon d = dungeonHere();
        d.prevX = d.x;
        d.prevY = d.y;
        d.x = x;
        d.y = y;
        steps++;
        final HexRoom room = d.here();
        d.see(room);
        if (room.guarded()) {
            deckChecked.clear();
            pendingEncounter = room.guard;
            pendingEncounter.ambush = false; // the decks are locked in here: nothing to look at first
            pendingEncounter.q = partyQ;
            pendingEncounter.r = partyR;
            log(msg(pendingEncounter.isGuardian() ? "lblHexJournalDungeonBoss" : "lblHexJournalDungeonGuard", pendingEncounter.enemyName));
            return MoveResult.ENCOUNTER;
        }
        if (room.kind == HexRoom.Kind.SHRINE && room.full()) {
            giveBuff(d, room);
        } else if (treasureWaits(room)) {
            takeTreasure(d, room);
        } else if (room.kind == HexRoom.Kind.TREASURE && room.full()) {
            log(msg("lblHexJournalTreasureSpent", String.valueOf(treasureCap())));
        }
        return MoveResult.NOTHING;
    }

    /** A treasure no enemy keeps: every head gets their share at once (shown like a reward, without a choice). */
    private void takeTreasure(final HexDungeon d, final HexRoom room) {
        final Set<String> taken = new java.util.HashSet<>();
        final Random rnd = new Random(seed * 131L + cycle * 7919L + steps * 61L);
        for (final HexPlayer p : players) {
            final HexReward reward = new HexReward();
            reward.playerName = p.name;
            openTreasure(p, reward, room.content, d.color, rnd, taken);
            pendingRewards.add(reward);
        }
        room.looted = true;
        treasuresTaken++;
    }

    /** The party may leave its dungeon: it stands at the entrance. */
    public boolean canLeaveLabyrinth() {
        final HexDungeon d = dungeonHere();
        return d != null && d.inside && !isBusy() && d.x == d.entranceX && d.y == d.entranceY;
    }

    /** The party leaves the dungeon; it starts anew (what was taken stays taken). */
    public boolean leaveLabyrinth() {
        if (!canLeaveLabyrinth()) {
            return false;
        }
        dungeonHere().reset();
        log(msg("lblHexJournalDungeonLeft"));
        return true;
    }

    /** A shrine gives its buff for the rest of the visit. */
    private void giveBuff(final HexDungeon d, final HexRoom room) {
        d.buffs.add(room.content);
        room.looted = true;
        if ("LIFE".equals(room.content)) {
            d.lifeLeft += DUNGEON_SHRINE_LIFE;
        }
        log(msg("lblHexJournalShrine" + room.content, String.valueOf(DUNGEON_SHRINE_LIFE), String.valueOf(DUNGEON_WEAKEN_LIFE)));
    }

    /** How often the shrines of the dungeon of the coming fight gave this buff (0 outside dungeons). */
    public int dungeonBuff(final String buff) {
        final HexDungeon d = pendingEncounter == null ? null : dungeonOf(pendingEncounter);
        return d == null ? 0 : d.buffCount(buff);
    }

    /** The rule card of the dungeon a fight is in (in play on the enemy's side), or null. */
    public String dungeonRuleCard(final HexEncounter enc) {
        final HexDungeon d = enc == null ? null : dungeonOf(enc);
        return d == null ? null : d.ruleCard;
    }

    /** Inside a dungeon the decks stay as they are: no cards change between deck and sideboard. */
    public boolean deckLocked() {
        return inLabyrinth();
    }

    /** The party's life at the start of the next fight: what it has left in a dungeon, else its full life. */
    public int partyStartLife() {
        final HexEncounter enc = pendingEncounter;
        final HexDungeon d = enc == null ? null : dungeonOf(enc);
        if (d != null && d.lifeLeft > 0) {
            return d.lifeLeft;
        }
        return fightLife();
    }

    /** A random of its own for dungeons placed later (old saves), so they do not change the other rolls. */
    private Random dungeonRandom() {
        return new Random(seed * 131L + cycle * 7919L + 29L + steps);
    }

    /**
     * The chest of a cleared dungeon, for one head: a very valuable card of the colour (or an artifact) that no
     * other head got, gold, and a relic for a free slot (else its worth in gold).
     */
    private void openChest(final HexPlayer p, final HexReward reward, final HexColor color, final Random rnd, final Set<String> taken) {
        treasureCard(p, reward, color, rnd, taken);
        reward.chestGold = withGoldBonus(p, gold(DUNGEON_CHEST_GOLD));
        earn(p, reward.chestGold);
        treasureRelic(p, reward, rnd);
        log(msg("lblHexJournalChest", p.name, reward.chestCard == null ? "-" : reward.chestCard,
                String.valueOf(reward.chestGold), reward.chestRelic == null ? "-" : reward.chestRelic));
    }

    /** A treasure room of a labyrinth, for one head: what its kind holds (CARD, GOLD or RELIC). */
    private void openTreasure(final HexPlayer p, final HexReward reward, final String kind, final HexColor color, final Random rnd,
            final Set<String> taken) {
        if ("CARD".equals(kind)) {
            treasureCard(p, reward, color, rnd, taken);
        } else if ("GOLD".equals(kind)) {
            reward.chestGold = withGoldBonus(p, gold(DUNGEON_TREASURE_GOLD));
            earn(p, reward.chestGold);
        } else {
            treasureRelic(p, reward, rnd);
        }
        log(msg("lblHexJournalTreasure", p.name, reward.chestCard != null ? reward.chestCard
                : reward.chestRelic != null ? reward.chestRelic : msg("lblHexGoldAmount", String.valueOf(reward.chestGold))));
    }

    /** A very valuable card of the colour (or an artifact) that no other head got, into the sideboard. */
    private void treasureCard(final HexPlayer p, final HexReward reward, final HexColor color, final Random rnd, final Set<String> taken) {
        final List<String> pool = new ArrayList<>(HexContent.get().treasureCards(color, players.size()));
        pool.removeAll(taken);
        if (!pool.isEmpty()) {
            final String card = pool.get(rnd.nextInt(pool.size()));
            taken.add(card);
            p.sideboard.add(card);
            reward.chestCard = card;
        }
    }

    /** A relic for a free slot, else its worth in gold. */
    private void treasureRelic(final HexPlayer p, final HexReward reward, final Random rnd) {
        final HexContent content = HexContent.get();
        final List<HexRelic> relics = content.ordinaryRelics(cycle);
        if (relics.isEmpty()) {
            return;
        }
        final HexRelic relic = content.upgrade(relics.get(rnd.nextInt(relics.size())), cycle, rnd);
        reward.chestRelic = relic.name;
        if (!p.relics.contains(relic.name) && relicInSlot(p, relic.slot) == null) {
            p.relics.add(relic.name);
        } else {
            reward.chestRelicGold = content.relicSellPrice(relic.name);
            earn(p, reward.chestRelicGold);
        }
    }

    // ------------------------------------------------------------------ sieges

    /**
     * A map starts with one besieged town, once per map: one the party has not been to, preferably
     * not a town near the centre, so the first steps stay open. Saves from before there were sieges
     * get theirs the first time they are played on (none if they have one already).
     */
    void placeSieges(final Random rnd) {
        if (siegesPlaced) {
            return;
        }
        siegesPlaced = true;
        final List<HexTile> towns = new ArrayList<>();
        final List<HexTile> outer = new ArrayList<>();
        for (final HexTile t : map.tilesWith(HexPoi.TOWN)) {
            if (!t.explored && t.siege == null && !t.freed && t != tile() && !(t.q == map.startQ && t.r == map.startR)) {
                towns.add(t);
                if (HexMap.distance(t.q, t.r, 0, 0) > HexMap.CENTER_TOWN_REACH) {
                    outer.add(t);
                }
            }
        }
        towns.sort((a, b) -> a.key().compareTo(b.key())); // a stable order, so seeds are reproducible
        outer.sort((a, b) -> a.key().compareTo(b.key()));
        final List<HexTile> pool = outer.isEmpty() ? towns : outer;
        for (int i = besiegedTowns().size(); i < START_SIEGES && !pool.isEmpty(); i++) {
            besiege(pool.remove(rnd.nextInt(pool.size())), rnd, false);
        }
    }

    /** Towns besieged at the same time: never more than there are heads in the party. */
    public int maxSieges() {
        return Math.max(1, players.size());
    }

    /** An enemy of the town's colour sets up camp before its gates. */
    private boolean besiege(final HexTile town, final Random rnd, final boolean late) {
        final HexEncounter enc = HexContent.get().siegeEncounter(town.color, rnd, cycle);
        if (enc == null) {
            return false;
        }
        enc.q = town.q;
        enc.r = town.r;
        enc.lateSiege = late;
        town.siege = enc;
        return true;
    }

    /** The towns besieged right now, in a stable order. */
    public List<HexTile> besiegedTowns() {
        final List<HexTile> result = new ArrayList<>();
        for (final HexTile t : map.tilesWith(HexPoi.TOWN)) {
            if (t.siege != null) {
                result.add(t);
            }
        }
        result.sort((a, b) -> a.key().compareTo(b.key()));
        return result;
    }

    /**
     * After a won fight: now and then an army besieges one more town, never one the party stands
     * in or next to, never one it freed in this cycle, and only while fewer towns are besieged
     * than there are heads in the party.
     */
    private void maybeBesiegeAnotherTown(final Random rnd) {
        if (besiegedTowns().size() >= maxSieges() || rnd.nextInt(100) >= SIEGE_CHANCE_PERCENT) {
            return;
        }
        final List<HexTile> towns = new ArrayList<>();
        for (final HexTile t : map.tilesWith(HexPoi.TOWN)) {
            if (t.siege == null && !t.freed && HexMap.distance(t.q, t.r, partyQ, partyR) >= 2) {
                towns.add(t);
            }
        }
        towns.sort((a, b) -> a.key().compareTo(b.key()));
        if (!towns.isEmpty()) {
            final HexTile town = towns.get(rnd.nextInt(towns.size()));
            if (besiege(town, rnd, true)) {
                log(msg("lblHexJournalNewSiege", town.name, town.siege.enemyName));
            }
        }
    }

    /** A random of its own for the sieges, so they do not change the other rolls of the campaign. */
    private Random siegeRandom() {
        return new Random(seed * 131L + cycle * 7919L + battlesWon * 31L + steps);
    }

    /** What a head pays in the town the party is in: a freed town sells cheaper. */
    public int townPrice(final int price) {
        final HexTile t = tile();
        return t != null && t.freed ? Math.max(1, price * (100 - SIEGE_DISCOUNT_PERCENT) / 100) : price;
    }

    // ------------------------------------------------------------------ New Game Plus

    /** The steps of an advantage the enemies have in this cycle. */
    public int advantage(final HexAdvantage a) {
        final Integer n = enemyAdvantages.get(a.name());
        return n == null ? 0 : Math.max(0, Math.min(a.getCap(), n));
    }

    /** The extra life the enemies have in this cycle. */
    public int cycleLife() {
        return LIFE_PER_CYCLE * Math.max(0, cycle - 1);
    }

    /** What the enemies of this cycle have, one line each; empty in the first run. */
    public List<String> cycleAdvantages() {
        final List<String> result = new ArrayList<>();
        if (cycle <= 1) {
            return result;
        }
        result.add(msg("lblHexAdvLife", String.valueOf(cycleLife())));
        for (final HexAdvantage a : HexAdvantage.values()) {
            if (advantage(a) > 0) {
                result.add(msg(a.getLabelKey(), String.valueOf(advantage(a))));
            }
        }
        return result;
    }

    /**
     * After the victory: the party starts again on a new map, with its decks, sideboards, gold,
     * relics and perks. The archmages and the last enemy are back, and all enemies are stronger:
     * more life, and two more advantages (rolled among those not at their cap).
     */
    public boolean startNewGamePlus() {
        if (!won || isBusy()) {
            return false; // not before everybody has taken the reward of the last fight
        }
        cycle++;
        final Random rnd = random();
        newAdvantages.clear();
        for (int i = 0; i < ADVANTAGES_PER_CYCLE; i++) {
            final List<HexAdvantage> open = new ArrayList<>();
            for (final HexAdvantage a : HexAdvantage.values()) {
                if (cycle >= a.getFromCycle() && advantage(a) < a.getCap() && !newAdvantages.contains(a.name())) {
                    open.add(a);
                }
            }
            if (open.isEmpty()) {
                break; // everything at its cap: from now on only the life grows
            }
            final HexAdvantage a = open.get(rnd.nextInt(open.size()));
            enemyAdvantages.put(a.name(), advantage(a) + 1);
            newAdvantages.add(a.name());
        }

        final HexCycleRecord record = new HexCycleRecord();
        record.cycle = cycle - 1;
        record.battlesWon = cycleBattles(HexBattle.Result.WON);
        record.battlesLost = cycleBattles(HexBattle.Result.LOST);
        record.battlesFled = cycleBattles(HexBattle.Result.FLED);
        record.questsDone = cycleQuests();
        record.defeated = defeated.size();
        cycleHistory.add(record);
        defeated.clear();
        for (final HexPlayer p : players) {
            p.stats = new HexHeadStats();
        }

        won = false;
        defeatedBosses.clear();
        treasuresTaken = 0;
        pendingEncounter = null;
        pendingRewards.clear();
        shops.clear();
        stockedAt.clear();
        themeStockedAt.clear();
        questOffers.clear();
        quests.clear();
        boostLife = 0;
        boostHand = 0;
        boostLand = false;
        boostEnemyLife = 0;
        boostDoubleGold = false;
        lastEvent = null;
        lastEventCards.clear();
        lastEventOwners.clear();
        deckChecked.clear();
        for (final HexPlayer p : players) {
            p.themes = new String[PICKED_THEMES]; // the towns of the new map have other colours
        }
        // a party that grew meanwhile gets a bigger map; a map never shrinks
        newMap(seed * 31L + cycle * 7919L, Math.max(map.radius, HexMap.radiusFor(players.size())));
        tile().explored = true;
        scout();
        final List<String> added = new ArrayList<>();
        for (final String name : newAdvantages) {
            final HexAdvantage a = HexAdvantage.valueOf(name);
            added.add(msg(a.getLabelKey(), String.valueOf(advantage(a))));
        }
        log(msg("lblHexJournalNewGamePlus", String.valueOf(cycle - 1), String.valueOf(cycleLife()),
                added.isEmpty() ? "–" : String.join(", ", added)));
        log(msg("lblHexJournalStart", tile().name));
        return true;
    }

    private static final String[] TOWN_A = { "Alt", "Neu", "Hoch", "Nieder", "Grau", "Stein", "Wind", "Nebel", "Dorn", "Eichen", "Raben", "Silber",
        "Rosen", "Falken", "Wolfs", "Kupfer", "Gold", "Schatten", "Tannen", "Mond" };
    private static final String[] TOWN_B = { "furt", "hafen", "burg", "tal", "wacht", "heim", "brück", "feld", "hain", "stein", "markt", "quell",
        "au", "dorf", "eck", "rode", "kirch", "berg" };

    /**
     * A name no other town of the map has, made of parts the other towns use as little as possible
     * (not five towns called Eichen-something).
     */
    private static String townName(final Random rnd, final Set<String> taken, final java.util.Map<String, Integer> partsUsed) {
        String best = null;
        String bestA = null;
        String bestB = null;
        int bestUse = Integer.MAX_VALUE;
        for (int i = 0; i < 60; i++) {
            final String a = TOWN_A[rnd.nextInt(TOWN_A.length)];
            final String b = TOWN_B[rnd.nextInt(TOWN_B.length)];
            final int use = partsUsed.getOrDefault(a, 0) + partsUsed.getOrDefault("-" + b, 0);
            if (!taken.contains(a + b) && use < bestUse) {
                best = a + b;
                bestA = a;
                bestB = b;
                bestUse = use;
            }
        }
        if (best == null) { // cannot really happen: far more names than towns
            best = TOWN_A[rnd.nextInt(TOWN_A.length)] + TOWN_B[rnd.nextInt(TOWN_B.length)] + " " + (taken.size() + 1);
        } else {
            partsUsed.merge(bestA, 1, Integer::sum);
            partsUsed.merge("-" + bestB, 1, Integer::sum);
        }
        taken.add(best);
        return best;
    }

    /** A new head joins the party (a friend connecting to a running campaign) with a starter deck. */
    public HexPlayer addPlayer(final String playerName) {
        final HexPlayer p = new HexPlayer(playerName, false);
        p.gold = gold(START_GOLD);
        p.deck = HexContent.get().starterDeck(random());
        players.add(p);
        log(msg("lblHexJournalJoined", playerName));
        offerPerks();
        return p;
    }

    /** Can the party step onto this field right now? */
    public boolean canEnter(final HexTile target) {
        if (target != null && target == tile()) {
            return canEnterLabyrinth(); // standing before a dungeon: in
        }
        if (target == null || isBusy() || won || !isAdjacent(target) || inLabyrinth()) {
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

    /** The enemy the party would have to fight on this field (boss, besieger, wanted enemy or roaming enemy), or null. */
    public HexEncounter enemyAt(final HexTile t) {
        if (t == null) {
            return null;
        }
        if (t.poi == HexPoi.TOWN) {
            return t.siege;
        }
        if (t.poi == HexPoi.BOSS) {
            return t.cleared ? null : HexContent.get().bossEncounter(t.color);
        }
        if (t.poi == HexPoi.DUNGEON) {
            return null; // its enemies wait inside
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
            if (t.event != null) {
                continue;
            }
            if (rnd.nextInt(100) < (first ? encounterChance : encounterChanceExplored)) {
                t.enemy = HexContent.get().randomEncounter(t.color, danger(t), rnd);
                t.enemy.q = t.q;
                t.enemy.r = t.r;
                // a few of the tougher ones hunt the party
                if (danger(t) >= ROAMING_DANGER && roamers().size() < MAX_ROAMERS && rnd.nextInt(100) < ROAMING_CHANCE) {
                    t.enemy.roaming = true;
                }
            } else if (first && rnd.nextInt(100) < EVENT_CHANCE) {
                t.event = HexEvent.values()[rnd.nextInt(HexEvent.values().length)].name();
            }
        }
    }

    /** The fields with an enemy that hunts the party, in a stable order. */
    public List<HexTile> roamers() {
        final List<HexTile> result = new ArrayList<>();
        for (final HexTile t : map.tiles.values()) {
            if (t.enemy != null && t.enemy.roaming) {
                result.add(t);
            }
        }
        result.sort((a, b) -> a.key().compareTo(b.key()));
        return result;
    }

    /**
     * After every step of the party: each roaming enemy close enough steps one field toward it,
     * never onto a town, a stronghold, a task's field, an event or another enemy. Returns the one
     * that reached the party's field (it catches the party), or null.
     */
    HexEncounter moveRoamers() {
        final HexTile party = tile();
        if (party.poi != HexPoi.NONE) {
            return null; // towns and strongholds are safe from them
        }
        HexEncounter caught = null;
        for (final HexTile from : roamers()) {
            final int far = HexMap.distance(from.q, from.r, partyQ, partyR);
            if (far == 0 || far > ROAM_RANGE || from.enemy == null) {
                continue;
            }
            HexTile to = null;
            final List<HexTile> next = new ArrayList<>(map.neighbors(from));
            next.sort((a, b) -> a.key().compareTo(b.key()));
            for (final HexTile t : next) {
                final boolean isParty = t == party;
                final boolean free = t.poi == HexPoi.NONE && t.enemy == null && t.event == null && questAt(t.q, t.r) == null;
                if (HexMap.distance(t.q, t.r, partyQ, partyR) == far - 1 && (isParty ? caught == null && party.enemy == null : free)) {
                    to = t;
                    break;
                }
            }
            if (to == null) {
                continue; // nowhere to go this time
            }
            to.enemy = from.enemy;
            from.enemy = null;
            to.enemy.q = to.q;
            to.enemy.r = to.r;
            to.scouted = true;
            if (to == party) {
                caught = to.enemy;
                caught.ambush = true; // no time to choose: but a moment to look at the decks
                log(msg("lblHexJournalCaught", caught.enemyName));
            }
        }
        return caught;
    }

    /**
     * All roaming enemies leave the map and what the party sees is rolled anew. Happens after
     * every fight and whenever the party enters a town. Bosses and wanted enemies stay.
     */
    public void reshuffleEnemies() {
        for (final HexTile t : map.tiles.values()) {
            t.enemy = null;
            t.event = null;
            t.scouted = false;
        }
        scout();
    }

    // ------------------------------------------------------------------ marching

    /**
     * The shortest way from the party to a field (the fields to step on, the goal last), or null.
     * Strongholds that are not cleared and the locked centre are only allowed as the goal.
     */
    public List<HexTile> route(final HexTile goal) {
        if (goal == null || goal == tile()) {
            return null;
        }
        final Map<HexTile, HexTile> from = new HashMap<>();
        final java.util.ArrayDeque<HexTile> todo = new java.util.ArrayDeque<>();
        from.put(tile(), tile());
        todo.add(tile());
        while (!todo.isEmpty()) {
            final HexTile t = todo.poll();
            if (t == goal) {
                break;
            }
            final List<HexTile> next = new ArrayList<>(map.neighbors(t));
            next.sort((a, b) -> a.key().compareTo(b.key())); // the same way on every computer
            for (final HexTile n : next) {
                if (from.containsKey(n)) {
                    continue;
                }
                final boolean blocked = n.poi == HexPoi.BOSS && !n.cleared
                        || n.poi == HexPoi.DUNGEON && n.dungeon != null && !n.dungeon.cleared; // marching never stumbles into one
                if (blocked && n != goal) {
                    continue;
                }
                from.put(n, t);
                todo.add(n);
            }
        }
        if (!from.containsKey(goal)) {
            return null;
        }
        final List<HexTile> path = new ArrayList<>();
        for (HexTile t = goal; t != tile(); t = from.get(t)) {
            path.add(0, t);
        }
        return path;
    }

    /** May the party set out for this far field (one it has seen, not next to it, not a fast-travel town)? */
    public boolean canRoute(final HexTile goal) {
        if (goal == null || isBusy() || won || inLabyrinth() || goal == tile() || isAdjacent(goal) || !(goal.explored || goal.scouted)) {
            return false;
        }
        if (canFastTravel(goal)) {
            return false; // that is fast travel
        }
        if (goal.poi == HexPoi.BOSS && goal.color == HexColor.COLORLESS && !allArchmagesDefeated()) {
            return false;
        }
        return route(goal) != null;
    }

    /**
     * The party walks toward a far field, one step at a time, and stops on the way at the first
     * town, enemy, event or hunter that catches it. Returns the result of the last step.
     */
    public MoveResult march(final HexTile goal) {
        final List<HexTile> path = route(goal);
        if (path == null) {
            return MoveResult.NOT_ADJACENT;
        }
        MoveResult result = MoveResult.NOTHING;
        int walked = 0;
        for (final HexTile t : path) {
            final int events = eventSerial;
            result = moveTo(t.q, t.r);
            if (result == MoveResult.BUSY || result == MoveResult.LOCKED || result == MoveResult.NOT_ADJACENT) {
                break;
            }
            walked++;
            if (result != MoveResult.NOTHING || eventSerial != events || pendingEncounter != null || isBusy()) {
                break;
            }
        }
        if (walked > 0) {
            log(msg("lblHexJournalMarch", goal.name != null ? goal.name : msg(goal.color.getLabelKey()), String.valueOf(walked)));
        }
        return result;
    }

    /** The journal notes a vote for fast travel, so everybody sees where somebody wants to go. */
    public void noteTravelVote(final String head, final HexTile town) {
        if (town != null && town.name != null) {
            log(msg("lblHexJournalVoteTravel", head, town.name, String.valueOf(FAST_TRAVEL_GOLD)));
        }
    }

    /** Moves the party one field. */
    public MoveResult moveTo(final int q, final int r) {
        final HexTile target = map.get(q, r);
        if (isBusy() || won || inLabyrinth()) {
            return MoveResult.BUSY;
        }
        if (!isAdjacent(target)) {
            return MoveResult.NOT_ADJACENT;
        }
        final boolean finalBoss = target.poi == HexPoi.BOSS && target.color == HexColor.COLORLESS;
        if (finalBoss && !allArchmagesDefeated()) {
            return MoveResult.LOCKED;
        }
        placeSieges(siegeRandom()); // a save from before there were sieges gets them now
        placeDungeons(dungeonRandom()); // the same for dungeons
        prevQ = partyQ;
        prevR = partyR;
        partyQ = q;
        partyR = r;
        steps++;
        target.explored = true;
        final boolean besieged = target.poi == HexPoi.TOWN && target.siege != null;
        if (target.poi == HexPoi.TOWN && !besieged) {
            reshuffleEnemies(); // while the party rests in town, the land around changes
        } else {
            scout();
            moveRoamers(); // the hunters close in (one that reaches the party fights it below)
        }

        if (besieged) {
            // the gates stay shut until the besieger is beaten
            deckChecked.clear();
            pendingEncounter = target.siege;
            pendingEncounter.q = q;
            pendingEncounter.r = r;
            log(msg("lblHexJournalBesieged", target.name, pendingEncounter.enemyName));
            return MoveResult.ENCOUNTER;
        }
        if (target.poi == HexPoi.TOWN) {
            log(msg("lblHexJournalTown", target.name));
            shop(); // stock the shelves now, so everybody at the table sees the same offers
            return MoveResult.TOWN;
        }
        if (target.poi == HexPoi.DUNGEON) {
            if (target.dungeon == null || target.dungeon.cleared) {
                return MoveResult.NOTHING;
            }
            return enterLabyrinth(); // into its labyrinth, at the entrance
        }
        final HexContent content = HexContent.get();
        final HexQuest quest = questAt(q, r);
        if (target.event != null && quest == null && target.poi == HexPoi.NONE && target.enemy == null) {
            triggerEvent(target);
            return MoveResult.NOTHING;
        }
        deckChecked.clear();
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
        if (pendingEncounter.boss) {
            // one line for everybody, chosen now (a guest shows the host's choice)
            pendingEncounter.intro = HexContent.get().bossIntro(pendingEncounter, random());
        }
        log(msg(pendingEncounter.boss ? "lblHexJournalBoss" : "lblHexJournalEncounter", pendingEncounter.enemyName));
        return MoveResult.ENCOUNTER;
    }

    /** Gold a head gets (rewards, bounties, finds, sales): counted in its statistics. Gifts of other heads are not. */
    private static void earn(final HexPlayer p, final int amount) {
        p.gold += amount;
        tally(p, s -> s.goldEarned += amount);
    }

    /** Gold a head pays (shops, travel, fleeing): counted in its statistics. */
    private static void spend(final HexPlayer p, final int amount) {
        p.gold -= amount;
        tally(p, s -> s.goldSpent += amount);
    }

    /** Counts something in the head's statistics of this cycle and of the whole campaign. */
    private static void tally(final HexPlayer p, final Consumer<HexHeadStats> count) {
        count.accept(p.stats);
        count.accept(p.totalStats);
    }

    /** Fights won, lost or fled in this cycle. */
    public int cycleBattles(final HexBattle.Result result) {
        int total = result == HexBattle.Result.WON ? battlesWon : result == HexBattle.Result.LOST ? battlesLost : battlesFled;
        for (final HexCycleRecord r : cycleHistory) {
            total -= result == HexBattle.Result.WON ? r.battlesWon : result == HexBattle.Result.LOST ? r.battlesLost : r.battlesFled;
        }
        return Math.max(0, total);
    }

    /** Tasks done in this cycle. */
    public int cycleQuests() {
        int total = questsDone;
        for (final HexCycleRecord r : cycleHistory) {
            total -= r.questsDone;
        }
        return Math.max(0, total);
    }

    /** Applies a won or lost fight (see {@link #resolveBattle(HexBattle.Result)}). */
    public void resolveBattle(final boolean partyWon) {
        resolveBattle(partyWon ? HexBattle.Result.WON : HexBattle.Result.LOST);
    }

    /** Keeps what every head did in the fight, and names the best of them in the journal. */
    public void recordFight(final HexFightSummary summary) {
        if (summary == null) {
            return;
        }
        summary.serial = lastFight == null ? 1 : lastFight.serial + 1;
        lastFight = summary;
        for (final HexFightSummary.Head h : summary.heads) {
            final HexPlayer p = player(h.name);
            if (p != null) {
                final boolean mvp = h.name.equals(summary.mvp);
                tally(p, s -> s.add(h, summary.enemyName, mvp));
            }
        }
        if (summary.mvp != null && players.size() > 1) {
            final HexFightSummary.Head h = summary.head(summary.mvp);
            log(h.topCard == null
                    ? msg("lblHexJournalMvp", h.name, String.valueOf(h.damage))
                    : msg("lblHexJournalMvpCard", h.name, String.valueOf(h.damage), h.topCard));
        }
    }

    /**
     * Applies the outcome of the pending fight. A win offers every head gold or a card of the
     * enemy's deck; a loss costs every head a random card and sends the party back; fleeing in the
     * first turns costs every head a little gold and sends the party back as well.
     */
    public void resolveBattle(final HexBattle.Result result) {
        final HexEncounter enc = pendingEncounter;
        if (enc == null) {
            return;
        }
        pendingEncounter = null;
        final HexContent content = HexContent.get();
        if (result == HexBattle.Result.FLED) {
            battlesFled++;
            final int price = gold(HexFight.FLEE_GOLD);
            for (final HexPlayer p : players) {
                spend(p, Math.min(p.gold, price));
            }
            log(msg("lblHexJournalFled", enc.enemyName, String.valueOf(price)));
            final HexDungeon fledFrom = dungeonOf(enc);
            if (fledFrom != null && fledFrom.inside) {
                fledFrom.x = fledFrom.prevX; // back to the room the party came from: still inside
                fledFrom.y = fledFrom.prevY;
            } else {
                partyQ = prevQ;
                partyR = prevR;
            }
        } else if (result == HexBattle.Result.WON) {
            battlesWon++;
            defeated.add(new HexDefeat(enc.enemyName, HexDefeat.kindOf(enc), enc.color, cycle,
                    lastFight != null && enc.enemyName.equals(lastFight.enemyName) ? lastFight.turns : 0));
            defeatedTotal++;
            log(msg("lblHexJournalWon", enc.enemyName));
            final HexTile besieged = enc.siege ? map.get(enc.q, enc.r) : null;
            if (besieged != null && besieged.siege != null) {
                // the town is free: its people thank the party, and its shops sell cheaper
                besieged.siege = null;
                besieged.freed = true;
                freedTown = besieged.name;
                freedFrom = enc.enemyName;
                freedSerial++;
                log(msg("lblHexJournalFreed", besieged.name, String.valueOf(SIEGE_DISCOUNT_PERCENT)));
            } else {
                maybeBesiegeAnotherTown(siegeRandom());
            }
            final HexDungeon dungeon = dungeonOf(enc);
            final HexRoom room = dungeon == null ? null : dungeon.room(enc.roomX, enc.roomY);
            if (room != null) {
                // a fight in a dungeon won: on with the life the party has left (and a breath)
                room.beaten = true;
                if (dungeon.inside) {
                    dungeon.see(dungeon.here()); // the enemy no longer blocks the view
                }
                final int left = lastFight != null && lastFight.partyLife > 0 ? lastFight.partyLife : fightLife();
                dungeon.lifeLeft = Math.min(fightLife() + DUNGEON_SHRINE_LIFE * dungeon.buffCount("LIFE"), left + DUNGEON_HEAL);
                if (enc.isGuardian()) {
                    dungeon.cleared = true;
                    dungeon.reset(); // the party is out again, on the dungeon's field
                    log(msg("lblHexJournalDungeonCleared", enc.enemyName));
                }
            }
            final boolean treasure = treasureWaits(room);
            if (room != null && room.kind == HexRoom.Kind.TREASURE && room.full() && !treasure) {
                log(msg("lblHexJournalTreasureSpent", String.valueOf(treasureCap())));
            }
            final Set<String> treasures = new java.util.HashSet<>();
            final Random chestRandom = new Random(seed * 131L + cycle * 7919L + battlesWon * 53L);
            for (final HexPlayer p : players) {
                final HexReward reward = new HexReward();
                reward.playerName = p.name;
                reward.gold = withGoldBonus(p, gold(enc.gold) * (boostDoubleGold ? 2 : 1));
                final Random rnd = random();
                for (int i = 0; i < REWARD_CHOICES * 4 && reward.cards.size() < REWARD_CHOICES; i++) {
                    final String card = content.randomAnteCard(enc, rnd);
                    if (card == null) {
                        break;
                    }
                    if (!reward.cards.contains(card)) {
                        reward.cards.add(card);
                    }
                }
                if (enc.boss) {
                    reward.trophy = awardTrophy(p, enc.color);
                }
                if (room != null && enc.isGuardian()) {
                    openChest(p, reward, dungeon.color, chestRandom, treasures);
                } else if (treasure) {
                    openTreasure(p, reward, room.content, dungeon.color, chestRandom, treasures);
                }
                pendingRewards.add(reward);
            }
            if (treasure) {
                room.looted = true;
                treasuresTaken++;
            } else if (room != null && room.kind == HexRoom.Kind.SHRINE && room.full()) {
                giveBuff(dungeon, room); // the shrine its enemy kept
            }
            final HexQuest quest = quest(enc.questId);
            if (quest != null) {
                quests.remove(quest);
                questsDone++;
                for (final HexPlayer p : players) {
                    earn(p, withGoldBonus(p, gold(quest.gold))); // the bounty: the same for every head (plus perks)
                }
                log(msg("lblHexJournalQuestDone", quest.enemyName, String.valueOf(gold(quest.gold))));
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
            if (enc.boss) {
                log(msg("lblHexJournalBossInsult", enc.enemyName, HexContent.get().bossInsult(enc, random())));
            }
            for (final HexPlayer p : players) {
                final String lost = loseRandomCard(p);
                if (lost != null) {
                    log(msg("lblHexJournalAnteLost", p.name, lost));
                }
            }
            final HexDungeon lostIn = dungeonOf(enc);
            if (lostIn != null && lostIn.inside) {
                lostIn.reset(); // thrown out: the dungeon starts anew
                log(msg("lblHexJournalDungeonOut"));
            }
            partyQ = prevQ;
            partyR = prevR;
        }
        // what events promised for this fight is used up
        boostLife = 0;
        boostHand = 0;
        boostLand = false;
        boostEnemyLife = 0;
        boostDoubleGold = false;
        deckChecked.clear();
        reshuffleEnemies();
        if (inTownField()) {
            shop(); // a freed town: stock the shelves now, so everybody at the table sees the same offers
        }
        offerPerks();
    }

    /** The dungeon a fight belongs to, or null. */
    private HexDungeon dungeonOf(final HexEncounter enc) {
        final HexTile t = enc.dungeonStage <= 0 ? null : map.get(enc.q, enc.r);
        return t == null ? null : t.dungeon;
    }

    /** The party stands on a town's field (busy or not). */
    private boolean inTownField() {
        final HexTile t = tile();
        return t != null && t.poi == HexPoi.TOWN && t.siege == null;
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

    /** A head takes its reward: the first card on offer (if any and it wants one), otherwise the gold. */
    public void chooseReward(final String playerName, final boolean takeCard) {
        chooseReward(playerName, takeCard ? 0 : -1);
    }

    /** A head takes its reward: the card with this index among those on offer, or the gold (index -1). */
    public void chooseReward(final String playerName, final int cardIndex) {
        final HexReward reward = pendingReward(playerName);
        final HexPlayer p = player(playerName);
        if (reward == null || p == null) {
            return;
        }
        pendingRewards.remove(reward);
        final List<String> options = reward.options();
        if (cardIndex >= 0 && cardIndex < options.size()) {
            p.deck.add(options.get(cardIndex));
            log(msg("lblHexJournalRewardCard", p.name, options.get(cardIndex)));
        } else {
            earn(p, reward.gold);
            if (reward.gold > 0) { // a treasure found without a fight has none
                log(msg("lblHexJournalRewardGold", p.name, String.valueOf(reward.gold)));
            }
        }
    }

    /** An AI head takes the most valuable card on offer. */
    public void chooseRewardForAi(final String playerName) {
        final HexReward reward = pendingReward(playerName);
        if (reward == null) {
            return;
        }
        final List<String> options = reward.options();
        int best = -1;
        for (int i = 0; i < options.size(); i++) {
            if (best < 0 || HexContent.get().price(options.get(i)) > HexContent.get().price(options.get(best))) {
                best = i;
            }
        }
        chooseReward(playerName, best);
    }

    // ------------------------------------------------------------------ perks

    /** How many perks the head may still choose (one every few won fights). */
    public int perkPicksDue(final HexPlayer p) {
        return p == null ? 0 : Math.max(0, battlesWon / PERK_EVERY_WINS - p.perks.size());
    }

    /** Gives every head that has a perk due a choice of perks (if it has none to choose from yet). */
    public void offerPerks() {
        for (final HexPlayer p : players) {
            if (perkPicksDue(p) <= 0 || !p.perkOffer.isEmpty()) {
                continue;
            }
            final List<HexPerk> open = new ArrayList<>();
            for (final HexPerk perk : HexPerk.values()) {
                if (perk.count(p) < perk.maxStacks) {
                    open.add(perk);
                }
            }
            final Random rnd = random();
            while (!open.isEmpty() && p.perkOffer.size() < PERK_CHOICES) {
                p.perkOffer.add(open.remove(rnd.nextInt(open.size())).name());
            }
        }
    }

    /** A head takes one of the perks offered to it. */
    public boolean choosePerk(final String playerName, final String perkName) {
        final HexPlayer p = player(playerName);
        final HexPerk perk = HexPerk.of(perkName);
        if (p == null || perk == null || !p.perkOffer.contains(perk.name()) || perkPicksDue(p) <= 0) {
            return false;
        }
        p.perks.add(perk.name());
        p.perkOffer.clear();
        log(msg("lblHexJournalPerk", p.name, perk.getLabel()));
        offerPerks(); // there may be another one due
        return true;
    }

    /** An AI head takes the first perk offered. */
    public void choosePerkForAi(final String playerName) {
        final HexPlayer p = player(playerName);
        if (p != null && !p.perkOffer.isEmpty()) {
            choosePerk(playerName, p.perkOffer.get(0));
        }
    }

    /** A gold reward of a head with its gold perks. */
    public int withGoldBonus(final HexPlayer p, final int gold) {
        return gold * (100 + HexPerk.GOLD_BONUS_PERCENT * HexPerk.GOLD_BONUS.count(p)) / 100;
    }

    /** What a head pays for an offer in the shops, with its discount perks (and less in a freed town). */
    public int priceFor(final HexPlayer p, final int price) {
        return townPrice(Math.max(1, price * (100 - HexPerk.DISCOUNT_PERCENT * HexPerk.DISCOUNT.count(p)) / 100));
    }

    /** What a shop pays a head for a card, with its sell perks. */
    public int sellPriceFor(final HexPlayer p, final String card) {
        return HexContent.get().sellPrice(card) * (100 + HexPerk.SELL_BONUS_PERCENT * HexPerk.SELL_BONUS.count(p)) / 100;
    }

    // ------------------------------------------------------------------ events

    /** The party steps onto a field with an event: it happens, and the field is empty afterwards. */
    private void triggerEvent(final HexTile t) {
        final HexEvent event = HexEvent.of(t.event);
        t.event = null;
        if (event == null) {
            return;
        }
        final StringBuilder text = new StringBuilder(event.getLabel()).append(": ").append(event.getDescription());
        lastEventCards.clear();
        lastEventOwners.clear();
        switch (event) {
            case SHRINE:
                boostLife += HexEvent.SHRINE_LIFE;
                break;
            case CHEST:
                for (final HexPlayer p : players) {
                    earn(p, gold(HexEvent.CHEST_GOLD));
                }
                text.append(' ').append(msg("lblHexEventGold", String.valueOf(gold(HexEvent.CHEST_GOLD))));
                break;
            case CAMP:
                final Random rnd = random();
                for (final HexPlayer p : players) {
                    final String card = HexContent.get().randomCardOfColor(t.color, rnd);
                    if (card != null) {
                        p.sideboard.add(card);
                        lastEventCards.add(card);
                        lastEventOwners.add(p.name);
                        text.append('\n').append(msg("lblHexEventFound", p.name, card));
                    }
                }
                break;
            case BLESSING:
                boostHand = Math.max(boostHand, 1);
                break;
            case LEY_LINE:
                boostLand = true;
                break;
            case ALTAR:
                boostEnemyLife += HexEvent.ALTAR_ENEMY_LIFE;
                boostDoubleGold = true;
                break;
            case RUINED_SHRINE:
            case CARAVAN:
                // every head decides for itself; companions leave it
                for (final HexPlayer p : players) {
                    if (!p.ai) {
                        p.eventOffer = event.name();
                        p.eventColor = t.color;
                    }
                }
                text.append('\n').append(msg("lblHexEventChoose"));
                break;
            default:
                break;
        }
        lastEvent = text.toString();
        eventSerial++;
        log(msg("lblHexJournalEvent", event.getLabel()));
    }

    /**
     * A head takes up the offer of a ruined shrine (a relic for a free slot, or gold if all are
     * taken) or a caravan (two cards of the field's colour, into the sideboard) for one of its
     * cards that is not a basic land. A deck may not fall below the minimum size.
     */
    public boolean chooseEvent(final String playerName, final String card, final boolean fromSideboard) {
        final HexPlayer p = player(playerName);
        final HexEvent event = p == null ? null : HexEvent.of(p.eventOffer);
        final HexContent content = HexContent.get();
        if (event == null || card == null || content.isBasicLand(card)) {
            return false;
        }
        if (fromSideboard ? !p.sideboard.contains(card) : p.deck.size() <= MIN_DECK_SIZE || !p.deck.contains(card)) {
            return false;
        }
        (fromSideboard ? p.sideboard : p.deck).remove(card);
        final Random rnd = random();
        // the trade, shown on the head's screen
        p.giftGiven = card;
        p.gifts = new ArrayList<>();
        p.giftRelic = null;
        p.giftGold = 0;
        if (event == HexEvent.RUINED_SHRINE) {
            final List<HexRelic> free = new ArrayList<>();
            for (final HexRelic relic : content.ordinaryRelics(cycle)) {
                if (!p.relics.contains(relic.name) && relicInSlot(p, relic.slot) == null) {
                    free.add(relic);
                }
            }
            if (free.isEmpty()) {
                earn(p, gold(HexEvent.CHEST_GOLD));
                p.giftGold = gold(HexEvent.CHEST_GOLD);
                log(msg("lblHexJournalShrineGold", p.name, card, String.valueOf(gold(HexEvent.CHEST_GOLD))));
            } else {
                final HexRelic relic = content.upgrade(free.get(rnd.nextInt(free.size())), cycle, rnd); // New Game Plus: maybe a stronger one
                p.relics.add(relic.name);
                p.giftRelic = relic.name;
                log(msg("lblHexJournalShrine", p.name, card, relic.name));
            }
        } else {
            final List<String> got = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final String c = content.randomCardOfColor(p.eventColor == null ? tile().color : p.eventColor, rnd);
                if (c != null) {
                    p.sideboard.add(c);
                    got.add(c);
                }
            }
            log(msg("lblHexJournalCaravan", p.name, card, String.join(", ", got)));
            p.gifts = got;
        }
        p.giftSerial++;
        p.eventOffer = null;
        p.eventColor = null;
        return true;
    }

    /** A head leaves the shrine or caravan alone. */
    public void skipEvent(final String playerName) {
        final HexPlayer p = player(playerName);
        if (p != null) {
            p.eventOffer = null;
            p.eventColor = null;
        }
    }

    /** What the next fight brings thanks to events, for the party panel; empty if nothing. */
    public List<String> fightBoosts() {
        final List<String> result = new ArrayList<>();
        if (boostLife > 0) {
            result.add(msg("lblHexBoostLife", String.valueOf(boostLife)));
        }
        if (boostHand > 0) {
            result.add(msg("lblHexBoostHand", String.valueOf(boostHand)));
        }
        if (boostLand) {
            result.add(msg("lblHexBoostLand"));
        }
        if (boostDoubleGold) {
            result.add(msg("lblHexBoostAltar", String.valueOf(boostEnemyLife)));
        }
        return result;
    }

    // ------------------------------------------------------------------ ready check

    /** A head looked at its deck while a fight is pending (shown in the ready check). */
    public void markDeckChecked(final String playerName) {
        if (pendingEncounter != null && player(playerName) != null && !deckChecked.contains(playerName)) {
            deckChecked.add(playerName);
        }
    }

    // ------------------------------------------------------------------ trading

    /** In town a head gives gold to a team mate. */
    public boolean giveGold(final String from, final String to, final int amount) {
        final HexPlayer giver = player(from);
        final HexPlayer taker = player(to);
        if (!inTown() || giver == null || taker == null || giver == taker || amount <= 0 || giver.gold < amount) {
            return false;
        }
        giver.gold -= amount;
        taker.gold += amount;
        log(msg("lblHexJournalGaveGold", giver.name, taker.name, String.valueOf(amount)));
        return true;
    }

    /** In town a head gives a card of its deck or sideboard to a team mate (it goes to the mate's sideboard). */
    public boolean giveCard(final String from, final String to, final String card, final boolean fromSideboard) {
        final HexPlayer giver = player(from);
        final HexPlayer taker = player(to);
        if (!inTown() || giver == null || taker == null || giver == taker || card == null) {
            return false;
        }
        if (fromSideboard ? !giver.sideboard.remove(card) : giver.deck.size() <= MIN_DECK_SIZE || !giver.deck.remove(card)) {
            return false;
        }
        taker.sideboard.add(card);
        log(msg("lblHexJournalGaveCard", giver.name, taker.name, card));
        return true;
    }

    // ------------------------------------------------------------------ fast travel

    /** Can the party travel from the town it is in to this town it has been to before? Every head pays a little gold. */
    public boolean canFastTravel(final HexTile target) {
        if (target == null || !inTown() || won || target == tile() || target.poi != HexPoi.TOWN || !target.explored
                || target.siege != null) {
            return false;
        }
        for (final HexPlayer p : players) {
            if (p.gold < FAST_TRAVEL_GOLD) {
                return false;
            }
        }
        return true;
    }

    /** Travels to another town the party has been to. */
    public boolean fastTravel(final int q, final int r) {
        final HexTile target = map.get(q, r);
        if (!canFastTravel(target)) {
            return false;
        }
        for (final HexPlayer p : players) {
            spend(p, FAST_TRAVEL_GOLD);
        }
        prevQ = partyQ;
        prevR = partyR;
        partyQ = q;
        partyR = r;
        steps++;
        reshuffleEnemies();
        log(msg("lblHexJournalFastTravel", target.name, String.valueOf(FAST_TRAVEL_GOLD)));
        shop();
        return true;
    }

    // ------------------------------------------------------------------ towns

    public boolean inTown() {
        return tile().poi == HexPoi.TOWN && !isBusy();
    }

    /** The theme tabs of a town's card shops: the town's own, the random one, then the two each head picked. */
    public static final int TAB_TOWN = 0;
    public static final int TAB_RANDOM = 1;
    public static final int TAB_PICKED = 2;
    /**
     * The "theme" of the random tab: no theme at all, any cards of the town's colour (and a few colourless
     * ones), so that cards that fit no theme can be bought too.
     */
    public static final String ANY_CARDS = "any";

    /**
     * The key of a theme tab of a head in the stock (HexShopOffer.tab). Every head has shelves of
     * its own in every tab, even where the theme is the same as another head's.
     */
    public static String tabKey(final String head, final int tab) {
        if (tab == TAB_TOWN) {
            return "town|" + head;
        }
        if (tab == TAB_RANDOM) {
            return "random|" + head;
        }
        return "pick|" + head + "|" + (tab - TAB_PICKED);
    }

    /** The key of a head's shelf at the relic dealer. */
    public static String relicTabKey(final String head) {
        return "relic|" + head;
    }

    /** The head whose shelf a tab key belongs to, or null. */
    public static String ownerOfTab(final String tab) {
        if (tab == null) {
            return null;
        }
        final String[] parts = tab.split("\\|");
        return parts.length >= 2 ? parts[1] : null;
    }

    /** The theme of a tab of the given head in the town the party is in (a HexTheme id), or null if it has none yet. */
    public String tabTheme(final String head, final int tab) {
        if (tile().poi != HexPoi.TOWN) {
            return null;
        }
        if (tab == TAB_TOWN) {
            return townTheme().id;
        }
        if (tab == TAB_RANDOM) {
            return ANY_CARDS;
        }
        final HexPlayer p = player(head);
        final int slot = tab - TAB_PICKED;
        return p == null || slot < 0 || slot >= PICKED_THEMES || HexTheme.get(p.themes[slot]) == null ? null : p.themes[slot];
    }

    /**
     * The stock of the town the party is in: every head has shelves of its own in every shop and
     * theme tab, and at the relic dealer. They are created at the first visit; the town's tab and
     * the relics are renewed for everybody now and then, the random tab and the picked tabs after
     * every fight; a head can also pay for new shelves of its own (restock).
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
                // a save from before the towns had several shops, before the shops had themes, or before every head had shelves of its own
                stale |= o.shop == null || (o.shop != HexShopType.RELIC && o.theme == null) || ownerOfTab(o.tab) == null;
            }
        }
        if (stale) {
            stock = new ArrayList<>();
            shops.put(key, stock);
            stockedAt.put(key, battlesWon);
            themeStockedAt.keySet().removeIf(k -> k.startsWith(key + "|"));
            questOffers.put(key, newQuests(tile()));
        }
        for (final HexPlayer p : players) {
            // the relics and the town's tab: until the town is restocked (or the head pays for it)
            if (!themeStockedAt.containsKey(key + "|" + relicTabKey(p.name) + "|relics")) {
                stockRelics(stock, p.name);
            }
            final String town = tabKey(p.name, TAB_TOWN);
            if (!themeStockedAt.containsKey(key + "|" + town + "|" + townTheme().id)) {
                stockTab(stock, town, townTheme().id);
            }
            // the random tab and the picked ones get fresh shelves after every fight, and when their theme changes
            final Map<String, String> wanted = new java.util.LinkedHashMap<>();
            wanted.put(tabKey(p.name, TAB_RANDOM), ANY_CARDS);
            for (int slot = 0; slot < PICKED_THEMES; slot++) {
                final String id = tabTheme(p.name, TAB_PICKED + slot);
                if (id != null) {
                    wanted.put(tabKey(p.name, TAB_PICKED + slot), id);
                }
            }
            for (final Map.Entry<String, String> e : wanted.entrySet()) {
                final Integer at = themeStockedAt.get(key + "|" + e.getKey() + "|" + e.getValue());
                if (at == null || at != fights()) {
                    stockTab(stock, e.getKey(), e.getValue());
                }
            }
        }
        return stock;
    }

    /** New relics on a head's shelf at the relic dealer of the town the party is in. */
    private void stockRelics(final List<HexShopOffer> stock, final String head) {
        final String key = tile().key();
        final String tab = relicTabKey(head);
        stock.removeIf(o -> tab.equals(o.tab));
        final HexPlayer p = player(head);
        for (final HexShopOffer o : HexContent.get().relicStock(random(), cycle, p != null && p.ai)) {
            o.tab = tab;
            stock.add(o);
        }
        themeStockedAt.put(key + "|" + tab + "|relics", fights());
    }

    /** How far a town looks for the regions of other colours: those it borders sell their colour pairs there. */
    public static final int BORDER_REACH = 2;

    /** The colours of the other regions within reach of a town (not the colourless centre): a border town's neighbours. */
    public java.util.Set<HexColor> borderColours(final HexTile town) {
        final java.util.Set<HexColor> result = java.util.EnumSet.noneOf(HexColor.class);
        if (town.color == HexColor.COLORLESS) {
            return result;
        }
        for (final HexTile t : map.tiles.values()) {
            if (t.color != town.color && t.color != HexColor.COLORLESS && HexMap.distance(t.q, t.r, town.q, town.r) <= BORDER_REACH) {
                result.add(t.color);
            }
        }
        return result;
    }

    /** New shelves for one theme tab of the town the party is in. */
    private void stockTab(final List<HexShopOffer> stock, final String tab, final String themeId) {
        final String key = tile().key();
        stock.removeIf(o -> tab.equals(o.tab));
        for (final HexShopOffer o : HexContent.get().themeStock(tile().color, borderColours(tile()), HexTheme.get(themeId), random())) {
            o.tab = tab;
            if (o.theme == null) {
                o.theme = themeId; // the random tab's any cards: marked, or the stock would look like an old save's
            }
            stock.add(o);
        }
        themeStockedAt.keySet().removeIf(k -> k.startsWith(key + "|" + tab + "|"));
        themeStockedAt.put(key + "|" + tab + "|" + themeId, fights());
    }

    /** What one shop of the town the party is in has on offer for the given head in a theme tab (relics: the head's shelf). */
    public List<HexShopOffer> shop(final HexShopType type, final String head, final int tab) {
        final String tabKey = type == HexShopType.RELIC ? relicTabKey(head) : tabKey(head, tab);
        final List<HexShopOffer> result = new ArrayList<>();
        for (final HexShopOffer o : shop()) {
            if (o.shop == type && tabKey.equals(o.tab)) {
                result.add(o);
            }
        }
        return result;
    }

    /** A head pays for new shelves of its own in the town the party is in: new cards in every tab, new relics. */
    public boolean restock(final String playerName) {
        final HexPlayer p = player(playerName);
        final int price = townPrice(RESTOCK_PRICE);
        if (!inTown() || p == null || p.gold < price) {
            return false;
        }
        final List<HexShopOffer> stock = shop();
        final String key = tile().key();
        spend(p, price);
        stock.removeIf(o -> playerName.equals(ownerOfTab(o.tab)));
        themeStockedAt.keySet().removeIf(k -> k.startsWith(key + "|") && playerName.equals(ownerOfTab(k.substring(key.length() + 1))));
        shop(); // the shelves of this head, new
        log(msg("lblHexJournalRestock", p.name, String.valueOf(price)));
        return true;
    }

    /** A gold reward (starting gold, a fight's reward, a bounty) with the campaign's gold multiplier. */
    public int gold(final int base) {
        return base * Math.max(1, goldMultiplier) * (100 + GOLD_PERCENT_PER_CYCLE * Math.max(0, cycle - 1)) / 100;
    }

    // ------------------------------------------------------------------ shop themes

    /** All fights fought so far, won or lost. */
    public int fights() {
        return battlesWon + battlesLost + battlesFled;
    }

    /** A theme that suits the town's colour, other than the given one. */
    private HexTheme rollTheme(final HexTile town, final String except) {
        final List<HexTheme> choices = new ArrayList<>(HexContent.get().themesFor(town.color));
        if (choices.size() > 1) {
            choices.removeIf(t -> t.id.equals(except));
        }
        if (choices.stream().anyMatch(t -> !t.isStrict())) {
            choices.removeIf(HexTheme::isStrict); // a town is not renowned for expensive cards; heads may still pick that theme
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
        stockTab(shop(), tabKey(p.name, TAB_PICKED + slot), theme.id); // a fresh assortment
        log(msg("lblHexJournalTheme", p.name, theme.getLabel()));
        return true;
    }

    public boolean buy(final String playerName, final HexShopOffer offer) {
        final HexPlayer p = player(playerName);
        final List<HexShopOffer> stock = shop();
        if (!inTown() || p == null || !stock.contains(offer) || !playerName.equals(ownerOfTab(offer.tab))) {
            return false; // only from the head's own shelves
        }
        if (offer.shop == HexShopType.RELIC) {
            return buyRelic(p, offer, stock);
        }
        final int price = priceFor(p, offer.price);
        if (p.gold < price) {
            return false;
        }
        spend(p, price);
        tally(p, s -> s.cardsBought++);
        p.deck.add(offer.card);
        stock.remove(offer);
        log(msg("lblHexJournalBought", p.name, offer.card, String.valueOf(price)));
        return true;
    }

    /**
     * Buys the offer for the named card (or relic) in the given shop and theme tab of the buyer
     * (TAB_TOWN, TAB_RANDOM, TAB_PICKED + slot; -1 for any tab), if the town has one.
     */
    public boolean buy(final String playerName, final HexShopType type, final int tab, final String card) {
        final String tabKey = tab < 0 ? null : tabKey(playerName, tab);
        for (final HexShopOffer offer : shop()) {
            if (offer.card.equals(card) && (type == null || offer.shop == type) && playerName.equals(ownerOfTab(offer.tab))
                    && (tabKey == null || offer.shop == HexShopType.RELIC || tabKey.equals(offer.tab))) {
                return buy(playerName, offer);
            }
        }
        return false;
    }

    /** Buys the offer for the named card, whichever shop sells it. */
    public boolean buy(final String playerName, final String card) {
        return buy(playerName, null, -1, card);
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
        final boolean trophy = old != null && old.trophy != null; // a trophy is put away, not sold
        final int refund = old == null || trophy ? 0 : content.relicSellPrice(old.name);
        final int price = priceFor(p, offer.price);
        if (p.gold + refund < price) {
            return false;
        }
        if (old != null) {
            p.relics.remove(old.name);
            if (!trophy) {
                log(msg("lblHexJournalSold", p.name, old.name, String.valueOf(refund)));
            }
        }
        earn(p, refund);
        spend(p, price);
        tally(p, s -> s.relicsBought++);
        p.relics.add(relic.name);
        stock.remove(offer);
        log(msg("lblHexJournalBought", p.name, relic.name, String.valueOf(price)));
        return true;
    }

    public boolean sellRelic(final String playerName, final String relicName) {
        final HexPlayer p = player(playerName);
        if (!inTown() || p == null || HexContent.get().isTrophy(relicName) || !p.relics.remove(relicName)) {
            return false;
        }
        final int price = HexContent.get().relicSellPrice(relicName);
        earn(p, price);
        log(msg("lblHexJournalSold", p.name, relicName, String.valueOf(price)));
        return true;
    }

    // ------------------------------------------------------------------ trophies

    /**
     * A beaten boss leaves the head its trophy; beaten again in a later cycle, the trophy grows a tier (up to III).
     * A head that wears the trophy wears the stronger one at once; a head with nothing on that slot puts it on, and
     * an AI head always wears its newest trophy (but keeps a relic it bought). Returns the trophy's name, or null.
     */
    private String awardTrophy(final HexPlayer p, final HexColor color) {
        final HexContent content = HexContent.get();
        final int tier = Math.min(HexContent.MAX_RELIC_TIER, p.trophies.getOrDefault(color.name(), 0) + 1);
        final HexRelic relic = content.trophy(color, tier);
        if (relic == null) {
            return null;
        }
        p.trophies.put(color.name(), tier);
        final HexRelic worn = relicInSlot(p, relic.slot);
        if (worn == null || color.name().equals(worn.trophy) || p.ai && worn.trophy != null) {
            if (worn != null) {
                p.relics.remove(worn.name);
            }
            p.relics.add(relic.name);
        }
        log(msg("lblHexJournalTrophy", p.name, relic.name));
        return relic.name;
    }

    /** The trophy a head owns of this colour's boss (at its tier), or null. */
    public HexRelic ownedTrophy(final HexPlayer p, final HexColor color) {
        final Integer tier = p.trophies.get(color.name());
        return tier == null ? null : HexContent.get().trophy(color, tier);
    }

    /**
     * A head wears one of its trophies (colour name), or none (null). It goes on the trophies' slot: a trophy worn
     * there is put away, a relic bought for it is sold to make room.
     */
    public boolean wearTrophy(final String playerName, final String color) {
        final HexPlayer p = player(playerName);
        if (p == null || pendingEncounter != null || deckLocked()) {
            return false;
        }
        HexRelic relic = null;
        if (color != null) {
            try {
                relic = ownedTrophy(p, HexColor.valueOf(color));
            } catch (final IllegalArgumentException e) {
                return false;
            }
            if (relic == null || p.relics.contains(relic.name)) {
                return false;
            }
        }
        final HexRelic worn = relicInSlot(p, relic != null ? relic.slot : TROPHY_SLOT);
        if (worn != null) {
            if (relic == null && worn.trophy == null) {
                return false; // taking off a trophy: there is none on
            }
            p.relics.remove(worn.name);
            if (worn.trophy == null) {
                final int refund = HexContent.get().relicSellPrice(worn.name);
                earn(p, refund);
                log(msg("lblHexJournalSold", p.name, worn.name, String.valueOf(refund)));
            }
        } else if (relic == null) {
            return false;
        }
        if (relic != null) {
            p.relics.add(relic.name);
            log(msg("lblHexJournalWearTrophy", p.name, relic.name));
        }
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
            final HexQuest quest = HexContent.get().questEnemy(t.color, rnd, cycle);
            if (quest == null || !names.add(quest.enemyName)) {
                continue;
            }
            quest.id = nextQuestId++;
            quest.q = t.q;
            quest.r = t.r;
            quest.town = town.name;
            quest.gold += 10 * HexMap.distance(t.q, t.r, town.q, town.r);
            // now and then an archenemy: alone, with a deck of schemes, and a bigger bounty (one per board)
            if (result.stream().noneMatch(o -> o.archenemy) && rnd.nextInt(ARCHENEMY_ODDS) == 0) {
                quest.archenemy = true;
                quest.gold = quest.gold * ARCHENEMY_BOUNTY_PERCENT / 100;
            }
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
        int life = teamLife + LIFE_PER_EXTRA_HEAD * Math.max(0, players.size() - 1) + boostLife;
        for (final HexPlayer p : players) {
            life += HexPerk.TEAM_LIFE_PER_STACK * HexPerk.TEAM_LIFE.count(p);
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

    /** The heads of this fight's enemy: an archenemy fights alone, every other enemy with enemyHeads(). */
    public int enemyHeads(final HexEncounter enc) {
        return enc != null && enc.archenemy ? 1 : enemyHeads();
    }

    /**
     * Shared life of the enemy side of a fight: the life of the enemy, ten more for every
     * follower, and what the relics of the party do to it.
     */
    public int enemyLife(final HexEncounter enc) {
        int life = enc.life + ENEMY_EXTRA_LIFE[Math.max(1, Math.min(ENEMY_EXTRA_LIFE.length, players.size())) - 1] + boostEnemyLife + cycleLife();
        if (enc.archenemy) {
            life += ARCHENEMY_LIFE_PER_MISSING_HEAD * (enemyHeads() - 1); // alone instead of with heads
        }
        if (enc.siege) {
            life += SIEGE_LIFE_PER_HEAD * players.size(); // a besieger is tougher than a wanted enemy
        }
        if (enc.isGuardian()) {
            life += DUNGEON_GUARDIAN_LIFE_PER_HEAD * players.size(); // and so is a dungeon's boss
        }
        final HexDungeon dungeon = dungeonOf(enc);
        if (dungeon != null) {
            life -= DUNGEON_WEAKEN_LIFE * dungeon.buffCount("WEAKEN"); // a shrine weakened the dungeon's enemies
        }
        final HexBiomeRule biome = HexBiomeRule.forFight(this, enc);
        if (biome != null) {
            life += biome.enemyLife; // New Game Plus: the wastes
        }
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
        if (p == null || deckLocked()) {
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
        final int price = townPrice(BASIC_LAND_PRICE);
        if (!inTown() || p == null || p.gold < price) {
            return false;
        }
        spend(p, price);
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
        final int price = sellPriceFor(p, card);
        earn(p, price);
        tally(p, s -> s.cardsSold++);
        log(msg("lblHexJournalSold", p.name, card, String.valueOf(price)));
        return true;
    }
}
