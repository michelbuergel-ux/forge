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
    /** Fights the party fled from in the first turns. */
    public int battlesFled;
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
        for (final HexTile t : map.tiles.values()) {
            if (t.poi == HexPoi.BOSS) {
                t.name = content.getBossName(t.color);
            } else if (t.poi == HexPoi.TOWN) {
                String townName = townName(t, random());
                for (int i = 0; i < 50 && !townNames.add(townName); i++) {
                    townName = townName(t, random()); // every town its own name
                }
                t.name = townName;
                t.theme = rollTheme(t, null).id; // every town is known for something
            }
        }
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

        won = false;
        defeatedBosses.clear();
        pendingEncounter = null;
        pendingRewards.clear();
        shops.clear();
        stockedAt.clear();
        randomThemes.clear();
        randomThemeAt.clear();
        themeStockedAt.clear();
        questOffers.clear();
        quests.clear();
        boostLife = 0;
        boostHand = 0;
        boostLand = false;
        boostEnemyLife = 0;
        boostDoubleGold = false;
        lastEvent = null;
        deckChecked.clear();
        for (final HexPlayer p : players) {
            p.themes = new String[PICKED_THEMES]; // the towns of the new map have other colours
        }
        newMap(seed * 31L + cycle * 7919L, map.radius);
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

    private static final String[] TOWN_A = { "Alt", "Neu", "Hoch", "Nieder", "Grau", "Stein", "Wind", "Nebel", "Dorn", "Eichen", "Raben", "Silber" };
    private static final String[] TOWN_B = { "furt", "hafen", "burg", "tal", "wacht", "heim", "brück", "feld", "hain", "stein", "markt", "quell" };

    private static String townName(final HexTile t, final Random rnd) {
        return TOWN_A[rnd.nextInt(TOWN_A.length)] + TOWN_B[rnd.nextInt(TOWN_B.length)];
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
            moveRoamers(); // the hunters close in (one that reaches the party fights it below)
        }

        if (target.poi == HexPoi.TOWN) {
            log(msg("lblHexJournalTown", target.name));
            shop(); // stock the shelves now, so everybody at the table sees the same offers
            return MoveResult.TOWN;
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
                p.gold -= Math.min(p.gold, price);
            }
            log(msg("lblHexJournalFled", enc.enemyName, String.valueOf(price)));
            partyQ = prevQ;
            partyR = prevR;
        } else if (result == HexBattle.Result.WON) {
            battlesWon++;
            log(msg("lblHexJournalWon", enc.enemyName));
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
                pendingRewards.add(reward);
            }
            final HexQuest quest = quest(enc.questId);
            if (quest != null) {
                quests.remove(quest);
                questsDone++;
                for (final HexPlayer p : players) {
                    p.gold += withGoldBonus(p, gold(quest.gold)); // the bounty: the same for every head (plus perks)
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
        offerPerks();
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
            p.gold += reward.gold;
            log(msg("lblHexJournalRewardGold", p.name, String.valueOf(reward.gold)));
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

    /** What a head pays for an offer in the shops, with its discount perks. */
    public int priceFor(final HexPlayer p, final int price) {
        return Math.max(1, price * (100 - HexPerk.DISCOUNT_PERCENT * HexPerk.DISCOUNT.count(p)) / 100);
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
        switch (event) {
            case SHRINE:
                boostLife += HexEvent.SHRINE_LIFE;
                break;
            case CHEST:
                for (final HexPlayer p : players) {
                    p.gold += gold(HexEvent.CHEST_GOLD);
                }
                text.append(' ').append(msg("lblHexEventGold", String.valueOf(gold(HexEvent.CHEST_GOLD))));
                break;
            case CAMP:
                final Random rnd = random();
                for (final HexPlayer p : players) {
                    final String card = HexContent.get().randomCardOfColor(t.color, rnd);
                    if (card != null) {
                        p.sideboard.add(card);
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
        if (event == HexEvent.RUINED_SHRINE) {
            final List<HexRelic> free = new ArrayList<>();
            for (final HexRelic relic : content.allRelics()) {
                if (!p.relics.contains(relic.name) && relicInSlot(p, relic.slot) == null) {
                    free.add(relic);
                }
            }
            if (free.isEmpty()) {
                p.gold += gold(HexEvent.CHEST_GOLD);
                log(msg("lblHexJournalShrineGold", p.name, card, String.valueOf(gold(HexEvent.CHEST_GOLD))));
            } else {
                final HexRelic relic = free.get(rnd.nextInt(free.size()));
                p.relics.add(relic.name);
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
        }
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
        if (target == null || !inTown() || won || target == tile() || target.poi != HexPoi.TOWN || !target.explored) {
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
            p.gold -= FAST_TRAVEL_GOLD;
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
     * The key of a theme tab in the stock (HexShopOffer.tab). The town's and the random tab are the
     * same for everybody; every head has picked tabs of its own, with shelves of their own even if
     * the theme is the same as that of another tab.
     */
    public static String tabKey(final String head, final int tab) {
        if (tab == TAB_TOWN) {
            return "town";
        }
        if (tab == TAB_RANDOM) {
            return "random";
        }
        return "pick|" + head + "|" + (tab - TAB_PICKED);
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
            return randomTheme().id;
        }
        final HexPlayer p = player(head);
        final int slot = tab - TAB_PICKED;
        return p == null || slot < 0 || slot >= PICKED_THEMES || HexTheme.get(p.themes[slot]) == null ? null : p.themes[slot];
    }

    /**
     * The stock of the town the party is in (all shops and theme tabs); created at the first visit.
     * The town's own tab and the relics are renewed now and then, the random tab and the tabs the
     * heads picked after every fight; a head can also pay for a whole new stock (restock).
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
                // a save from before the towns had several shops, before the shops had themes, or before every tab had shelves of its own
                stale |= o.shop == null || (o.shop != HexShopType.RELIC && (o.theme == null || o.tab == null));
            }
        }
        if (stale) {
            stock = new ArrayList<>();
            shops.put(key, stock);
            stockedAt.put(key, battlesWon);
            stockTown(stock);
            questOffers.put(key, newQuests(tile()));
        }
        // the random tab and the picked ones get fresh shelves after every fight, and when their theme changes
        final Map<String, String> wanted = new java.util.LinkedHashMap<>();
        wanted.put(tabKey(null, TAB_RANDOM), randomTheme().id);
        for (final HexPlayer p : players) {
            for (int slot = 0; slot < PICKED_THEMES; slot++) {
                final String id = tabTheme(p.name, TAB_PICKED + slot);
                if (id != null) {
                    wanted.put(tabKey(p.name, TAB_PICKED + slot), id);
                }
            }
        }
        for (final Map.Entry<String, String> e : wanted.entrySet()) {
            final Integer at = themeStockedAt.get(key + "|" + e.getKey() + "|" + e.getValue());
            if (at == null || at != fights()) {
                stockTab(stock, e.getKey(), e.getValue());
            }
        }
        return stock;
    }

    /** A whole new stock for the town the party is in: the relics and the town's tab; the other tabs follow in shop(). */
    private void stockTown(final List<HexShopOffer> stock) {
        final String key = tile().key();
        stock.clear();
        stock.addAll(HexContent.get().relicStock(random()));
        themeStockedAt.keySet().removeIf(k -> k.startsWith(key + "|"));
        stockTab(stock, tabKey(null, TAB_TOWN), townTheme().id);
    }

    /** New shelves for one theme tab of the town the party is in. */
    private void stockTab(final List<HexShopOffer> stock, final String tab, final String themeId) {
        final String key = tile().key();
        stock.removeIf(o -> tab.equals(o.tab));
        for (final HexShopOffer o : HexContent.get().themeStock(tile().color, HexTheme.get(themeId), random())) {
            o.tab = tab;
            stock.add(o);
        }
        themeStockedAt.keySet().removeIf(k -> k.startsWith(key + "|" + tab + "|"));
        themeStockedAt.put(key + "|" + tab + "|" + themeId, fights());
    }

    /** What one shop of the town the party is in has on offer in a theme tab of the given head (relics: in every tab). */
    public List<HexShopOffer> shop(final HexShopType type, final String head, final int tab) {
        final String tabKey = tabKey(head, tab);
        final List<HexShopOffer> result = new ArrayList<>();
        for (final HexShopOffer o : shop()) {
            if (o.shop == type && (type == HexShopType.RELIC || tabKey.equals(o.tab))) {
                result.add(o);
            }
        }
        return result;
    }

    /** A head pays for a whole new stock of the town the party is in: new cards in every tab, new relics. */
    public boolean restock(final String playerName) {
        final HexPlayer p = player(playerName);
        if (!inTown() || p == null || p.gold < RESTOCK_PRICE) {
            return false;
        }
        final List<HexShopOffer> stock = shop();
        p.gold -= RESTOCK_PRICE;
        stockTown(stock);
        shop(); // the random and the picked tabs
        log(msg("lblHexJournalRestock", p.name, String.valueOf(RESTOCK_PRICE)));
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
        stockTab(shop(), tabKey(p.name, TAB_PICKED + slot), theme.id); // a fresh assortment
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
        final int price = priceFor(p, offer.price);
        if (p.gold < price) {
            return false;
        }
        p.gold -= price;
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
            if (offer.card.equals(card) && (type == null || offer.shop == type)
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
        final int refund = old == null ? 0 : content.relicSellPrice(old.name);
        final int price = priceFor(p, offer.price);
        if (p.gold + refund < price) {
            return false;
        }
        if (old != null) {
            p.relics.remove(old.name);
            log(msg("lblHexJournalSold", p.name, old.name, String.valueOf(refund)));
        }
        p.gold += refund - price;
        p.relics.add(relic.name);
        stock.remove(offer);
        log(msg("lblHexJournalBought", p.name, relic.name, String.valueOf(price)));
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

    /**
     * Shared life of the enemy side of a fight: the life of the enemy, ten more for every
     * follower, and what the relics of the party do to it.
     */
    public int enemyLife(final HexEncounter enc) {
        int life = enc.life + ENEMY_EXTRA_LIFE[Math.max(1, Math.min(ENEMY_EXTRA_LIFE.length, players.size())) - 1] + boostEnemyLife + cycleLife();
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
        final int price = sellPriceFor(p, card);
        p.gold += price;
        log(msg("lblHexJournalSold", p.name, card, String.valueOf(price)));
        return true;
    }
}
