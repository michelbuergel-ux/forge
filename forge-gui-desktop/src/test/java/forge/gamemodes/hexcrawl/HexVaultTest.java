package forge.gamemodes.hexcrawl;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.deck.Deck;
import forge.item.PaperCard;

/** The Vault of the Lost: it opens after the party lost enough cards, and its Collector gives them back. */
public class HexVaultTest extends AITest {

    private static HexCampaign campaign(final HexDifficulty level) {
        final HexCampaign c = HexCampaign.create("Vault", 37L, 7, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        c.difficulty = level;
        return c;
    }

    /** Fills the heads' lists of lost cards up to the vault's number with red spells. */
    private static void loseMany(final HexCampaign c) {
        final String[] red = { "Lightning Bolt", "Shock", "Lava Spike", "Fireblast", "Searing Spear" };
        int i = 0;
        while (c.lostCardsInParty() < HexCampaign.VAULT_LOST_CARDS) {
            c.players.get(i % 2).lostCards.add(red[i % red.length]);
            i++;
        }
    }

    @Test
    public void itOpensAfterEnoughLostCardsButNotOnTheEasyLevel() {
        final HexCampaign easy = campaign(HexDifficulty.EASY);
        loseMany(easy);
        easy.maybeOpenVault();
        AssertJUnit.assertNull("nothing is lost on the easy level", easy.openVault());

        final HexCampaign c = campaign(HexDifficulty.NORMAL);
        c.players.get(0).lostCards.add("Shock");
        c.maybeOpenVault();
        AssertJUnit.assertNull("not before its number", c.openVault());
        loseMany(c);
        c.maybeOpenVault();
        final HexTile vault = c.openVault();
        AssertJUnit.assertNotNull(vault);
        AssertJUnit.assertEquals("the colour of the lost cards", HexColor.RED, vault.dungeon.color);
        final HexRoom keep = vault.dungeon.room(vault.dungeon.bossX, vault.dungeon.bossY);
        AssertJUnit.assertTrue(keep.guard.collector);
        AssertJUnit.assertFalse("no dungeon Arzakon waits for", c.dungeons().stream().filter(t -> !t.dungeon.vault)
                .anyMatch(t -> t == vault));
        c.maybeOpenVault();
        AssertJUnit.assertEquals("one at a time", 1, c.dungeons().stream().filter(t -> t.dungeon.vault).count());
    }

    @Test
    public void theCollectorPlaysSomeOfTheLostCardsInAWholeDeck() {
        final HexCampaign c = campaign(HexDifficulty.NORMAL);
        loseMany(c);
        c.maybeOpenVault();
        final HexTile vault = c.openVault();
        final HexEncounter collector = vault.dungeon.room(vault.dungeon.bossX, vault.dungeon.bossY).guard;
        collector.q = vault.q;
        collector.r = vault.r;
        final Deck own = HexContent.get().loadEnemyDeck(collector);
        final Deck deck = HexBattle.collectorDeck(c, collector);
        AssertJUnit.assertEquals("as many cards", own.getMain().countAll(), deck.getMain().countAll());
        AssertJUnit.assertEquals("as many lands", lands(own), lands(deck));
        int lost = 0;
        for (final String card : Arrays.asList("Lightning Bolt", "Shock", "Lava Spike", "Fireblast", "Searing Spear")) {
            lost += deck.getMain().count(HexContent.get().getCard(card)) - own.getMain().count(HexContent.get().getCard(card));
        }
        AssertJUnit.assertTrue("some lost cards in it: " + lost, lost > 0);
    }

    private static int lands(final Deck deck) {
        int n = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            n += e.getKey().getRules().getType().isLand() ? e.getValue() : 0;
        }
        return n;
    }

    @Test
    public void beatingTheCollectorGivesTheLostCardsBack() {
        final HexCampaign c = campaign(HexDifficulty.NORMAL);
        loseMany(c);
        c.maybeOpenVault();
        final HexTile vault = c.openVault();
        final HexDungeon d = vault.dungeon;
        final HexEncounter collector = d.room(d.bossX, d.bossY).guard;
        collector.q = vault.q;
        collector.r = vault.r;
        collector.roomX = d.bossX;
        collector.roomY = d.bossY;
        c.partyQ = c.prevQ = vault.q;
        c.partyR = c.prevR = vault.r;
        d.inside = true;
        d.x = d.bossX;
        d.y = d.bossY;
        final HexPlayer ann = c.player("Ann");
        final int bolts = Collections.frequency(ann.sideboard, "Lightning Bolt");
        final int lostBolts = Collections.frequency(ann.lostCards, "Lightning Bolt");
        c.pendingEncounter = collector;
        c.resolveBattle(HexBattle.Result.WON);
        AssertJUnit.assertTrue(d.cleared);
        AssertJUnit.assertEquals("back in the sideboard", bolts + lostBolts, Collections.frequency(ann.sideboard, "Lightning Bolt"));
        AssertJUnit.assertTrue(ann.lostCards.isEmpty());
        AssertJUnit.assertFalse(c.pendingReward("Ann").recovered.isEmpty());
        AssertJUnit.assertNull("cleared", c.openVault());
    }

    @Test
    public void theCollectorStartsWithItsEngine() {
        for (final int cycle : new int[] { 1, 2 }) {
            final HexCampaign c = campaign(HexDifficulty.NORMAL);
            c.cycle = cycle;
            loseMany(c);
            c.maybeOpenVault();
            final HexTile vault = c.openVault();
            final HexEncounter collector = vault.dungeon.room(vault.dungeon.bossX, vault.dungeon.bossY).guard;
            collector.q = vault.q;
            collector.r = vault.r;
            final java.util.List<String> inPlay = new java.util.ArrayList<>();
            for (final forge.game.player.RegisteredPlayer rp : HexBattle.createPlayers(c, collector, true)) {
                if (rp.getTeamNumber() == HexBattle.ENEMY_TEAM && rp.getCardsOnBattlefield() != null) {
                    for (final forge.item.IPaperCard card : rp.getCardsOnBattlefield()) {
                        inPlay.add(card.getName());
                    }
                    break; // the lead head
                }
            }
            AssertJUnit.assertTrue(cycle + ": " + inPlay, inPlay.contains(HexCampaign.COLLECTOR_ENGINE));
            AssertJUnit.assertEquals(cycle + ": " + inPlay, cycle > 1, inPlay.contains(HexCampaign.COLLECTOR_ENGINE_HARD));
        }
    }
}
