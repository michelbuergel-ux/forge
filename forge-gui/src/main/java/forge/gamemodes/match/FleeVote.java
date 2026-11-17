package forge.gamemodes.match;

import java.util.function.BiConsumer;

import forge.game.player.Player;

/**
 * A vote to flee a match (see {@link forge.interfaces.IGameController#voteFlee}): only a game mode that lets its
 * players flee counts the votes; it registers itself with {@link #setCounter}.
 */
public final class FleeVote {
    private static volatile BiConsumer<Player, Boolean> counter;

    private FleeVote() { }

    public static void setCounter(final BiConsumer<Player, Boolean> counter0) {
        counter = counter0;
    }

    /** The player votes to flee (true) or to fight on (false). */
    public static void cast(final Player voter, final boolean flee) {
        final BiConsumer<Player, Boolean> c = counter;
        if (c != null) {
            c.accept(voter, flee);
        }
    }
}
