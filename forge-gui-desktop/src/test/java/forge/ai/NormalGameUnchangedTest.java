package forge.ai;

import java.lang.reflect.Method;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.player.Player;
import forge.player.PlayerControllerHuman;

/**
 * The team play and Hex Campaign additions stay out of other game modes: a normal game behaves as in Forge
 * itself (no "waited for" marks, the plain notice window, no team layout), a team game gets them.
 */
public class NormalGameUnchangedTest extends AITest {

    private static boolean teamOrHex(final PlayerControllerHuman human) throws Exception {
        final Method m = PlayerControllerHuman.class.getDeclaredMethod("isTeamOrHexGame");
        m.setAccessible(true);
        return (Boolean) m.invoke(human);
    }

    @Test
    public void aNormalGameGetsNoneOfTheTeamOrHexAdditions() throws Exception {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(0);
        AssertJUnit.assertFalse(game.getRules().hasSharedTeamTurns());
        AssertJUnit.assertFalse(game.getRules().hasStreamlinedPriority());
        AssertJUnit.assertFalse("no team layout on the screens", game.getView().isSharedTeamTurns());
        AssertJUnit.assertFalse(game.getView().isStreamlinedPriority());

        final PlayerControllerHuman human = new PlayerControllerHuman(game, p, p.getLobbyPlayer());
        AssertJUnit.assertFalse("the plain notice window, no mute button", teamOrHex(human));
        human.noteInputOpen(true);
        AssertJUnit.assertFalse("no 'waited for' mark", p.getView().isWaitedFor());
        human.noteInputOpen(false);
    }

    @Test
    public void aTeamGameGetsThem() throws Exception {
        final Game game = initAndCreateTeamGame();
        final Player p = game.getPlayers().get(0);
        AssertJUnit.assertTrue(game.getView().isSharedTeamTurns());
        final PlayerControllerHuman human = new PlayerControllerHuman(game, p, p.getLobbyPlayer());
        AssertJUnit.assertTrue(teamOrHex(human));
        human.noteInputOpen(true);
        AssertJUnit.assertTrue(p.getView().isWaitedFor());
        human.noteInputOpen(false);
    }
}
