package forge.ai;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.gamemodes.hexcrawl.HexShopType;
import forge.gamemodes.hexcrawl.HexTheme;
import forge.item.PaperCard;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The theme tabs of the Hex Campaign's town shops. */
public class HexShopThemesTest extends AITest {

    private static HexCampaign newCampaign() {
        final HexCampaign c = HexCampaign.create("Test", 42L, 4, Collections.singletonList(new HexPlayer("Ann", false)));
        AssertJUnit.assertEquals("the party starts in a town", HexPoi.TOWN, c.tile().poi);
        return c;
    }

    @Test
    public void testThemesSuitTheTownColour() {
        final HexContent content = HexContent.get();
        AssertJUnit.assertTrue(content.themesFor(HexColor.RED).contains(HexTheme.get("tribe.Goblin")));
        AssertJUnit.assertTrue(content.themesFor(HexColor.GREEN).contains(HexTheme.get("tribe.Elf")));
        for (final HexColor color : HexColor.COLORS) {
            AssertJUnit.assertTrue("few themes for " + color, content.themesFor(color).size() >= 8);
        }

        // a rich theme: every card fits it and the town's colour (or is colourless)
        final HexTheme goblins = HexTheme.get("tribe.Goblin");
        final List<HexShopOffer> stock = content.themeStock(HexColor.RED, goblins, new Random(3));
        int creatures = 0;
        for (final HexShopOffer o : stock) {
            AssertJUnit.assertEquals(goblins.id, o.theme);
            if (o.shop != HexShopType.CREATURE) {
                continue;
            }
            creatures++;
            final PaperCard card = content.getCard(o.card);
            AssertJUnit.assertTrue(o.card + " is no Goblin card", goblins.fits(card.getRules()));
            final byte color = card.getRules().getColor().getColor();
            AssertJUnit.assertTrue(o.card + " is not red", color == forge.card.MagicColor.RED || color == forge.card.MagicColor.COLORLESS);
        }
        AssertJUnit.assertEquals(HexContent.CARDS_PER_SHOP, creatures);
    }

    @Test
    public void testTownRandomAndPickedThemes() {
        final HexCampaign c = newCampaign();
        final List<HexShopOffer> stock = c.shop();
        final HexTheme town = c.townTheme();
        final HexTheme random = c.randomTheme();
        AssertJUnit.assertNotNull(c.tile().theme);
        AssertJUnit.assertFalse("the random theme differs from the town's", town.equals(random));
        AssertJUnit.assertFalse(c.shop(HexShopType.CREATURE, town.id).isEmpty());
        AssertJUnit.assertFalse(c.shop(HexShopType.CREATURE, random.id).isEmpty());
        AssertJUnit.assertFalse(c.shop(HexShopType.RELIC, null).isEmpty() && stock.isEmpty());

        // pick a theme: its shelves are stocked, and it is locked until the next fight
        HexTheme pick = null;
        for (final HexTheme t : c.themeChoices()) {
            if (!t.equals(town) && !t.equals(random)) {
                pick = t;
                break;
            }
        }
        AssertJUnit.assertNotNull(pick);
        AssertJUnit.assertTrue(c.mayPickTheme("Ann", 0));
        AssertJUnit.assertTrue(c.pickTheme("Ann", 0, pick.id));
        AssertJUnit.assertEquals(pick.id, c.player("Ann").themes[0]);
        AssertJUnit.assertFalse(c.shop(HexShopType.CREATURE, pick.id).isEmpty());
        AssertJUnit.assertFalse("one change per fight", c.pickTheme("Ann", 0, town.id));
        AssertJUnit.assertTrue("the other slot is still free", c.mayPickTheme("Ann", 1));

        // buying in the theme tab
        HexShopOffer cheapest = null;
        for (final HexShopOffer o : c.shop(HexShopType.CREATURE, pick.id)) {
            if (cheapest == null || o.price < cheapest.price) {
                cheapest = o;
            }
        }
        final int gold = c.player("Ann").gold;
        AssertJUnit.assertTrue(c.buy("Ann", HexShopType.CREATURE, pick.id, cheapest.card));
        AssertJUnit.assertEquals(gold - cheapest.price, c.player("Ann").gold);
        AssertJUnit.assertTrue(c.player("Ann").deck.contains(cheapest.card));

        // after a fight: the pick may change again and the random theme is rolled anew
        c.battlesWon++;
        AssertJUnit.assertTrue(c.mayPickTheme("Ann", 0));
        c.shop();
        AssertJUnit.assertEquals(Integer.valueOf(c.fights()), c.randomThemeAt.get(c.tile().key()));
        AssertJUnit.assertEquals("the town keeps its theme", town, c.townTheme());
    }

    @Test
    public void testSaveFromBeforeThemesGetsThemedShops() {
        final HexCampaign c = newCampaign();
        for (final HexShopOffer o : c.shop()) {
            o.theme = null;
        }
        c.tile().theme = null;
        c.shop();
        AssertJUnit.assertNotNull("the town got a theme", c.tile().theme);
        for (final HexShopOffer o : c.shop()) {
            AssertJUnit.assertTrue(o.shop == HexShopType.RELIC || o.theme != null);
        }
    }
}
