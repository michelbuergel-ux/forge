package forge.gamemodes.hexcrawl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;

/** The five-colour dungeon: once a cycle, after some won fights; harder, richer, with two rule cards. */
public class HexRainbowDungeonTest extends AITest {

    private static HexCampaign campaign() {
        return HexCampaign.create("Rainbow", 23L, 7, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
    }

    /** The campaign with its five-colour dungeon opened. */
    private static HexCampaign opened() {
        final HexCampaign c = campaign();
        c.battlesWon = c.rainbowAt();
        c.maybeOpenRainbowDungeon();
        AssertJUnit.assertNotNull("open", c.rainbowDungeon());
        return c;
    }

    private static int count(final HexCampaign c) {
        int n = 0;
        for (final HexTile t : c.dungeons()) {
            if (t.dungeon.multicolor) {
                n++;
            }
        }
        return n;
    }

    /** The colour whose own rule cards (not the colourless ones) hold this card. */
    private static HexColor colourOf(final String rule) {
        final List<String> colourless = HexLabyrinth.ruleCards(HexColor.COLORLESS);
        for (final HexColor color : HexColor.COLORS) {
            if (HexLabyrinth.ruleCards(color).contains(rule) && !colourless.contains(rule)) {
                return color;
            }
        }
        return null;
    }

    @Test
    public void itOpensOnceACycleAfterItsWonFights() {
        final HexCampaign c = campaign();
        final int at = c.rainbowAt();
        AssertJUnit.assertTrue(at >= HexCampaign.RAINBOW_MIN_WINS && at <= HexCampaign.RAINBOW_MAX_WINS);
        AssertJUnit.assertEquals("rolled once", at, c.rainbowAt());
        c.battlesWon = at - 1;
        c.maybeOpenRainbowDungeon();
        AssertJUnit.assertNull("not yet", c.rainbowDungeon());
        c.battlesWon = at;
        c.maybeOpenRainbowDungeon();
        final HexTile t = c.rainbowDungeon();
        AssertJUnit.assertNotNull(t);
        AssertJUnit.assertTrue("not next to the party", HexMap.distance(t.q, t.r, c.partyQ, c.partyR) >= 2);
        AssertJUnit.assertEquals(HexPoi.DUNGEON, t.poi);
        c.battlesWon = at + 5;
        c.maybeOpenRainbowDungeon();
        AssertJUnit.assertEquals("only one a cycle", 1, count(c));
    }

    @Test
    public void itIsHarderAndRicherWithTwoRuleCardsOfTwoColours() {
        final HexCampaign c = opened();
        final HexDungeon d = c.rainbowDungeon().dungeon;
        AssertJUnit.assertTrue(d.multicolor);
        AssertJUnit.assertSame(HexColor.COLORLESS, d.color);
        AssertJUnit.assertEquals("one treasure room more", HexLabyrinth.TREASURES + 1, d.treasureRooms());
        AssertJUnit.assertEquals(HexLabyrinth.TREASURES + 1, d.treasuresLeft());
        int guards = 0;
        final Set<HexColor> colours = new HashSet<>();
        for (final HexRoom r : d.rooms) {
            if (r.guard == null) {
                continue;
            }
            AssertJUnit.assertSame("the room's colour", d.roomColor(r), r.guard.color);
            AssertJUnit.assertTrue(Arrays.asList(HexColor.COLORS).contains(r.guard.color));
            colours.add(r.guard.color);
            if (r.kind != HexRoom.Kind.BOSS) {
                guards++;
            }
        }
        AssertJUnit.assertEquals("more enemies", HexLabyrinth.ENEMIES + HexLabyrinth.MULTICOLOR_ENEMIES, guards);
        AssertJUnit.assertTrue("enemies of several colours", colours.size() >= 2);
        final List<String> rules = d.ruleCards();
        AssertJUnit.assertEquals(2, rules.size());
        AssertJUnit.assertNotNull(colourOf(rules.get(0)));
        AssertJUnit.assertNotNull(colourOf(rules.get(1)));
        AssertJUnit.assertNotSame("of two colours", colourOf(rules.get(0)), colourOf(rules.get(1)));
        // both are in play in its fights
        for (final HexRoom r : d.rooms) {
            if (r.guard != null) {
                AssertJUnit.assertEquals(rules, c.dungeonRuleCards(r.guard));
            }
        }
    }

    @Test
    public void anOtherDungeonKeepsOneRuleCardAndItsColour() {
        final HexCampaign c = campaign();
        final HexDungeon d = c.dungeons().get(0).dungeon;
        AssertJUnit.assertFalse(d.multicolor);
        AssertJUnit.assertEquals(1, d.ruleCards().size());
        for (final HexRoom r : d.rooms) {
            AssertJUnit.assertSame(d.color, d.roomColor(r));
        }
        AssertJUnit.assertEquals(HexLabyrinth.TREASURES, d.treasureRooms());
    }

    @Test
    public void aNewCycleRollsItAnew() {
        final HexCampaign c = opened();
        c.won = true;
        AssertJUnit.assertTrue(c.startNewGamePlus());
        AssertJUnit.assertFalse(c.rainbowPlaced);
        AssertJUnit.assertNull("the new map has none yet", c.rainbowDungeon());
        final int at = c.rainbowAt();
        AssertJUnit.assertTrue(at >= HexCampaign.RAINBOW_MIN_WINS && at <= HexCampaign.RAINBOW_MAX_WINS);
        c.battlesWon += at; // the new cycle's wins count from its start
        c.maybeOpenRainbowDungeon();
        AssertJUnit.assertNotNull(c.rainbowDungeon());
    }

    @Test
    public void aGuestSeesTheSameFiveColourDungeon() {
        final HexCampaign c = opened();
        final HexSession host = new HexSession(c);
        final HexSession guest = HexSession.forGuest();
        guest.applyState(host.toStateJson(), "Bob");
        final HexTile mine = c.rainbowDungeon();
        final HexTile theirs = guest.getCampaign().rainbowDungeon();
        AssertJUnit.assertNotNull(theirs);
        AssertJUnit.assertEquals(mine.key(), theirs.key());
        AssertJUnit.assertEquals(mine.dungeon.ruleCards(), theirs.dungeon.ruleCards());
        AssertJUnit.assertTrue(theirs.dungeon.multicolor);
    }
}
