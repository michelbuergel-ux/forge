package forge.gamemodes.hexcrawl;

import java.util.Arrays;
import java.util.List;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.deck.Deck;

/** Arzakon, the secret lord: behind Emrakul in a cycle whose five-colour dungeon was cleared; one try a cycle. */
public class HexArzakonTest extends AITest {

    private static HexTile centre(final HexCampaign c) {
        for (final HexTile t : c.map.tilesWith(HexPoi.BOSS)) {
            if (t.color == HexColor.COLORLESS) {
                return t;
            }
        }
        throw new AssertionError("no centre");
    }

    /** A campaign whose archmages are beaten, the party next to the centre, nothing else on the map. */
    private static HexCampaign beforeTheCentre(final boolean rainbowCleared) {
        final HexCampaign c = HexCampaign.create("Arzakon", 31L, 7, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        c.defeatedBosses.addAll(Arrays.asList(HexColor.COLORS));
        if (rainbowCleared) {
            c.battlesWon = c.rainbowAt();
            c.maybeOpenRainbowDungeon();
            c.rainbowDungeon().dungeon.cleared = true;
        }
        for (final HexTile any : c.map.tiles.values()) {
            any.enemy = null;
            any.event = null;
        }
        c.quests.clear();
        final HexTile centre = centre(c);
        for (final HexTile n : c.map.neighbors(centre)) {
            if (n.poi == HexPoi.NONE) {
                c.partyQ = c.prevQ = n.q;
                c.partyR = c.prevR = n.r;
                return c;
            }
        }
        throw new AssertionError("no free field next to the centre");
    }

    private static void fight(final HexCampaign c, final HexBattle.Result result) {
        final HexFightSummary summary = new HexFightSummary();
        summary.enemyName = c.pendingEncounter.enemyName;
        summary.result = result.name();
        summary.partyLife = result == HexBattle.Result.WON ? 10 : 0;
        c.recordFight(summary);
        c.resolveBattle(result);
        c.pendingRewards.clear();
    }

    /** Into the centre, and Emrakul beaten. */
    private static void beatEmrakul(final HexCampaign c) {
        final HexTile centre = centre(c);
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(centre.q, centre.r));
        AssertJUnit.assertEquals(HexContent.get().getBossName(HexColor.COLORLESS), c.pendingEncounter.enemyName);
        fight(c, HexBattle.Result.WON);
    }

    @Test
    public void heIsReadFromTheOldBorderPlane() {
        final HexContent content = HexContent.get();
        final HexEncounter enc = content.arzakonEncounter();
        AssertJUnit.assertNotNull(enc);
        AssertJUnit.assertTrue(enc.boss);
        AssertJUnit.assertTrue(enc.secretBoss);
        AssertJUnit.assertEquals(HexContent.ARZAKON, enc.enemyName);
        AssertJUnit.assertEquals(HexContent.ARZAKON_LIFE, enc.life);
        AssertJUnit.assertTrue("the planeswalker in play", enc.startCards.contains(HexContent.ARZAKON_CARD));
        final Deck deck = content.loadEnemyDeck(enc);
        AssertJUnit.assertTrue("his old Shandalar deck", deck.getMain().countAll() >= 60);
        AssertJUnit.assertNotNull("his sprite", content.getSpriteAtlas(enc.enemyName));
        final List<forge.item.IPaperCard> command = content.bossCommandCards(enc, 1);
        AssertJUnit.assertEquals("his presence and his awakening", 2, command.size());
        AssertJUnit.assertEquals("Arzakon Unleashed", content.phaseTwoCard(enc));
        AssertJUnit.assertNotNull("his trophy", content.trophy(HexContent.ARZAKON_TROPHY, 1));
        AssertJUnit.assertNotNull(content.trophy(HexContent.ARZAKON_TROPHY, 3));
    }

    @Test
    public void withoutTheFiveColourDungeonEmrakulEndsTheCycle() {
        final HexCampaign c = beforeTheCentre(false);
        beatEmrakul(c);
        AssertJUnit.assertTrue(c.won);
        AssertJUnit.assertFalse(c.arzakonAwake);
        AssertJUnit.assertNull(c.enemyAt(centre(c)));
        AssertJUnit.assertEquals(HexContent.get().getBossName(HexColor.COLORLESS), c.finalBossName());
    }

    @Test
    public void afterItEmrakulWasOnlyTheGatekeeper() {
        final HexCampaign c = beforeTheCentre(true);
        final HexTile centre = centre(c);
        beatEmrakul(c);
        AssertJUnit.assertFalse("not won yet", c.won);
        AssertJUnit.assertTrue(c.arzakonAwake);
        AssertJUnit.assertTrue(c.arzakonWaits(centre));
        AssertJUnit.assertTrue("thrown back", c.isAdjacent(centre));
        AssertJUnit.assertEquals(HexContent.ARZAKON, centre.name);
        AssertJUnit.assertTrue(c.enemyAt(centre).secretBoss);
        // he is the next fight on the centre
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(centre.q, centre.r));
        AssertJUnit.assertTrue(c.pendingEncounter.secretBoss);
        fight(c, HexBattle.Result.WON);
        AssertJUnit.assertTrue("the true ending", c.won);
        AssertJUnit.assertTrue(c.arzakonBeaten);
        AssertJUnit.assertFalse(c.arzakonAwake);
        AssertJUnit.assertEquals(HexContent.ARZAKON, c.finalBossName());
        for (final HexPlayer p : c.players) {
            AssertJUnit.assertEquals("his trophy", Integer.valueOf(1), p.trophies.get(HexContent.ARZAKON_TROPHY));
            AssertJUnit.assertNotNull(c.ownedTrophy(p, HexContent.ARZAKON_TROPHY));
        }
    }

    @Test
    public void heGivesOneTryACycle() {
        final HexCampaign c = beforeTheCentre(true);
        final HexTile centre = centre(c);
        beatEmrakul(c);
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(centre.q, centre.r));
        fight(c, HexBattle.Result.LOST);
        AssertJUnit.assertTrue("Emrakul's fall still wins the cycle", c.won);
        AssertJUnit.assertFalse(c.arzakonAwake);
        AssertJUnit.assertFalse(c.arzakonBeaten);
        AssertJUnit.assertNull("he is gone", c.enemyAt(centre));
        // a new cycle: he sleeps again
        AssertJUnit.assertTrue(c.startNewGamePlus());
        AssertJUnit.assertFalse(c.arzakonAwake);
        AssertJUnit.assertFalse(c.arzakonBeaten);
    }

    @Test
    public void aGuestSeesHimWaiting() {
        final HexCampaign c = beforeTheCentre(true);
        beatEmrakul(c);
        final HexSession host = new HexSession(c);
        final HexSession guest = HexSession.forGuest();
        guest.applyState(host.toStateJson(), "Bob");
        final HexCampaign theirs = guest.getCampaign();
        AssertJUnit.assertTrue(theirs.arzakonAwake);
        AssertJUnit.assertTrue(theirs.arzakonWaits(centre(theirs)));
        AssertJUnit.assertTrue(theirs.enemyAt(centre(theirs)).secretBoss);
    }
}
