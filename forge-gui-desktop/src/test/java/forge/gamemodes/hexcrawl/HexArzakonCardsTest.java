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

    /** Arzakon's game with his presence (of the first cycle or a later one), at the party's cleanup. */
    private Game withPresence(final Player[] ps, final String presence) {
        final Game game = game(ps);
        addCardToZone(presence, ps[1], ZoneType.Command);
        fillLibrary(ps[0], 20);
        fillLibrary(ps[1], 20);
        game.getPhaseHandler().devModeSet(PhaseType.CLEANUP, ps[0]);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private void throughHisUpkeep(final Game game, final Player arzakon) {
        if (game.getPhaseHandler().isPlayerTurn(arzakon)) {
            playUntilNextTurn(game);
        }
        playUntilNextTurn(game); // into his turn
        playUntilPhase(game, PhaseType.DRAW);
    }

    private static Card angel(final Game game, final String name) {
        Card found = null;
        for (final Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals(name)) {
                AssertJUnit.assertNull("only one", found);
                found = c;
            }
        }
        return found;
    }

    private static final String PLATINUM = "Arzakon's Platinum Angel";
    private static final String DARKSTEEL = "Arzakon's Darksteel Angel";
    private static final String BLIGHTSTEEL = "Arzakon's Blightsteel Angel";

    /** What the card's text shows: all of these, none of it greyed out as if it did not apply. */
    private static void showsAll(final Game game, final Card angel, final String... lines) {
        game.getRules().setUseGrayText(true);
        final String text = angel.getAbilityText();
        for (final String line : lines) {
            AssertJUnit.assertTrue(line + " in: " + text, text.toLowerCase().contains(line.toLowerCase()));
        }
        AssertJUnit.assertFalse("nothing greyed out: " + text, text.contains("color:gray"));
    }

    /** The token shows the angel's own picture (a whole card, from the custom card pictures). */
    private static void hasItsPicture(final Card angel) {
        final String key = angel.getImageKey();
        AssertJUnit.assertTrue(key, key.startsWith(forge.ImageKeys.ADVENTURECARD_PREFIX));
        AssertJUnit.assertNotNull("the picture is there: " + key, forge.ImageKeys.getImageFile(key));
    }

    @Test
    public void eachOfHisTurnsHeConjuresAPlatinumAngelIfHeHasNone() {
        final Player[] ps = new Player[2];
        final Game game = withPresence(ps, "Arzakon's Presence");
        final Player arzakon = ps[1];
        throughHisUpkeep(game, arzakon);
        final Card angel = angel(game, PLATINUM);
        AssertJUnit.assertNotNull("conjured", angel);
        AssertJUnit.assertTrue(angel.isToken());
        AssertJUnit.assertEquals(arzakon, angel.getController());
        AssertJUnit.assertFalse("a plain one in the first cycle", angel.hasKeyword(forge.game.keyword.Keyword.INDESTRUCTIBLE));
        AssertJUnit.assertTrue("it guards him", angel.hasKeyword(forge.game.keyword.Keyword.DEFENDER));
        showsAll(game, angel, "Flying", "Defender", "can't lose the game", "owner controls it, exile it");
        hasItsPicture(angel);

        // he can't lose while it is there
        arzakon.setLife(0, null);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertFalse(game.isGameOver());
        arzakon.setLife(20, null);

        throughHisUpkeep(game, arzakon);
        AssertJUnit.assertSame("not a second one while he has it", angel, angel(game, PLATINUM));

        game.getAction().exile(angel, null, null);
        throughHisUpkeep(game, arzakon);
        AssertJUnit.assertNotNull("a new one when it is gone", angel(game, PLATINUM));
    }

    @Test
    public void theSecondCycleConjuresADarksteelAngel() {
        final Player[] ps = new Player[2];
        final Game game = withPresence(ps, "Arzakon's Dread Presence");
        final Player arzakon = ps[1];
        throughHisUpkeep(game, arzakon);
        final Card angel = angel(game, DARKSTEEL);
        AssertJUnit.assertNotNull(angel);
        AssertJUnit.assertTrue(angel.hasKeyword(forge.game.keyword.Keyword.INDESTRUCTIBLE));
        AssertJUnit.assertTrue(angel.hasKeyword(forge.game.keyword.Keyword.DEFENDER));
        AssertJUnit.assertNull(angel(game, PLATINUM));
        arzakon.addPoisonCounters(10, ps[0], null);
        AssertJUnit.assertEquals("no poison while it is there", 0, arzakon.getPoisonCounters());
        showsAll(game, angel, "Flying", "Indestructible", "Defender", "can't lose the game", "can't get poison counters",
                "-1/-1 counters", "owner controls it, exile it");
        hasItsPicture(angel);
    }

    @Test
    public void laterCyclesConjureABlightsteelAngelThatAttacks() {
        final Player[] ps = new Player[2];
        final Game game = withPresence(ps, "Arzakon's Blighted Presence");
        throughHisUpkeep(game, ps[1]);
        final Card angel = angel(game, BLIGHTSTEEL);
        AssertJUnit.assertNotNull(angel);
        AssertJUnit.assertTrue(angel.hasKeyword(forge.game.keyword.Keyword.INFECT));
        AssertJUnit.assertTrue(angel.hasKeyword(forge.game.keyword.Keyword.INDESTRUCTIBLE));
        AssertJUnit.assertFalse("no defender: it attacks", angel.hasKeyword(forge.game.keyword.Keyword.DEFENDER));
        AssertJUnit.assertTrue(angel.getType().hasCreatureType("Angel"));
        showsAll(game, angel, "Infect", "Flying", "Indestructible", "can't lose the game", "can't get poison counters",
                "-1/-1 counters", "owner controls it, exile it");
        hasItsPicture(angel);
    }

    @Test
    public void anAngelTakenFromHimIsExiled() {
        final Player[] ps = new Player[2];
        final Game game = withPresence(ps, "Arzakon's Presence");
        final Player party = ps[0];
        throughHisUpkeep(game, ps[1]);
        AssertJUnit.assertNotNull(angel(game, PLATINUM));
        final Card thief = addCard("Grizzly Bears", party);
        final forge.game.spellability.SpellAbility steal = forge.game.ability.AbilityFactory.getAbility(
                "DB$ GainControl | AllValid$ Creature.namedArzakon's_Platinum_Angel | NewController$ You", thief);
        steal.setActivatingPlayer(party);
        forge.game.ability.AbilityUtils.resolve(steal);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);
        AssertJUnit.assertNull("another player controlled it: exiled", angel(game, PLATINUM));
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
