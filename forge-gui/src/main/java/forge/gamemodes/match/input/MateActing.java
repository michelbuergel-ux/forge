package forge.gamemodes.match.input;

import java.util.List;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.util.ITriggerEvent;
import forge.util.Localizer;

/**
 * Team play: a team mate acts (casts, pays) while this player's priority prompt stays open. The prompt says what
 * the mate does and shows its card; there is no OK meanwhile, and a card picked is played right after the mate's.
 * Part of an InputPassPriority.
 */
final class MateActing {
    private final InputPassPriority inp;
    /** What the mate does, and its card; null while no mate acts. */
    private volatile String what;
    private volatile CardView card;
    /** A card picked while the mate acted: it is played right after (see done). */
    private Card queuedCard;
    private List<Card> queuedOthers;
    private ITriggerEvent queuedEvent;

    MateActing(final InputPassPriority inp0) {
        inp = inp0;
    }

    boolean isActive() {
        return what != null;
    }

    /** A card picked meanwhile: played right after the mate's action. */
    void queue(final Card picked, final List<Card> others, final ITriggerEvent event) {
        queuedCard = picked;
        queuedOthers = others;
        queuedEvent = event;
        showPrompt();
    }

    /** Cancel while the mate acts: the card picked is dropped again. */
    void dropQueued() {
        if (queuedCard != null) {
            queuedCard = null;
            showPrompt();
        }
    }

    /** A team mate acts: the prompt says so and shows its card; no OK meanwhile, and a card picked waits for the mate. */
    void show(final String what0, final CardView card0) {
        what = what0;
        card = card0;
        inp.partnerCancel();
        if (!inp.isFinished()) {
            showPrompt();
        }
    }

    boolean hasQueuedCard() {
        return queuedCard != null;
    }

    void showPrompt() {
        final Localizer loc = Localizer.getInstance();
        final String hint = queuedCard == null ? loc.getMessage("lblTeamMateActingHint")
                : loc.getMessage("lblTeamCardQueued", forge.util.CardTranslation.getTranslatedName(queuedCard.getName()));
        final String box = "<table width=\"100%\" cellpadding=\"6\" bgcolor=\"#5a4a1f\"><tr><td><font color=\"#ffffff\"><b>"
                + PartnerPrompt.escape(what) + "</b><br>" + PartnerPrompt.escape(hint) + "</font></td></tr></table>";
        inp.showMessage(box + inp.getTurnPhasePriorityMessage(inp.getController().getGame()), card);
        if (card != null) {
            inp.getController().getGui().setCard(card); // the mate's card big in the picture panel
        }
        inp.getController().getGui().updateButtons(inp.getOwner(), loc.getMessage("lblOK"), loc.getMessage("lblCancel"), false, false, false);
    }

    /**
     * The mate's action is done: the prompt shows the new state (an OK said before is void, the game changed), and a
     * card picked meanwhile is played now.
     */
    void done() {
        what = null;
        card = null;
        if (inp.isFinished()) {
            return;
        }
        inp.newTeamRound();
        final Card picked = queuedCard;
        final List<Card> others = queuedOthers;
        final ITriggerEvent event = queuedEvent;
        queuedCard = null;
        queuedOthers = null;
        queuedEvent = null;
        inp.showMessage();
        if (picked != null) {
            forge.gui.FThreads.invokeInEdtLater(() -> inp.replayCard(picked, others, event));
        }
    }
}
