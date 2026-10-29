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
 * Dungeons: labyrinths whose ways show room by room, with treasures and shrines in their dead ends, more than one
 * way to their lord, the party's life carried over, the decks locked, and a fresh start after losing or leaving.
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
                    AssertJUnit.assertEquals("every room can be reached: " + where, d.rooms.size(), reachable(d).size());
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
                    final Set<String> kinds = new HashSet<>();
                    for (final HexRoom r : d.rooms) {
                        if (r.kind == HexRoom.Kind.TREASURE) {
                            treasures++;
                            kinds.add(r.content);
                            AssertJUnit.assertTrue("a treasure is guarded", r.guarded());
                        } else if (r.kind == HexRoom.Kind.SHRINE) {
                            shrines++;
                            AssertJUnit.assertTrue(HexLabyrinth.BUFFS.contains(r.content));
                        }
                        if (r.doorCount() == 1 && r.kind != HexRoom.Kind.ENTRANCE && r.kind != HexRoom.Kind.BOSS) {
                            deadEnds++;
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
        final List<HexRoom> next = d.neighbours(d.entrance());
        for (final HexRoom r : d.rooms) {
            AssertJUnit.assertEquals("seen: the entrance and the rooms behind its doors", r == d.entrance() || next.contains(r), r.seen);
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
        HexRoom guarded = null;
        for (final HexRoom r : d.rooms) {
            if (r.guarded() && r.kind == HexRoom.Kind.EMPTY) {
                guarded = r;
            }
        }
        AssertJUnit.assertNotNull(guarded);
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
            AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, stepInto(c, d, room));
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
        if (shrines.get(1).guard == null) {
            shrines.get(1).guard = HexContent.get().randomEncounter(d.color, 0.5, new java.util.Random(1));
            shrines.get(1).guard.dungeonStage = HexDungeon.GUARD;
            shrines.get(1).guard.q = c.partyQ;
            shrines.get(1).guard.r = c.partyR;
            shrines.get(1).guard.roomX = shrines.get(1).x;
            shrines.get(1).guard.roomY = shrines.get(1).y;
        }
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
        final HexRoom treasure = first(d, HexRoom.Kind.TREASURE);
        stepInto(c, d, treasure);
        win(c, 15);
        c.pendingRewards.clear();
        d.buffs.add("HAND");
        HexRoom corridor = null;
        for (final HexRoom r : d.rooms) {
            if (r.guarded() && r.kind == HexRoom.Kind.EMPTY) {
                corridor = r;
            }
        }
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
        final HexRoom treasure = first(d, HexRoom.Kind.TREASURE);
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
        final HexRoom treasure = first(d, HexRoom.Kind.TREASURE);
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
        AssertJUnit.assertFalse(c.canEnterLabyrinth());
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
    public void theRuleCardIsInPlayOnTheEnemySide() {
        final HexCampaign c = newCampaign(17L, 7);
        final HexDungeon d = enter(c);
        final HexRoom treasure = first(d, HexRoom.Kind.TREASURE);
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
