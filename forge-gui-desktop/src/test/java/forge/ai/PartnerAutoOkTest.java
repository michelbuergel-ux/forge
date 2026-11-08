package forge.ai;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.interfaces.IGuiGame;
import forge.player.PlayerControllerHuman;

/** The OK for a team mate's spell in a Hex fight: it comes by itself, even when the prompt is shown again meanwhile. */
public class PartnerAutoOkTest extends AITest {

    /** A screen that shows nothing and chooses nothing (every question is answered with "none"). */
    private static IGuiGame silentScreen() {
        return (IGuiGame) Proxy.newProxyInstance(IGuiGame.class.getClassLoader(), new Class<?>[] { IGuiGame.class }, (proxy, method, args) -> {
            final Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return false;
            } else if (type == int.class || type == long.class || type == short.class || type == byte.class) {
                return 0;
            } else if (type == double.class || type == float.class) {
                return 0.0;
            } else if (type == char.class) {
                return ' ';
            } else if (type == List.class) {
                return java.util.Collections.emptyList();
            } else if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            } else if ("equals".equals(method.getName())) {
                return proxy == args[0];
            } else if ("toString".equals(method.getName())) {
                return "silent screen";
            }
            return null;
        });
    }

    private void castBy(final Game game, final Player p) {
        final Card bears = addCardToZone("Grizzly Bears", p, ZoneType.Hand);
        final SpellAbility sa = bears.getSpellAbilities().get(0);
        sa.setActivatingPlayer(p);
        game.getStack().addAndUnfreeze(sa);
    }

    private PlayerControllerHuman humanOf(final Game game, final Player p, final boolean alwaysOk) {
        final PlayerControllerHuman human = new PlayerControllerHuman(game, p, p.getLobbyPlayer());
        human.setGui(silentScreen());
        human.getYieldController().setPref(forge.localinstance.properties.ForgePreferences.FPref.YIELD_AUTO_OK_PARTNER, String.valueOf(alwaysOk));
        return human;
    }

    /** Waits until the prompt is answered, showing it again every few hundred milliseconds as a busy screen would. */
    private static boolean answeredWhileShownAgain(final InputPassPriority prompt, final long millis) throws InterruptedException {
        final long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            if (!prompt.isOpen()) {
                return true;
            }
            prompt.showMessage();
            Thread.sleep(300);
        }
        return !prompt.isOpen();
    }

    @Test(timeOut = 30000)
    public void testTheCountdownGoesOnWhenThePromptIsShownAgain() throws Exception {
        final Game game = initAndCreateTeamGame();
        game.getRules().setStreamlinedPriority(true);
        final Player a1 = game.getPlayers().get(0);
        final Player a2 = game.getPlayers().get(2); // a1's team mate
        AssertJUnit.assertTrue(a1.sameTeam(a2));
        castBy(game, a2);
        final InputPassPriority prompt = new InputPassPriority(humanOf(game, a1, false));
        prompt.showMessage();
        AssertJUnit.assertTrue("the mate's spell is OK'd after its countdown, though the prompt was shown again and again",
                answeredWhileShownAgain(prompt, InputPassPriority.PARTNER_AUTO_OK_SECONDS * 1000L + 4000));
    }

    @Test(timeOut = 30000)
    public void testNoOkForAnOpponentsSpell() throws Exception {
        final Game game = initAndCreateTeamGame();
        game.getRules().setStreamlinedPriority(true);
        final Player a1 = game.getPlayers().get(0);
        castBy(game, game.getPlayers().get(1)); // b1, an opponent
        final InputPassPriority prompt = new InputPassPriority(humanOf(game, a1, true));
        prompt.showMessage();
        AssertJUnit.assertFalse("an opponent's spell waits for the player", answeredWhileShownAgain(prompt,
                InputPassPriority.PARTNER_AUTO_OK_SECONDS * 1000L + 1500));
    }

    @Test(timeOut = 30000)
    public void testAChoiceCancelledGivesTheOkBack() throws Exception {
        choiceCancelled(false);
    }

    @Test(timeOut = 30000)
    public void testAChoiceCancelledGivesTheOkBackWithAlwaysOk() throws Exception {
        choiceCancelled(true);
    }

    private void choiceCancelled(final boolean alwaysOk) throws Exception {
        final Game game = initAndCreateTeamGame();
        game.getRules().setStreamlinedPriority(true);
        final Player a1 = game.getPlayers().get(0);
        final Player a2 = game.getPlayers().get(2);
        // a land with several abilities: picking it asks which one, and the (silent) player picks none
        final Card wastes = addCardToZone("Adarkar Wastes", a1, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertTrue("the land offers a choice", wastes.getAllPossibleAbilities(a1, true).size() > 1);
        final InputPassPriority prompt = new InputPassPriority(humanOf(game, a1, alwaysOk));
        // the player picks the land before the mate's spell is OK'd (as a player would, a moment after it was cast)
        prompt.showMessage();
        castBy(game, a2);
        final Method select = forge.gamemodes.match.input.InputPassPriority.class.getDeclaredMethod("onCardSelected",
                Card.class, List.class, forge.util.ITriggerEvent.class);
        select.setAccessible(true);
        select.invoke(prompt, wastes, null, null);
        AssertJUnit.assertTrue("nothing was played", prompt.isOpen());
        AssertJUnit.assertTrue("the mate's spell is still OK'd by itself", answeredWhileShownAgain(prompt,
                InputPassPriority.PARTNER_AUTO_OK_SECONDS * 1000L + 4000));
    }
}
