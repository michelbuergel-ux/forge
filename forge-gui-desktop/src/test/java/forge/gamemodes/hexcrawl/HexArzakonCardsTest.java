package forge.gamemodes.hexcrawl;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** Arzakon's cards in a game: his presence drains and strengthens his planeswalkers, his awakening comes at half life. */
public class HexArzakonCardsTest extends AITest {

    private Game game(final Player[] players) {
        HexCustomCards.loadOnce();
        final Game game = initAndCreateGame();
        players[0] = game.getPlayers().get(0);
        players[1] = game.getPlayers().get(1);
        return game;
    }

    @Test
    public void hisPresenceDrainsTheOpponentAndFeedsHisPlaneswalker() {
        final Player[] ps = new Player[2];
        final Game game = game(ps);
        final Player arzakon = ps[1];
        final Player party = ps[0];
        addCardToZone("Arzakon's Presence", arzakon, ZoneType.Command);
        // as the fight puts him in play (HexBattle: with ETB counters), he enters with his loyalty
        final Card walker = createCard(HexContent.ARZAKON_CARD, arzakon);
        walker.setStartsGameInPlay(true);
        arzakon.getZone(ZoneType.None).add(walker);
        game.getAction().moveToPlay(walker, arzakon, null, null);
        final int loyalty = walker.getCounters(CounterEnumType.LOYALTY);
        AssertJUnit.assertEquals("his starting loyalty", 4, loyalty);
        fillLibrary(party, 10);
        fillLibrary(arzakon, 10);
        game.getPhaseHandler().devModeSet(PhaseType.CLEANUP, party);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.DRAW); // through Arzakon's upkeep
        AssertJUnit.assertEquals("a loyalty counter more", loyalty + 1, walker.getCounters(CounterEnumType.LOYALTY));
        playUntilNextTurn(game);
        playUntilPhase(game, PhaseType.DRAW); // through the party's upkeep
        AssertJUnit.assertTrue("drained", party.getLife() < party.getStartingLife());
        AssertJUnit.assertTrue(arzakon.getLife() > arzakon.getStartingLife());
    }

    @Test
    public void atHalfLifeHeIsUnleashed() {
        final Player[] ps = new Player[2];
        final Game game = game(ps);
        final Player arzakon = ps[1];
        addCardToZone("Arzakon Unleashed", arzakon, ZoneType.Command);
        final Card bear = addCard("Grizzly Bears", arzakon);
        fillLibrary(ps[0], 10);
        fillLibrary(arzakon, 10);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, arzakon);
        game.getAction().checkStateEffects(true);
        arzakon.setLife(arzakon.getStartingLife() / 2, null);
        game.getAction().checkStateEffects(true);
        playUntilStackClear(game);
        AssertJUnit.assertEquals("a Black Lotus", 1, countCardsWithName(game, "Black Lotus", ZoneType.Battlefield));
        AssertJUnit.assertEquals("two Golems", 2, countCardsWithName(game, "Golem Token", ZoneType.Battlefield)
                + countCardsWithName(game, "Golem", ZoneType.Battlefield));
        AssertJUnit.assertEquals("+1/+1 from then on", 3, bear.getNetPower());
        // only once
        arzakon.setLife(arzakon.getStartingLife() / 4, null);
        game.getAction().checkStateEffects(true);
        playUntilStackClear(game);
        AssertJUnit.assertEquals(1, countCardsWithName(game, "Black Lotus", ZoneType.Battlefield));
    }
}
