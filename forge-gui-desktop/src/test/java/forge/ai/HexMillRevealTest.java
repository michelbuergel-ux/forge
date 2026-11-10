package forge.ai;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gui.interfaces.IGuiGame;
import forge.player.PlayerControllerHuman;

/** Cards revealed only to be put into the graveyard (Consuming Aberration) show no window in a Hex fight. */
public class HexMillRevealTest extends AITest {

    /** A screen that counts the reveal windows it is asked to show and answers everything else with "none". */
    private static IGuiGame countingScreen(final AtomicInteger reveals) {
        return (IGuiGame) Proxy.newProxyInstance(IGuiGame.class.getClassLoader(), new Class<?>[] { IGuiGame.class }, (proxy, method, args) -> {
            if ("reveal".equals(method.getName()) || "message".equals(method.getName())) {
                reveals.incrementAndGet();
            }
            final Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return false;
            } else if (type == int.class || type == long.class) {
                return 0;
            } else if (type == List.class) {
                return java.util.Collections.emptyList();
            } else if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            } else if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            return null;
        });
    }

    /** Consuming Aberration's trigger against an opponent played by a person; returns how many windows that person saw. */
    private int windowsSeen(final boolean hexFight) {
        final Game game = initAndCreateGame();
        game.getRules().setStreamlinedPriority(hexFight);
        final Player me = game.getPlayers().get(0);
        final Player opponent = game.getPlayers().get(1);
        final Card aberration = addCard("Consuming Aberration", me);
        addCardToZone("Plains", opponent, ZoneType.Library);
        final SpellAbility grind = AbilityFactory.getAbility(aberration.getSVar("Grind"), aberration);
        grind.setActivatingPlayer(me);

        final AtomicInteger reveals = new AtomicInteger();
        final PlayerControllerHuman person = new PlayerControllerHuman(game, opponent, opponent.getLobbyPlayer());
        person.setGui(countingScreen(reveals));
        opponent.runWithController(() -> AbilityUtils.resolve(grind), person);

        AssertJUnit.assertEquals("the land went into the graveyard", 1, countCardsWithName(game, "Plains", ZoneType.Graveyard));
        return reveals.get();
    }

    @Test
    public void testNoWindowInAHexFight() {
        AssertJUnit.assertEquals(0, windowsSeen(true));
    }

    @Test
    public void testTheWindowElsewhere() {
        AssertJUnit.assertEquals(1, windowsSeen(false));
    }
}
