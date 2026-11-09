package forge.ai;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexEvent;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexTile;
import forge.gamemodes.net.event.HexActionEvent;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** Voting where to go (with a clock and a leader), marching to far fields, and when fights start. */
public class HexMovementTest extends AITest {

    private static HexCampaign newCampaign() {
        final HexCampaign c = HexCampaign.create("Test", 23L, 5, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        c.encounterChance = 0; // the tests place the enemies
        c.encounterChanceExplored = 0;
        for (final HexTile t : c.map.tiles.values()) {
            t.enemy = null;
            t.event = null;
        }
        return c;
    }

    /** A session on the host with Ann (the host) and Bob at the table; counts the fights it starts. */
    private static HexSession session(final HexCampaign c, final AtomicInteger fights) {
        final HexSession s = new HexSession(c);
        s.setListener(new HexSession.Listener() {
            @Override public void sessionChanged() { }
            @Override public void startFight() { fights.incrementAndGet(); }
        });
        s.setGuests(Collections.singletonList("Bob"));
        return s;
    }

    private static HexActionEvent vote(final HexTile t) {
        return new HexActionEvent(HexActionEvent.VOTE, t.q, t.r, null, false, null);
    }

    private static HexTile emptyNeighbour(final HexCampaign c) {
        for (final HexTile t : c.map.neighbors(c.tile())) {
            if (t.poi == HexPoi.NONE && c.questAt(t.q, t.r) == null) {
                t.enemy = null;
                t.event = null;
                return t;
            }
        }
        throw new AssertionError("no empty field next to the party");
    }

    private static HexEncounter enemy() {
        return HexContent.get().randomEncounter(HexColor.RED, 0.5, new Random(5));
    }

    private static void place(final HexTile t, final HexEncounter e) {
        t.enemy = e;
        e.q = t.q;
        e.r = t.r;
    }

    /** A far field (3 away) the party can march to on a way of ordinary fields. */
    private static HexTile farField(final HexCampaign c) {
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.NONE && HexMap.distance(t.q, t.r, c.partyQ, c.partyR) == 3) {
                t.scouted = true;
                final List<HexTile> way = c.route(t);
                if (way != null && way.size() == 3 && way.stream().allMatch(s -> s.poi == HexPoi.NONE && c.questAt(s.q, s.r) == null)) {
                    return t;
                }
            }
        }
        throw new AssertionError("no far field");
    }

    // ------------------------------------------------------------------ votes

    @Test
    public void testACampaignIsExportedAndImported() throws Exception {
        final HexCampaign c = newCampaign();
        final String stamp = Long.toHexString(System.nanoTime());
        c.name = "ExportTest " + stamp;
        final java.io.File dir = forge.gamemodes.hexcrawl.HexCampaignIO.dir();
        final java.io.File exported = java.io.File.createTempFile("hexexport", ".json");
        final java.io.File junk = java.io.File.createTempFile("hexjunk", ".json");
        final java.io.File first = new java.io.File(dir, c.name + ".json");
        final java.io.File second = new java.io.File(dir, c.name + " 2.json");
        try {
            // the page of a new campaign refuses a name that is taken (a new save would replace the old one)
            AssertJUnit.assertFalse(forge.gamemodes.hexcrawl.HexCampaignIO.exists(c.name));
            forge.gamemodes.hexcrawl.HexCampaignIO.exportTo(c, exported);
            final HexCampaign back = forge.gamemodes.hexcrawl.HexCampaignIO.importFrom(exported);
            AssertJUnit.assertNotNull(back);
            AssertJUnit.assertEquals(c.players.size(), back.players.size());
            AssertJUnit.assertEquals(c.map.tiles.size(), back.map.tiles.size());
            AssertJUnit.assertEquals(c.name, forge.gamemodes.hexcrawl.HexCampaignIO.saveImported(back));
            AssertJUnit.assertTrue(first.isFile());
            AssertJUnit.assertTrue(forge.gamemodes.hexcrawl.HexCampaignIO.exists(c.name));
            AssertJUnit.assertTrue(forge.gamemodes.hexcrawl.HexCampaignIO.exists(" " + c.name + " "));
            AssertJUnit.assertFalse(forge.gamemodes.hexcrawl.HexCampaignIO.exists("  "));
            // imported again: the name is taken, it gets another
            final HexCampaign again = forge.gamemodes.hexcrawl.HexCampaignIO.importFrom(exported);
            AssertJUnit.assertEquals(c.name + " 2", forge.gamemodes.hexcrawl.HexCampaignIO.saveImported(again));
            AssertJUnit.assertTrue(second.isFile());
            // a file that is no campaign
            java.nio.file.Files.writeString(junk.toPath(), "{\"deck\": [1, 2, 3]}");
            AssertJUnit.assertNull(forge.gamemodes.hexcrawl.HexCampaignIO.importFrom(junk));
        } finally {
            exported.delete();
            junk.delete();
            first.delete();
            second.delete();
        }
    }

    @Test
    public void testTheMenuListsTheLastCampaignsWithoutTheOpenOne() throws Exception {
        final String stamp = Long.toHexString(System.nanoTime());
        final HexCampaign older = newCampaign();
        older.name = "RecentTest A " + stamp;
        final HexCampaign open = newCampaign();
        open.name = "RecentTest B " + stamp;
        try {
            forge.gamemodes.hexcrawl.HexCampaignIO.save(older);
            Thread.sleep(20);
            forge.gamemodes.hexcrawl.HexCampaignIO.save(open);
            // the newest file first; the open campaign is left out
            final java.io.File a = forge.gamemodes.hexcrawl.HexCampaignIO.saveFile(older.name);
            AssertJUnit.assertTrue(a.isFile());
            a.setLastModified(System.currentTimeMillis() + 60_000);
            final java.util.List<String> recent = forge.gamemodes.hexcrawl.HexCampaignIO.recent(open.name, 5);
            AssertJUnit.assertEquals(older.name, recent.get(0));
            AssertJUnit.assertFalse(recent.contains(open.name));
            AssertJUnit.assertTrue(recent.size() <= 5);
            AssertJUnit.assertTrue(forge.gamemodes.hexcrawl.HexCampaignIO.recent(null, 50).contains(open.name));
        } finally {
            forge.gamemodes.hexcrawl.HexCampaignIO.saveFile(older.name).delete();
            forge.gamemodes.hexcrawl.HexCampaignIO.saveFile(open.name).delete();
        }
    }

    @Test
    public void testTheHostRenamesTheCampaignAndItsSave() {
        final HexCampaign c = newCampaign();
        final String stamp = Long.toHexString(System.nanoTime());
        c.name = "RenameTest " + stamp;
        final java.io.File dir = forge.gamemodes.hexcrawl.HexCampaignIO.dir();
        final java.io.File before = new java.io.File(dir, c.name + ".json");
        final java.io.File after = new java.io.File(dir, "Renamed " + stamp + ".json");
        try {
            forge.gamemodes.hexcrawl.HexCampaignIO.save(c);
            final HexSession s = session(c, new AtomicInteger());
            s.handle("Bob", new HexActionEvent(HexActionEvent.RENAME, 0, 0, "Bob's name", false, null));
            AssertJUnit.assertEquals("only the host", "RenameTest " + stamp, c.name);
            AssertJUnit.assertFalse("not empty", forge.gamemodes.hexcrawl.HexCampaignIO.canRename(c, "  "));
            s.rename("Renamed " + stamp);
            AssertJUnit.assertEquals("Renamed " + stamp, c.name);
            AssertJUnit.assertTrue(after.isFile());
            AssertJUnit.assertFalse("the old save is gone", before.exists());
            AssertJUnit.assertEquals("Renamed " + stamp, forge.gamemodes.hexcrawl.HexCampaignIO.load("Renamed " + stamp).name);
        } finally {
            before.delete();
            after.delete();
        }
    }

    @Test
    public void testAPingReachesTheOthers() {
        final HexCampaign c = newCampaign();
        final HexSession s = session(c, new AtomicInteger());
        final HexTile there = emptyNeighbour(c);
        s.handle("Bob", new HexActionEvent(HexActionEvent.PING, there.q, there.r, null, false, null));
        final String first = s.getPings().get("Bob");
        AssertJUnit.assertTrue(first.startsWith(there.key() + "#"));
        // the same field again is a new ping
        s.handle("Bob", new HexActionEvent(HexActionEvent.PING, there.q, there.r, null, false, null));
        AssertJUnit.assertFalse(first.equals(s.getPings().get("Bob")));
        final HexSession guest = HexSession.forGuest();
        guest.applyState(s.toStateJson(), "Bob");
        AssertJUnit.assertEquals(s.getPings(), guest.getPings());
    }

    @Test
    public void testTheOthersSeeWhoIsInTheShops() {
        final HexCampaign c = newCampaign();
        AssertJUnit.assertTrue("the party starts in a town", c.inTown());
        final HexSession s = session(c, new AtomicInteger());
        final HexActionEvent bobOpens = new HexActionEvent(HexActionEvent.SHOPPING, 0, 0, null, true, null);
        s.setShopping(true);
        s.handle("Bob", bobOpens);
        AssertJUnit.assertEquals(Arrays.asList("Ann", "Bob"), new java.util.ArrayList<>(s.getShopping()));

        // the guests get it with the state
        final HexSession guest = HexSession.forGuest();
        guest.applyState(s.toStateJson(), "Bob");
        AssertJUnit.assertEquals(s.getShopping(), guest.getShopping());

        s.setShopping(false);
        AssertJUnit.assertEquals(Collections.singleton("Bob"), s.getShopping());

        // once the party moved on nobody is in the shops, and nobody can be
        c.moveTo(emptyNeighbour(c).q, emptyNeighbour(c).r);
        s.handle("Bob", bobOpens);
        AssertJUnit.assertTrue(s.getShopping().isEmpty());
    }

    @Test
    public void testVotesWaitForEverybodyOrTheClock() {
        final HexCampaign c = newCampaign();
        final AtomicInteger fights = new AtomicInteger();
        final HexSession s = session(c, fights);
        final HexTile a = emptyNeighbour(c);

        s.handle("Ann", vote(a));
        AssertJUnit.assertEquals("waits for Bob", a.key(), s.getVotes().get("Ann"));
        AssertJUnit.assertNotSame(a, c.tile());
        final int left = s.voteSecondsLeft(System.currentTimeMillis());
        AssertJUnit.assertTrue(left > 0 && left <= HexSession.VOTE_SECONDS);

        AssertJUnit.assertFalse("not yet", s.tick(System.currentTimeMillis()));
        AssertJUnit.assertTrue(s.tick(System.currentTimeMillis() + HexSession.VOTE_SECONDS * 1000L + 1));
        AssertJUnit.assertSame("the clock ran out: Ann's vote decided", a, c.tile());
        AssertJUnit.assertTrue(s.getVotes().isEmpty());
        AssertJUnit.assertEquals(-1, s.voteSecondsLeft(System.currentTimeMillis()));

        // both vote: no need to wait
        final HexTile b = emptyNeighbour(c);
        s.handle("Ann", vote(b));
        s.handle("Bob", vote(b));
        AssertJUnit.assertSame(b, c.tile());
        AssertJUnit.assertEquals(0, fights.get());
    }

    @Test
    public void testTheLeaderDecides() {
        final HexCampaign c = newCampaign();
        final HexSession s = session(c, new AtomicInteger());
        s.handle("Bob", new HexActionEvent(HexActionEvent.LEADER, 0, 0, "Bob", false, null));
        AssertJUnit.assertNull("only the host names the leader", c.leader);
        s.handle("Ann", new HexActionEvent(HexActionEvent.LEADER, 0, 0, "Bob", false, null));
        AssertJUnit.assertEquals("Bob", c.leader);

        final HexTile a = emptyNeighbour(c);
        s.handle("Ann", vote(a));
        AssertJUnit.assertNotSame("Ann's vote is only a suggestion", a, c.tile());
        s.handle("Bob", vote(a));
        AssertJUnit.assertSame(a, c.tile());
        final HexTile b = emptyNeighbour(c);
        s.handle("Bob", vote(b));
        AssertJUnit.assertSame("the leader's vote decides at once", b, c.tile());
    }

    // ------------------------------------------------------------------ fights

    @Test
    public void testFightsStartAtOnceButAnAmbushWaits() {
        final HexCampaign c = newCampaign();
        final AtomicInteger fights = new AtomicInteger();
        final HexSession s = session(c, fights);
        final HexTile a = emptyNeighbour(c);
        place(a, enemy());
        s.handle("Ann", vote(a));
        s.handle("Bob", vote(a));
        AssertJUnit.assertNotNull(c.pendingEncounter);
        AssertJUnit.assertTrue("no ready check", s.isFightRunning());
        AssertJUnit.assertEquals(1, fights.get());
        s.fightFinished(forge.gamemodes.hexcrawl.HexBattle.Result.FLED, null);
        AssertJUnit.assertFalse(s.isFightRunning());

        // a hunter next to the field the party goes to catches it there
        for (final HexTile t : c.map.tiles.values()) {
            t.enemy = null;
            t.event = null;
        }
        final HexTile ahead = emptyNeighbour(c);
        HexTile beside = null;
        for (final HexTile t : c.map.neighbors(ahead)) {
            if (t != c.tile() && t.poi == HexPoi.NONE && c.questAt(t.q, t.r) == null) {
                beside = t;
                break;
            }
        }
        AssertJUnit.assertNotNull(beside);
        final HexEncounter hunter = enemy();
        hunter.roaming = true;
        place(beside, hunter);
        s.handle("Ann", vote(ahead));
        s.handle("Bob", vote(ahead));
        AssertJUnit.assertSame(hunter, c.pendingEncounter);
        AssertJUnit.assertTrue(hunter.ambush);
        AssertJUnit.assertFalse("time to look at the decks", s.isFightRunning());
        s.handle("Ann", new HexActionEvent(HexActionEvent.FIGHT, 0, 0, null, true, null));
        AssertJUnit.assertFalse(s.isFightRunning());
        s.handle("Bob", new HexActionEvent(HexActionEvent.FIGHT, 0, 0, null, true, null));
        AssertJUnit.assertTrue(s.isFightRunning());
        AssertJUnit.assertEquals(2, fights.get());
    }

    @Test
    public void testAFightWaitingInALoadedCampaignWaitsForTheFriends() {
        final HexCampaign c = newCampaign();
        final HexEncounter enc = enemy();
        c.pendingEncounter = enc; // saved standing before an enemy (in a dungeon, say), then opened again
        final AtomicInteger fights = new AtomicInteger();
        final HexSession s = new HexSession(c);
        s.setListener(new HexSession.Listener() {
            @Override public void sessionChanged() { }
            @Override public void startFight() { fights.incrementAndGet(); }
        });
        AssertJUnit.assertFalse(s.isFightRunning());
        // the host starts hosting and the first friend sits down: the fight does not start without the others
        s.setGuests(Collections.singletonList("Bob"));
        AssertJUnit.assertFalse("friends can still join", s.isFightRunning());
        AssertJUnit.assertEquals(0, fights.get());
        s.handle("Bob", new HexActionEvent(HexActionEvent.PING, c.partyQ, c.partyR, null, false, null));
        AssertJUnit.assertFalse(s.isFightRunning());
        AssertJUnit.assertSame(enc, c.pendingEncounter);
        // once the table is complete, somebody presses Fight
        s.handle("Ann", new HexActionEvent(HexActionEvent.FIGHT, 0, 0, null, true, null));
        AssertJUnit.assertTrue(s.isFightRunning());
        AssertJUnit.assertEquals(1, fights.get());
    }

    @Test
    public void testABrokenOffFightWaitsForFightAndTheHostMayResetAHangingOne() {
        final HexCampaign c = newCampaign();
        final AtomicInteger fights = new AtomicInteger();
        final HexSession s = session(c, fights);
        final HexTile a = emptyNeighbour(c);
        final HexEncounter enc = enemy();
        place(a, enc);
        s.handle("Ann", vote(a));
        s.handle("Bob", vote(a));
        AssertJUnit.assertTrue(s.isFightRunning());
        AssertJUnit.assertEquals(1, fights.get());

        // broken off without a result: nothing changes, the enemy stays
        final int gold = c.player("Ann").gold;
        final int deck = c.player("Ann").deck.size();
        s.fightAborted();
        AssertJUnit.assertFalse(s.isFightRunning());
        AssertJUnit.assertSame(enc, c.pendingEncounter);
        AssertJUnit.assertEquals(gold, c.player("Ann").gold);
        AssertJUnit.assertEquals(deck, c.player("Ann").deck.size());
        AssertJUnit.assertNull("no fight recorded", c.lastFight);
        // not again by itself (a ping or a vote settles the session), only when somebody presses Fight
        s.handle("Bob", new HexActionEvent(HexActionEvent.PING, a.q, a.r, null, false, null));
        AssertJUnit.assertFalse(s.isFightRunning());
        AssertJUnit.assertEquals(1, fights.get());
        s.handle("Bob", new HexActionEvent(HexActionEvent.FIGHT, 0, 0, null, true, null));
        AssertJUnit.assertTrue("one press is enough", s.isFightRunning());
        AssertJUnit.assertEquals(2, fights.get());

        // the end of the fight never came (no fight runs on this computer): the host sets it back
        AssertJUnit.assertNull(forge.gamemodes.hexcrawl.HexFight.current());
        AssertJUnit.assertTrue(s.resetFight());
        AssertJUnit.assertFalse(s.isFightRunning());
        AssertJUnit.assertSame(enc, c.pendingEncounter);
        AssertJUnit.assertFalse("nothing to set back", s.resetFight());
        AssertJUnit.assertFalse("a guest cannot", HexSession.forGuest().resetFight());
        s.handle("Ann", new HexActionEvent(HexActionEvent.FIGHT, 0, 0, null, true, null));
        AssertJUnit.assertEquals(3, fights.get());
    }

    // ------------------------------------------------------------------ marching

    @Test
    public void testRoutesAndMarches() {
        final HexCampaign c = newCampaign();
        final HexTile goal = farField(c);
        final List<HexTile> way = c.route(goal);
        AssertJUnit.assertSame(goal, way.get(way.size() - 1));
        AssertJUnit.assertTrue(c.canRoute(goal));
        AssertJUnit.assertFalse("a neighbour is a plain step", c.canRoute(emptyNeighbour(c)));
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.BOSS && t.color == HexColor.COLORLESS) {
                t.scouted = true;
                AssertJUnit.assertFalse("the centre stays locked", c.canRoute(t));
            }
        }

        // an event on the way stops the march there
        way.get(1).event = HexEvent.CHEST.name();
        AssertJUnit.assertEquals(HexCampaign.MoveResult.NOTHING, c.march(goal));
        AssertJUnit.assertSame(way.get(1), c.tile());

        // and so does an enemy
        final List<HexTile> rest = c.route(goal);
        AssertJUnit.assertEquals(1, rest.size());
        place(goal, enemy());
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.march(goal));
        AssertJUnit.assertSame(goal, c.tile());
    }

    @Test
    public void testAFarVoteMarches() {
        final HexCampaign c = newCampaign();
        final HexSession s = session(c, new AtomicInteger());
        final HexTile goal = farField(c);
        s.handle("Ann", vote(goal));
        s.handle("Bob", vote(goal));
        AssertJUnit.assertSame("the party marched all the way", goal, c.tile());
    }
}
