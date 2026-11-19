package forge.ai;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

/**
 * A defending team whose combined block is illegal (a lone blocker on a menace attacker) declares again. The head that
 * made the block must be asked again even though its only creature blocks already, and a block nobody changes must not
 * be asked about forever.
 */
public class SharedTeamIllegalBlockTest extends AITest {

    /** Blocks the menace attacker with its one creature; from the given ask on it takes the block back (0: never). */
    private static class LoneBlocker extends PlayerControllerAi {
        final AtomicInteger asked = new AtomicInteger();
        final AtomicInteger told = new AtomicInteger();
        final int fixesOnAsk;
        Card attacker;

        LoneBlocker(final Game game, final Player p, final int fixesOnAsk) {
            super(game, p, p.getLobbyPlayer());
            this.fixesOnAsk = fixesOnAsk;
        }

        @Override
        public void declareBlockers(final Player defender, final Combat combat) {
            final int ask = asked.incrementAndGet();
            final Card mine = defender.getCreaturesInPlay().getFirst();
            AssertJUnit.assertTrue("endless asking", ask < 10);
            if (fixesOnAsk > 0 && ask >= fixesOnAsk) {
                combat.removeFromCombat(mine);
            } else if (!combat.isBlocking(mine)) {
                combat.addBlocker(attacker, mine);
            }
        }

        @Override
        public void notifyOfValue(final forge.game.spellability.SpellAbility sa, final forge.game.GameObject realtedTarget, final String value) {
            told.incrementAndGet();
        }
    }

    private LoneBlocker run(final int fixesOnAsk) throws Exception {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player b1 = game.getPlayers().get(1);
        final Card brute = addCard("Boggart Brute", a1); // menace
        brute.setSickness(false);
        addCard("Grizzly Bears", b1).setSickness(false);
        final LoneBlocker head = new LoneBlocker(game, b1, fixesOnAsk);
        head.attacker = brute;
        b1.dangerouslySetController(head);

        final PhaseHandler ph = game.getPhaseHandler();
        ph.devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, a1);
        final Combat combat = new Combat(a1);
        combat.addAttacker(brute, b1);
        ph.setCombat(combat);
        final Method declare = PhaseHandler.class.getDeclaredMethod("declareBlockersTurnBasedAction");
        declare.setAccessible(true);
        declare.invoke(ph);

        AssertJUnit.assertTrue("the illegal lone block stayed", combat.getBlockers(brute).isEmpty());
        return head;
    }

    @Test(timeOut = 60000)
    public void testHeadWhoseCreatureBlocksIsAskedAgain() throws Exception {
        final LoneBlocker head = run(2);
        AssertJUnit.assertEquals("the head was not asked again", 2, head.asked.get());
        AssertJUnit.assertEquals(1, head.told.get());
    }

    @Test(timeOut = 60000)
    public void testUnchangedIllegalBlockIsUndoneInsteadOfAskingForever() throws Exception {
        final LoneBlocker head = run(0);
        AssertJUnit.assertEquals(2, head.asked.get());
    }
}
