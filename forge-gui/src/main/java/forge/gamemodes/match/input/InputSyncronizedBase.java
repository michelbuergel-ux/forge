package forge.gamemodes.match.input;

import forge.util.IHasForgeLog;
import forge.util.Localizer;
import forge.game.phase.TeamReady;
import forge.game.player.Player;
import forge.gamemodes.match.Selection;
import forge.gui.FThreads;
import forge.gui.error.BugReporter;
import forge.player.PlayerControllerHuman;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public abstract class InputSyncronizedBase extends InputBase implements InputSynchronized, IHasForgeLog {

    private static final long serialVersionUID = 8756177361251703052L;
    private final CountDownLatch cdlDone;

    public InputSyncronizedBase(final PlayerControllerHuman controller) {
        super(controller);
        cdlDone = new CountDownLatch(1);
    }

    @Override
    public void awaitLatchRelease() {
        FThreads.assertExecutedByEdt(false);
        netLog.trace("awaitLatchRelease() starting on {}, thread = {}", this.getClass().getSimpleName(), Thread.currentThread().getName());
        try {
            getController().getGui().awaitInput(cdlDone);
        } catch (final InterruptedException e) {
            BugReporter.reportException(e);
        }
        netLog.trace("awaitLatchRelease() UNBLOCKED on {}, thread = {}", this.getClass().getSimpleName(), Thread.currentThread().getName());
    }

    @Override
    public final void relaseLatchWhenGameIsOver() {
        cdlDone.countDown();
    }

    public void showAndWait() {
        watchTeam();
        // the game waits for this player while the prompt is open
        getController().noteInputOpen(true);
        try {
            getController().getInputQueue().setInput(this);
            awaitLatchRelease();
        } finally {
            getController().noteInputOpen(false);
        }
    }

    @Override
    public final void stop() {
        onStop();
        getController().getGui().setSelection(Selection.ofChosen(getController().getKeptMarks()));

        // ensure input won't accept any user actions.
        FThreads.invokeInEdtNowOrLater(this::setFinished);

        // thread irrelevant
        if (getController().getInputQueue().getInput() != null) {
            getController().getInputQueue().removeInput(InputSyncronizedBase.this);
        }
        cdlDone.countDown();
    }

    protected void onStop() { }

    // ------------------------------------------------------------------ team mates asked at the same time

    /** Said OK and waits for the team mates asked at the same time (shared team turns). */
    private volatile boolean waitingForMates;

    protected final boolean isWaitingForMates() {
        return waitingForMates;
    }

    private TeamReady teamReady() {
        return getController().getGame() == null ? null : getController().getGame().getPhaseHandler().getTeamReady();
    }

    /**
     * OK: with team mates asked at the same time the prompt waits (OK off, Cancel takes it back) until all of
     * them said OK, then finishes; otherwise, or as the last one, it finishes at once.
     */
    protected final void okWithMates(final Runnable finish) {
        final TeamReady team = teamReady();
        if (team == null) {
            finish.run();
            return;
        }
        waitingForMates = true; // before: the last mate's OK may finish this prompt right away
        if (!team.ready(getController().getPlayer(), finish)) {
            waitingForMates = false;
            return;
        }
        getController().noteOkSaid(true);
        showWaitingForMates();
    }

    /** A team mate's OK (or taking it back) shows on this prompt at once. */
    protected final void watchTeam() {
        final TeamReady team = teamReady();
        if (team != null) {
            team.watch(getController().getPlayer(), this::reshowMessage);
        }
    }

    /** A new round of the team's decision (the game changed): an OK said before no longer counts. */
    protected final void forgetOk() {
        if (waitingForMates) {
            waitingForMates = false;
            getController().noteOkSaid(false);
        }
    }

    /** A line on top of the prompt: the team mates asked at the same time that said OK already. */
    @Override
    protected String decorateMessage(final String message) {
        final TeamReady team = teamReady();
        if (team == null || waitingForMates) {
            return message;
        }
        final List<String> names = new ArrayList<>();
        for (final Player p : team.readyMates(getController().getPlayer())) {
            names.add(p.getName());
        }
        if (names.isEmpty()) {
            return message;
        }
        final String line = Localizer.getInstance().getMessage("lblTeamMatesSaidOk", String.join(", ", names));
        return "<table width=\"100%\" cellpadding=\"4\" bgcolor=\"#2b5a8a\"><tr><td><font color=\"#ffffff\"><b>\u2714 "
                + line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</b></font></td></tr></table>" + message;
    }

    /** The waiting prompt: who is still deciding, OK off, Cancel takes the OK back. */
    protected final void showWaitingForMates() {
        final TeamReady team = teamReady();
        if (team == null || isFinished()) {
            return;
        }
        final List<String> names = new ArrayList<>();
        for (final Player p : team.waitingFor(getController().getPlayer())) {
            names.add(p.getName());
        }
        final Localizer loc = Localizer.getInstance();
        showMessage(loc.getMessage("lblTeamWaitingForMates", String.join(", ", names)));
        getController().getGui().updateButtons(getOwner(), loc.getMessage("lblOK"), loc.getMessage("lblTakeBack"), false, true, false);
    }

    /** Takes the OK back, so the prompt is open again; false if it is too late (all of them said OK). */
    protected final boolean takeBackOk() {
        final TeamReady team = teamReady();
        if (!waitingForMates || team == null || !team.takeBack(getController().getPlayer())) {
            return false;
        }
        waitingForMates = false;
        getController().noteOkSaid(false);
        return true;
    }
}
