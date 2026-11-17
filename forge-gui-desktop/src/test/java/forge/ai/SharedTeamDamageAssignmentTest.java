package forge.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

/** Shared team turns: the controller of an attacking creature assigns its combat damage, not the turn's leader. */
public class SharedTeamDamageAssignmentTest extends AITest {

    /** A head that keeps which attackers it was asked to assign the damage of. */
    private static class AssigningHead extends PlayerControllerAi {
        final List<String> asked = new ArrayList<>();

        AssigningHead(final Game game, final Player p) {
            super(game, p, p.getLobbyPlayer());
        }

        @Override
        public Map<Card, Integer> assignCombatDamage(final Card attacker, final CardCollectionView blockers, final CardCollectionView remaining,
                final int damageDealt, final GameEntity defender, final boolean overrideOrder) {
            asked.add(attacker.getName());
            return super.assignCombatDamage(attacker, blockers, remaining, damageDealt, defender, overrideOrder);
        }
    }

    @Test(timeOut = 60000)
    public void testATeamMatesBlockedAttackerIsAssignedByThatTeamMate() {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0); // the turn's leader (the host, in a campaign fight)
        final Player b1 = game.getPlayers().get(1);
        final Player a2 = game.getPlayers().get(2);
        final Card wurm = addCard("Craw Wurm", a2);
        wurm.setSickness(false);
        final Card leaderWurm = addCard("Craw Wurm", a1);
        leaderWurm.setSickness(false);
        final Card bear1 = addCard("Grizzly Bears", b1);
        final Card bear2 = addCard("Grizzly Bears", b1);
        final Card bear3 = addCard("Grizzly Bears", b1);
        final Card bear4 = addCard("Grizzly Bears", b1);

        final AssigningHead h1 = new AssigningHead(game, a1);
        final AssigningHead h2 = new AssigningHead(game, a2);
        a1.dangerouslySetController(h1);
        a2.dangerouslySetController(h2);

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, a1);
        final Combat combat = new Combat(a1);
        game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(wurm, b1);
        combat.addAttacker(leaderWurm, b1);
        combat.addBlocker(wurm, bear1);
        combat.addBlocker(wurm, bear2);
        combat.addBlocker(leaderWurm, bear3);
        combat.addBlocker(leaderWurm, bear4);
        combat.orderBlockersForDamageAssignment();
        AssertJUnit.assertTrue(combat.assignCombatDamage(false));

        AssertJUnit.assertEquals("a2 assigns its own Wurm's damage", List.of("Craw Wurm"), h2.asked);
        AssertJUnit.assertEquals("a1 only its own", List.of("Craw Wurm"), h1.asked);
    }
}
