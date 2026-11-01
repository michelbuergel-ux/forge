package forge.ai;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexDefeat;
import forge.gamemodes.hexcrawl.HexDungeon;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexFightSummary;
import forge.gamemodes.hexcrawl.HexLabyrinth;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexReward;
import forge.gamemodes.hexcrawl.HexRoom;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexTile;
import forge.item.IPaperCard;
import forge.item.PaperCard;

/**
 * Dungeons: labyrinths with rock between their ways, which show as far as the party can see down them, with
 * treasures and shrines in their dead ends, enemies scattered about, more than one way to their lord, the party's
 * life carried over, the decks locked, and a fresh start after losing or leaving.
 */
public class HexDungeonTest extends AITest {

    private static HexCampaign newCampaign(final long seed, final int radius) {
        return HexCampaign.create("Dungeon", seed, radius, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
    }

    /** Puts the party next to a dungeon, on a free field; the land around is emptied. */
    private static HexTile dungeonNextToParty(final HexCampaign c) {
        for (final HexTile dungeon : c.dungeons()) {
            for (final HexTile n : c.map.neighbors(dungeon)) {
                if (n.poi == HexPoi.NONE && c.questAt(n.q, n.r) == null) {
                    c.partyQ = c.prevQ = n.q;
                    c.partyR = c.prevR = n.r;
                    for (final HexTile any : c.map.tiles.values()) {
                        any.enemy = null;
                        any.event = null;
                    }
                    return dungeon;
                }
            }
        }
        throw new AssertionError("no dungeon with a free field next to it");
    }

    /** The party goes into the dungeon next to it. */
    private static HexDungeon enter(final HexCampaign c) {
        final HexTile t = dungeonNextToParty(c);
        AssertJUnit.assertEquals(HexCampaign.MoveResult.DUNGEON, c.moveTo(t.q, t.r));
        return t.dungeon;
    }

    /** The party steps into a room from one next to it (as if it had walked there). */
    private static HexCampaign.MoveResult stepInto(final HexCampaign c, final HexDungeon d, final HexRoom room) {
        final HexRoom from = d.neighbours(room).get(0);
        d.x = from.x;
        d.y = from.y;
        return c.stepRoom(room.x, room.y);
    }

    /** The fight ends with the party at this much life. */
    private static void win(final HexCampaign c, final int partyLife) {
        final HexFightSummary summary = new HexFightSummary();
        summary.enemyName = c.pendingEncounter.enemyName;
        summary.result = HexBattle.Result.WON.name();
        summary.partyLife = partyLife;
        c.recordFight(summary);
        c.resolveBattle(HexBattle.Result.WON);
    }

    /** Makes sure an enemy waits in the room. */
    private static HexRoom guarded(final HexCampaign c, final HexDungeon d, final HexRoom room) {
        if (room.guard == null) {
            room.guard = HexContent.get().randomEncounter(d.color, 0.5, new java.util.Random(room.x * 7L + room.y));
            room.guard.dungeonStage = HexDungeon.GUARD;
            room.guard.color = d.color;
            room.guard.q = c.partyQ;
            room.guard.r = c.partyR;
            room.guard.roomX = room.x;
            room.guard.roomY = room.y;
        }
        room.beaten = false;
        return room;
    }

    /** The rooms seen from this one: down each open door as long as the way runs straight, up to an enemy. */
    private static Set<HexRoom> inSight(final HexDungeon d, final HexRoom from) {
        final Set<HexRoom> result = new HashSet<>();
        result.add(from);
        for (final int door : HexDungeon.DOORS) {
            HexRoom r = from;
            while ((r.doors & door) != 0) {
                r = d.room(r.x + HexDungeon.dx(door), r.y + HexDungeon.dy(door));
                result.add(r);
                if (r.guarded()) {
                    break;
                }
            }
        }
        return result;
    }

    private static HexRoom first(final HexDungeon d, final HexRoom.Kind kind) {
        for (final HexRoom r : d.rooms) {
            if (r.kind == kind) {
                return r;
            }
        }
        throw new AssertionError("no " + kind);
    }

    /** Rooms reached from the entrance through open doors. */
    private static Set<HexRoom> reachable(final HexDungeon d) {
        final Set<HexRoom> seen = new HashSet<>();
        final ArrayDeque<HexRoom> todo = new ArrayDeque<>();
        todo.add(d.entrance());
        seen.add(d.entrance());
        while (!todo.isEmpty()) {
            for (final HexRoom n : d.neighbours(todo.poll())) {
                if (seen.add(n)) {
                    todo.add(n);
                }
            }
        }
        return seen;
    }

    @Test
    public void everyLabyrinthHasWaysDeadEndsTreasuresShrinesAndTwoWaysToItsLord() {
        int labyrinths = 0;
        int withRock = 0;
        int openTreasures = 0;
        for (final int radius : new int[] { 6, 7, 8 }) {
            for (long seed = 0; seed < 10; seed++) {
                final HexCampaign c = newCampaign(seed, radius);
                final String where = "radius " + radius + ", seed " + seed;
                final Set<HexColor> colours = new HashSet<>();
                for (final HexTile t : c.dungeons()) {
                    AssertJUnit.assertTrue("one per colour: " + where, colours.add(t.color));
                    final HexDungeon d = t.dungeon;
                    AssertJUnit.assertEquals(HexDungeon.SIZE * HexDungeon.SIZE, d.rooms.size());
                    AssertJUnit.assertFalse(d.inside);
                    int rooms = 0;
                    for (final HexRoom r : d.rooms) {
                        if (r.kind == HexRoom.Kind.ROCK) {
                            AssertJUnit.assertEquals("rock has no doors", 0, r.doors);
                            AssertJUnit.assertNull("nobody stands in the rock", r.guard);
                            AssertJUnit.assertNull(d.room(r.x, r.y));
                        } else {
                            rooms++;
                        }
                    }
                    labyrinths++;
                    withRock += rooms < d.rooms.size() ? 1 : 0;
                    AssertJUnit.assertEquals("every room can be reached: " + where, rooms, reachable(d).size());
                    for (final HexRoom r : d.rooms) {
                        for (final int door : HexDungeon.DOORS) {
                            final HexRoom other = d.room(r.x + HexDungeon.dx(door), r.y + HexDungeon.dy(door));
                            if ((r.doors & door) != 0) {
                                AssertJUnit.assertNotNull("no door to the outside", other);
                                AssertJUnit.assertTrue("doors on both sides", (other.doors & HexDungeon.opposite(door)) != 0);
                            }
                        }
                    }
                    AssertJUnit.assertTrue("two ways to the lord: " + where, HexLabyrinth.twoWays(d));
                    int treasures = 0;
                    int shrines = 0;
                    int deadEnds = 0;
                    int enemies = 0;
                    final Set<String> kinds = new HashSet<>();
                    for (final HexRoom r : d.rooms) {
                        if (r.kind == HexRoom.Kind.TREASURE) {
                            treasures++;
                            kinds.add(r.content);
                            openTreasures += r.guarded() ? 0 : 1;
                        } else if (r.kind == HexRoom.Kind.SHRINE) {
                            shrines++;
                            AssertJUnit.assertTrue(HexLabyrinth.BUFFS.contains(r.content));
                        }
                        if (r.doorCount() == 1 && r.kind != HexRoom.Kind.ENTRANCE && r.kind != HexRoom.Kind.BOSS) {
                            deadEnds++;
                        }
                        if (r.guard != null && r.kind != HexRoom.Kind.BOSS) {
                            enemies++;
                            AssertJUnit.assertTrue("not at the door", r.kind != HexRoom.Kind.ENTRANCE && !d.neighbours(d.entrance()).contains(r));
                        }
                        if (r.guard != null) {
                            AssertJUnit.assertEquals(t.q, r.guard.q);
                            AssertJUnit.assertEquals(r.x, r.guard.roomX);
                            AssertJUnit.assertEquals(r.y, r.guard.roomY);
                        }
                    }
                    AssertJUnit.assertEquals(3, treasures);
                    AssertJUnit.assertEquals(new HashSet<>(HexLabyrinth.TREASURE_KINDS), kinds);
                    AssertJUnit.assertEquals(2, shrines);
                    AssertJUnit.assertEquals("enemies scattered about: " + where, HexLabyrinth.ENEMIES, enemies);
                    AssertJUnit.assertTrue("dead ends: " + where, deadEnds >= 1);
                    AssertJUnit.assertEquals(HexRoom.Kind.ENTRANCE, d.entrance().kind);
                    AssertJUnit.assertEquals(HexRoom.Kind.BOSS, d.boss().kind);
                    AssertJUnit.assertTrue(d.boss().guard.isGuardian());
                    AssertJUnit.assertTrue("the lord has copies of itself as heads", d.boss().guard.headsAlike());
                    final IPaperCard rule = HexContent.get().findStartCard(d.ruleCard);
                    AssertJUnit.assertNotNull(d.ruleCard, rule);
                    AssertJUnit.assertTrue(d.ruleCard, rule.getRules().getType().isArtifact() || rule.getRules().getType().isEnchantment());
                    AssertJUnit.assertTrue(HexLabyrinth.ruleCards(d.color).contains(d.ruleCard));
                    AssertJUnit.assertNull("its enemies wait inside", c.enemyAt(t));
                }
                AssertJUnit.assertEquals(where, HexColor.COLORS.length, colours.size());
            }
        }
        AssertJUnit.assertTrue("most labyrinths have gaps of rock: " + withRock + " of " + labyrinths, withRock * 10 >= labyrinths * 9);
        AssertJUnit.assertTrue("some treasures lie open", openTreasures > 0);
    }

    @Test
    public void insideTheWaysShowRoomByRoomAndTheDecksAreLocked() {
        final HexCampaign c = newCampaign(3L, 7);
        final HexTile outside = dungeonNextToParty(c);
        final HexTile before = c.tile();
        final HexDungeon d = enter(c);
        AssertJUnit.assertTrue(c.inLabyrinth());
        AssertJUnit.assertSame(outside, c.tile());
        AssertJUnit.assertSame(d.entrance(), d.here());
        AssertJUnit.assertEquals(c.fightLife(), d.lifeLeft);
        AssertJUnit.assertNull("no fight at the entrance", c.pendingEncounter);
        final Set<HexRoom> sight = inSight(d, d.entrance());
        for (final HexRoom r : d.rooms) {
            AssertJUnit.assertEquals("seen: the entrance and the ways straight on from its doors", sight.contains(r), r.seen);
            AssertJUnit.assertEquals(r == d.entrance(), r.visited);
        }
        // no steps on the hex map, no changes to the decks
        AssertJUnit.assertEquals(HexCampaign.MoveResult.BUSY, c.moveTo(before.q, before.r));
        AssertJUnit.assertFalse(c.canEnter(before));
        AssertJUnit.assertTrue(c.deckLocked());
        c.player("Ann").sideboard.add("Lightning Bolt");
        AssertJUnit.assertFalse(c.moveCard("Ann", "Lightning Bolt", false));
        // no steps through walls
        for (final int door : HexDungeon.DOORS) {
            final HexRoom r = d.room(d.entranceX + HexDungeon.dx(door), d.entranceY + HexDungeon.dy(door));
            if (r != null && (d.entrance().doors & door) == 0) {
                AssertJUnit.assertFalse("a wall", c.canStepRoom(r.x, r.y));
            }
        }
        final HexRoom far = d.boss();
        AssertJUnit.assertFalse("not next to the party", c.canStepRoom(far.x, far.y));
        // a step into a guarded room starts its fight
        final HexRoom guarded = guarded(c, d, first(d, HexRoom.Kind.EMPTY));
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, stepInto(c, d, guarded));
        AssertJUnit.assertSame(guarded.guard, c.pendingEncounter);
        AssertJUnit.assertTrue(guarded.visited);
    }

    @Test
    public void treasuresGiveEveryHeadTheirShareAndTheLifeCarriesOver() {
        final HexCampaign c = newCampaign(5L, 7);
        final HexDungeon d = enter(c);
        final int full = c.fightLife();
        int life = 9;
        for (final HexRoom room : d.rooms) {
            if (room.kind != HexRoom.Kind.TREASURE) {
                continue;
            }
            AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, stepInto(c, d, guarded(c, d, room)));
            AssertJUnit.assertEquals(d.lifeLeft, c.partyStartLife());
            final int goldBefore = c.player("Ann").gold;
            win(c, life);
            AssertJUnit.assertTrue(room.beaten);
            AssertJUnit.assertTrue(room.looted);
            AssertJUnit.assertEquals(Math.min(full, life + HexCampaign.DUNGEON_HEAL), d.lifeLeft);
            final Set<String> cards = new HashSet<>();
            for (final HexPlayer p : c.players) {
                final HexReward reward = c.pendingReward(p.name);
                if ("CARD".equals(room.content)) {
                    AssertJUnit.assertNotNull(reward.chestCard);
                    AssertJUnit.assertTrue("a different card each", cards.add(reward.chestCard));
                    AssertJUnit.assertTrue(p.sideboard.contains(reward.chestCard));
                    AssertJUnit.assertTrue(HexContent.get().price(reward.chestCard) >= HexContent.TREASURE_PRICE);
                    final PaperCard card = HexContent.get().getCard(reward.chestCard);
                    AssertJUnit.assertNotNull(card);
                } else if ("GOLD".equals(room.content)) {
                    AssertJUnit.assertTrue(reward.chestGold > 0);
                    AssertJUnit.assertNull(reward.chestCard);
                } else {
                    AssertJUnit.assertNotNull(reward.chestRelic);
                    AssertJUnit.assertTrue(p.relics.contains(reward.chestRelic) || reward.chestRelicGold > 0);
                }
            }
            if ("GOLD".equals(room.content)) {
                AssertJUnit.assertTrue(c.player("Ann").gold > goldBefore);
            }
            AssertJUnit.assertTrue("still inside", c.inLabyrinth());
            c.pendingRewards.clear();
            life += 4;
        }
    }

    @Test
    public void shrinesGiveBuffsForTheVisit() {
        final HexCampaign c = newCampaign(7L, 7);
        final HexDungeon d = enter(c);
        final java.util.List<HexRoom> shrines = new java.util.ArrayList<>();
        for (final HexRoom r : d.rooms) {
            if (r.kind == HexRoom.Kind.SHRINE) {
                shrines.add(r);
            }
        }
        // an unguarded shrine gives its buff at once; a guarded one after the fight
        shrines.get(0).guard = null;
        shrines.get(0).content = "LIFE";
        shrines.get(1).content = "WEAKEN";
        guarded(c, d, shrines.get(1));
        final int life = d.lifeLeft;
        AssertJUnit.assertEquals(HexCampaign.MoveResult.NOTHING, stepInto(c, d, shrines.get(0)));
        AssertJUnit.assertEquals(life + HexCampaign.DUNGEON_SHRINE_LIFE, d.lifeLeft);
        AssertJUnit.assertTrue(shrines.get(0).looted);
        AssertJUnit.assertEquals(1, d.buffCount("LIFE"));

        final HexEncounter guard = shrines.get(1).guard;
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, stepInto(c, d, shrines.get(1)));
        final int strong = c.enemyLife(guard);
        win(c, 30);
        c.pendingRewards.clear();
        AssertJUnit.assertEquals(1, d.buffCount("WEAKEN"));
        AssertJUnit.assertEquals("the life buff counts above full life", Math.min(c.fightLife() + HexCampaign.DUNGEON_SHRINE_LIFE, 30 + HexCampaign.DUNGEON_HEAL), d.lifeLeft);
        c.pendingEncounter = guard;
        AssertJUnit.assertEquals(strong - HexCampaign.DUNGEON_WEAKEN_LIFE, c.enemyLife(guard));
        d.buffs.add("HAND");
        d.buffs.add("LAND");
        AssertJUnit.assertEquals(1, c.dungeonBuff("HAND"));
        AssertJUnit.assertEquals(1, c.dungeonBuff("LAND"));
        c.pendingEncounter = null;
        AssertJUnit.assertEquals("outside a dungeon fight: nothing", 0, c.dungeonBuff("HAND"));
    }

    @Test
    public void losingThrowsThePartyOutAndTheDungeonStartsAnew() {
        final HexCampaign c = newCampaign(9L, 7);
        final HexTile outside = dungeonNextToParty(c);
        final HexTile before = c.tile();
        final HexDungeon d = enter(c);
        final HexRoom treasure = guarded(c, d, first(d, HexRoom.Kind.TREASURE));
        stepInto(c, d, treasure);
        win(c, 15);
        c.pendingRewards.clear();
        d.buffs.add("HAND");
        final HexRoom corridor = guarded(c, d, first(d, HexRoom.Kind.EMPTY));
        stepInto(c, d, corridor);
        c.resolveBattle(HexBattle.Result.LOST);
        AssertJUnit.assertFalse(c.inLabyrinth());
        AssertJUnit.assertSame("back outside, on the field before", before, c.tile());
        AssertJUnit.assertTrue(d.buffs.isEmpty());
        AssertJUnit.assertEquals(0, d.lifeLeft);
        for (final HexRoom r : d.rooms) {
            AssertJUnit.assertFalse("dark again", r.seen);
        }
        AssertJUnit.assertTrue("its enemies are back", corridor.guarded());
        AssertJUnit.assertTrue("what was taken stays taken", treasure.looted);
        AssertJUnit.assertFalse("and its guard stays beaten", treasure.guarded());
        AssertJUnit.assertSame(outside.dungeon, d);
    }

    @Test
    public void fleeingStepsBackIntoTheRoomBefore() {
        final HexCampaign c = newCampaign(11L, 7);
        final HexDungeon d = enter(c);
        final HexRoom treasure = guarded(c, d, first(d, HexRoom.Kind.TREASURE));
        final HexRoom from = d.neighbours(treasure).get(0);
        stepInto(c, d, treasure);
        c.resolveBattle(HexBattle.Result.FLED);
        AssertJUnit.assertTrue("still inside", c.inLabyrinth());
        AssertJUnit.assertSame(from, d.here());
        AssertJUnit.assertTrue("the enemy is still there", treasure.guarded());
        AssertJUnit.assertFalse(treasure.looted);
    }

    @Test
    public void leavingOnlyFromTheEntranceAndThenItStartsAnew() {
        final HexCampaign c = newCampaign(13L, 7);
        final HexDungeon d = enter(c);
        final HexRoom treasure = guarded(c, d, first(d, HexRoom.Kind.TREASURE));
        stepInto(c, d, treasure);
        win(c, 15);
        c.pendingRewards.clear();
        AssertJUnit.assertFalse("away from the entrance", c.canLeaveLabyrinth());
        AssertJUnit.assertFalse(c.leaveLabyrinth());
        d.x = d.entranceX;
        d.y = d.entranceY;
        AssertJUnit.assertTrue(c.leaveLabyrinth());
        AssertJUnit.assertFalse(c.inLabyrinth());
        AssertJUnit.assertFalse(c.deckLocked());
        AssertJUnit.assertEquals(HexPoi.DUNGEON, c.tile().poi);
        AssertJUnit.assertTrue("it can go in again", c.canEnterLabyrinth());
        AssertJUnit.assertTrue(c.canEnter(c.tile()));
        AssertJUnit.assertTrue(treasure.looted);
        AssertJUnit.assertEquals(HexCampaign.MoveResult.DUNGEON, c.enterLabyrinth());
        AssertJUnit.assertSame(d.entrance(), d.here());
    }

    @Test
    public void aTreasureWithoutAnEnemyIsThePartysAtOnce() {
        final HexCampaign c = newCampaign(27L, 7);
        final HexDungeon d = enter(c);
        final HexRoom treasure = first(d, HexRoom.Kind.TREASURE);
        treasure.guard = null;
        treasure.content = "GOLD";
        final int gold = c.player("Ann").gold;
        AssertJUnit.assertEquals(HexCampaign.MoveResult.NOTHING, stepInto(c, d, treasure));
        AssertJUnit.assertNull("no fight", c.pendingEncounter);
        AssertJUnit.assertTrue(treasure.looted);
        AssertJUnit.assertTrue(c.player("Ann").gold > gold);
        for (final HexPlayer p : c.players) {
            final HexReward reward = c.pendingReward(p.name);
            AssertJUnit.assertTrue("shown like a reward", reward.chestGold > 0);
            AssertJUnit.assertEquals("nothing to choose", 0, reward.gold);
            AssertJUnit.assertTrue(reward.options().isEmpty());
        }
        AssertJUnit.assertTrue("the party waits until everybody has seen it", c.isBusy());
        final int after = c.player("Ann").gold;
        c.chooseReward("Ann", -1);
        c.chooseReward("Bob", -1);
        AssertJUnit.assertFalse(c.isBusy());
        AssertJUnit.assertEquals(after, c.player("Ann").gold);
        AssertJUnit.assertEquals("taken once", HexCampaign.MoveResult.NOTHING, stepInto(c, d, treasure));
        AssertJUnit.assertTrue(c.pendingRewards.isEmpty());
    }

    @Test
    public void theDungeonsGiveOnlySoManyTreasuresInACycle() {
        final HexCampaign c = newCampaign(29L, 7);
        AssertJUnit.assertEquals("two for each of the two heads", 4, c.treasureCap());
        c.treasuresTaken = c.treasureCap() - 1;
        final HexDungeon d = enter(c);
        final java.util.List<HexRoom> chests = new java.util.ArrayList<>();
        for (final HexRoom r : d.rooms) {
            if (r.kind == HexRoom.Kind.TREASURE) {
                chests.add(r);
            }
        }
        // the last treasure of the cycle
        chests.get(0).guard = null;
        AssertJUnit.assertEquals(1, c.treasuresLeft());
        stepInto(c, d, chests.get(0));
        AssertJUnit.assertTrue(chests.get(0).looted);
        AssertJUnit.assertEquals(0, c.treasuresLeft());
        c.chooseReward("Ann", -1);
        c.chooseReward("Bob", -1);
        // then the chests are empty: open ones give nothing, guarded ones only what their enemy has
        chests.get(1).guard = null;
        AssertJUnit.assertFalse(c.treasureWaits(chests.get(1)));
        AssertJUnit.assertEquals(HexCampaign.MoveResult.NOTHING, stepInto(c, d, chests.get(1)));
        AssertJUnit.assertTrue("nothing to take", c.pendingRewards.isEmpty());
        AssertJUnit.assertFalse(chests.get(1).looted);
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, stepInto(c, d, guarded(c, d, chests.get(2))));
        win(c, 20);
        for (final HexPlayer p : c.players) {
            final HexReward reward = c.pendingReward(p.name);
            AssertJUnit.assertNull(reward.chestCard);
            AssertJUnit.assertNull(reward.chestRelic);
            AssertJUnit.assertEquals(0, reward.chestGold);
        }
        c.pendingRewards.clear();
        AssertJUnit.assertEquals("never below none", 0, c.treasuresLeft());
        AssertJUnit.assertEquals(c.treasureCap(), c.treasuresTaken);
        // leaving and coming back brings nothing back
        d.x = d.entranceX;
        d.y = d.entranceY;
        AssertJUnit.assertTrue(c.leaveLabyrinth());
        AssertJUnit.assertEquals(HexCampaign.MoveResult.DUNGEON, c.enterLabyrinth());
        AssertJUnit.assertEquals(0, c.treasuresLeft());
        // a new cycle: new labyrinths, and their treasure
        d.x = d.entranceX;
        d.y = d.entranceY;
        c.leaveLabyrinth();
        c.won = true;
        AssertJUnit.assertTrue(c.startNewGamePlus());
        AssertJUnit.assertEquals(c.treasureCap(), c.treasuresLeft());
    }

    @Test
    public void thePartySeesDownStraightWaysUpToAnEnemy() {
        // a corridor of four rooms in a row, west to east, in a labyrinth of rock
        final HexDungeon d = new HexDungeon();
        d.size = 4;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                final HexRoom r = new HexRoom(x, y);
                r.kind = y == 0 ? HexRoom.Kind.EMPTY : y == 1 && x == 0 ? HexRoom.Kind.EMPTY : HexRoom.Kind.ROCK;
                d.rooms.add(r);
            }
        }
        for (int x = 0; x < 4; x++) {
            d.room(x, 0).doors = (x > 0 ? HexDungeon.WEST : 0) | (x < 3 ? HexDungeon.EAST : 0);
        }
        d.room(1, 0).doors |= HexDungeon.SOUTH; // a door into the rock leads nowhere
        d.room(0, 0).doors |= HexDungeon.SOUTH;
        d.room(0, 1).doors = HexDungeon.NORTH;
        AssertJUnit.assertNull(d.room(1, 1));
        d.see(d.room(0, 1));
        for (int x = 0; x < 4; x++) {
            AssertJUnit.assertEquals("only straight on: " + x, x == 0, d.room(x, 0).seen);
        }
        d.see(d.room(0, 0));
        for (int x = 0; x < 4; x++) {
            AssertJUnit.assertTrue("the whole corridor: " + x, d.room(x, 0).seen);
        }
        // an enemy in the way: the rooms beyond it stay dark
        d.reset();
        d.room(1, 0).guard = new HexEncounter();
        d.see(d.room(0, 0));
        AssertJUnit.assertTrue(d.room(1, 0).seen);
        AssertJUnit.assertFalse(d.room(2, 0).seen);
        AssertJUnit.assertFalse(d.room(3, 0).seen);
        d.room(1, 0).beaten = true;
        d.see(d.room(0, 0));
        AssertJUnit.assertTrue("beaten, it no longer blocks the view", d.room(3, 0).seen);
    }

    @Test
    public void theLordGuardsTheChest() {
        final HexCampaign c = newCampaign(15L, 7);
        final HexDungeon d = enter(c);
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, stepInto(c, d, d.boss()));
        AssertJUnit.assertTrue(c.pendingEncounter.isGuardian());
        final int lordLife = c.enemyLife(c.pendingEncounter);
        c.pendingEncounter.dungeonStage = HexDungeon.GUARD;
        AssertJUnit.assertEquals("tougher by 5 per head", lordLife - HexCampaign.DUNGEON_GUARDIAN_LIFE_PER_HEAD * 2, c.enemyLife(c.pendingEncounter));
        c.pendingEncounter.dungeonStage = HexDungeon.BOSS;
        win(c, 5);
        AssertJUnit.assertTrue(d.cleared);
        AssertJUnit.assertFalse("out again", c.inLabyrinth());
        AssertJUnit.assertFalse("collapsed", c.canEnterLabyrinth());
        AssertJUnit.assertEquals(HexDefeat.Kind.DUNGEON, c.defeated.get(c.defeated.size() - 1).kind);
        final Set<String> cards = new HashSet<>();
        for (final HexPlayer p : c.players) {
            final HexReward reward = c.pendingReward(p.name);
            AssertJUnit.assertNotNull(reward.chestCard);
            AssertJUnit.assertTrue(cards.add(reward.chestCard));
            AssertJUnit.assertTrue(reward.chestGold > 0);
            AssertJUnit.assertNotNull(reward.chestRelic);
        }
    }

    @Test
    public void aChestRelicForATakenSlotIsTheHeadsChoice() {
        final HexCampaign c = newCampaign(15L, 7);
        // every slot of every head holds a relic already
        for (final HexPlayer p : c.players) {
            p.relics.clear();
            final Set<String> slots = new HashSet<>();
            for (final forge.gamemodes.hexcrawl.HexRelic relic : HexContent.get().ordinaryRelics()) {
                if (slots.add(relic.slot)) {
                    p.relics.add(relic.name);
                }
            }
        }
        final HexDungeon d = enter(c);
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, stepInto(c, d, d.boss()));
        win(c, 5);
        final HexPlayer ann = c.player("Ann");
        final HexReward annReward = c.pendingReward("Ann");
        AssertJUnit.assertFalse("this seed's chest holds a relic Ann does not wear", ann.relics.contains(annReward.chestRelic));
        final String worn = annReward.chestRelicWorn;
        AssertJUnit.assertNotNull("Ann chooses", worn);
        AssertJUnit.assertTrue(ann.relics.contains(worn));
        AssertJUnit.assertEquals("nothing sold yet", 0, annReward.chestRelicGold);

        // Ann keeps the new relic: the worn one is sold
        final int gold = ann.gold;
        AssertJUnit.assertTrue(c.chooseChestRelic("Ann", true));
        AssertJUnit.assertTrue(ann.relics.contains(annReward.chestRelic));
        AssertJUnit.assertFalse(ann.relics.contains(worn));
        AssertJUnit.assertEquals(worn, annReward.chestRelicSold);
        AssertJUnit.assertEquals(gold + HexContent.get().relicSellPrice(worn), ann.gold);
        AssertJUnit.assertFalse("only once", c.chooseChestRelic("Ann", false));

        // Bob takes his reward without choosing: his worn relic stays, the new one is sold
        final HexReward bobReward = c.pendingReward("Bob");
        final String bobWorn = bobReward.chestRelicWorn;
        c.chooseReward("Bob", -1);
        if (bobWorn != null) {
            AssertJUnit.assertTrue(c.player("Bob").relics.contains(bobWorn));
            AssertJUnit.assertEquals(bobReward.chestRelic, bobReward.chestRelicSold);
        }
    }

    @Test
    public void theRuleCardIsInPlayOnTheEnemySide() {
        final HexCampaign c = newCampaign(17L, 7);
        final HexDungeon d = enter(c);
        final HexRoom treasure = guarded(c, d, first(d, HexRoom.Kind.TREASURE));
        stepInto(c, d, treasure);
        final String rule = HexContent.get().findStartCard(d.ruleCard).getName();
        boolean found = false;
        for (final forge.game.player.RegisteredPlayer rp : HexBattle.createPlayers(c, c.pendingEncounter, true)) {
            for (final IPaperCard card : rp.getCardsOnBattlefield()) {
                if (card.getName().equals(rule)) {
                    AssertJUnit.assertEquals("on the enemy's side", HexBattle.ENEMY_TEAM, rp.getTeamNumber());
                    found = true;
                }
            }
        }
        AssertJUnit.assertTrue(rule, found);
    }

    @Test
    public void theHeadsVoteThroughTheLabyrinth() {
        final HexCampaign c = HexCampaign.create("Votes", 19L, 7, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", true)));
        final HexSession s = new HexSession(c);
        final HexDungeon d = enter(c);
        HexRoom free = null;
        for (final HexRoom r : d.neighbours(d.entrance())) {
            if (!r.guarded() && r.kind == HexRoom.Kind.EMPTY) {
                free = r;
            }
        }
        if (free == null) {
            free = d.neighbours(d.entrance()).get(0);
            free.guard = null;
            free.kind = HexRoom.Kind.EMPTY;
        }
        s.voteRoom(free.x, free.y);
        AssertJUnit.assertSame(free, d.here());
        s.voteRoom(d.entranceX, d.entranceY);
        AssertJUnit.assertSame(d.entrance(), d.here());
        s.voteExit();
        AssertJUnit.assertFalse(c.inLabyrinth());
    }

    @Test
    public void newGamePlusBringsNewLabyrinthsAndOldDungeonsGetOne() {
        final HexCampaign c = newCampaign(21L, 7);
        for (final HexTile t : c.dungeons()) {
            t.dungeon.cleared = true;
        }
        c.won = true;
        c.startNewGamePlus();
        AssertJUnit.assertEquals(HexColor.COLORS.length, c.dungeons().size());
        for (final HexTile t : c.dungeons()) {
            AssertJUnit.assertFalse(t.dungeon.cleared);
            AssertJUnit.assertFalse(t.dungeon.rooms.isEmpty());
        }
        // a dungeon of yesterday's saves: three fights, no rooms
        final HexCampaign old = newCampaign(23L, 7);
        final HexTile t = dungeonNextToParty(old);
        t.dungeon.rooms.clear();
        AssertJUnit.assertEquals(HexCampaign.MoveResult.DUNGEON, old.moveTo(t.q, t.r));
        AssertJUnit.assertEquals(HexDungeon.SIZE * HexDungeon.SIZE, t.dungeon.rooms.size());
        AssertJUnit.assertTrue(old.inLabyrinth());
    }

    @Test
    public void marchingGoesAroundDungeons() {
        final HexCampaign c = newCampaign(25L, 7);
        for (final HexTile t : c.map.tiles.values()) {
            t.explored = true;
        }
        for (final HexTile goal : c.map.tiles.values()) {
            final List<HexTile> way = c.route(goal);
            if (way == null) {
                continue;
            }
            for (int i = 0; i < way.size() - 1; i++) {
                AssertJUnit.assertFalse("no dungeon on the way to " + goal.key(), way.get(i).poi == HexPoi.DUNGEON);
            }
        }
    }

    @Test
    public void everyColourHasEnoughTreasure() {
        for (final HexColor color : HexColor.values()) {
            final List<String> treasure = HexContent.get().treasureCards(color, 4);
            AssertJUnit.assertTrue(color + ": " + treasure.size(), treasure.size() >= 4);
            AssertJUnit.assertEquals(treasure.size(), new HashSet<>(treasure).size());
        }
    }
}
