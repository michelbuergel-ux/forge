package forge.ai;

import forge.gamemodes.hexcrawl.HexActionEvent;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexTile;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

/** Cards of the sideboard marked for sale in the deck window, sold together in a town. */
public class HexSaleMarksTest extends AITest {

    private static HexCampaign campaign(final boolean inTown) {
        final HexCampaign c = HexCampaign.create("Sale", 17L, 6, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        if (inTown) {
            for (final HexTile t : c.map.tiles.values()) {
                if (t.poi == HexPoi.TOWN) {
                    c.partyQ = c.prevQ = t.q;
                    c.partyR = c.prevR = t.r;
                    break;
                }
            }
            AssertJUnit.assertTrue(c.inTown());
        } else {
            for (final HexTile t : c.map.tiles.values()) {
                if (t.poi == HexPoi.NONE) {
                    c.partyQ = c.prevQ = t.q;
                    c.partyR = c.prevR = t.r;
                    break;
                }
            }
            AssertJUnit.assertFalse(c.inTown());
        }
        final HexPlayer ann = c.player("Ann");
        ann.sideboard.addAll(Arrays.asList("Grizzly Bears", "Grizzly Bears", "Grizzly Bears", "Shock"));
        return c;
    }

    @Test
    public void markedCardsAreSoldTogetherInTown() {
        final HexCampaign c = campaign(false);
        final HexPlayer ann = c.player("Ann");
        // marked anywhere, only sideboard cards
        AssertJUnit.assertTrue(c.markForSale("Ann", "Grizzly Bears", true));
        AssertJUnit.assertFalse("once", c.markForSale("Ann", "Grizzly Bears", true));
        AssertJUnit.assertFalse("not in the sideboard", c.markForSale("Ann", "Lightning Bolt", true));
        AssertJUnit.assertTrue(c.markForSale("Ann", "Shock", true));
        AssertJUnit.assertEquals(0, c.sellMarked("Ann")); // not in a town
        AssertJUnit.assertEquals(Arrays.asList("Grizzly Bears", "Shock"), c.markedForSale(ann));
        AssertJUnit.assertTrue(c.markForSale("Ann", "Shock", false));

        final HexCampaign town = campaign(true);
        final HexPlayer a = town.player("Ann");
        town.markForSale("Ann", "Grizzly Bears", true);
        final int value = town.markedSaleValue(a);
        AssertJUnit.assertEquals(3 * town.sellPriceFor(a, "Grizzly Bears"), value);
        final int gold = a.gold;
        AssertJUnit.assertEquals(value, town.sellMarked("Ann"));
        AssertJUnit.assertEquals(gold + value, a.gold);
        AssertJUnit.assertEquals("every copy", 0, Collections.frequency(a.sideboard, "Grizzly Bears"));
        AssertJUnit.assertTrue("the rest stays", a.sideboard.contains("Shock"));
        AssertJUnit.assertTrue("the marks are gone", a.forSale.isEmpty());
    }

    @Test
    public void aCardTakenIntoTheDeckLosesItsMark() {
        final HexCampaign c = campaign(true);
        final HexPlayer ann = c.player("Ann");
        c.markForSale("Ann", "Shock", true);
        ann.sideboard.remove("Shock");
        ann.deck.add("Shock");
        AssertJUnit.assertTrue(c.markedForSale(ann).isEmpty());
        final int decked = Collections.frequency(ann.deck, "Shock");
        c.sellMarked("Ann");
        AssertJUnit.assertEquals("a deck card is not sold", decked, Collections.frequency(ann.deck, "Shock"));
    }

    @Test
    public void aGuestMarksAndSellsThroughTheHost() {
        final HexCampaign c = campaign(true);
        final HexSession s = new HexSession(c);
        s.handle("Ann", new HexActionEvent(HexActionEvent.MARK_SALE, 0, 0, "Grizzly Bears", true, "Ann"));
        AssertJUnit.assertEquals(Collections.singletonList("Grizzly Bears"), c.player("Ann").forSale);
        s.handle("Ann", new HexActionEvent(HexActionEvent.SELL_MARKED, 0, 0, null, false, "Ann"));
        AssertJUnit.assertFalse(c.player("Ann").sideboard.contains("Grizzly Bears"));
    }
}
