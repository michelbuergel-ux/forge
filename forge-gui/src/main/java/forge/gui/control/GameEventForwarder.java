package forge.gui.control;

import com.google.common.eventbus.Subscribe;
import forge.game.event.GameEvent;
import forge.game.event.GameEventPlayerPriority;
import forge.game.event.GameEventTurnEnded;
import forge.gui.interfaces.IGuiGame;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * Buffers game events and flushes them to the GUI in batches.
 *
 * <p>Flush triggers (all on the game thread):
 * <ul>
 *   <li>Priority: a player receives it once the game has settled, so a batch holds whole actions
 *       and what one player did is sent before anyone chooses what to do next. A pass with nothing
 *       but redraw hints since the last batch sends nothing</li>
 *   <li>End of turn: normally no player receives priority in the cleanup step, so what happened in
 *       it is sent before the turn ends and never travels with the next turn's start</li>
 *   <li>Input queue change: registered as {@link Observer} on player InputQueues,
 *       ensuring events are delivered before the game thread blocks for input</li>
 *   <li>Sync points: explicit {@link #flush()} from {@code flushPendingEvents()}</li>
 * </ul>
 *
 * <p>No daemon thread — delta collection runs on the game thread, or with shared team priority
 * on the threads of the team mates' prompts while the game thread waits for them. Those prompts
 * open at the same time and each flushes every forwarder, so all forwarders share one lock.
 */
public class GameEventForwarder implements Observer {
    private final IGuiGame gui;
    private final List<GameEvent> pendingEvents = new ArrayList<>();
    /** Whether anything but a redraw hint is waiting, which is what makes a pass of priority worth a batch. */
    private boolean somethingHappened;
    /**
     * One lock for all forwarders and network state walks: a flush walks the shared game state to
     * build the deltas. Taken by {@code ProtocolGuiGame.updateGameView} as well.
     */
    public static final Object LOCK = new Object();

    public GameEventForwarder(IGuiGame gui) {
        this.gui = gui;
    }

    @Subscribe
    public void receiveGameEvent(GameEvent ev) {
        synchronized (LOCK) {
            if (ev instanceof GameEventTurnEnded && somethingHappened) {
                flush();
            }
            pendingEvents.add(ev);
            if (!(ev instanceof GameEventPlayerPriority)) {
                somethingHappened |= !ev.isRedrawHint();
            } else if (somethingHappened) {
                flush();
            }
        }
    }

    public void flush() {
        synchronized (LOCK) {
            if (pendingEvents.isEmpty()) {
                return;
            }
            List<GameEvent> batch = new ArrayList<>(pendingEvents);
            pendingEvents.clear();
            somethingHappened = false;
            gui.handleGameEvents(batch);
        }
    }

    public boolean hasPendingEvents() {
        synchronized (LOCK) {
            return !pendingEvents.isEmpty();
        }
    }

    /**
     * Called when an InputQueue changes (setInput/removeInput/clearInputs).
     * Flushes any pending events on the game thread before it blocks for input.
     */
    @Override
    public void update(Observable o, Object arg) {
        flush();
    }

}
