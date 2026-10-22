package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/** The AI heads of a team (shared team turns) plan their combat together: attacks and blocks. */
public class SharedTeamAiCombatTest extends AITest {

    private static List<Card> ready(final AITest t, final String name, final int n, final Player p) {
        final List<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final Card c = t.addCard(name, p);
            c.setSickness(false);
            cards.add(c);
        }
        return cards;
    }

    /** Both AI heads of the attacking team declare into one combat, one after the other (as the game does). */
    private static Combat declareTeamAttack(final Game game, final Player a1, final Player a2) {
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, a1);
        final Combat combat = new Combat(a1);
        game.getPhaseHandler().setCombat(combat);
        a1.getController().declareAttackers(a1, combat);
        a2.getController().declareAttackers(a2, combat);
        return combat;
    }

    private static int damageOf(final List<Card> cards) {
        int dmg = 0;
        for (final Card c : cards) {
            dmg += c.getNetCombatDamage();
        }
        return dmg;
    }

    @Test
    public void testTheTeamAttacksTogetherForLethal() {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player b1 = game.getPlayers().get(1);
        final Player a2 = game.getPlayers().get(2);
        final Player b2 = game.getPlayers().get(3);
        // each head alone: 3 bears into 2 bigger blockers, 2 damage through and two bears lost;
        // together: 6 bears against 2 blockers, 8 damage through, and the enemy team has 4 life
        ready(this, "Grizzly Bears", 3, a1);
        ready(this, "Grizzly Bears", 3, a2);
        ready(this, "Hill Giant", 1, b1);
        ready(this, "Hill Giant", 1, b2);
        b1.setLife(4, null);
        AssertJUnit.assertEquals("shared life", 4, b2.getLife());

        final Combat combat = declareTeamAttack(game, a1, a2);
        final List<Card> attackers = combat.getAttackers();
        AssertJUnit.assertEquals("a1 attacks with everything", 3, CardLists.filterControlledBy(attackers, a1).size());
        AssertJUnit.assertEquals("a2 attacks with everything", 3, CardLists.filterControlledBy(attackers, a2).size());
        AssertJUnit.assertTrue("lethal past two blockers", damageOf(attackers) - 2 * 2 >= 4);
    }

    @Test
    public void testTheTeamKeepsBlockersAgainstABigerEnemyTeam() {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player b1 = game.getPlayers().get(1);
        final Player a2 = game.getPlayers().get(2);
        final Player b2 = game.getPlayers().get(3);
        // no lethal for the AI team, and the enemy team would kill it on the backswing if it attacked with everything
        ready(this, "Grizzly Bears", 2, a1);
        ready(this, "Grizzly Bears", 2, a2);
        ready(this, "Hill Giant", 3, b1);
        ready(this, "Hill Giant", 3, b2);
        a1.setLife(12, null); // 6 giants hit for 18 next turn
        b1.setLife(20, null);

        final Combat combat = declareTeamAttack(game, a1, a2);
        AssertJUnit.assertTrue("not everything into six bigger blockers", combat.getAttackers().size() < 4);
    }

    @Test
    public void testTheTeamBlocksTogether() {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player b1 = game.getPlayers().get(1);
        final Player a2 = game.getPlayers().get(2);
        final Player b2 = game.getPlayers().get(3);
        // the b team (AI) is attacked by two giants that would be lethal together; each b head has one chump blocker
        final List<Card> giants = ready(this, "Hill Giant", 2, a1);
        final Card bear1 = ready(this, "Grizzly Bears", 1, b1).get(0);
        final Card bear2 = ready(this, "Grizzly Bears", 1, b2).get(0);
        b1.setLife(5, null);

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, a1);
        final Combat combat = new Combat(a1);
        combat.addAttacker(giants.get(0), b1);
        combat.addAttacker(giants.get(1), b2);
        game.getPhaseHandler().setCombat(combat);
        b1.getController().declareBlockers(b1, combat);
        b2.getController().declareBlockers(b2, combat);

        AssertJUnit.assertTrue(combat.isBlocking(bear1));
        AssertJUnit.assertTrue(combat.isBlocking(bear2));
        AssertJUnit.assertFalse("each giant blocked once", combat.getBlockers(giants.get(0)).isEmpty());
        AssertJUnit.assertFalse(combat.getBlockers(giants.get(1)).isEmpty());
    }
}
