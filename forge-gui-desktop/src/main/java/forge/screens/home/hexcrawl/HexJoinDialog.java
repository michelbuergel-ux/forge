package forge.screens.home.hexcrawl;

import java.util.Arrays;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import forge.gamemodes.hexcrawl.HexInvite;
import forge.gui.FThreads;
import forge.toolbox.FButton;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin;
import forge.toolbox.FTextArea;
import forge.toolbox.FTextField;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * A friend joins a hosted campaign with the invitation code (or a plain address): all
 * addresses in the code are tried at once, the first one that answers is used. If none does,
 * the dialog says what went wrong with each of them and what usually helps.
 */
final class HexJoinDialog {
    private static final int TIMEOUT_MS = 4000;

    private final Localizer loc = Localizer.getInstance();
    private final FTextField txtCode = new FTextField.Builder().ghostText("HEX-...").build();
    private final FTextArea txtResult = new FTextArea();
    private final FButton btnJoin = new FButton();
    private FOptionPane pane;

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    void show() {
        final JPanel panel = new JPanel(new MigLayout("insets 0, gap 6, wrap 2", "[540!, fill][140!, fill]"));
        panel.setOpaque(false);
        final FTextArea intro = new FTextArea(t("lblHexJoinIntro"));
        intro.setFont(FSkin.getFont(13).getBaseFont());
        panel.add(intro, "span 2, h 40!");
        panel.add(txtCode, "h 28!");
        btnJoin.setText(t("lblHexJoin"));
        btnJoin.addActionListener(e -> tryJoin());
        txtCode.addActionListener(e -> tryJoin());
        panel.add(btnJoin, "h 28!");
        txtResult.setFont(FSkin.getFont(12).getBaseFont());
        panel.add(new FScrollPane(txtResult, true), "span 2, h 220!");
        pane = new FOptionPane(null, t("lblHexJoinTitle"), null, panel, Arrays.asList(t("lblHexClose")), 0);
        try {
            pane.setVisible(true);
        } finally {
            pane.dispose();
        }
    }

    private void tryJoin() {
        final List<HexInvite.Address> addresses = HexInvite.decode(txtCode.getText());
        if (addresses.isEmpty()) {
            txtResult.setText(t("lblHexJoinNoCode"));
            return;
        }
        btnJoin.setEnabled(false);
        txtResult.setText(t("lblHexJoinTrying", String.valueOf(addresses.size())));
        FThreads.invokeInBackgroundThread(() -> {
            final List<HexInvite.ProbeResult> results = HexInvite.probeAll(addresses, HexInviteDialog.port(), TIMEOUT_MS);
            final HexInvite.Address found = HexInvite.firstReachable(results);
            SwingUtilities.invokeLater(() -> {
                btnJoin.setEnabled(true);
                if (found != null) {
                    txtResult.setText(t("lblHexJoinFound", found.url));
                    pane.setVisible(false);
                    forge.screens.home.online.CSubmenuOnlineLobby.SINGLETON_INSTANCE.joinFromCampaign(found.url);
                    return;
                }
                final StringBuilder sb = new StringBuilder(t("lblHexJoinFailed")).append("\n\n");
                boolean refused = false;
                for (final HexInvite.ProbeResult r : results) {
                    sb.append(t("lblHexAddr" + r.address.kind)).append(' ').append(r.address.url).append(": ")
                            .append(t("lblHexProbe" + r.probe.name())).append('\n');
                    refused |= r.probe == HexInvite.Probe.REFUSED;
                }
                sb.append('\n').append(t(refused ? "lblHexJoinAdviceRefused" : "lblHexJoinAdvice"));
                txtResult.setText(sb.toString());
                txtResult.setCaretPosition(0);
            });
        });
    }
}
