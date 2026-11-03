package forge.ai;

import java.util.Arrays;
import java.util.Collections;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexAdvantage;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexCampaignIO;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexDifficulty;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexFightSummary;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexTile;

/**
 * The levels of a campaign: the enemies' life, decks and advantages, the party's life and what a defeat costs.
 * Normal is the game as it was before the levels, and an old save plays on it.
 */
public class HexDifficultyTest extends AITest {

    private static HexCampaign campaign(final HexDifficulty level) {
        return HexCampaign.create("Level", 77L, 6, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)), 1, level);
    }

    private static int advantages(final HexCampaign c) {
        int n = 0;
        for (final HexAdvantage a : HexAdvantage.values()) {
            n += c.advantage(a);
        }
        return n;
    }

    @Test
    public void testAnOldSavePlaysOnNormalAsBefore() {
        final HexCampaign before = HexCampaign.create("Level", 77L, 6, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        before.difficulty = null; // as a save from before the levels has it (no field at all)
        final HexCampaign json = HexCampaignIO.fromJson(HexCampaignIO.toJson(before));
        AssertJUnit.assertNull("saved before the levels", json.difficulty);
        AssertJUnit.assertSame(HexDifficulty.NORMAL, json.difficulty());
        final HexDifficulty n = HexDifficulty.NORMAL;
        AssertJUnit.assertEquals(100, n.enemyLifePercent);
        AssertJUnit.assertEquals(0.0, n.dangerShift);
        AssertJUnit.assertEquals(0, n.startAdvantages);
        AssertJUnit.assertEquals(20, n.teamLife);
        AssertJUnit.assertEquals(1, n.cardsLost);
        AssertJUnit.assertEquals(HexCampaign.ANTE_MAX_PRICE, n.lossMaxPrice);
        AssertJUnit.assertEquals(HexCampaign.DUNGEON_HEAL, n.dungeonHeal);
        // a Normal campaign is the very one it was before (the same map, decks and enemies)
        final HexCampaign normal = campaign(HexDifficulty.NORMAL);
        AssertJUnit.assertEquals(before.player("Ann").deck, normal.player("Ann").deck);
        AssertJUnit.assertEquals(before.fightLife(), normal.fightLife());
        AssertJUnit.assertEquals(0, advantages(normal));
        AssertJUnit.assertTrue(normal.cycleAdvantages().isEmpty());
    }

    @Test
    public void testTheEnemiesGetStrongerWithTheLevel() {
        final HexEncounter enc = HexContent.get().randomEncounter(HexColor.RED, 0.5, new java.util.Random(3));
        int last = 0;
        for (final HexDifficulty level : HexDifficulty.values()) {
            final int life = campaign(level).enemyLife(enc);
            AssertJUnit.assertTrue(level + " " + life + " after " + last, life > last);
            last = life;
        }
        final HexEncounter tiny = HexContent.get().randomEncounter(HexColor.RED, 0.0, new java.util.Random(3));
        tiny.life = 1;
        AssertJUnit.assertTrue("at least 1", campaign(HexDifficulty.EASY).enemyLife(tiny) >= 1);

        // stronger decks earlier: the danger of a field is shifted (and stays within 0 and 1); roamers by distance alone
        final HexCampaign normal = campaign(HexDifficulty.NORMAL);
        final HexCampaign brutal = campaign(HexDifficulty.BRUTAL);
        final HexCampaign easy = campaign(HexDifficulty.EASY);
        for (final HexTile t : normal.map.tiles.values()) {
            final HexTile same = brutal.map.get(t.q, t.r);
            AssertJUnit.assertEquals(Math.min(1.0, normal.danger(t) + 0.35), brutal.danger(same), 1e-9);
            AssertJUnit.assertEquals(normal.distanceDanger(t), brutal.distanceDanger(same), 1e-9);
            AssertJUnit.assertTrue(easy.danger(easy.map.get(t.q, t.r)) >= 0);
        }
        AssertJUnit.assertEquals("the same chance of enemies on every level", normal.encounterChance, brutal.encounterChance);
    }

    @Test
    public void testTheHarderLevelsStartWithEnemyAdvantages() {
        AssertJUnit.assertEquals(0, advantages(campaign(HexDifficulty.EASY)));
        final HexCampaign hard = campaign(HexDifficulty.HARD);
        AssertJUnit.assertEquals(1, advantages(hard));
        AssertJUnit.assertEquals("shown with the enemy", 1, hard.cycleAdvantages().size());
        final HexCampaign brutal = campaign(HexDifficulty.BRUTAL);
        AssertJUnit.assertEquals(2, advantages(brutal));
        AssertJUnit.assertEquals("not the extra draws yet", 0, brutal.advantage(HexAdvantage.DRAW));
        for (final HexAdvantage a : HexAdvantage.values()) {
            AssertJUnit.assertTrue(brutal.advantage(a) <= 1);
        }
        // New Game Plus adds its two on top
        brutal.won = true;
        AssertJUnit.assertTrue(brutal.startNewGamePlus());
        AssertJUnit.assertEquals(4, advantages(brutal));

        AssertJUnit.assertEquals(25, campaign(HexDifficulty.EASY).teamLife);
        AssertJUnit.assertEquals(18, brutal.teamLife);
    }

    /** Both heads lose a fight; their decks are 38 Forests, two Grizzly Bears and a Black Lotus. */
    private static HexCampaign lose(final HexDifficulty level) {
        final HexCampaign c = campaign(level);
        for (final HexPlayer p : c.players) {
            p.deck.clear();
            for (int i = 0; i < 38; i++) {
                p.deck.add("Forest");
            }
            p.deck.add("Grizzly Bears");
            p.deck.add("Grizzly Bears");
            p.deck.add("Black Lotus");
        }
        c.pendingEncounter = HexContent.get().randomEncounter(HexColor.GREEN, 0.5, new java.util.Random(1));
        final HexFightSummary summary = new HexFightSummary();
        summary.enemyName = c.pendingEncounter.enemyName;
        summary.result = "LOST";
        for (final HexPlayer p : c.players) {
            final HexFightSummary.Head h = new HexFightSummary.Head();
            h.name = p.name;
            summary.heads.add(h);
        }
        c.recordFight(summary);
        c.resolveBattle(HexBattle.Result.LOST);
        return c;
    }

    private static int count(final java.util.List<String> deck, final String card) {
        return Collections.frequency(deck, card);
    }

    @Test
    public void testADefeatCostsWhatTheLevelSays() {
        AssertJUnit.assertTrue(HexContent.get().price("Black Lotus") >= 1000);
        final HexCampaign easy = lose(HexDifficulty.EASY);
        AssertJUnit.assertEquals("nothing lost", 2, count(easy.player("Ann").deck, "Grizzly Bears"));
        AssertJUnit.assertTrue(easy.lastFight.head("Ann").lostCards().isEmpty());

        final HexCampaign normal = lose(HexDifficulty.NORMAL);
        AssertJUnit.assertEquals(1, count(normal.player("Ann").deck, "Grizzly Bears"));
        AssertJUnit.assertEquals("Grizzly Bears", normal.lastFight.head("Ann").lostCard);

        final HexCampaign brutal = lose(HexDifficulty.BRUTAL);
        for (final HexPlayer p : brutal.players) {
            AssertJUnit.assertEquals("two cards each", 0, count(p.deck, "Grizzly Bears"));
            AssertJUnit.assertTrue("never the valuable one", p.deck.contains("Black Lotus"));
            AssertJUnit.assertEquals(Arrays.asList("Grizzly Bears", "Grizzly Bears"), brutal.lastFight.head(p.name).lostCards());
            AssertJUnit.assertEquals("Grizzly Bears", brutal.lastFight.head(p.name).lostCard);
            AssertJUnit.assertTrue(p.deck.size() >= HexCampaign.MIN_DECK_SIZE);
        }
    }

    @Test
    public void testAGuestPlaysOnTheHostsLevel() {
        final HexSession host = new HexSession(campaign(HexDifficulty.HARD));
        final HexSession guest = HexSession.forGuest();
        guest.applyState(host.toStateJson(), "Bob");
        AssertJUnit.assertSame(HexDifficulty.HARD, guest.getCampaign().difficulty());
        AssertJUnit.assertEquals(advantages(host.getCampaign()), advantages(guest.getCampaign()));
    }
}
