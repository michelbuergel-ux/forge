package forge.ai;

import forge.game.player.RegisteredPlayer;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexQuest;
import forge.gamemodes.hexcrawl.HexRelic;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.item.IPaperCard;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The Hex Campaign's own relics: the stronger versions of New Game Plus, and the Vanguard avatars. */
public class HexRelicTierTest extends AITest {

    private static HexCampaign party() {
        return HexCampaign.create("Test", 7L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", true)));
    }

    @Test
    public void testEveryRelicLoadsWithItsCards() {
        final HexContent content = HexContent.get();
        AssertJUnit.assertTrue("the old relics are all there: " + content.ordinaryRelics().size(), content.ordinaryRelics().size() >= 80);
        AssertJUnit.assertTrue("avatars: " + content.avatars(false).size(), content.avatars(false).size() >= 90);
        for (final HexRelic relic : content.allRelics()) {
            AssertJUnit.assertTrue(relic.name + " has a slot", Arrays.asList("Left", "Right", "Neck", "Body", "Boots", HexContent.AVATAR_SLOT).contains(relic.slot));
            AssertJUnit.assertTrue(relic.name + " has a price", relic.price > 0);
            for (final String card : relic.battlefield) {
                AssertJUnit.assertNotNull(relic.name + ": " + card, content.findStartCard(card));
            }
            if (relic.avatar != null) {
                AssertJUnit.assertNotNull(relic.name, content.findAvatar(relic.avatar));
                AssertJUnit.assertEquals(HexContent.AVATAR_SLOT, relic.slot);
                AssertJUnit.assertTrue(relic.name + " costs about 1000: " + relic.price, relic.price >= 800 && relic.price <= 1200);
            }
        }
    }

    @Test
    public void testStrongerMoxen() {
        final HexContent content = HexContent.get();
        final HexRelic one = content.getRelic("Mox Ruby");
        final HexRelic two = content.getRelic("Mox Ruby II");
        final HexRelic three = content.getRelic("Mox Ruby III");
        AssertJUnit.assertEquals(Collections.singletonList("Mox Ruby"), one.battlefield);
        AssertJUnit.assertEquals(Arrays.asList("Mox Ruby", "Mox Ruby"), two.battlefield);
        AssertJUnit.assertEquals(Arrays.asList("Mox Ruby", "Mox Ruby", "Mox Ruby"), three.battlefield);
        AssertJUnit.assertEquals(Math.round(one.price * 2.25), two.price);
        AssertJUnit.assertEquals(Math.round(two.price * 2.25), three.price);
        AssertJUnit.assertEquals(2, two.tier);
        AssertJUnit.assertEquals("Mox Ruby", three.base);
        AssertJUnit.assertEquals(one.slot, three.slot);
    }

    @Test
    public void testLegendaryCardsStaySingle() {
        final HexContent content = HexContent.get();
        int checked = 0;
        for (final HexRelic relic : content.allRelics()) {
            if (relic.tier < 2) {
                continue;
            }
            final HexRelic base = content.getRelic(relic.base);
            AssertJUnit.assertNotNull(relic.name, base);
            for (final String card : base.battlefield) {
                final IPaperCard paper = content.findStartCard(card);
                final long copies = relic.battlefield.stream().filter(card::equals).count();
                final long baseCopies = base.battlefield.stream().filter(card::equals).count();
                if (paper.getRules().getType().isLegendary()) {
                    AssertJUnit.assertEquals(relic.name + ": " + card, baseCopies, copies);
                    checked++;
                } else {
                    AssertJUnit.assertEquals(relic.name + ": " + card, baseCopies * relic.tier, copies);
                }
            }
            AssertJUnit.assertTrue(relic.name + " is stronger", relic.battlefield.size() > base.battlefield.size()
                    || relic.life > base.life || relic.hand > base.hand);
        }
        AssertJUnit.assertTrue("some legendary cards among the relics", checked > 0);
    }

    @Test
    public void testTiersOnlyInNewGamePlus() {
        final HexContent content = HexContent.get();
        int tier2 = 0;
        int tier3 = 0;
        for (int seed = 0; seed < 60; seed++) {
            for (final HexShopOffer offer : content.relicStock(new Random(seed), 1, false)) {
                AssertJUnit.assertEquals("only ordinary relics in the first cycle", 1, content.getRelic(offer.card).tier);
            }
            for (final HexShopOffer offer : content.relicStock(new Random(seed), 2, false)) {
                AssertJUnit.assertTrue("tier II at most in the second cycle", content.getRelic(offer.card).tier <= 2);
                tier2 += content.getRelic(offer.card).tier == 2 ? 1 : 0;
            }
            for (final HexShopOffer offer : content.relicStock(new Random(seed), 3, false)) {
                tier3 += content.getRelic(offer.card).tier == 3 ? 1 : 0;
            }
            final HexQuest quest = content.questEnemy(HexColor.RED, new Random(seed), 1);
            if (quest.relic != null) {
                AssertJUnit.assertEquals(1, content.getRelic(quest.relic).tier);
            }
        }
        AssertJUnit.assertTrue("tier II comes up in the second cycle: " + tier2, tier2 > 0);
        AssertJUnit.assertTrue("tier III comes up in the third cycle: " + tier3, tier3 > 0);
    }

    @Test
    public void testTheDealerOffersOneAvatar() {
        final HexContent content = HexContent.get();
        for (int seed = 0; seed < 30; seed++) {
            final List<HexShopOffer> stock = content.relicStock(new Random(seed), 1, true);
            long avatars = stock.stream().filter(o -> content.getRelic(o.card).avatar != null).count();
            AssertJUnit.assertEquals(1, avatars);
            for (final HexShopOffer o : stock) {
                AssertJUnit.assertTrue("an avatar the AI plays well", content.getRelic(o.card).aiPlayable);
            }
        }
    }

    @Test
    public void testAnAvatarGoesIntoTheCommandZone() {
        final HexCampaign c = party();
        final HexRelic avatar = HexContent.get().getRelic("Akroma, Angel of Wrath Avatar");
        AssertJUnit.assertNotNull(avatar);
        final int life = c.fightLife();
        c.player("Ann").relics.add(avatar.name);
        AssertJUnit.assertEquals("its life in the party's shared life", life + avatar.life, c.fightLife());
        final List<RegisteredPlayer> players = HexBattle.createPlayers(c, HexContent.get().randomEncounter(HexColor.RED, 0.5, new Random(2)), true);
        RegisteredPlayer ann = null;
        RegisteredPlayer bob = null;
        for (final RegisteredPlayer rp : players) {
            if ("Ann".equals(rp.getPlayer().getName())) {
                ann = rp;
            } else if ("Bob".equals(rp.getPlayer().getName())) {
                bob = rp;
            }
        }
        AssertJUnit.assertNotNull(ann);
        AssertJUnit.assertNotNull(bob);
        boolean found = false;
        for (final IPaperCard card : ann.getExtraCardsInCommandZone()) {
            found |= card.getName().equals(avatar.avatar);
        }
        AssertJUnit.assertTrue("Akroma in Ann's command zone", found);
        AssertJUnit.assertEquals("its hand bonus for Ann", bob.getStartingHand() + avatar.hand, ann.getStartingHand());
    }
}
