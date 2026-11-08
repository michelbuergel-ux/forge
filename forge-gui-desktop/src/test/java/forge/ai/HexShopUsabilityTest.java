package forge.ai;

import java.util.Arrays;
import java.util.Collections;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexCardSearch;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.gamemodes.hexcrawl.HexShopType;
import forge.gamemodes.hexcrawl.HexTile;
import forge.gamemodes.net.event.HexActionEvent;
import forge.item.PaperCard;
import forge.model.FModel;

/** The shop: cards bought into the deck or the sideboard, a purchase undone, and the search over the whole town. */
public class HexShopUsabilityTest extends AITest {

    /** A campaign with the party in a town, Ann rich. */
    private static HexCampaign inTown() {
        final HexCampaign c = HexCampaign.create("Shop", 17L, 6, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.TOWN) {
                c.partyQ = c.prevQ = t.q;
                c.partyR = c.prevR = t.r;
                break;
            }
        }
        AssertJUnit.assertTrue(c.inTown());
        c.player("Ann").gold = 100000;
        c.shop();
        return c;
    }

    /** One of Ann's card offers (not a relic). */
    private static HexShopOffer annsOffer(final HexCampaign c, final int skip) {
        int n = 0;
        for (final HexShopOffer o : c.shop()) {
            if (o.shop != HexShopType.RELIC && "Ann".equals(HexCampaign.ownerOfTab(o.tab)) && n++ == skip) {
                return o;
            }
        }
        throw new AssertionError("no offer for Ann");
    }

    @Test
    public void testACardBoughtIntoTheSideboardAndUndone() {
        final HexCampaign c = inTown();
        final HexPlayer ann = c.player("Ann");
        final HexShopOffer offer = annsOffer(c, 0);
        final int deck = ann.deck.size();
        final int gold = ann.gold;
        final int inSide = Collections.frequency(ann.sideboard, offer.card);

        AssertJUnit.assertTrue(c.buy("Ann", offer, true));
        AssertJUnit.assertEquals("the deck is left alone", deck, ann.deck.size());
        AssertJUnit.assertEquals(inSide + 1, Collections.frequency(ann.sideboard, offer.card));
        AssertJUnit.assertFalse(c.shop().contains(offer));
        final int paid = gold - ann.gold;
        AssertJUnit.assertTrue(paid > 0);

        // undone: sold back at what it cost, the offer back on the shelf
        AssertJUnit.assertTrue(c.sell("Ann", offer.card, true));
        AssertJUnit.assertEquals(gold, ann.gold);
        AssertJUnit.assertEquals(inSide, Collections.frequency(ann.sideboard, offer.card));
        AssertJUnit.assertTrue(c.shop().contains(offer));
    }

    @Test
    public void testACardBoughtIntoTheDeckAndUndone() {
        final HexCampaign c = inTown();
        final HexPlayer ann = c.player("Ann");
        final HexShopOffer offer = annsOffer(c, 1);
        final int deck = ann.deck.size();
        final int gold = ann.gold;
        AssertJUnit.assertTrue(c.buy("Ann", offer.shop, -1, offer.card));
        AssertJUnit.assertEquals(deck + 1, ann.deck.size());
        AssertJUnit.assertTrue(c.sell("Ann", offer.card, false));
        AssertJUnit.assertEquals(deck, ann.deck.size());
        AssertJUnit.assertEquals(gold, ann.gold);
    }

    @Test
    public void testAGuestBuysIntoTheSideboard() {
        final HexCampaign c = inTown();
        final HexSession s = new HexSession(c);
        s.setGuests(Collections.singletonList("Bob"));
        // the host buys for Ann over the same way a guest's action comes in
        final HexShopOffer offer = annsOffer(c, 2);
        final HexPlayer ann = c.player("Ann");
        final int deck = ann.deck.size();
        s.handle("Ann", new HexActionEvent(HexActionEvent.BUY, offer.shop.ordinal(), -1, offer.card, true, "Ann"));
        AssertJUnit.assertEquals(deck, ann.deck.size());
        AssertJUnit.assertTrue(ann.sideboard.contains(offer.card));
    }

    private static PaperCard card(final String name) {
        final PaperCard card = FModel.getMagicDb().getCommonCards().getCard(name);
        AssertJUnit.assertNotNull(name, card);
        return card;
    }

    @Test
    public void testTheSearchMatchesNameTypeAndText() {
        AssertJUnit.assertTrue(HexCardSearch.matches(card("Lightning Bolt"), "lightning"));
        AssertJUnit.assertTrue(HexCardSearch.matches(card("Lightning Bolt"), "  BOLT "));
        AssertJUnit.assertTrue("by its text", HexCardSearch.matches(card("Serra Angel"), "flying"));
        AssertJUnit.assertFalse(HexCardSearch.matches(card("Grizzly Bears"), "flying"));
        AssertJUnit.assertTrue("by its type line", HexCardSearch.matches(card("Goblin Guide"), "goblin"));
        AssertJUnit.assertTrue("by its type line", HexCardSearch.matches(card("Raging Goblin"), "creature"));
        AssertJUnit.assertFalse("reminder text does not count", HexCardSearch.matches(card("Crush of Tentacles"), "teammate"));
        AssertJUnit.assertTrue("an empty search finds everything", HexCardSearch.matches(card("Grizzly Bears"), " "));
        AssertJUnit.assertFalse(HexCardSearch.matches(null, "bear"));
    }
}
