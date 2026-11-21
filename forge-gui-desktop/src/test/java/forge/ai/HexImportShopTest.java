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
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
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
                t.siege = null; // no sieges, unless a test sets one
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
        // 10% for every blue town not visited yet
        final List<HexTile> blue = townsOf(c, HexColor.BLUE);
        AssertJUnit.assertTrue("blue towns: " + blue.size(), blue.size() >= 2);
        final int per = HexCampaign.IMPORT_PERCENT_PER_TOWN;
        AssertJUnit.assertEquals(per * blue.size(), c.importSurcharge("Counterspell"));
        blue.get(0).explored = true;
        AssertJUnit.assertEquals(per * (blue.size() - 1), c.importSurcharge("Counterspell"));
        // a white and blue card: the colour the party knows least
        AssertJUnit.assertEquals(per * Math.max(blue.size() - 1, townsOf(c, HexColor.WHITE).size()), c.importSurcharge("Azorius Charm"));
        for (final HexTile t : blue) {
            t.explored = true;
        }
        AssertJUnit.assertEquals("every blue town visited: no surcharge", 0, c.importSurcharge("Counterspell"));
        // a besieged blue town: 20% more
        blue.get(0).siege = forge.gamemodes.hexcrawl.HexContent.get().randomEncounter(HexColor.BLUE, 0.5, new java.util.Random(3));
        AssertJUnit.assertEquals(HexCampaign.IMPORT_PERCENT_PER_SIEGE, c.importSurcharge("Counterspell"));
        blue.get(0).siege = null;

        final HexPlayer ann = c.player("Ann");
        final HexShopOffer offer = new HexShopOffer(HexShopType.INSTANT, "Azorius Charm", 100);
        AssertJUnit.assertEquals(c.priceFor(ann, 100 * (100 + c.importSurcharge("Azorius Charm")) / 100), c.priceFor(ann, offer));
    }

    /** Opens imports: a five-colour town (on a free field far from the party) whose siege is lifted. */
    private static void openImports(final HexCampaign c) {
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.NONE && forge.gamemodes.hexcrawl.HexMap.distance(t.q, t.r, c.partyQ, c.partyR) > 2) {
                t.poi = HexPoi.TOWN;
                t.rainbow = true;
                t.siege = null;
                break;
            }
        }
        AssertJUnit.assertTrue(c.importsOpen());
    }

    /** Ann's own tabs (random and picked): every card must suit the colour test. */
    private static int ownCards(final HexCampaign c, final java.util.function.Predicate<ColorSet> fits) {
        int n = 0;
        for (final HexShopOffer o : c.shop()) {
            if (o.shop == HexShopType.RELIC || !"Ann".equals(HexCampaign.ownerOfTab(o.tab))
                    || o.tab.equals(HexCampaign.tabKey("Ann", HexCampaign.TAB_TOWN))) {
                continue;
            }
            final ColorSet color = HexContent.get().getCard(o.card).getRules().getColor();
            AssertJUnit.assertTrue(o.card, color.isColorless() || fits.test(color));
            n++;
        }
        return n;
    }

    @Test
    public void aTownSellsTheRouteOfItsColourForTheWholeParty() {
        final HexCampaign c = inTownOf(HexColor.RED);
        AssertJUnit.assertTrue("none at the start", c.importRoutes().isEmpty());
        AssertJUnit.assertEquals(HexColor.RED.name(), c.importRouteForSale());
        final int price = HexCampaign.IMPORT_ROUTE_PRICE * c.goldMultiplier;
        AssertJUnit.assertEquals(price, c.importRoutePrice(HexColor.RED.name()));
        AssertJUnit.assertEquals(HexCampaign.IMPORT_RAINBOW_ROUTE_PRICE * c.goldMultiplier, c.importRoutePrice(HexCampaign.IMPORT_RAINBOW));
        c.player("Ann").gold = price - 1;
        AssertJUnit.assertFalse("too dear", c.buyImportRoute("Ann"));
        c.player("Ann").gold = price + 5;
        AssertJUnit.assertTrue(c.buyImportRoute("Ann"));
        AssertJUnit.assertEquals(5, c.player("Ann").gold);
        AssertJUnit.assertTrue(c.hasImportRoute(HexColor.RED.name()));
        AssertJUnit.assertNull("bought: no longer for sale", c.importRouteForSale());
        AssertJUnit.assertFalse(c.buyImportRoute("Bob"));
    }

    @Test
    public void aSaveFromBeforeRoutesKeepsTheImportsItUses() {
        final HexCampaign c = inTownOf(HexColor.RED);
        c.importRoutes = null; // as read from an old save
        c.player("Ann").importFrom = HexColor.BLUE.name();
        AssertJUnit.assertTrue(c.hasImportRoute(HexColor.BLUE.name()));
        AssertJUnit.assertFalse(c.hasImportRoute(HexColor.GREEN.name()));
        AssertJUnit.assertEquals(HexColor.BLUE.name(), c.importSource(c.player("Ann")));
    }

    @Test
    public void ownShelvesSellTheTownsGoodsUntilAnImportIsChosen() {
        final HexCampaign c = inTownOf(HexColor.RED);
        AssertJUnit.assertNull(c.importSource(c.player("Ann")));
        AssertJUnit.assertTrue(ownCards(c, ColorSet::hasRed) > 20);
    }

    @Test
    public void aHeadImportsFromTheColourItChooses() {
        final HexCampaign c = inTownOf(HexColor.RED);
        AssertJUnit.assertFalse("only over a route the party bought", c.chooseImport("Ann", HexColor.BLUE.name()));
        c.importRoutes().add(HexColor.BLUE.name());
        c.importRoutes().add(HexColor.GREEN.name());
        AssertJUnit.assertTrue(c.chooseImport("Ann", HexColor.BLUE.name()));
        AssertJUnit.assertEquals(HexColor.BLUE.name(), c.importSource(c.player("Ann")));
        AssertJUnit.assertTrue("blue at once", ownCards(c, ColorSet::hasBlue) > 20);
        for (final HexShopOffer o : c.shop()) {
            if (o.tab.equals(HexCampaign.tabKey("Ann", HexCampaign.TAB_TOWN)) && o.shop != HexShopType.RELIC) {
                final ColorSet color = HexContent.get().getCard(o.card).getRules().getColor();
                AssertJUnit.assertTrue("the town's tab keeps its colour " + o.card, color.isColorless() || color.hasRed());
            }
        }
        // as often as Ann likes: back to blue brings the same shelves, not new ones
        final List<String> blue = ownTabCards(c);
        AssertJUnit.assertTrue(c.chooseImport("Ann", HexColor.GREEN.name()));
        AssertJUnit.assertTrue(ownCards(c, ColorSet::hasGreen) > 20);
        AssertJUnit.assertTrue(c.chooseImport("Ann", HexColor.BLUE.name()));
        AssertJUnit.assertEquals("the same blue shelves", blue, ownTabCards(c));
        // paid new goods: new shelves
        c.player("Ann").gold = 10000;
        AssertJUnit.assertTrue(c.restock("Ann"));
        AssertJUnit.assertFalse("new shelves", blue.equals(ownTabCards(c)));
        AssertJUnit.assertTrue("Bob chooses for himself", c.chooseImport("Bob", null));
    }

    @Test
    public void theFiveColourTownOnlyOnceItIsFree() {
        final HexCampaign c = inTownOf(HexColor.RED);
        AssertJUnit.assertFalse("not before it is free", c.chooseImport("Ann", HexCampaign.IMPORT_RAINBOW));
        openImports(c);
        AssertJUnit.assertFalse("not without its route", c.chooseImport("Ann", HexCampaign.IMPORT_RAINBOW));
        c.importRoutes().add(HexCampaign.IMPORT_RAINBOW);
        AssertJUnit.assertTrue(c.chooseImport("Ann", HexCampaign.IMPORT_RAINBOW));
        final java.util.Set<HexColor> seen = java.util.EnumSet.noneOf(HexColor.class);
        ownCards(c, color -> {
            for (final HexColor h : HexColor.COLORS) {
                if (color.hasAnyColor(forge.card.MagicColor.fromName(h.getSymbol()))) {
                    seen.add(h);
                }
            }
            return true;
        });
        AssertJUnit.assertEquals("every colour", 5, seen.size());
        // besieged again (a new cycle's town not yet free): back to the town's goods
        c.rainbowTown().siege = HexContent.get().randomEncounter(HexColor.BLUE, 0.5, new java.util.Random(3));
        AssertJUnit.assertNull(c.importSource(c.player("Ann")));
    }

    /** The cards of Ann's own tabs, in shelf order. */
    private static List<String> ownTabCards(final HexCampaign c) {
        final List<String> cards = new ArrayList<>();
        for (final HexShopOffer o : c.shop()) {
            if (o.shop != HexShopType.RELIC && "Ann".equals(HexCampaign.ownerOfTab(o.tab))
                    && !o.tab.equals(HexCampaign.tabKey("Ann", HexCampaign.TAB_TOWN))) {
                cards.add(o.tab + "/" + o.card);
            }
        }
        java.util.Collections.sort(cards);
        return cards;
    }
}
