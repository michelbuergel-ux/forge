package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.phase.TeamReady;
import forge.game.player.Player;
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
        public void interruptSpellAbilityChoice(final Player actedFirst, final Card card) {
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

    @Test(timeOut = 60000)
    public void testTeamMateCanActWhileTheOtherIsStillThinking() {
        final Game game = initAndCreateTeamGame();
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

    /** A head whose prompt stays open while its mate acts: it is told, and shown anew afterwards. */
    private static class KeepOpenHead extends ConcurrentHead {
        final java.util.List<String> mateActs = new java.util.concurrent.CopyOnWriteArrayList<>();
        final CountDownLatch refreshed = new CountDownLatch(1);

        KeepOpenHead(final Game game, final Player p) {
            super(game, p);
        }

        @Override
        public boolean canKeepSpellAbilityChoiceOpen() {
            return true;
        }

        @Override
        public void mateActing(final Player mate, final Card card) {
            mateActs.add(mate.getName() + ":" + (card == null ? "-" : card.getName()));
        }

        @Override
        public void refreshSpellAbilityChoice() {
            refreshed.countDown();
        }
    }

    @Test(timeOut = 60000)
    public void testAPromptStaysOpenWhileTheMateActs() {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player a2 = game.getPlayers().get(2);
        final Card forest = addCardToZone("Forest", a2, ZoneType.Hand);
        final KeepOpenHead c1 = new KeepOpenHead(game, a1);
        final ConcurrentHead c2 = new ConcurrentHead(game, a2);
        a1.dangerouslySetController(c1);
        a2.dangerouslySetController(c2);

        final CountDownLatch a1Thinking = new CountDownLatch(1);
        // a1 thinks through a2's land: its prompt is never closed, only shown anew; then it passes
        c1.script = n -> {
            a1Thinking.countDown();
            await(c1.refreshed);
            return null;
        };
        c2.script = n -> {
            if (n == 1) {
                await(a1Thinking);
                for (final SpellAbility sa : forest.getAllPossibleAbilities(a2, true)) {
                    if (sa.isLandAbility()) {
                        return Collections.singletonList(sa);
                    }
                }
            }
            return null;
        };

        game.getPhaseHandler().mainLoopStep();

        AssertJUnit.assertTrue(a2.getCardsIn(ZoneType.Battlefield).contains(game.getCardState(forest)));
        AssertJUnit.assertEquals("never interrupted", 0, c1.interrupts.get());
        AssertJUnit.assertEquals("one prompt, kept open", 1, c1.prompts.get());
        AssertJUnit.assertEquals("told who acts with what", Collections.singletonList(a2.getName() + ":Forest"), c1.mateActs);
        AssertJUnit.assertEquals(0, c1.refreshed.getCount());
        AssertJUnit.assertEquals("a2 was asked again after its land", 2, c2.prompts.get());
        AssertJUnit.assertFalse(game.getPhaseHandler().getPriorityPlayer().sameTeam(a1));
    }

    private static void await(final CountDownLatch latch) {
        try {
            AssertJUnit.assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test(timeOut = 60000)
    public void testAPassWaitsForTheMateAndCanBeTakenBack() {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player a2 = game.getPlayers().get(2);
        final ConcurrentHead c1 = new ConcurrentHead(game, a1);
        final ConcurrentHead c2 = new ConcurrentHead(game, a2);
        a1.dangerouslySetController(c1);
        a2.dangerouslySetController(c2);

        final CountDownLatch a1Waits = new CountDownLatch(1);
        final CountDownLatch a1TookBack = new CountDownLatch(1);
        final CountDownLatch a1WaitsAgain = new CountDownLatch(1);
        final CountDownLatch a1Finished = new CountDownLatch(1);
        final AtomicInteger reshows = new AtomicInteger();
        // a1 says OK, takes it back, and says OK again: its prompt only ends once a2 said OK too
        c1.script = n -> {
            final TeamReady team = game.getPhaseHandler().getTeamReady();
            AssertJUnit.assertTrue("a1 waits for a2", team.ready(a1, a1Finished::countDown));
            AssertJUnit.assertEquals(Collections.singletonList(a2), team.waitingFor(a1));
            a1Waits.countDown();
            await(a1TookBack);
            AssertJUnit.assertTrue(team.ready(a1, a1Finished::countDown));
            a1WaitsAgain.countDown();
            await(a1Finished);
            return null;
        };
        c2.script = n -> {
            final TeamReady team = game.getPhaseHandler().getTeamReady();
            await(a1Waits);
            AssertJUnit.assertEquals("a2's prompt shows a1's OK", Collections.singletonList(a1), team.readyMates(a2));
            team.watch(a2, reshows::incrementAndGet);
            AssertJUnit.assertTrue("a2 has not said OK yet: a1 may take its OK back", team.takeBack(a1));
            AssertJUnit.assertTrue(team.readyMates(a2).isEmpty());
            a1TookBack.countDown();
            await(a1WaitsAgain);
            AssertJUnit.assertEquals("a2's prompt showed both changes", 2, reshows.get());
            AssertJUnit.assertFalse("a2 is the last one: no waiting", team.ready(a2, () -> { }));
            AssertJUnit.assertFalse("too late to take it back", team.takeBack(a1));
            return null;
        };

        game.getPhaseHandler().mainLoopStep();

        AssertJUnit.assertEquals(0, a1Finished.getCount());
        AssertJUnit.assertEquals(1, c1.prompts.get());
        AssertJUnit.assertEquals(1, c2.prompts.get());
        AssertJUnit.assertEquals(0, c1.interrupts.get() + c2.interrupts.get());
        AssertJUnit.assertFalse("the team passed", game.getPhaseHandler().getPriorityPlayer().sameTeam(a1));
        AssertJUnit.assertNull(game.getPhaseHandler().getTeamReady());
    }

    @Test
    public void testThePlayersSeeWhoTheGameWaitsFor() {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final forge.player.PlayerControllerHuman human = new forge.player.PlayerControllerHuman(game, a1, a1.getLobbyPlayer());
        AssertJUnit.assertFalse(a1.getView().isWaitedFor());
        human.noteInputOpen(true);
        AssertJUnit.assertTrue("a prompt of a1 is open", a1.getView().isWaitedFor());
        human.noteInputOpen(true); // a prompt inside it (a target, say)
        human.noteInputOpen(false);
        AssertJUnit.assertTrue("the first prompt is still open", a1.getView().isWaitedFor());
        human.noteOkSaid(true);
        AssertJUnit.assertFalse("a1 said OK and waits for its mate", a1.getView().isWaitedFor());
        human.noteOkSaid(false);
        AssertJUnit.assertTrue("taken back", a1.getView().isWaitedFor());
        human.noteInputOpen(false);
        AssertJUnit.assertFalse(a1.getView().isWaitedFor());
    }

    @Test(timeOut = 60000)
    public void testPhaseEndsOnlyAfterBothTeamsPassed() {
        final Game game = initAndCreateTeamGame();
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
