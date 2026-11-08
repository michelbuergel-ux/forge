package forge.gamemodes.match.input;

import java.util.concurrent.ScheduledFuture;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.util.Localizer;
import forge.util.ThreadUtil;

/**
 * Hex Campaign fights (streamlined priority): a team mate's spell or ability on top of the stack waits for this
 * player's OK, shown in a green box and OK'd by itself after a few seconds (at once with "Always OK my partner"),
 * unless the player holds or starts to act. Part of an InputPassPriority.
 */
final class PartnerPrompt {
    private final InputPassPriority inp;
    /** The running countdown for a team mate's stack item, if any. */
    private ScheduledFuture<?> countdown;
    private int secondsLeft;
    /** The team mate's stack item the countdown runs for. */
    private SpellAbilityStackInstance counted;
    /** The player pressed Hold (or started to act): no countdown for this prompt any more. */
    private volatile boolean held;

    PartnerPrompt(final InputPassPriority inp0) {
        inp = inp0;
    }

    boolean isHeld() {
        return held;
    }

    /** A new round of the team's decision: the hold is over. */
    void release() {
        held = false;
    }

    /**
     * The item on top of the stack if a team mate (not this player, not an opponent) put it there,
     * in a game with streamlined priority (Hex Campaign fights); otherwise null.
     */
    SpellAbilityStackInstance item() {
        final Game game = inp.getController().getGame();
        final Player me = inp.getController().getPlayer();
        if (game == null || me == null || !game.getRules().hasStreamlinedPriority() || game.getStack().isEmpty()) {
            return null;
        }
        final SpellAbilityStackInstance top = game.getStack().peek();
        final Player by = top == null ? null : top.getActivatingPlayer();
        return by == null || by.equals(me) || by.isOpponentOf(me) ? null : top;
    }

    /** Is the player's "Always OK my partner" on? */
    private boolean alwaysOk() {
        return inp.getController().getYieldController().getBoolPref(FPref.YIELD_AUTO_OK_PARTNER);
    }

    /**
     * A team mate's spell or ability waits for this player's OK: it is shown highlighted with its
     * card, and OK'd by itself after a few seconds (at once with "Always OK my partner"), unless the
     * player holds or starts to act.
     */
    void show(final SpellAbilityStackInstance item) {
        final Localizer loc = Localizer.getInstance();
        final Card source = item.getSourceCard();
        final String what = source == null ? item.getStackDescription() : source.getName();
        final String hint;
        if (held) {
            hint = loc.getMessage("lblPartnerHeld");
        } else if (alwaysOk()) {
            hint = loc.getMessage("lblPartnerAlwaysOk");
        } else {
            hint = loc.getMessage("lblPartnerAutoOk", String.valueOf(InputPassPriority.PARTNER_AUTO_OK_SECONDS));
        }
        // a coloured box on top of the prompt (the desktop prompt shows HTML)
        final String box = "<table width=\"100%\" cellpadding=\"6\" bgcolor=\"#1f6f43\"><tr><td><font color=\"#ffffff\"><b>"
                + escape(loc.getMessage("lblPartnerAction", item.getActivatingPlayer().getName(), what)) + "</b><br>"
                + escape(hint) + "</font></td></tr></table>";
        inp.showMessage(box + inp.getTurnPhasePriorityMessage(inp.getController().getGame()), source == null ? null : source.getView());
        inp.clearChosen();
        if (source != null) {
            inp.getController().getGui().setCard(source.getView()); // the card big in the picture panel
        }
        if (held) {
            inp.getController().getGui().updateButtons(inp.getOwner(), loc.getMessage("lblOK"), loc.getMessage("lblHold"), true, false, true);
        } else if (alwaysOk()) {
            inp.getController().getGui().updateButtons(inp.getOwner(), loc.getMessage("lblOK"), loc.getMessage("lblHold"), true, true, true);
            start(0, item);
        } else if (!countsDown(item)) {
            start(InputPassPriority.PARTNER_AUTO_OK_SECONDS, item);
        } else {
            // shown again (a mate's state changed, say): the countdown goes on, it does not start over
            showCountdownButtons();
        }
        inp.getController().getGui().alertUser();
    }

    static String escape(final String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Is the countdown running for this item? */
    synchronized boolean countsDown(final SpellAbilityStackInstance item) {
        return countdown != null && item != null && item == counted;
    }

    private synchronized void start(final int seconds, final SpellAbilityStackInstance item) {
        cancel();
        counted = item;
        secondsLeft = seconds;
        if (seconds <= 0) {
            countdown = ThreadUtil.delay(0, this::tick);
            return;
        }
        showCountdownButtons();
        countdown = ThreadUtil.delay(1000, this::tick);
    }

    private void showCountdownButtons() {
        final Localizer loc = Localizer.getInstance();
        inp.getController().getGui().updateButtons(inp.getOwner(), loc.getMessage("lblOK") + " (" + secondsLeft + ")",
                loc.getMessage("lblHold"), true, true, true);
    }

    /** One second of the countdown has passed; at zero the team mate's item is OK'd. */
    private void tick() {
        synchronized (this) {
            if (countdown == null || inp.isFinished() || held) {
                return;
            }
            secondsLeft--;
            if (secondsLeft > 0) {
                showCountdownButtons();
                countdown = ThreadUtil.delay(1000, this::tick);
                return;
            }
            countdown = null;
        }
        if (item() != null) {
            inp.passPriority();
        }
    }

    synchronized void cancel() {
        if (countdown != null) {
            countdown.cancel(false);
            countdown = null;
        }
        counted = null;
    }

    /** The player starts to act (picks a card or an ability): the countdown must not pass for them. */
    void hold() {
        held = true;
        cancel();
    }

    /** "Always OK my partner" was switched on while a team mate's item waits: OK it now. */
    void onAlwaysOkChanged() {
        final SpellAbilityStackInstance item = item();
        if (!inp.isFinished() && !held && alwaysOk() && item != null) {
            start(0, item);
        }
    }
}
