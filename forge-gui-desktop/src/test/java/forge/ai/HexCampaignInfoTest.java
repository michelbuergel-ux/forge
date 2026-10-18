package forge.ai;

import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexDeckStats;
import forge.gamemodes.hexcrawl.HexEncounter;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** What the Hex Campaign tells the players between fights: deck statistics and enemy scouting. */
public class HexCampaignInfoTest extends AITest {

    @Test
    public void testDeckStatistics() {
        final List<String> deck = Arrays.asList(
                "Forest", "Forest", "Mountain",
                "Llanowar Elves",       // creature, mana value 1, green
                "Grizzly Bears",        // creature, 2, green
                "Lightning Bolt",       // instant, 1, red
                "Shivan Dragon",        // creature, 6, red
                "Boros Charm");         // instant, 2, red and white
        final HexDeckStats stats = HexDeckStats.of(deck, HexContent.get());

        AssertJUnit.assertEquals(3, stats.lands);
        AssertJUnit.assertEquals(3, stats.creatures);
        AssertJUnit.assertEquals(2, stats.otherSpells);
        AssertJUnit.assertEquals(2, stats.curve[1]);
        AssertJUnit.assertEquals(2, stats.curve[2]);
        AssertJUnit.assertEquals(1, stats.curve[6]);
        AssertJUnit.assertEquals(12.0 / 5, stats.averageManaValue(), 0.001);
        AssertJUnit.assertEquals(1, stats.colors[0]); // white: Boros Charm
        AssertJUnit.assertEquals(3, stats.colors[3]); // red
        AssertJUnit.assertEquals(2, stats.colors[4]); // green
    }

    @Test
    public void testScoutReportOfAnEnemy() {
        final HexContent content = HexContent.get();
        final HexEncounter enc = content.randomEncounter(HexColor.RED, 0.5, new Random(7));
        AssertJUnit.assertNotNull(enc);
        final HexContent.ScoutReport report = content.scout(enc);
        AssertJUnit.assertNotNull("the deck of " + enc.enemyName + " could not be read", report);
        AssertJUnit.assertFalse("an enemy deck has at least one colour", report.colors.isEmpty());
        AssertJUnit.assertFalse(report.keyCards.isEmpty());
        AssertJUnit.assertTrue(report.keyCards.size() <= 3);
        // read once, then remembered
        AssertJUnit.assertSame(report, content.scout(enc));
    }
}
