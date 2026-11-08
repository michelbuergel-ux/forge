package forge.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.deck.DeckSection;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexPractice;
import forge.gamemodes.hexcrawl.HexTile;
import forge.item.PaperCard;

/** The practice ground: a head's deck against a training dummy or a sparring partner, for fun only. */
public class HexPracticeTest extends AITest {

    private static HexCampaign inTown() {
        final HexCampaign c = HexCampaign.create("Practice", 23L, 6, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.TOWN) {
                c.partyQ = c.prevQ = t.q;
                c.partyR = c.prevR = t.r;
                break;
            }
        }
        AssertJUnit.assertTrue(c.inTown());
        return c;
    }

    @Test
    public void testTheTrainingDummy() {
        final HexCampaign c = inTown();
        final HexPlayer ann = c.player("Ann");
        final List<RegisteredPlayer> players = HexPractice.createPlayers(c, ann, HexPractice.Opponent.DUMMY, new Random(1));
        AssertJUnit.assertEquals(2, players.size());
        final RegisteredPlayer me = players.get(0);
        final RegisteredPlayer dummy = players.get(1);
        AssertJUnit.assertEquals("Ann", me.getPlayer().getName());
        AssertJUnit.assertEquals(HexPractice.LIFE, me.getStartingLife());
        AssertJUnit.assertEquals(ann.deck.size(), me.getDeck().getMain().countAll());
        AssertJUnit.assertEquals(HexBattle.PARTY_TEAM, me.getTeamNumber());
        AssertJUnit.assertEquals(HexBattle.ENEMY_TEAM, dummy.getTeamNumber());
        AssertJUnit.assertEquals(HexPractice.DUMMY_LIFE, dummy.getStartingLife());
        // lands, nothing else: it never does anything
        AssertJUnit.assertEquals(40, dummy.getDeck().getMain().countAll());
        for (final PaperCard card : dummy.getDeck().get(DeckSection.Main).toFlatList()) {
            AssertJUnit.assertTrue(card.getName(), card.getRules().getType().isBasicLand());
        }
        // nothing of the campaign comes along: no relics, perks or extra cards
        AssertJUnit.assertTrue(me.getCardsOnBattlefield() == null || !me.getCardsOnBattlefield().iterator().hasNext());
    }

    @Test
    public void testASparringPartner() {
        final HexCampaign c = inTown();
        final List<RegisteredPlayer> players = HexPractice.createPlayers(c, c.player("Bob"), HexPractice.Opponent.SPARRING, new Random(5));
        final RegisteredPlayer partner = players.get(1);
        AssertJUnit.assertEquals(HexPractice.LIFE, partner.getStartingLife());
        AssertJUnit.assertTrue("an enemy's own deck", partner.getDeck().getMain().countAll() >= 40);
        AssertJUnit.assertFalse(partner.getPlayer().getName().isEmpty());
    }

    @Test
    public void testAPlainDuel() {
        AssertJUnit.assertEquals(GameType.Constructed, HexPractice.createRules().getGameType());
        AssertJUnit.assertEquals(1, HexPractice.createRules().getGamesPerMatch());
        AssertJUnit.assertFalse(HexPractice.createRules().hasAppliedVariant(GameType.MultiHeadedGiant));
        AssertJUnit.assertFalse("nothing runs before it starts", HexPractice.isRunning());
    }
}
