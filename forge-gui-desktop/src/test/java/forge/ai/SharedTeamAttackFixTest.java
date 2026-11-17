package forge.ai;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameObject;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Shared team turns: when the team's attack breaks a rule, only the head that can fix it declares again. */
public class SharedTeamAttackFixTest extends AITest {

    /** A head that declares by a script and keeps what it is told. */
    private static class ScriptedHead extends PlayerControllerAi {
        int declared;
        final List<String> told = new ArrayList<>();
        BiConsumer<Integer, Combat> script = (n, c) -> { };

        ScriptedHead(final Game game, final Player p) {
            super(game, p, p.getLobbyPlayer());
        }

        @Override
        public void declareAttackers(final Player attacker, final Combat combat) {
            script.accept(++declared, combat);
        }

        @Override
        public void notifyOfValue(final SpellAbility sa, final GameObject target, final String value) {
            told.add(value);
        }
    }

    @Test(timeOut = 60000)
    public void testOnlyTheHeadThatBrokeTheRuleDeclaresAgain() {
        final Game game = initAndCreateTeamGame();
        final Player a1 = game.getPlayers().get(0);
        final Player a2 = game.getPlayers().get(2);
        final Card juggernaut = addCard("Juggernaut", a1); // attacks each combat if able
        juggernaut.setSickness(false);
        final Card bears = addCard("Grizzly Bears", a2);
        bears.setSickness(false);

        final ScriptedHead h1 = new ScriptedHead(game, a1);
        final ScriptedHead h2 = new ScriptedHead(game, a2);
        a1.dangerouslySetController(h1);
        a2.dangerouslySetController(h2);
        // a1 forgets its Juggernaut at first; a2 attacks with its bears
        h1.script = (n, combat) -> {
            if (n > 1) {
                combat.addAttacker(juggernaut, defender(combat));
            }
        };
        h2.script = (n, combat) -> combat.addAttacker(bears, defender(combat));

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, a1);
        final Combat combat = new Combat(a1);
        game.getPhaseHandler().setCombat(combat);
        game.getPhaseHandler().devDeclareAttackers();

        AssertJUnit.assertEquals("a1 declared again", 2, h1.declared);
        AssertJUnit.assertEquals("a2's attack stayed", 1, h2.declared);
        AssertJUnit.assertTrue(combat.isAttacking(juggernaut));
        AssertJUnit.assertTrue(combat.isAttacking(bears));
        AssertJUnit.assertEquals(1, h1.told.size());
        AssertJUnit.assertTrue("a1 is told what could attack: " + h1.told, h1.told.get(0).contains("Juggernaut"));
        AssertJUnit.assertEquals(1, h2.told.size());
        AssertJUnit.assertTrue("a2 is told who declares again: " + h2.told, h2.told.get(0).contains(a1.getName()));
    }

    private static GameEntity defender(final Combat combat) {
        return combat.getDefenders().getFirst();
    }
}
