package forge.ai;

import java.util.Arrays;
import java.util.Collections;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexReward;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.net.event.HexActionEvent;

/** A won card and treasure cards go into the deck or the sideboard, as the head says. */
public class HexRewardPlacementTest extends AITest {

    private static HexCampaign campaign() {
        return HexCampaign.create("Rewards", 31L, 5, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
    }

    /** A reward of three cards to choose from and a chest's card, which lies in the sideboard already. */
    private static HexReward reward(final HexCampaign c, final String head) {
        final HexReward reward = new HexReward();
        reward.playerName = head;
        reward.gold = 30;
        reward.cards.addAll(Arrays.asList("Grizzly Bears", "Shock", "Divination"));
        reward.chestCard = "Sol Ring";
        c.player(head).sideboard.add(reward.chestCard);
        c.pendingRewards.add(reward);
        return reward;
    }

    private static int count(final java.util.List<String> cards, final String card) {
        return Collections.frequency(cards, card);
    }

    @Test
    public void testIntoTheDeck() {
        final HexCampaign c = campaign();
        final HexPlayer ann = c.player("Ann");
        reward(c, "Ann");
        final int deck = ann.deck.size();
        c.chooseReward("Ann", 1, false);
        AssertJUnit.assertEquals(deck + 2, ann.deck.size());
        AssertJUnit.assertEquals(1, count(ann.deck, "Shock"));
        AssertJUnit.assertEquals("the chest's card too", 1, count(ann.deck, "Sol Ring"));
        AssertJUnit.assertEquals(0, count(ann.sideboard, "Sol Ring"));
        AssertJUnit.assertNull(c.pendingReward("Ann"));
    }

    @Test
    public void testIntoTheSideboard() {
        final HexCampaign c = campaign();
        final HexPlayer ann = c.player("Ann");
        reward(c, "Ann");
        final int deck = ann.deck.size();
        c.chooseReward("Ann", 2, true);
        AssertJUnit.assertEquals("the deck is left alone", deck, ann.deck.size());
        AssertJUnit.assertEquals(1, count(ann.sideboard, "Divination"));
        AssertJUnit.assertEquals(1, count(ann.sideboard, "Sol Ring"));
    }

    @Test
    public void testTheGoldAndTheTreasureIntoTheDeck() {
        final HexCampaign c = campaign();
        final HexPlayer ann = c.player("Ann");
        reward(c, "Ann");
        final int gold = ann.gold;
        c.chooseReward("Ann", -1, false);
        AssertJUnit.assertTrue("the gold", ann.gold > gold);
        AssertJUnit.assertEquals(1, count(ann.deck, "Sol Ring"));
        AssertJUnit.assertEquals(0, count(ann.deck, "Shock"));
    }

    @Test
    public void testOverTheNetworkAndFromAnOlderBuild() {
        final HexCampaign c = campaign();
        final HexSession s = new HexSession(c);
        s.setGuests(Collections.singletonList("Bob"));
        final HexPlayer bob = c.player("Bob");

        // a guest says: into the sideboard
        reward(c, "Bob");
        s.handle("Bob", new HexActionEvent(HexActionEvent.REWARD, 0, 2, null, true, "Bob"));
        AssertJUnit.assertEquals(1, count(bob.sideboard, "Grizzly Bears"));
        AssertJUnit.assertEquals(1, count(bob.sideboard, "Sol Ring"));

        // a guest says: into the deck
        reward(c, "Bob");
        s.handle("Bob", new HexActionEvent(HexActionEvent.REWARD, 0, 1, null, true, "Bob"));
        AssertJUnit.assertEquals(1, count(bob.deck, "Grizzly Bears"));
        AssertJUnit.assertEquals(1, count(bob.deck, "Sol Ring"));

        // an older build says nothing: the card into the deck, the treasure stays in the sideboard, as before
        reward(c, "Bob");
        s.handle("Bob", new HexActionEvent(HexActionEvent.REWARD, 1, 0, null, true, "Bob"));
        AssertJUnit.assertEquals(1, count(bob.deck, "Shock"));
        AssertJUnit.assertEquals(1, count(bob.deck, "Sol Ring"));
        AssertJUnit.assertEquals(2, count(bob.sideboard, "Sol Ring"));
    }
}
