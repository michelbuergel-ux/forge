package forge.screens.home.hexcrawl;

import java.awt.Font;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexSession;
import forge.toolbox.FLabel;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin;
import forge.toolbox.FTextArea;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * Who is in the party and who is connected right now, for every player (a guest too): the host, the friends,
 * the companions of the AI, and the line about the connection. Kept up to date while it is open.
 */
final class HexPartyDialog {
    private final Localizer loc = Localizer.getInstance();
    private final HexSession session;
    private final Supplier<String> connection;
    private final FTextArea txtParty = new FTextArea();
    private final FTextArea txtConnection = new FTextArea();

    /** @param connection0 the connection line of the side panel (null when there is none) */
    HexPartyDialog(final HexSession session0, final Supplier<String> connection0) {
        session = session0;
        connection = connection0;
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    void show() {
        final JPanel panel = new JPanel(new MigLayout("insets 0, gap 6, wrap 1", "[520!, fill]"));
        panel.setOpaque(false);
        txtParty.setFont(FSkin.getFont(14).getBaseFont());
        txtConnection.setFont(FSkin.getFont(13).getBaseFont());
        panel.add(new FLabel.Builder().text(t("lblHexPartyTitle")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 22!");
        panel.add(new FScrollPane(txtParty, true), "h 110!");
        panel.add(new FLabel.Builder().text(t("lblHexPartyConnection")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 22!, gaptop 6");
        panel.add(txtConnection, "h 44!");
        final Timer timer = new Timer(1000, e -> fill());
        fill();
        timer.start();
        final FOptionPane pane = new FOptionPane(null, t("lblHexPartyWindow"), null, panel, Collections.singletonList(t("lblHexClose")), 0);
        try {
            pane.setVisible(true);
        } finally {
            timer.stop();
            pane.dispose();
        }
    }

    private void fill() {
        txtParty.setText(partyText(session));
        final String line = connection.get();
        txtConnection.setText(line == null || line.trim().isEmpty() ? t("lblHexPartyAlone") : line);
    }

    /** One line per head: its name and who plays it, as this computer sees it. */
    String partyText(final HexSession s) {
        final HexCampaign c = s.getCampaign();
        if (c == null) {
            return "";
        }
        final Set<String> present = s.getPresent();
        final Set<String> otherBuild = s.guestsWithOtherBuild();
        final StringBuilder sb = new StringBuilder();
        for (final HexPlayer p : c.players) {
            sb.append(p.name).append(" – ");
            final boolean mine = p.name.equals(s.getMyHead());
            if (p.name.equals(s.getHostHead())) {
                sb.append(t(mine ? "lblHexPartyHost" : "lblHexPartyTheHost"));
            } else if (p.ai) {
                sb.append(t("lblHexCompanion"));
            } else if (mine) {
                sb.append(t("lblHexYou"));
            } else if (otherBuild.contains(p.name)) {
                sb.append(t("lblHexPartyConnected")).append(" – ").append(t("lblHexOtherBuild"));
            } else if (present.contains(p.name)) {
                sb.append(t("lblHexPartyConnected"));
            } else {
                sb.append(t("lblHexAway"));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }
}
