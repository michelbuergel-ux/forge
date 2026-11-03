package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.player.Player;
import forge.gamemodes.match.TeamSignal;
import forge.gui.interfaces.IGuiGame;
import forge.player.PlayerControllerHuman;
import forge.util.Localizer;

/**
 * A running fight of the campaign on the host: its statistics and the party's vote to flee.
 * Fleeing needs every human head of the party (AI heads agree by themselves) and is possible in
 * the first turns only; then the party concedes together, and the campaign treats it as fled.
 */
public final class HexFight {
    /** Fleeing is possible up to this turn. */
    public static final int FLEE_MAX_TURN = 3;
    /** What every head pays for fleeing (before the campaign's gold multiplier). */
    public static final int FLEE_GOLD = 15;

    private static volatile HexFight current;

    private final HexFightStats stats;
    private final String enemyName;
    private volatile Game game;
    private volatile boolean fled;
    /** The host broke the fight off: it ends without a result (see {@link #abort}). */
    private volatile boolean aborted;
    /** The fight is over, with a result or without one: told once. */
    private final AtomicBoolean over = new AtomicBoolean();
    /** Told when the fight ended without a result (see {@link #abort}). */
    private final Runnable onAborted;
    private final Set<String> fleeVotes = new LinkedHashSet<>();
    /** How long {@link #abort} waits for the game loop to end the game before it ends the fight itself. */
    static final long ABORT_WAIT_MS = 5000;

    HexFight(final HexCampaign campaign, final HexEncounter enc, final Runnable onAborted0) {
        onAborted = onAborted0 == null ? () -> { } : onAborted0;
        final List<String> party = new ArrayList<>();
        for (final HexPlayer p : campaign.players) {
            party.add(p.name);
        }
        stats = new HexFightStats(party);
        enemyName = enc.enemyName;
    }

    /** The fight running on this computer (host or single player), or null. */
    public static HexFight current() {
        return current;
    }

    /** The match has started: count what happens, and take flee votes. */
    void begin(final Game game0) {
        game = game0;
        if (game0 != null) {
            stats.setGame(game0);
            game0.subscribeToEvents(stats);
        }
        current = this;
    }

    /** The fight is over: what happened in it. */
    HexFightSummary end(final HexBattle.Result result) {
        over.set(true);
        if (current == this) {
            current = null;
        }
        final Game g = game;
        final int turns = g == null ? 0 : g.getPhaseHandler().getTurn();
        stats.countTurn(); // the last turn's draws and life gain
        final HexFightSummary summary = stats.summary(enemyName, result.name(), turns);
        if (g != null) {
            for (final forge.game.player.Player p : g.getRegisteredPlayers()) {
                if (p.getTeam() == HexBattle.PARTY_TEAM) {
                    summary.partyLife = Math.max(summary.partyLife, p.getLife()); // the team shares one life
                }
            }
        }
        return summary;
    }

    boolean hasFled() {
        return fled;
    }

    boolean isAborted() {
        return aborted;
    }

    /** The fight ends without a result: told once (the game loop and {@link #abort} may both try). */
    void endWithoutResult() {
        if (!over.compareAndSet(false, true)) {
            return;
        }
        if (current == this) {
            current = null;
        }
        forge.gamemodes.net.server.FServerManager.getInstance().setAiStandInWhileDisconnected(false);
        onAborted.run();
    }

    /** Is the game loop of this computer still running (it is gone if an error ended it)? */
    static boolean gameLoopRunning() {
        for (final StackTraceElement[] stack : Thread.getAllStackTraces().values()) {
            for (final StackTraceElement frame : stack) {
                if ("forge.game.GameAction".equals(frame.getClassName()) && "startGame".equals(frame.getMethodName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The human controllers of the fight (the host's, every guest's, and players the AI stands in for). */
    private List<PlayerControllerHuman> humanControllers() {
        final List<PlayerControllerHuman> result = new ArrayList<>();
        final Game g = game;
        if (g != null) {
            for (final Player p : g.getRegisteredPlayers()) {
                if (p.getController() instanceof PlayerControllerHuman pch) {
                    result.add(pch);
                }
            }
        }
        return result;
    }

    /**
     * Host: the fight hangs. Every prompt open right now (anybody's, the host's included) is closed as if
     * passed, and the guests are shown their prompt again; the game goes on with its next step. Not on the
     * screen thread. Returns false if the game loop does not run any more: then only {@link #abort} helps.
     */
    public boolean forceNextStep() {
        final Game g = game;
        if (g == null || g.isGameOver()) {
            return true;
        }
        if (!gameLoopRunning()) {
            return false;
        }
        for (final PlayerControllerHuman pch : humanControllers()) {
            pch.getInputQueue().clearInputs(); // shows every screen its next prompt, too
        }
        return true;
    }

    /**
     * Host: breaks the fight off without a result. The game ends as a draw for everybody; then, instead
     * of the result, the campaign is told that the fight did not take place: nobody loses anything and the
     * enemy stays, to be fought again. If the game loop does not end the game (an error ended it before),
     * the fight is ended here after a few seconds. Not on the screen thread.
     */
    public void abort() {
        aborted = true;
        final Game g = game;
        final boolean ending = g != null && !g.isGameOver();
        if (ending) {
            // as an accepted draw ends a game: otherwise everybody still in it counts as a winner
            for (final Player p : g.getPlayers()) {
                p.intentionalDraw();
            }
            g.setGameOver(GameEndReason.Draw);
        }
        for (final PlayerControllerHuman pch : humanControllers()) {
            pch.getInputQueue().onGameOver(true); // the game loop waits for nobody any more
        }
        final long until = gameLoopRunning() ? System.currentTimeMillis() + ABORT_WAIT_MS : 0;
        while (!over.get() && System.currentTimeMillis() < until) {
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!over.get()) {
            // no game loop told the screens: they show the end of the game now
            if (ending) {
                g.fireEvent(new forge.game.event.GameEventGameFinished());
            }
            endWithoutResult();
        }
    }

    /** May the party still flee (the first turns, game not over)? */
    public boolean mayFlee() {
        final Game g = game;
        return g != null && !g.isGameOver() && !fled && g.getPhaseHandler().getTurn() <= FLEE_MAX_TURN;
    }

    /** The human heads of the party (AI heads, companions and stand-ins agree by themselves). */
    private List<Player> humanHeads() {
        final List<Player> result = new ArrayList<>();
        final Game g = game;
        if (g == null) {
            return result;
        }
        for (final Player p : g.getPlayers()) {
            if (p.getTeam() == HexBattle.PARTY_TEAM && p.getController() instanceof PlayerControllerHuman) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * A head votes to flee (or to fight on, which ends the vote). Once all human heads agree, the
     * party flees: every head concedes and the campaign sends it one field back for a little gold.
     */
    public void vote(final Player voter, final boolean flee) {
        final Localizer loc = Localizer.getInstance();
        final List<Player> humans;
        final String detail;
        boolean go = false;
        synchronized (this) {
            if (voter == null || voter.getTeam() != HexBattle.PARTY_TEAM) {
                return;
            }
            if (!mayFlee()) {
                tell(voter, voter, loc.getMessage("lblFleeTooLate", String.valueOf(FLEE_MAX_TURN)));
                return;
            }
            humans = humanHeads();
            if (!flee) {
                if (fleeVotes.isEmpty()) {
                    return;
                }
                fleeVotes.clear();
                detail = loc.getMessage("lblFleeDeclined", voter.getName());
            } else {
                fleeVotes.add(voter.getName());
                int yes = 0;
                for (final Player p : humans) {
                    if (fleeVotes.contains(p.getName())) {
                        yes++;
                    }
                }
                go = yes >= humans.size();
                detail = go ? loc.getMessage("lblFleeAgreed") : loc.getMessage("lblFleeAsk", voter.getName(), String.valueOf(yes), String.valueOf(humans.size()));
                if (go) {
                    fled = true;
                }
            }
        }
        for (final Player p : humans) {
            tell(p, voter, detail);
        }
        if (go) {
            flee();
        }
    }

    private static void tell(final Player to, final Player from, final String detail) {
        if (to.getController() instanceof PlayerControllerHuman pch) {
            final IGuiGame gui = pch.getGui();
            if (gui != null) {
                gui.showTeamSignal(from.getView(), TeamSignal.FLEE_VOTE, detail);
            }
        }
    }

    /** Every head of the party concedes; the game ends at once (as with an accepted draw). */
    private void flee() {
        final Game g = game;
        if (g == null || g.isGameOver()) {
            return;
        }
        for (final Player p : new ArrayList<>(g.getPlayers())) {
            if (p.getTeam() == HexBattle.PARTY_TEAM) {
                p.concede();
            }
        }
        g.getAction().checkGameOverCondition();
        if (g.isGameOver()) {
            for (final Player p : g.getRegisteredPlayers()) {
                if (p.getController() instanceof PlayerControllerHuman pch) {
                    pch.getInputQueue().onGameOver(true);
                }
            }
        }
    }
}
