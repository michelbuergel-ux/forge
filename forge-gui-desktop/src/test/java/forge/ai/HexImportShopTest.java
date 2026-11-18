package forge.ai;

import forge.card.ColorSet;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.gamemodes.hexcrawl.HexShopType;
import forge.gamemodes.hexcrawl.HexTile;
import forge.item.PaperCard;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Imports: a head's own shelves sell its deck's colours in any town, at a surcharge that falls with every town of the colour. */
public class HexImportShopTest extends AITest {

    /** A campaign with the party in a town of this colour; the other towns not yet visited. */
    private static HexCampaign inTownOf(final HexColor color) {
        final HexCampaign c = HexCampaign.create("Imports", 23L, 6, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        HexTile town = null;
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.TOWN) {
                t.explored = false;
                if (town == null && t.color == color) {
                    town = t;
                }
            }
        }
        AssertJUnit.assertNotNull("a town of " + color, town);
        town.explored = true;
        c.partyQ = c.prevQ = town.q;
        c.partyR = c.prevR = town.r;
        AssertJUnit.assertTrue(c.inTown());
        return c;
    }

    private static List<HexTile> townsOf(final HexCampaign c, final HexColor color) {
        final List<HexTile> result = new ArrayList<>();
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.TOWN && t.color == color) {
                result.add(t);
            }
        }
        return result;
    }

    @Test
    public void anImportCostsLessWithEveryTownOfItsColour() {
        final HexCampaign c = inTownOf(HexColor.RED);
        AssertJUnit.assertEquals("the town's colour", 0, c.importSurcharge("Lightning Bolt"));
        AssertJUnit.assertEquals("colourless", 0, c.importSurcharge("Sol Ring"));
        AssertJUnit.assertEquals("of the town's colour too", 0, c.importSurcharge("Izzet Charm"));
        AssertJUnit.assertEquals(HexCampaign.IMPORT_SURCHARGE_PERCENT, c.importSurcharge("Counterspell"));
        final List<HexTile> blue = townsOf(c, HexColor.BLUE);
        AssertJUnit.assertTrue("blue towns: " + blue.size(), blue.size() >= 2);
        blue.get(0).explored = true;
        AssertJUnit.assertEquals(HexCampaign.IMPORT_SURCHARGE_PERCENT - HexCampaign.IMPORT_DISCOUNT_PER_TOWN, c.importSurcharge("Counterspell"));
        // a white and blue card: the colour the party knows least
        AssertJUnit.assertEquals(HexCampaign.IMPORT_SURCHARGE_PERCENT, c.importSurcharge("Azorius Charm"));
        blue.get(1).explored = true;
        if (blue.size() > 2) {
            blue.get(2).explored = true;
            AssertJUnit.assertEquals("three towns: no surcharge", 0, c.importSurcharge("Counterspell"));
        }

        final HexPlayer ann = c.player("Ann");
        final HexShopOffer offer = new HexShopOffer(HexShopType.INSTANT, "Azorius Charm", 100);
        AssertJUnit.assertEquals(c.priceFor(ann, 100 * (100 + c.importSurcharge("Azorius Charm")) / 100), c.priceFor(ann, offer));
    }

    @Test
    public void aHeadsOwnShelvesFollowItsDeck() {
        final HexCampaign c = inTownOf(HexColor.RED);
        final HexPlayer ann = c.player("Ann");
        ann.deck = new ArrayList<>(Collections.nCopies(20, "Counterspell"));
        ann.deck.addAll(Collections.nCopies(20, "Island"));
        c.shop();
        int ownCards = 0;
        int townCards = 0;
        for (final HexShopOffer o : c.shop()) {
            if (o.shop == HexShopType.RELIC || !"Ann".equals(HexCampaign.ownerOfTab(o.tab))) {
                continue;
            }
            final PaperCard card = HexContent.get().getCard(o.card);
            final ColorSet color = card.getRules().getColor();
            if (o.tab.equals(HexCampaign.tabKey("Ann", HexCampaign.TAB_TOWN))) {
                townCards++;
                AssertJUnit.assertTrue("the town's tab: red or colourless " + o.card, color.isColorless() || color.hasRed());
            } else {
                ownCards++;
                AssertJUnit.assertTrue("Ann's own tabs: blue or colourless " + o.card, color.isColorless() || color.hasBlue());
            }
        }
        AssertJUnit.assertTrue(ownCards > 20);
        AssertJUnit.assertTrue(townCards > 20);
    }
}
