package forge.ai;

import com.google.common.collect.Lists;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Shared team turns: the heads of a team hold priority together and are prompted at the same time. */
public class SharedTeamPriorityTest extends AITest {

    /** A head that is asked "concurrently", answering through a script instead of a screen. */
    private static class ConcurrentHead extends PlayerControllerAi {
        final AtomicInteger prompts = new AtomicInteger();
        final AtomicInteger interrupts = new AtomicInteger();
        volatile CountDownLatch interrupted = new CountDownLatch(1);
        Function<Integer, List<SpellAbility>> script = n -> null;

        ConcurrentHead(final Game game, final Player p) {
            super(game, p, p.getLobbyPlayer());
        }

        @Override
        public List<SpellAbility> chooseSpellAbilityToPlay() {
            return script.apply(prompts.incrementAndGet());
        }

        @Override
        public boolean canChooseSpellAbilityConcurrently() {
            return true;
        }

        @Override
        public void setConcurrentSpellAbilityChoice(final boolean concurrent) {
            if (concurrent) {
                interrupted = new CountDownLatch(1);
            }
        }

        @Override
        public void interruptSpellAbilityChoice() {
            interrupts.incrementAndGet();
            interrupted.countDown();
        }

        boolean awaitInterrupt() {
            try {
                return interrupted.await(10, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                return false;
            }
        }
    }

    private Game createTeamGame() {
        final List<RegisteredPlayer> players = Lists.newArrayList();
        final String[] names = {"a1", "b1", "a2", "b2"}; // teams sit alternately
        for (int i = 0; i < names.length; i++) {
            final RegisteredPlayer rp = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi(names[i], null));
            rp.setTeamNumber(i % 2);
            players.add(rp);
        }
        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setSharedTeamTurns(true);
        final Match match = new Match(rules, players, "Test");
        final Game game = new Game(players, rules, match);
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        game.getPhaseHandler().onStackResolved();
        return game;
    }

    @Test(timeOut = 60000)
    public void testTeamMateCanActWhileTheOtherIsStillThinking() {
        final Game game = createTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player a2 = game.getPlayers().get(2);
        AssertJUnit.assertTrue(a1.sameTeam(a2));

        final Card forest = addCardToZone("Forest", a2, ZoneType.Hand);
        final ConcurrentHead c1 = new ConcurrentHead(game, a1);
        final ConcurrentHead c2 = new ConcurrentHead(game, a2);
        a1.dangerouslySetController(c1);
        a2.dangerouslySetController(c2);

        final CountDownLatch a1Thinking = new CountDownLatch(1);
        // a1 takes its time and never decides: it is interrupted once a2 acts
        c1.script = n -> {
            if (n == 1) {
                a1Thinking.countDown();
                AssertJUnit.assertTrue("a1 was not interrupted", c1.awaitInterrupt());
            }
            return null;
        };
        // a2 plays its land while a1 is still thinking (only possible if both are asked at once)
        c2.script = n -> {
            if (n == 1) {
                try {
                    AssertJUnit.assertTrue("heads were not prompted at the same time", a1Thinking.await(10, TimeUnit.SECONDS));
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
                for (final SpellAbility sa : forest.getAllPossibleAbilities(a2, true)) {
                    if (sa.isLandAbility()) {
                        return Collections.singletonList(sa);
                    }
                }
                AssertJUnit.fail("no land ability");
            }
            return null;
        };

        game.getPhaseHandler().mainLoopStep();

        AssertJUnit.assertTrue(a2.getCardsIn(ZoneType.Battlefield).contains(game.getCardState(forest)));
        AssertJUnit.assertEquals(1, c1.interrupts.get());
        // after the land both heads were asked again, and the team passed only when both passed
        AssertJUnit.assertEquals(2, c1.prompts.get());
        AssertJUnit.assertEquals(2, c2.prompts.get());
        // priority moved on to the other team, still in the same main phase
        AssertJUnit.assertFalse(game.getPhaseHandler().getPriorityPlayer().sameTeam(a1));
        AssertJUnit.assertTrue(game.getPhaseHandler().is(PhaseType.MAIN1));
    }

    @Test(timeOut = 60000)
    public void testPhaseEndsOnlyAfterBothTeamsPassed() {
        final Game game = createTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player a2 = game.getPlayers().get(2);
        final ConcurrentHead c1 = new ConcurrentHead(game, a1);
        final ConcurrentHead c2 = new ConcurrentHead(game, a2);
        a1.dangerouslySetController(c1);
        a2.dangerouslySetController(c2);

        // team a passes, then the AI team b passes: the phase ends
        do {
            game.getPhaseHandler().mainLoopStep();
        } while (!game.isGameOver() && game.getPhaseHandler().is(PhaseType.MAIN1));
        AssertJUnit.assertEquals(1, c1.prompts.get());
        AssertJUnit.assertEquals(1, c2.prompts.get());
        AssertJUnit.assertEquals(0, c1.interrupts.get() + c2.interrupts.get());
    }
}
