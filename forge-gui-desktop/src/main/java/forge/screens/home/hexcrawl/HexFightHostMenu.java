package forge.screens.home.hexcrawl;

import java.util.Arrays;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import forge.gamemodes.hexcrawl.HexFight;
import forge.gamemodes.net.event.MessageEvent;
import forge.gamemodes.net.server.FServerManager;
import forge.gamemodes.net.server.HostingServer;
import forge.toolbox.FOptionPane;
import forge.toolbox.FSkin.SkinnedMenuItem;
import forge.util.Localizer;
import forge.util.ThreadUtil;

/**
 * The host's way out of a campaign fight that hangs, in the match's Game menu: the next step forced (every open
 * prompt passed), or the fight broken off without a result. Only on the computer the fight runs on (the host's,
 * or a single player's); the guests don't see these items.
 */
public final class HexFightHostMenu {
    private HexFightHostMenu() { }

    private static String t(final String key, final Object... args) {
        return Localizer.getInstance().getMessage(key, args);
    }

    /** The items for the Game menu; {@link #refresh} shows them while a campaign fight runs on this computer. */
    public static List<JMenuItem> items() {
        final SkinnedMenuItem force = new SkinnedMenuItem(t("lblHexForceStep"));
        force.setToolTipText(t("lblHexForceStepHint"));
        force.addActionListener(e -> forceNextStep());
        final SkinnedMenuItem abort = new SkinnedMenuItem(t("lblHexAbortFight") + "...");
        abort.setToolTipText(t("lblHexAbortFightHint"));
        abort.addActionListener(e -> abortFight());
        final List<JMenuItem> items = Arrays.asList(force, abort);
        refresh(items);
        return items;
    }

    /** Shown only while a campaign fight runs on this computer. */
    public static void refresh(final List<JMenuItem> items) {
        final boolean here = HexFight.current() != null;
        for (final JMenuItem item : items) {
            item.setVisible(here);
        }
    }

    /** A line in everybody's chat (when hosting). */
    static void tellEverybody(final String message) {
        if (HostingServer.isHosting()) {
            FServerManager.getInstance().broadcast(MessageEvent.warning(message));
        }
    }

    private static void forceNextStep() {
        final HexFight fight = HexFight.current();
        if (fight == null || !FOptionPane.showConfirmDialog(t("lblHexForceStepAsk"), t("lblHexForceStep"),
                t("lblHexForceStepYes"), t("lblHexBack"))) {
            return;
        }
        ThreadUtil.invokeInGameThread(() -> {
            if (fight.forceNextStep()) {
                tellEverybody(t("lblHexForceStepChat"));
                return;
            }
            // the game loop is gone (an error ended it): nothing goes on any more, only breaking off helps
            SwingUtilities.invokeLater(() -> {
                if (FOptionPane.showConfirmDialog(t("lblHexForceStepDead"), t("lblHexForceStep"),
                        t("lblHexAbortFightYes"), t("lblHexBack"))) {
                    abortNow(fight);
                }
            });
        });
    }

    private static void abortFight() {
        final HexFight fight = HexFight.current();
        if (fight != null && FOptionPane.showConfirmDialog(t("lblHexAbortFightAsk"), t("lblHexAbortFight"),
                t("lblHexAbortFightYes"), t("lblHexBack"))) {
            abortNow(fight);
        }
    }

    private static void abortNow(final HexFight fight) {
        tellEverybody(t("lblHexFightAbortedChat"));
        ThreadUtil.invokeInGameThread(fight::abort);
    }
}
