package forge.screens.match;

import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenu;

import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.event.GameEventSpellAbilityCast;

/**
 * What a game mode adds to the match screen of its own matches (a banner above the fields, buttons below the team
 * signals, menu items, its own result screen). One mode registers itself with {@link #set}; every method does nothing
 * unless the match is one of that mode's.
 */
public interface MatchModeExtras {
    /** The match screen was laid out: the mode places what it shows. */
    default void placeOn(final CMatchUI ui) { }

    /** Another screen comes up: the mode takes it away again. */
    default void removeFrom(final CMatchUI ui) { }

    /** The match starts on this screen (the mode may set its music, for example). */
    default void matchStarting(final CMatchUI ui) { }

    /** Life totals or the turn changed. */
    default void refresh(final CMatchUI ui) { }

    default void noteTeamCast(final CMatchUI ui, final GameEventSpellAbilityCast event) { }

    default void noteTeamLand(final CMatchUI ui, final CardView land) { }

    /** Buttons below the team signals, shown while the list is not empty. */
    default List<? extends JComponent> promptButtons(final CMatchUI ui) {
        return Collections.emptyList();
    }

    default void addMenuItems(final JMenu menu, final CMatchUI ui) { }

    /** The game menu opens: the mode's items may show or hide. */
    default void menuOpened(final CMatchUI ui) { }

    /** Whether the result screen is the mode's: no next game and no rematch, its title and quit text. */
    default boolean ownsResult() {
        return false;
    }

    default String resultTitle(final GameView game, final int winningTeam) {
        return null;
    }

    default String resultQuitText() {
        return null;
    }

    /** The result screen of one of the mode's matches was closed. */
    default void resultClosed() { }

    static MatchModeExtras get() {
        return Registry.current;
    }

    static void set(final MatchModeExtras extras) {
        Registry.current = extras == null ? Registry.NONE : extras;
    }

    /** The registered mode (none: every method does nothing). */
    final class Registry {
        private static final MatchModeExtras NONE = new MatchModeExtras() { };
        private static volatile MatchModeExtras current = NONE;

        private Registry() { }
    }
}
