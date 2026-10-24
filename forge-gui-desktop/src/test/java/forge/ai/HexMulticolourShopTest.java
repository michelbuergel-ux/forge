package forge.ai;

import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.gamemodes.hexcrawl.HexShopType;
import forge.gamemodes.hexcrawl.HexTheme;
import forge.gamemodes.hexcrawl.HexTile;
import forge.item.PaperCard;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/** Multicoloured cards in the towns of the Hex Campaign: in every town of their colours, more of a pair on its border, three colours rare. */
public class HexMulticolourShopTest extends AITest {

    /** What the shops of a town sold over many stockings. */
    private static final class Tally {
        int cards;
        int gold;
        int twoColours;
        int manyColours;
        int pair;
        int goldLands;
    }

    private static ColorSet colours(final HexShopOffer o) {
        final PaperCard card = HexContent.get().getCard(o.card);
        return o.shop == HexShopType.LAND ? card.getRules().getColorIdentity() : card.getRules().getColor();
    }

    private static Tally tally(final HexColor town, final Set<HexColor> neighbours, final byte pair) {
        final Tally t = new Tally();
        for (int seed = 0; seed < 60; seed++) {
            for (final HexShopOffer o : HexContent.get().themeStock(town, neighbours, null, new Random(seed))) {
                t.cards++;
                final ColorSet c = colours(o);
                if (c.countColors() < 2) {
                    continue;
                }
                AssertJUnit.assertTrue(o.card + " has the town's colour", c.hasAnyColor(MagicColor.fromName(town.getSymbol())));
                t.gold++;
                if (c.countColors() == 2) {
                    t.twoColours++;
                    if (c.getColor() == pair) {
                        t.pair++;
                    }
                } else {
                    t.manyColours++;
                }
                if (o.shop == HexShopType.LAND) {
                    t.goldLands++;
                }
            }
        }
        return t;
    }

    @Test
    public void testEveryTownSellsMulticolouredCardsOfItsColour() {
        final byte redGreen = (byte) (MagicColor.RED | MagicColor.GREEN);
        final Tally inland = tally(HexColor.GREEN, Collections.emptySet(), redGreen);
        final double share = (double) inland.gold / inland.cards;
        AssertJUnit.assertTrue("about a sixth of the cards are multicoloured, not " + share, share > 0.08 && share < 0.25);
        AssertJUnit.assertTrue("three colours or more are rarer: " + inland.manyColours + " vs " + inland.twoColours,
                inland.manyColours > 0 && inland.manyColours < inland.twoColours);
        AssertJUnit.assertTrue("dual lands are sold too", inland.goldLands > 0);

        // on the border to the red region: more multicoloured cards, and mostly red-green ones
        final Tally border = tally(HexColor.GREEN, EnumSet.of(HexColor.RED), redGreen);
        AssertJUnit.assertTrue("more multicoloured cards on the border", border.gold > inland.gold);
        AssertJUnit.assertTrue("red-green leads on the red border: " + border.pair + " of " + border.twoColours,
                border.pair * 2 > border.twoColours);
        AssertJUnit.assertTrue("inland red-green is just one pair of four: " + inland.pair + " of " + inland.twoColours,
                inland.pair * 2 < inland.twoColours);
    }

    @Test
    public void testSliversReachEveryColouredTown() {
        final HexTheme slivers = HexTheme.get("tribe.Sliver");
        AssertJUnit.assertNotNull(slivers);
        for (final HexColor color : HexColor.COLORS) {
            AssertJUnit.assertTrue(color + " offers Slivers", HexContent.get().themesFor(color).contains(slivers));
        }
    }

    @Test
    public void testBorderTownsKnowTheirNeighbours() {
        final HexCampaign c = HexCampaign.create("Test", 42L, 6, Collections.singletonList(new HexPlayer("Ann", false)));
        boolean someBorder = false;
        for (final HexTile town : c.map.tilesWith(HexPoi.TOWN)) {
            final Set<HexColor> neighbours = c.borderColours(town);
            AssertJUnit.assertFalse(neighbours.contains(town.color));
            AssertJUnit.assertFalse(neighbours.contains(HexColor.COLORLESS));
            someBorder |= !neighbours.isEmpty();
        }
        AssertJUnit.assertTrue("some town lies on a border", someBorder);
    }
}
