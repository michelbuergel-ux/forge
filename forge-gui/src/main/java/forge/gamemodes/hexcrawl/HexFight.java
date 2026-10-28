package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import forge.game.Game;
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
    private final Set<String> fleeVotes = new LinkedHashSet<>();

    HexFight(final HexCampaign campaign, final HexEncounter enc) {
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
