package forge.ai;

import java.util.Arrays;
import java.util.List;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.gamemodes.hexcrawl.HexTile;

/** Besieged towns: where they are, the fight at the gates, and the cheaper shops of a freed town. */
public class HexSiegeTest extends AITest {

    private static HexCampaign newCampaign(final long seed, final int radius) {
        return HexCampaign.create("Siege", seed, radius, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
    }

    /** Puts the party next to a besieged town, on a free field (and the town in its sight). */
    private static HexTile besiegedNextToParty(final HexCampaign c) {
        for (final HexTile town : c.besiegedTowns()) {
            for (final HexTile n : c.map.neighbors(town)) {
                if (n.poi == HexPoi.NONE && c.questAt(n.q, n.r) == null) {
                    c.partyQ = c.prevQ = n.q;
                    c.partyR = c.prevR = n.r;
                    for (final HexTile any : c.map.tiles.values()) {
                        any.enemy = null;
                        any.event = null;
                    }
                    return town;
                }
            }
        }
        throw new AssertionError("no besieged town with a free field next to it");
    }

    @Test
    public void aMapStartsWithOneBesiegedTownButNotTheStart() {
        for (final int radius : new int[] { 6, 7, 8 }) {
            for (long seed = 0; seed < 20; seed++) {
                final HexCampaign c = newCampaign(seed, radius);
                final String where = "radius " + radius + ", seed " + seed;
                AssertJUnit.assertTrue(where, c.siegesPlaced);
                AssertJUnit.assertNull("the start town is open: " + where, c.tile().siege);
                AssertJUnit.assertTrue("the party starts in town: " + where, c.inTown());
                int besieged = 0;
                for (final HexTile t : c.map.tiles.values()) {
                    if (t.siege != null) {
                        besieged++;
                        AssertJUnit.assertEquals(HexPoi.TOWN, t.poi);
                        AssertJUnit.assertTrue(t.siege.siege);
                        AssertJUnit.assertTrue("copies of itself as heads", t.siege.headsAlike());
                        AssertJUnit.assertSame(t.siege, c.enemyAt(t));
                    }
                }
                AssertJUnit.assertEquals(where, HexCampaign.START_SIEGES, besieged);
            }
        }
    }

    @Test
    public void aBesiegerIsTougherThanAWantedEnemy() {
        final HexCampaign c = newCampaign(3L, 7);
        final HexEncounter enc = c.besiegedTowns().get(0).siege;
        final boolean was = enc.siege;
        enc.siege = false;
        final int plain = c.enemyLife(enc);
        enc.siege = was;
        AssertJUnit.assertEquals(plain + HexCampaign.SIEGE_LIFE_PER_HEAD * 2, c.enemyLife(enc));
    }

    @Test
    public void theGatesStayShutUntilTheBesiegerIsBeaten() {
        final HexCampaign c = newCampaign(5L, 7);
        final HexTile town = besiegedNextToParty(c);
        final HexTile before = c.tile();

        // lost: back to the field before, the siege stays
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(town.q, town.r));
        AssertJUnit.assertTrue(c.pendingEncounter.siege);
        AssertJUnit.assertFalse("no shopping while besieged", c.inTown());
        c.resolveBattle(HexBattle.Result.LOST);
        AssertJUnit.assertSame(before, c.tile());
        AssertJUnit.assertNotNull(town.siege);
        AssertJUnit.assertFalse(town.freed);

        // fled: the same
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(town.q, town.r));
        c.resolveBattle(HexBattle.Result.FLED);
        AssertJUnit.assertSame(before, c.tile());
        AssertJUnit.assertNotNull(town.siege);

        // won: the town is free and the party in it
        final int serial = c.freedSerial;
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(town.q, town.r));
        final String besieger = c.pendingEncounter.enemyName;
        c.resolveBattle(HexBattle.Result.WON);
        AssertJUnit.assertSame(town, c.tile());
        AssertJUnit.assertNull(town.siege);
        AssertJUnit.assertTrue(town.freed);
        AssertJUnit.assertNull(c.enemyAt(town));
        AssertJUnit.assertEquals(serial + 1, c.freedSerial);
        AssertJUnit.assertEquals(town.name, c.freedTown);
        AssertJUnit.assertEquals(besieger, c.freedFrom);
        AssertJUnit.assertFalse("the shelves are stocked for everybody", c.shops.get(town.key()).isEmpty());
        c.pendingRewards.clear();
        AssertJUnit.assertTrue(c.inTown());
    }

    @Test
    public void aFreedTownSellsCheaper() {
        final HexCampaign c = newCampaign(7L, 7);
        final HexPlayer ann = c.player("Ann");
        AssertJUnit.assertEquals(100, c.priceFor(ann, 100));
        AssertJUnit.assertEquals(HexCampaign.RESTOCK_PRICE, c.townPrice(HexCampaign.RESTOCK_PRICE));
        c.tile().freed = true;
        AssertJUnit.assertEquals(75, c.priceFor(ann, 100));
        AssertJUnit.assertEquals(HexCampaign.RESTOCK_PRICE * 3 / 4, c.townPrice(HexCampaign.RESTOCK_PRICE));

        // what is paid is the cheaper price
        ann.gold = 1000;
        HexShopOffer bought = null;
        for (final HexShopOffer offer : new java.util.ArrayList<>(c.shop())) {
            if (offer.shop != forge.gamemodes.hexcrawl.HexShopType.RELIC && c.buy("Ann", offer)) {
                bought = offer; // the first card on her own shelves
                break;
            }
        }
        AssertJUnit.assertNotNull(bought);
        AssertJUnit.assertEquals(1000 - c.priceFor(ann, bought.price), ann.gold);
        final int gold = ann.gold;
        AssertJUnit.assertTrue(c.restock("Ann"));
        AssertJUnit.assertEquals(gold - c.townPrice(HexCampaign.RESTOCK_PRICE), ann.gold);
        final int before = ann.gold;
        AssertJUnit.assertTrue(c.buyBasicLand("Ann", HexColor.RED));
        AssertJUnit.assertEquals(before - c.townPrice(HexCampaign.BASIC_LAND_PRICE), ann.gold);
    }

    @Test
    public void noFastTravelIntoABesiegedTown() {
        final HexCampaign c = newCampaign(9L, 7);
        final HexTile town = c.besiegedTowns().get(0);
        town.explored = true;
        AssertJUnit.assertFalse(c.canFastTravel(town));
        town.siege = null;
        AssertJUnit.assertTrue(c.canFastTravel(town));
    }

    @Test
    public void armiesBesiegeMoreTownsAfterWinsButNeverMoreThanThereAreHeads() {
        for (final int heads : new int[] { 2, 3 }) {
            final java.util.List<HexPlayer> party = new java.util.ArrayList<>();
            for (int h = 0; h < heads; h++) {
                party.add(new HexPlayer("Head" + h, false));
            }
            final HexCampaign c = HexCampaign.create("Siege", 13L, 8, party);
            AssertJUnit.assertEquals(heads, c.maxSieges());
            AssertJUnit.assertEquals(HexCampaign.START_SIEGES, c.besiegedTowns().size());
            int most = 0;
            for (int i = 0; i < 200; i++) {
                c.pendingEncounter = forge.gamemodes.hexcrawl.HexContent.get().randomEncounter(HexColor.RED, 0.5, new java.util.Random(i));
                c.pendingEncounter.q = c.partyQ;
                c.pendingEncounter.r = c.partyR;
                c.resolveBattle(HexBattle.Result.WON);
                c.pendingRewards.clear();
                final List<HexTile> now = c.besiegedTowns();
                most = Math.max(most, now.size());
                for (final HexTile t : now) {
                    AssertJUnit.assertFalse(t.freed);
                    if (t.siege.lateSiege) {
                        AssertJUnit.assertTrue("not next to the party", forge.gamemodes.hexcrawl.HexMap.distance(t.q, t.r, c.partyQ, c.partyR) >= 2);
                    }
                }
            }
            AssertJUnit.assertEquals(heads + " heads: new sieges come, but never more at once than heads", heads, most);
        }
    }

    @Test
    public void newGamePlusBringsNewSieges() {
        final HexCampaign c = newCampaign(15L, 7);
        for (final HexTile t : c.besiegedTowns()) {
            t.siege = null;
            t.freed = true;
        }
        c.won = true;
        c.startNewGamePlus();
        AssertJUnit.assertEquals(HexCampaign.START_SIEGES, c.besiegedTowns().size());
        for (final HexTile t : c.map.tilesWith(HexPoi.TOWN)) {
            AssertJUnit.assertFalse("a new map: nothing freed yet", t.freed);
        }
    }

    @Test
    public void anOldSaveGetsSiegesOnlyWhereThePartyHasNotBeen() {
        final HexCampaign c = newCampaign(17L, 7);
        // as a save from before there were sieges
        for (final HexTile t : c.besiegedTowns()) {
            t.siege = null;
        }
        c.siegesPlaced = false;
        final HexTile visited = c.map.tilesWith(HexPoi.TOWN).stream().filter(t -> t != c.tile()).findFirst().get();
        visited.explored = true;
        HexTile step = null;
        for (final HexTile n : c.map.neighbors(c.tile())) {
            if (n.poi == HexPoi.NONE && c.questAt(n.q, n.r) == null) {
                n.enemy = null;
                n.event = null;
                step = n;
                break;
            }
        }
        AssertJUnit.assertNotNull(step);
        c.moveTo(step.q, step.r);
        AssertJUnit.assertTrue(c.siegesPlaced);
        AssertJUnit.assertFalse(c.besiegedTowns().isEmpty());
        for (final HexTile t : c.besiegedTowns()) {
            AssertJUnit.assertFalse("only towns the party has not been to", t.explored);
        }
        AssertJUnit.assertNull(visited.siege);
    }
}
