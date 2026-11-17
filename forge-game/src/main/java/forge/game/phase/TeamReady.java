package forge.game.phase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.game.player.Player;

/**
 * Team mates asked at the same time (shared team turns: passing priority, declaring attackers or blockers).
 * A head's OK only counts once all of them said OK; until then it may take it back. Each prompt hands in how
 * it finishes, and when the last head says OK all of them finish.
 */
public final class TeamReady {
    /** The heads still deciding. */
    private final Set<Player> deciding;
    /** The heads that said OK, with how their prompts finish. */
    private final Map<Player, Runnable> ready = new LinkedHashMap<>();
    /** How each head's open prompt shows again when a mate says OK or takes it back. */
    private final Map<Player, Runnable> watchers = new LinkedHashMap<>();
    private boolean closed;

    public TeamReady(final Collection<Player> heads) {
        deciding = new LinkedHashSet<>(heads);
    }

    /**
     * The head says OK. Returns true if it now waits for its mates; otherwise (the last one, or not one of the heads
     * deciding together) the prompts finish at once, its own included.
     */
    public boolean ready(final Player head, final Runnable finish) {
        final List<Runnable> finishing;
        final List<Runnable> others;
        synchronized (this) {
            if (closed || !deciding.contains(head)) {
                finishing = List.of(finish);
                others = List.of();
            } else {
                ready.put(head, finish);
                finishing = allReady();
                others = finishing.isEmpty() ? othersWatching(head) : List.of();
            }
        }
        if (finishing.isEmpty()) {
            others.forEach(Runnable::run); // the mates' prompts show this OK
            return true;
        }
        finishing.forEach(Runnable::run);
        return false;
    }

    /** The head takes its OK back; false if it is too late (all of them said OK). */
    public boolean takeBack(final Player head) {
        final List<Runnable> others;
        synchronized (this) {
            if (closed || ready.remove(head) == null) {
                return false;
            }
            others = othersWatching(head);
        }
        others.forEach(Runnable::run);
        return true;
    }

    /** The head's prompt is open: it shows again (with the mates' OKs) whenever a mate says OK or takes it back. */
    public synchronized void watch(final Player head, final Runnable reshow) {
        if (!closed) {
            watchers.put(head, reshow);
        }
    }

    /** The mates of the head that said OK already. */
    public synchronized List<Player> readyMates(final Player head) {
        final List<Player> mates = new ArrayList<>();
        if (!closed) {
            for (final Player p : ready.keySet()) {
                if (p != head) {
                    mates.add(p);
                }
            }
        }
        return mates;
    }

    private List<Runnable> othersWatching(final Player head) {
        final List<Runnable> others = new ArrayList<>();
        watchers.forEach((p, reshow) -> {
            if (p != head) {
                others.add(reshow);
            }
        });
        return others;
    }

    /** The head's answer came in some other way (it acted, or passed without a prompt): the others don't wait for it. */
    public void answered(final Player head) {
        final List<Runnable> finishing;
        synchronized (this) {
            if (closed || !deciding.remove(head)) {
                return;
            }
            ready.remove(head);
            finishing = allReady();
        }
        finishing.forEach(Runnable::run);
    }

    /** The heads the given one waits for. */
    public synchronized List<Player> waitingFor(final Player head) {
        final List<Player> open = new ArrayList<>();
        for (final Player p : deciding) {
            if (p != head && !ready.containsKey(p)) {
                open.add(p);
            }
        }
        return open;
    }

    /** No more OKs or take backs: the decision is over. */
    public synchronized void close() {
        closed = true;
        watchers.clear();
    }

    private List<Runnable> allReady() {
        if (deciding.isEmpty() || !ready.keySet().containsAll(deciding)) {
            return List.of();
        }
        closed = true;
        watchers.clear();
        return new ArrayList<>(ready.values());
    }
}
