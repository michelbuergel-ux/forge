package forge.ai;

import forge.deck.Deck;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexReward;
import forge.gamemodes.hexcrawl.HexTile;
import forge.item.PaperCard;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** The doppelganger: a rare wanderer whose heads play the party's decks, and copies a card of a head's deck as its reward. */
public class HexDoppelgangerTest extends AITest {

    private static final String[] MARKERS = { "Lightning Bolt", "Counterspell", "Llanowar Elves", "Dark Ritual" };

    /** A party of this many heads, each with a card in its deck that no other head has. */
    private static HexCampaign party(final int heads) {
        final List<HexPlayer> players = new ArrayList<>();
        for (int i = 0; i < heads; i++) {
            players.add(new HexPlayer("Head" + i, i > 0));
        }
        final HexCampaign c = HexCampaign.create("Mirror", 11L, 4, players);
        for (int i = 0; i < heads; i++) {
            c.players.get(i).deck.add(MARKERS[i]);
        }
        return c;
    }

    private static HexEncounter doppelganger(final HexCampaign c) {
        final HexEncounter rolled = HexContent.get().randomEncounter(HexColor.BLUE, 0.5, new Random(3));
        rolled.q = c.partyQ;
        rolled.r = c.partyR;
        final HexEncounter enc = HexContent.get().doppelganger(rolled);
        AssertJUnit.assertNotNull("the Adventure mode has a doppelganger", enc);
        AssertJUnit.assertTrue(enc.doppelganger);
        return enc;
    }

    /** The markers in the enemy heads' decks: one per head, each the marker of the party head it mirrors. */
    private static List<String> mirroredMarkers(final List<RegisteredPlayer> players) {
        final List<String> found = new ArrayList<>();
        for (final RegisteredPlayer rp : players) {
            if (rp.getTeamNumber() != HexBattle.ENEMY_TEAM) {
                continue;
            }
            final Deck deck = rp.getDeck();
            String marker = null;
            for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                if (Arrays.asList(MARKERS).contains(e.getKey().getName())) {
                    AssertJUnit.assertNull("one head's deck", marker);
                    marker = e.getKey().getName();
                }
            }
            AssertJUnit.assertNotNull("an enemy head plays a deck of the party", marker);
            found.add(marker);
        }
        return found;
    }

    @Test
    public void itIsNoOrdinaryWanderer() {
        for (final HexColor color : HexColor.values()) {
            for (int seed = 0; seed < 40; seed++) {
                for (final double danger : new double[] { 0, 0.5, 1 }) {
                    AssertJUnit.assertFalse(HexContent.get().randomEncounter(color, danger, new Random(seed)).doppelganger);
                }
            }
        }
    }

    @Test
    public void itTurnsUpNowAndThen() {
        int enemies = 0;
        int doppelgangers = 0;
        for (int seed = 0; seed < 40; seed++) {
            final HexCampaign c = HexCampaign.create("Seen", seed, 4, Arrays.asList(new HexPlayer("Ann", false)));
            c.encounterChance = 100;
            c.encounterChanceExplored = 100;
            c.reshuffleEnemies();
            c.scout();
            for (final HexTile t : c.map.tiles.values()) {
                if (t.enemy != null && !t.enemy.boss && t.enemy.questId == 0) {
                    enemies++;
                    doppelgangers += t.enemy.doppelganger ? 1 : 0;
                }
            }
        }
        AssertJUnit.assertTrue("enemies: " + enemies, enemies > 200);
        AssertJUnit.assertTrue("a few doppelgangers: " + doppelgangers + " of " + enemies,
                doppelgangers * 100 >= enemies && doppelgangers * 100 <= enemies * 12);
    }

    @Test
    public void everyHeadMirrorsAnotherHeadOfTheParty() {
        final HexCampaign c = party(3);
        final List<String> markers = mirroredMarkers(HexBattle.createPlayers(c, doppelganger(c), true));
        AssertJUnit.assertEquals(3, markers.size());
        AssertJUnit.assertEquals("each head of the party once", 3, new HashSet<>(markers).size());
    }

    @Test
    public void againstFourHeadsItMirrorsThreeOfThem() {
        final HexCampaign c = party(4);
        final HexEncounter enc = doppelganger(c);
        final List<String> markers = mirroredMarkers(HexBattle.createPlayers(c, enc, true));
        AssertJUnit.assertEquals(HexCampaign.ENEMY_HEADS[3], markers.size());
        AssertJUnit.assertEquals(markers.size(), new HashSet<>(markers).size());
        final Set<String> named = new HashSet<>();
        for (final HexPlayer p : c.mirroredHeads(enc)) {
            named.add(MARKERS[c.players.indexOf(p)]);
        }
        AssertJUnit.assertEquals("the heads the fight's window names", named, new HashSet<>(markers));
        AssertJUnit.assertEquals("the same ones every time", markers, mirroredMarkers(HexBattle.createPlayers(c, enc, true)));
    }

    @Test
    public void itCopiesACardOfTheHeadsOwnDeck() {
        final HexCampaign c = party(2);
        c.pendingEncounter = doppelganger(c);
        c.resolveBattle(HexBattle.Result.WON);
        final HexPlayer ann = c.player("Head0");
        final HexReward reward = c.pendingReward(ann.name);
        AssertJUnit.assertNotNull(reward);
        AssertJUnit.assertTrue(reward.copy);
        AssertJUnit.assertEquals(HexCampaign.doppelgangerCopies(ann), reward.options());
        AssertJUnit.assertTrue(reward.options().contains(MARKERS[0]));
        AssertJUnit.assertFalse("not the other head's cards", reward.options().contains(MARKERS[1]));
        for (final String card : reward.options()) {
            AssertJUnit.assertFalse(card, HexContent.get().isBasicLand(card));
        }
        final int bolts = (int) ann.deck.stream().filter(MARKERS[0]::equals).count();
        final int size = ann.deck.size();
        c.chooseReward(ann.name, reward.options().indexOf(MARKERS[0]));
        AssertJUnit.assertEquals("a copy more", bolts + 1, ann.deck.stream().filter(MARKERS[0]::equals).count());
        AssertJUnit.assertEquals(size + 1, ann.deck.size());
        // the AI head copies its most valuable card
        final HexPlayer bob = c.player("Head1");
        final int bobSize = bob.deck.size() + bob.sideboard.size();
        c.chooseRewardForAi(bob.name);
        AssertJUnit.assertNull(c.pendingReward(bob.name));
        AssertJUnit.assertEquals(bobSize + 1, bob.deck.size() + bob.sideboard.size());
    }
}
