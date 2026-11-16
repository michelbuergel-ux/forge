package forge.gamemodes.hexcrawl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.IPaperCard;
import forge.model.FModel;

/** The relics of experience: their emblems in the command zone, how they get counters and what the counters do. */
public class HexExperienceRelicsTest extends AITest {

    private static final List<String> RELICS = Arrays.asList("Veteran's Circlet", "Scholar's Wand", "Medallion of Hard Lessons",
            "Pathfinder's Boots", "Battle-Scarred Hauberk", "Tome of Retold Tales");
    private static final List<String> SLOTS = Arrays.asList("Head", "Right", "Neck", "Boots", "Body", "Left");
    private static final String HARD_WON = "Hard-Won Experience";

    private Game game(final Player[] players) {
        HexCustomCards.loadOnce();
        final Game game = initAndCreateGame();
        players[0] = game.getPlayers().get(0);
        players[1] = game.getPlayers().get(1);
        fillLibrary(players[0], 30);
        fillLibrary(players[1], 30);
        return game;
    }

    private static int experience(final Player p) {
        return p.getCounters(CounterEnumType.EXPERIENCE);
    }

    @Test
    public void theRelicsLoadInTheirSlotsAndStartWithMoreExperience() {
        final HexContent content = HexContent.get();
        for (int i = 0; i < RELICS.size(); i++) {
            final String name = RELICS.get(i);
            final HexRelic relic = content.getRelic(name);
            AssertJUnit.assertNotNull(name, relic);
            AssertJUnit.assertEquals(name, SLOTS.get(i), relic.slot);
            AssertJUnit.assertEquals(Collections.singletonList(name), relic.command);
            AssertJUnit.assertTrue(name + " from the first cycle", content.ordinaryRelics(1).contains(relic));
            AssertJUnit.assertEquals(Arrays.asList(name, HARD_WON), content.getRelic(name + " II").command);
            AssertJUnit.assertEquals(Arrays.asList(name, HARD_WON, HARD_WON), content.getRelic(name + " III").command);
        }
    }

    @Test
    public void theEmblemGoesIntoTheCommandZoneOfItsHead() {
        final HexCampaign c = HexCampaign.create("Test", 7L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", true)));
        c.player("Ann").relics.add("Tome of Retold Tales II");
        final HexEncounter enc = HexContent.get().randomEncounter(HexColor.RED, 0.5, new Random(2));
        int tomes = 0;
        int hardWon = 0;
        for (final RegisteredPlayer rp : HexBattle.createPlayers(c, enc, true)) {
            if (!"Ann".equals(rp.getPlayer().getName()) || rp.getExtraCardsInCommandZone() == null) {
                continue;
            }
            for (final IPaperCard card : rp.getExtraCardsInCommandZone()) {
                tomes += card.getName().equals("Tome of Retold Tales") ? 1 : 0;
                hardWon += card.getName().equals(HARD_WON) ? 1 : 0;
            }
        }
        AssertJUnit.assertEquals(1, tomes);
        AssertJUnit.assertEquals(1, hardWon);
    }

    @Test
    public void theCountersShopSellsTheExperienceCards() {
        final HexTheme counters = HexTheme.get("counters");
        for (final String name : new String[] { "Mizzix of the Izmagnus", "Daxos the Returned", "Ezuri, Claw of Progress", "Meren of Clan Nel Toth" }) {
            AssertJUnit.assertTrue(name, counters.fits(FModel.getMagicDb().getCommonCards().getCard(name).getRules()));
        }
        AssertJUnit.assertFalse(counters.fits(FModel.getMagicDb().getCommonCards().getCard("Grizzly Bears").getRules()));
    }

    @Test
    public void hardWonExperienceGivesTwoCountersAtTheStart() {
        final Player[] ps = new Player[2];
        final Game game = game(ps);
        addCardToZone(HARD_WON, ps[0], ZoneType.Command);
        addCardToZone(HARD_WON, ps[0], ZoneType.Command);
        game.getTriggerHandler().resetActiveTriggers();
        game.getTriggerHandler().runTrigger(TriggerType.NewGame, AbilityKey.newMap(), true);
        playUntilStackClear(game);
        AssertJUnit.assertEquals(4, experience(ps[0]));
    }

    @Test
    public void theTomeDrawsMoreTheMoreExperience() {
        final Player[] ps = new Player[2];
        final Game game = game(ps);
        final Player me = ps[0];
        addCardToZone("Tome of Retold Tales", me, ZoneType.Command);
        me.setCounters(CounterEnumType.EXPERIENCE, 7, me, false);
        game.getPhaseHandler().devModeSet(PhaseType.CLEANUP, ps[1]);
        game.getAction().checkStateEffects(true);
        final int hand = me.getCardsIn(ZoneType.Hand).size();
        playUntilPhase(game, PhaseType.MAIN1); // through my upkeep and draw
        AssertJUnit.assertEquals("an experience counter in the upkeep", 8, experience(me));
        AssertJUnit.assertEquals("1 + 8/4 for the counter, 1 for the turn", hand + 4, me.getCardsIn(ZoneType.Hand).size());
    }

    @Test
    public void theHauberkGrowsTheCreaturesAndLearnsFromDeath() {
        final Player[] ps = new Player[2];
        final Game game = game(ps);
        final Player me = ps[0];
        addCardToZone("Battle-Scarred Hauberk", me, ZoneType.Command);
        final Card bear = addCard("Grizzly Bears", me);
        final Card other = addCard("Grizzly Bears", me);
        me.setCounters(CounterEnumType.EXPERIENCE, 5, me, false);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertEquals("a third of 5", 3, bear.getNetPower());
        game.getAction().destroy(other, null, false, null);
        playUntilStackClear(game);
        AssertJUnit.assertEquals("a creature died", 6, experience(me));
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertEquals("a third of 6", 4, bear.getNetToughness());
    }

    @Test
    public void theMedallionHealsAtTheEndOfTheTurn() {
        final Player[] ps = new Player[2];
        final Game game = game(ps);
        final Player me = ps[0];
        addCardToZone("Medallion of Hard Lessons", me, ZoneType.Command);
        me.setCounters(CounterEnumType.EXPERIENCE, 3, me, false);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, me);
        game.getAction().checkStateEffects(true);
        final int life = me.getLife();
        playUntilPhase(game, PhaseType.CLEANUP);
        AssertJUnit.assertEquals(life + 3, me.getLife());
    }

    @Test
    public void theCircletPutsCountersOnACreatureAtCombat() {
        final Player[] ps = new Player[2];
        final Game game = game(ps);
        final Player me = ps[0];
        addCardToZone("Veteran's Circlet", me, ZoneType.Command);
        final Card bear = addCard("Grizzly Bears", me);
        me.setCounters(CounterEnumType.EXPERIENCE, 5, me, false);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, me);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.COMBAT_DECLARE_ATTACKERS);
        AssertJUnit.assertEquals("half of 5", 2, bear.getCounters(CounterEnumType.P1P1));
    }

    @Test
    public void theBootsLearnFromTheFirstLandEachTurn() {
        final Player[] ps = new Player[2];
        final Game game = game(ps);
        final Player me = ps[0];
        addCardToZone("Pathfinder's Boots", me, ZoneType.Command);
        game.getAction().checkStateEffects(true);
        for (int i = 0; i < 2; i++) {
            final Card forest = addCardToZone("Forest", me, ZoneType.Hand);
            game.getAction().moveToPlay(forest, me, null, null);
        }
        playUntilStackClear(game);
        AssertJUnit.assertEquals("once each turn", 1, experience(me));
    }
}
