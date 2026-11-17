package forge.ai;

import forge.card.CardRarity;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEvent;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexTile;
import forge.item.PaperCard;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** The wandering archivist: a rare event at which every head may buy any card the shops know, at the archivist's price. */
public class HexArchivistTest extends AITest {

    /** A card the archivist gets, of this rarity and without a price of its own (its price comes from the rarity). */
    private static PaperCard ordinary(final CardRarity rarity) {
        final HexContent content = HexContent.get();
        for (final PaperCard card : content.archivistCards()) {
            if (card.getRarity() == rarity && content.price(card.getName()) * 3 / 2 < content.archivistPrice(card.getName())) {
                return card;
            }
        }
        throw new AssertionError("no " + rarity);
    }

    @Test
    public void thePricesAreTheLeastForTheRarityOrOneAndAHalfTimesTheWorth() {
        final HexContent content = HexContent.get();
        AssertJUnit.assertEquals(60, content.archivistPrice(ordinary(CardRarity.Common).getName()));
        AssertJUnit.assertEquals(150, content.archivistPrice(ordinary(CardRarity.Uncommon).getName()));
        AssertJUnit.assertEquals(500, content.archivistPrice(ordinary(CardRarity.Rare).getName()));
        AssertJUnit.assertEquals(1000, content.archivistPrice(ordinary(CardRarity.MythicRare).getName()));
        // a treasure with a price of its own: one and a half times that
        AssertJUnit.assertTrue(content.archivistSells("Sol Ring"));
        AssertJUnit.assertEquals(Math.round(content.price("Sol Ring") * 1.5), content.archivistPrice("Sol Ring"));
    }

    @Test
    public void heGetsWhatTheShopsMaySell() {
        final HexContent content = HexContent.get();
        AssertJUnit.assertTrue("thousands of cards: " + content.archivistCards().size(), content.archivistCards().size() > 5000);
        for (final String name : new String[] { "Lightning Bolt", "Counterspell", "Llanowar Elves", "Serra Angel", "Wrath of God" }) {
            AssertJUnit.assertTrue(name, content.archivistSells(name));
        }
        for (final String name : new String[] { "Forest", "Command Tower", "Tome of Retold Tales", "Arzakon's Platinum Angel" }) {
            AssertJUnit.assertFalse(name, content.archivistSells(name));
        }
    }

    @Test
    public void aHeadBuysTheCardItNames() {
        final HexCampaign c = HexCampaign.create("Archive", 17L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Cid", true)));
        c.encounterChance = 0;
        c.encounterChanceExplored = 0;
        HexTile t = null;
        for (final HexTile n : c.map.neighbors(c.tile())) {
            if (n.poi == HexPoi.NONE && c.questAt(n.q, n.r) == null) {
                t = n;
                break;
            }
        }
        AssertJUnit.assertNotNull(t);
        t.enemy = null;
        t.event = HexEvent.ARCHIVIST.name();
        c.moveTo(t.q, t.r);
        final HexPlayer ann = c.player("Ann");
        AssertJUnit.assertEquals(HexEvent.ARCHIVIST.name(), ann.eventOffer);
        AssertJUnit.assertNull("companions leave it", c.player("Cid").eventOffer);
        AssertJUnit.assertTrue(HexEvent.ARCHIVIST.isChoice());

        final String bolt = "Lightning Bolt";
        final int price = HexContent.get().archivistPrice(bolt);
        ann.gold = price - 1;
        AssertJUnit.assertFalse("too dear", c.chooseEvent("Ann", bolt, false));
        ann.gold = price + 7;
        AssertJUnit.assertFalse("not a card he gets", c.chooseEvent("Ann", "Forest", false));
        AssertJUnit.assertFalse(c.chooseEvent("Ann", "No Such Card", false));
        final int inDeck = Collections.frequency(ann.deck, bolt);
        AssertJUnit.assertTrue(c.chooseEvent("Ann", bolt, false));
        AssertJUnit.assertEquals("paid", 7, ann.gold);
        AssertJUnit.assertEquals("into the deck", inDeck + 1, Collections.frequency(ann.deck, bolt));
        AssertJUnit.assertNull(ann.eventOffer);
        ann.gold = 10000;
        AssertJUnit.assertFalse("only once", c.chooseEvent("Ann", bolt, false));
    }

    @Test
    public void intoTheSideboardIfTheHeadSaysSo() {
        final HexCampaign c = HexCampaign.create("Archive", 19L, 4, Arrays.asList(new HexPlayer("Ann", false)));
        final HexPlayer ann = c.player("Ann");
        ann.eventOffer = HexEvent.ARCHIVIST.name();
        ann.gold = 2000;
        final int inSideboard = Collections.frequency(ann.sideboard, "Serra Angel");
        AssertJUnit.assertTrue(c.chooseEvent("Ann", "Serra Angel", true));
        AssertJUnit.assertEquals(inSideboard + 1, Collections.frequency(ann.sideboard, "Serra Angel"));
        AssertJUnit.assertEquals(2000 - HexContent.get().archivistPrice("Serra Angel"), ann.gold);
    }

    @Test
    public void heIsAsRareAsTheMirror() {
        final Map<HexEvent, Integer> seen = new EnumMap<>(HexEvent.class);
        final Random rnd = new Random(9);
        final int rolls = 90000;
        for (int i = 0; i < rolls; i++) {
            seen.merge(HexEvent.random(rnd), 1, Integer::sum);
        }
        final int archivists = seen.getOrDefault(HexEvent.ARCHIVIST, 0);
        final int mirrors = seen.getOrDefault(HexEvent.MIRROR, 0);
        final int share = rolls / HexEvent.values().length;
        AssertJUnit.assertTrue("it comes: " + seen, archivists > share / 8);
        AssertJUnit.assertTrue("rare: " + seen, archivists < share / 3);
        AssertJUnit.assertTrue("like the mirror: " + seen, Math.abs(archivists - mirrors) < share / 10);
    }
}
