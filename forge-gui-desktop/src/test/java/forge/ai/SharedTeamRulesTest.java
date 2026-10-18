package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.mulligan.MulliganService;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Two-Headed Giant rules in games with shared team turns: combat and mulligans of a team. */
public class SharedTeamRulesTest extends AITest {

    @Test
    public void testTeamMateMayBlockAnAttackOnTheOtherHead() {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player b1 = game.getPlayers().get(1);
        final Player b2 = game.getPlayers().get(3);
        final Card attacker = addCard("Grizzly Bears", a1);
        final Card blocker = addCard("Grizzly Bears", b2);
        attacker.setSickness(false);

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, a1);
        final Combat combat = new Combat(a1);
        combat.addAttacker(attacker, b1);
        game.getPhaseHandler().setCombat(combat);

        // an attack on one head is an attack on the team, and the team blocks together
        AssertJUnit.assertTrue(combat.isPlayerAttacked(b2));
        AssertJUnit.assertTrue(CombatUtil.canBlock(b2, combat));
        AssertJUnit.assertTrue(CombatUtil.canBlock(attacker, blocker, combat));
    }

    @Test
    public void testLifeLossOfOneHeadIsShared() {
        final Game game = initAndCreateTeamGame();
        final Player b1 = game.getPlayers().get(1);
        final Player b2 = game.getPlayers().get(3);
        final int before = b2.getLife();
        AssertJUnit.assertEquals(before, b1.getLife());

        b1.loseLife(2, false, false, null);

        AssertJUnit.assertEquals(before - 2, b1.getLife());
        AssertJUnit.assertEquals(before - 2, b2.getLife());
    }

    @Test
    public void testTeamMatesSeeEachOthersHands() {
        final Game game = initAndCreateTeamGame();
        for (final Player p : game.getPlayers()) {
            p.updateOpponentsForView();
        }
        final Player a1 = game.getPlayers().get(0);
        final Player b1 = game.getPlayers().get(1);
        final Player a2 = game.getPlayers().get(2);
        final Card inHand = addCardToZone("Grizzly Bears", a1, ZoneType.Hand);

        AssertJUnit.assertTrue(inHand.getView().canBeShownTo(a1.getView()));
        AssertJUnit.assertTrue("team mate can't see the hand", inHand.getView().canBeShownTo(a2.getView()));
        AssertJUnit.assertFalse("opponent sees the hand", inHand.getView().canBeShownTo(b1.getView()));
    }

    /** Answers the mulligan question through a script; asked "concurrently" like a human at its own screen. */
    private static class MulliganHead extends PlayerControllerAi {
        final AtomicInteger asked = new AtomicInteger();
        volatile int lastCardsToReturn = -1;
        CountDownLatch bothAsked;
        boolean mulliganOnce;

        MulliganHead(final Game game, final Player p) {
            super(game, p, p.getLobbyPlayer());
        }

        @Override
        public boolean mulliganKeepHand(final Player firstPlayer, final int cardsToReturn) {
            lastCardsToReturn = cardsToReturn;
            if (asked.incrementAndGet() == 1 && bothAsked != null) {
                bothAsked.countDown();
                try {
                    AssertJUnit.assertTrue("team mates were not asked at the same time", bothAsked.await(10, TimeUnit.SECONDS));
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return !(mulliganOnce && asked.get() == 1);
        }

        @Override
        public boolean canChooseSpellAbilityConcurrently() {
            return true;
        }
    }

    @Test(timeOut = 60000)
    public void testTeamDecidesMulligansTogetherAndTheFirstIsFree() {
        final Game game = initAndCreateTeamGame();
        final Map<Player, MulliganHead> heads = new ConcurrentHashMap<>();
        final CountDownLatch bothAsked = new CountDownLatch(2);
        for (final Player p : game.getPlayers()) {
            fillLibrary(p, 30);
            for (int i = 0; i < 7; i++) {
                p.drawCard();
            }
            final MulliganHead head = new MulliganHead(game, p);
            if (p.getTeam() == 0) {
                head.bothAsked = bothAsked;
                head.mulliganOnce = p == game.getPlayers().get(0);
            }
            p.dangerouslySetController(head);
            heads.put(p, head);
        }
        final Player a1 = game.getPlayers().get(0);

        new MulliganService(a1).perform();

        // a1 took a mulligan: it was asked again, and the free first mulligan cost no card
        final MulliganHead h1 = heads.get(a1);
        AssertJUnit.assertEquals(2, h1.asked.get());
        AssertJUnit.assertEquals(7, a1.getCardsIn(ZoneType.Hand).size() - h1.lastCardsToReturn);
        // its team mate kept right away
        AssertJUnit.assertEquals(1, heads.get(game.getPlayers().get(2)).asked.get());
    }
}
