package forge.gamemodes.hexcrawl;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.item.PaperCard;

/** The five-colour town: it opens in the course of a cycle, besieged, and sells every colour as its own. */
public class HexRainbowTownTest extends AITest {

    private static HexCampaign campaign() {
        return HexCampaign.create("Prism", 29L, 6, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
    }

    @Test
    public void itOpensBesiegedAfterItsWinsOnce() {
        final HexCampaign c = campaign();
        final int at = c.rainbowTownAt();
        AssertJUnit.assertTrue(at >= HexCampaign.RAINBOW_TOWN_MIN_WINS && at <= HexCampaign.RAINBOW_TOWN_MAX_WINS);
        c.battlesWon = at - 1;
        c.maybeOpenRainbowTown();
        AssertJUnit.assertNull("not before its wins", c.rainbowTown());
        c.battlesWon = at;
        c.maybeOpenRainbowTown();
        final HexTile town = c.rainbowTown();
        AssertJUnit.assertNotNull(town);
        AssertJUnit.assertEquals(HexPoi.TOWN, town.poi);
        AssertJUnit.assertNotNull("besieged", town.siege);
        AssertJUnit.assertNotNull(town.name);
        AssertJUnit.assertTrue("a world of many colours", HexTheme.get(town.theme).isMulticoloured());
        c.battlesWon = at + 3;
        c.maybeOpenRainbowTown();
        AssertJUnit.assertEquals("one a cycle", 1, c.map.tilesWith(HexPoi.TOWN).stream().filter(t -> t.rainbow).count());
        // it is no town of its field's colour
        final int towns = c.towns(town.color);
        town.explored = true;
        AssertJUnit.assertEquals(towns, c.towns(town.color));
        AssertJUnit.assertEquals(0, c.visitedTowns(town.color) - (int) c.map.tilesWith(HexPoi.TOWN).stream()
                .filter(t -> !t.rainbow && t.explored && t.color == town.color).count());

        // New Game Plus: a new map, and the town opens again later
        c.won = true;
        AssertJUnit.assertTrue(c.startNewGamePlus());
        AssertJUnit.assertNull(c.rainbowTown());
        AssertJUnit.assertFalse(c.rainbowTownPlaced);
    }

    @Test
    public void itSellsEveryColourWithoutImports() {
        final HexCampaign c = campaign();
        c.battlesWon = c.rainbowTownAt();
        c.maybeOpenRainbowTown();
        final HexTile town = c.rainbowTown();
        town.siege = null;
        town.explored = true;
        c.partyQ = c.prevQ = town.q;
        c.partyR = c.prevR = town.r;
        AssertJUnit.assertTrue(c.inTown());
        for (final String card : new String[] { "Counterspell", "Lightning Bolt", "Swords to Plowshares", "Dark Ritual", "Giant Growth" }) {
            AssertJUnit.assertEquals(card, 0, c.importSurcharge(card));
        }
        final Set<HexColor> seen = EnumSet.noneOf(HexColor.class);
        for (final HexShopOffer o : c.shop()) {
            if (o.shop == HexShopType.RELIC || !o.tab.equals(HexCampaign.tabKey("Ann", HexCampaign.TAB_TOWN))) {
                continue;
            }
            final PaperCard card = HexContent.get().getCard(o.card);
            final byte bits = card.getRules().getColor().getColor();
            for (final HexColor color : HexColor.COLORS) {
                if ((bits & HexContent.bitOf(color)) != 0) {
                    seen.add(color);
                }
            }
        }
        AssertJUnit.assertEquals("the town's own shelves hold every colour", 5, seen.size());
    }

    @Test
    public void itIsReachedByFastTravelOnceFreed() {
        final HexCampaign c = campaign();
        c.battlesWon = c.rainbowTownAt();
        c.maybeOpenRainbowTown();
        final HexTile town = c.rainbowTown();
        AssertJUnit.assertTrue("the party starts in a town", c.inTown());
        for (final HexPlayer p : c.players) {
            p.gold = 1000;
        }
        AssertJUnit.assertFalse("not while besieged", c.canFastTravel(town));
        town.siege = null;
        town.explored = false;
        AssertJUnit.assertTrue("from any town once freed", c.canFastTravel(town));
        AssertJUnit.assertTrue(c.fastTravel(town.q, town.r));
        AssertJUnit.assertEquals(town, c.tile());
    }
}
