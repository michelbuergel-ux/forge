package forge.ai;

import java.util.Arrays;
import java.util.Random;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexCampaignIO;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexRelic;
import forge.gamemodes.hexcrawl.HexReward;
import forge.gamemodes.hexcrawl.HexShopOffer;

/** The bosses' trophies: won, grown a tier with every new win, worn or put away, never sold. */
public class HexTrophyTest extends AITest {

    private static HexCampaign newCampaign(final boolean aiFriend) {
        return HexCampaign.create("Trophies", 11L, 6, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", aiFriend)));
    }

    /** The party beats the boss of this colour (as often as the test likes: a later cycle's boss). */
    private static void beatBoss(final HexCampaign c, final HexColor color) {
        final HexEncounter boss = HexContent.get().bossEncounter(color);
        boss.q = c.partyQ;
        boss.r = c.partyR;
        c.pendingEncounter = boss;
        c.resolveBattle(HexBattle.Result.WON);
    }

    @Test
    public void everyBossHasATrophyOfEveryTier() {
        for (final HexColor color : HexColor.values()) {
            for (int tier = 1; tier <= HexContent.MAX_RELIC_TIER; tier++) {
                final HexRelic trophy = HexContent.get().trophy(color, tier);
                AssertJUnit.assertNotNull(color + " " + tier, trophy);
                AssertJUnit.assertEquals(HexCampaign.TROPHY_SLOT, trophy.slot);
                AssertJUnit.assertTrue(HexContent.get().isTrophy(trophy.name));
            }
        }
    }

    @Test
    public void aTrophyGrowsWithEveryWinUpToTierThree() {
        final HexCampaign c = newCampaign(false);
        final HexPlayer ann = c.player("Ann");
        final String[] names = { "Akroma's Halo", "Akroma's Halo II", "Akroma's Halo III", "Akroma's Halo III" };
        for (int win = 0; win < names.length; win++) {
            beatBoss(c, HexColor.WHITE);
            AssertJUnit.assertEquals(Math.min(3, win + 1), (int) ann.trophies.get("WHITE"));
            AssertJUnit.assertTrue("worn: " + ann.relics, ann.relics.contains(names[win]));
            AssertJUnit.assertEquals("one relic on the slot", names[win], c.relicInSlot(ann, HexCampaign.TROPHY_SLOT).name);
            final HexReward reward = c.pendingReward("Ann");
            AssertJUnit.assertEquals(names[win], reward.trophy);
            AssertJUnit.assertEquals(names[win], c.pendingReward("Bob").trophy);
            c.pendingRewards.clear();
        }
    }

    @Test
    public void trophiesAreNeverSoldOrFound() {
        final HexContent content = HexContent.get();
        for (int cycle = 1; cycle <= 3; cycle++) {
            for (final HexRelic relic : content.ordinaryRelics(cycle)) {
                AssertJUnit.assertNull(relic.name, relic.trophy);
            }
            for (int seed = 0; seed < 40; seed++) {
                for (final HexShopOffer offer : content.relicStock(new Random(seed), cycle, false)) {
                    AssertJUnit.assertFalse(offer.card, content.isTrophy(offer.card));
                }
            }
        }
        for (final HexRelic avatar : content.avatars(false)) {
            AssertJUnit.assertNull(avatar.trophy);
        }
        // nor sold back to a town
        final HexCampaign c = newCampaign(false);
        beatBoss(c, HexColor.RED);
        c.pendingRewards.clear();
        AssertJUnit.assertTrue(c.inTown());
        AssertJUnit.assertFalse(c.sellRelic("Ann", "Lathliss' Fang"));
        AssertJUnit.assertTrue(c.player("Ann").relics.contains("Lathliss' Fang"));
    }

    @Test
    public void aHeadChoosesWhichTrophyToWear() {
        final HexCampaign c = newCampaign(false);
        final HexPlayer ann = c.player("Ann");
        beatBoss(c, HexColor.WHITE);
        beatBoss(c, HexColor.RED);
        c.pendingRewards.clear();
        AssertJUnit.assertTrue("the first trophy stays on", ann.relics.contains("Akroma's Halo"));
        AssertJUnit.assertFalse(ann.relics.contains("Lathliss' Fang"));

        AssertJUnit.assertTrue(c.wearTrophy("Ann", "RED"));
        AssertJUnit.assertTrue(ann.relics.contains("Lathliss' Fang"));
        AssertJUnit.assertFalse("put away, not lost", ann.relics.contains("Akroma's Halo"));
        AssertJUnit.assertEquals(1, (int) ann.trophies.get("WHITE"));
        AssertJUnit.assertFalse("not a trophy she has", c.wearTrophy("Ann", "BLUE"));

        AssertJUnit.assertTrue(c.wearTrophy("Ann", null));
        AssertJUnit.assertNull(c.relicInSlot(ann, HexCampaign.TROPHY_SLOT));
        AssertJUnit.assertFalse("nothing to take off", c.wearTrophy("Ann", null));

        // a relic bought for the slot is sold to make room
        ann.relics.add("Helm of Awakening");
        final int gold = ann.gold;
        AssertJUnit.assertTrue(c.wearTrophy("Ann", "WHITE"));
        AssertJUnit.assertFalse(ann.relics.contains("Helm of Awakening"));
        AssertJUnit.assertEquals(gold + HexContent.get().relicSellPrice("Helm of Awakening"), ann.gold);
    }

    @Test
    public void aiHeadsWearTheirNewestTrophyButKeepBoughtRelics() {
        final HexCampaign c = newCampaign(true);
        final HexPlayer bob = c.player("Bob");
        beatBoss(c, HexColor.WHITE);
        beatBoss(c, HexColor.GREEN);
        AssertJUnit.assertTrue(bob.relics.contains("Ghalta's Tooth"));
        AssertJUnit.assertFalse(bob.relics.contains("Akroma's Halo"));
        bob.relics.remove("Ghalta's Tooth");
        bob.relics.add("Helm of Awakening");
        beatBoss(c, HexColor.BLACK);
        AssertJUnit.assertTrue(bob.relics.contains("Helm of Awakening"));
        AssertJUnit.assertEquals(3, bob.trophies.size());
    }

    @Test
    public void anOldSaveHasNoTrophies() {
        final HexCampaign c = newCampaign(false);
        final String json = HexCampaignIO.toJson(c).replace("\"trophies\": {},", "");
        AssertJUnit.assertFalse(json.contains("\"trophies\""));
        final HexCampaign loaded = HexCampaignIO.fromJson(json);
        AssertJUnit.assertNotNull(loaded.player("Ann").trophies);
        AssertJUnit.assertTrue(loaded.player("Ann").trophies.isEmpty());
    }
}
