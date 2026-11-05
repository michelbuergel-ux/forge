package forge.gamemodes.hexcrawl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import forge.game.Game;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.player.PlayerControllerHuman;

/**
 * The record of a fight that hangs, for finding out why later: the state of the game (whose priority, what is on the
 * stack, which prompts are open) and the stack of every thread with the locks it holds and waits for. Written when
 * the host forces the fight on or breaks it off, into a file of its own: Forge's log stays locked while it runs.
 */
public final class HexFightDump {
    /** The dumps kept: the oldest go once there are more. */
    static final int KEEP = 20;

    private HexFightDump() { }

    /** The folder of the dumps (in the campaigns' folder; the tests give one of their own: forge.hex.dumpDir). */
    public static File dir() {
        final String own = System.getProperty("forge.hex.dumpDir");
        return own != null ? new File(own) : new File(HexCampaignIO.dir(), "diagnostics");
    }

    /** Writes a dump into {@link #dir()}; returns the file, or null if it could not be written (never throws). */
    public static File write(final String reason, final Game game, final String enemyName) {
        return write(dir(), reason, game, enemyName);
    }

    static File write(final File dir, final String reason, final Game game, final String enemyName) {
        try {
            dir.mkdirs();
            final String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            final File file = new File(dir, "stuck-fight-" + stamp + "-" + reason + ".txt");
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                out.println("Hex Campaign: stuck fight (" + reason + ") at " + new Date());
                out.println("Enemy: " + enemyName + "; thread writing this: " + Thread.currentThread().getName());
                out.println();
                game(out, game);
                out.println();
                threads(out);
            }
            prune(dir);
            System.err.println("Hex Campaign: wrote the record of a stuck fight to " + file);
            return file;
        } catch (final Throwable t) {
            System.err.println("Hex Campaign: could not write the record of a stuck fight: " + t);
            return null;
        }
    }

    /** The game's state; every part on its own, so that one failing does not lose the rest. */
    private static void game(final PrintWriter out, final Game game) {
        out.println("=== Game");
        if (game == null) {
            out.println("(no game)");
            return;
        }
        try {
            final PhaseHandler ph = game.getPhaseHandler();
            out.println("Turn " + ph.getTurn() + ", " + ph.getPhase() + ", turn of " + ph.getPlayerTurn()
                    + ", priority: " + ph.getPriorityPlayer() + ", game over: " + game.isGameOver());
            out.println("Team decision open: " + (ph.getTeamReady() != null));
        } catch (final Throwable t) {
            out.println("(phase: " + t + ")");
        }
        try {
            out.println("Stack (" + game.getStack().size() + "):");
            for (final SpellAbilityStackInstance si : game.getStack()) {
                out.println("  " + si);
            }
        } catch (final Throwable t) {
            out.println("(stack: " + t + ")");
        }
        try {
            for (final Player p : game.getRegisteredPlayers()) {
                final StringBuilder sb = new StringBuilder("Player ").append(p.getName())
                        .append(" (team ").append(p.getTeam()).append(")")
                        .append(p.hasLost() ? " lost" : "").append(", life ").append(p.getLife())
                        .append(", controller ").append(p.getController().getClass().getSimpleName())
                        .append(", waited for: ").append(p.getView().isWaitedFor());
                if (p.getController() instanceof PlayerControllerHuman pch) {
                    sb.append(", screen ").append(pch.getGui() == null ? "-" : pch.getGui().getClass().getSimpleName());
                    sb.append(", open input: ").append(pch.getInputQueue().getInput());
                }
                out.println(sb);
            }
        } catch (final Throwable t) {
            out.println("(players: " + t + ")");
        }
    }

    /** Every thread: its state, what it waits for and who holds that, its whole stack and the locks it holds. */
    private static void threads(final PrintWriter out) {
        final ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        final long[] deadlocked = mx.findDeadlockedThreads();
        out.println("=== Deadlocked threads: " + (deadlocked == null ? "none" : Arrays.toString(deadlocked)));
        out.println();
        final ThreadInfo[] infos = mx.dumpAllThreads(mx.isObjectMonitorUsageSupported(), mx.isSynchronizerUsageSupported());
        Arrays.sort(infos, Comparator.comparing(ThreadInfo::getThreadName));
        for (final ThreadInfo info : infos) {
            out.println("\"" + info.getThreadName() + "\" id=" + info.getThreadId() + " " + info.getThreadState()
                    + (info.getLockName() == null ? "" : " on " + info.getLockName())
                    + (info.getLockOwnerName() == null ? "" : " held by \"" + info.getLockOwnerName() + "\" id=" + info.getLockOwnerId()));
            final StackTraceElement[] frames = info.getStackTrace();
            final MonitorInfo[] monitors = info.getLockedMonitors();
            for (int i = 0; i < frames.length; i++) {
                out.println("    at " + frames[i]);
                for (final MonitorInfo m : monitors) {
                    if (m.getLockedStackDepth() == i) {
                        out.println("      - holds " + m);
                    }
                }
            }
            for (final LockInfo l : info.getLockedSynchronizers()) {
                out.println("    - holds " + l);
            }
            out.println();
        }
    }

    /** Keeps the newest {@link #KEEP} dumps. */
    private static void prune(final File dir) {
        final File[] dumps = dir.listFiles((d, n) -> n.startsWith("stuck-fight-") && n.endsWith(".txt"));
        if (dumps == null || dumps.length <= KEEP) {
            return;
        }
        Arrays.sort(dumps, Comparator.comparing(File::getName)); // the time is in the name: oldest first
        for (int i = 0; i < dumps.length - KEEP; i++) {
            dumps[i].delete();
        }
    }
}
