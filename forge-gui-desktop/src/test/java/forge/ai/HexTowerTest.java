package forge.ai;

import java.util.Arrays;
import java.util.Collections;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexEvent;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexTile;

/**
 * The Tower of the Archivist: bought once in a town for a high price (the gold setting's share of it, less the
 * archivist's seals), it stands on the map of this and every later cycle and sells any card at twice the archivist's price.
 */
public class HexTowerTest extends AITest {

    private static HexCampaign campaign(final int goldMultiplier) {
        final HexCampaign c = HexCampaign.create("Tower", 23L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)),
                goldMultiplier);
        c.encounterChance = 0;
        c.encounterChanceExplored = 0;
        return c;
    }

    private static void stand(final HexCampaign c, final HexTile t) {
        c.partyQ = c.prevQ = t.q;
        c.partyR = c.prevR = t.r;
    }

    private static HexTile town(final HexCampaign c) {
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.TOWN && t.siege == null && !t.rainbow) {
                return t;
            }
        }
        throw new AssertionError("no town");
    }

    @Test
    public void thePriceFollowsTheGoldSettingAndTheSeals() {
        AssertJUnit.assertEquals(40000, campaign(1).towerPrice());
        final HexCampaign c = campaign(5);
        AssertJUnit.assertEquals("Normal", 200000, c.towerPrice());
        c.archivistSeals = 4;
        AssertJUnit.assertEquals("four seals: 20% off", 160000, c.towerPrice());
        c.archivistSeals = 25;
        AssertJUnit.assertEquals("no more than ten seals count: half price", 100000, c.towerPrice());
        AssertJUnit.assertEquals(800000, campaign(20).towerPrice());
    }

    @Test
    public void aCardFromTheArchivistGivesASeal() {
        final HexCampaign c = campaign(5);
        final HexPlayer ann = c.player("Ann");
        ann.eventOffer = HexEvent.ARCHIVIST.name();
        ann.gold = 5000;
        AssertJUnit.assertTrue(c.chooseEvent("Ann", "Serra Angel", false));
        AssertJUnit.assertEquals(1, c.archivistSeals);
        AssertJUnit.assertEquals(190000, c.towerPrice());
    }

    @Test
    public void openedInTownItRisesOnTheMapAndSellsAnyCard() {
        final HexCampaign c = campaign(1);
        final HexPlayer ann = c.player("Ann");
        ann.gold = 50000;
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.NONE) {
                stand(c, t); // the party starts in a town: out on a field first
                break;
            }
        }
        AssertJUnit.assertFalse("only in a town", c.unlockTower("Ann"));
        stand(c, town(c));
        AssertJUnit.assertTrue(c.inTown());
        ann.gold = 39999;
        AssertJUnit.assertFalse("too dear", c.unlockTower("Ann"));
        ann.gold = 50000;
        AssertJUnit.assertTrue(c.unlockTower("Ann"));
        AssertJUnit.assertEquals(10000, ann.gold);
        AssertJUnit.assertTrue(c.towerUnlocked);
        AssertJUnit.assertFalse("once", c.unlockTower("Bob"));
        final HexTile tower = c.towerTile();
        AssertJUnit.assertNotNull(tower);
        AssertJUnit.assertTrue("a journey away", HexMap.distance(tower.q, tower.r, c.partyQ, c.partyR) >= 2);

        AssertJUnit.assertFalse("only in the tower", c.buyAtTower("Ann", "Lightning Bolt", false));
        stand(c, tower);
        AssertJUnit.assertTrue(c.atTower());
        final int price = HexCampaign.towerCardPrice("Black Lotus");
        AssertJUnit.assertEquals(2 * forge.gamemodes.hexcrawl.HexContent.get().archivistPrice("Black Lotus"), price);
        ann.gold = price;
        final int inDeck = Collections.frequency(ann.deck, "Black Lotus");
        AssertJUnit.assertTrue(c.buyAtTower("Ann", "Black Lotus", false));
        AssertJUnit.assertEquals(0, ann.gold);
        AssertJUnit.assertEquals(inDeck + 1, Collections.frequency(ann.deck, "Black Lotus"));
        AssertJUnit.assertFalse("no gold left", c.buyAtTower("Ann", "Black Lotus", false));
        ann.gold = 100000;
        AssertJUnit.assertFalse("not a card it sells", c.buyAtTower("Ann", "Forest", false));
        final int inSideboard = Collections.frequency(ann.sideboard, "Serra Angel");
        AssertJUnit.assertTrue(c.buyAtTower("Ann", "Serra Angel", true));
        AssertJUnit.assertEquals(inSideboard + 1, Collections.frequency(ann.sideboard, "Serra Angel"));
    }

    @Test
    public void itStandsOnTheMapOfEveryNewCycle() {
        final HexCampaign c = campaign(1);
        c.player("Ann").gold = 40000;
        c.archivistSeals = 3;
        stand(c, town(c));
        AssertJUnit.assertTrue(c.unlockTower("Ann"));
        c.won = true;
        AssertJUnit.assertTrue(c.startNewGamePlus());
        AssertJUnit.assertTrue(c.towerUnlocked);
        AssertJUnit.assertEquals("the seals stay too", 3, c.archivistSeals);
        final HexTile tower = c.towerTile();
        AssertJUnit.assertNotNull("on the new map", tower);
        AssertJUnit.assertTrue(HexMap.distance(tower.q, tower.r, c.partyQ, c.partyR) >= 2);
    }

    @Test
    public void aClosedTowerIsNotOnTheMap() {
        final HexCampaign c = campaign(5);
        AssertJUnit.assertNull(c.towerTile());
        AssertJUnit.assertFalse(c.atTower());
    }
}
