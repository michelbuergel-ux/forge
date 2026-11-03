package forge.gamemodes.hexcrawl;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;

/**
 * The host's way out of a fight that hangs: forcing the next step, and breaking the fight off without a
 * result, also when an error ended the game loop before (then no loop is left to end the game).
 */
public class HexFightAbortTest extends AITest {

    private static HexFight fight(final AtomicInteger aborted) {
        final HexCampaign c = HexCampaign.create("Abort", 5L, 6, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        final HexEncounter enc = HexContent.get().randomEncounter(HexColor.RED, 0.5, new java.util.Random(2));
        return new HexFight(c, enc, aborted::incrementAndGet);
    }

    @Test
    public void testAFightWhoseGameLoopIsGoneIsBrokenOffWithoutAResult() {
        final Game game = initAndCreateGame(); // no game loop runs it, as after an error that ended the loop
        final AtomicInteger aborted = new AtomicInteger();
        final HexFight fight = fight(aborted);
        fight.begin(game);
        AssertJUnit.assertSame(fight, HexFight.current());

        AssertJUnit.assertFalse("forcing it on does not help: no game loop", HexFight.gameLoopRunning());
        AssertJUnit.assertFalse(fight.forceNextStep());

        final long t0 = System.currentTimeMillis();
        fight.abort();
        AssertJUnit.assertTrue("no waiting for a loop that is gone", System.currentTimeMillis() - t0 < HexFight.ABORT_WAIT_MS);
        AssertJUnit.assertTrue(game.isGameOver());
        AssertJUnit.assertTrue("a draw for everybody", game.getOutcome().isDraw());
        AssertJUnit.assertFalse(HexBattle.partyWon(game));
        AssertJUnit.assertEquals("told once", 1, aborted.get());
        AssertJUnit.assertNull(HexFight.current());

        fight.abort(); // again: nothing more
        fight.endWithoutResult();
        AssertJUnit.assertEquals(1, aborted.get());
    }

    @Test
    public void testTheGameLoopEndsAnAbortedFightOnlyOnce() {
        final Game game = initAndCreateGame();
        final AtomicInteger aborted = new AtomicInteger();
        final HexFight fight = fight(aborted);
        fight.begin(game);
        AssertJUnit.assertFalse(fight.isAborted());
        fight.abort();
        AssertJUnit.assertTrue(fight.isAborted());
        // the end of the game loop comes afterwards (see HexBattle): it is not told a second time
        fight.endWithoutResult();
        AssertJUnit.assertEquals(1, aborted.get());
        // a fight that ended with a result is not broken off afterwards
        final AtomicInteger later = new AtomicInteger();
        final HexFight won = fight(later);
        won.begin(initAndCreateGame());
        won.end(HexBattle.Result.WON);
        won.endWithoutResult();
        AssertJUnit.assertEquals(0, later.get());
        AssertJUnit.assertNull(HexFight.current());
    }
}
