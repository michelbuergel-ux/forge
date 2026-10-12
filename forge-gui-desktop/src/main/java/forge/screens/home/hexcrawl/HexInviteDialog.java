package forge.screens.home.hexcrawl;

import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexInvite;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.net.server.FServerManager;
import forge.gamemodes.net.server.HostingServer;
import forge.gui.FThreads;
import forge.localinstance.properties.ForgeNetPreferences;
import forge.model.FModel;
import forge.toolbox.FButton;
import forge.toolbox.FLabel;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin;
import forge.toolbox.FTextArea;
import forge.toolbox.FTextField;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * The host's view of its table: the invitation code to send to friends, whether the router
 * let the port through, under which addresses the computer can be reached, and who of the
 * party is connected right now.
 */
final class HexInviteDialog {
    /** The internet address, looked up once per program run (it needs a request to the outside). */
    private static volatile String externalAddress;
    private static volatile boolean externalLookedUp;

    private final Localizer loc = Localizer.getInstance();
    private final HexSession session;
    private final FTextField txtCode = new FTextField.Builder().readonly().build();
    private final FTextArea txtStatus = new FTextArea();
    private final FTextArea txtParty = new FTextArea();
    private final FTextArea txtHelp = new FTextArea();
    private String code;

    HexInviteDialog(final HexSession session0) {
        session = session0;
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    static int port() {
        return FModel.getNetPreferences().getPrefInt(ForgeNetPreferences.FNetPref.NET_PORT);
    }

    /** Looks the internet address up in the background, once; the dialogs show it when it is there. */
    static void lookUpExternalAddress() {
        if (externalLookedUp) {
            return;
        }
        externalLookedUp = true;
        FThreads.invokeInBackgroundThread(() -> externalAddress = FServerManager.getExternalAddress());
    }

    /** The invitation code for this computer as it is now. */
    static String currentCode() {
        return HexInvite.encode(HexInvite.collectAddresses(externalAddress, port()));
    }

    void show() {
        lookUpExternalAddress();
        final JPanel panel = new JPanel(new MigLayout("insets 0, gap 6, wrap 2", "[560!, fill][120!, fill]"));
        panel.setOpaque(false);
        final FTextArea steps = new FTextArea(t("lblHexInviteSteps"));
        steps.setFont(FSkin.getFont(13).getBaseFont());
        panel.add(steps, "span 2, h 40!");
        panel.add(new FLabel.Builder().text(t("lblHexInviteCode")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "span 2, h 22!");
        panel.add(txtCode, "h 28!");
        final FButton btnCopy = new FButton(t("lblHexCopy"));
        btnCopy.addActionListener(e -> {
            if (code != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
                btnCopy.setText(t("lblHexCopied"));
            }
        });
        panel.add(btnCopy, "h 28!");
        txtStatus.setFont(FSkin.getFont(12).getBaseFont());
        txtParty.setFont(FSkin.getFont(13).getBaseFont());
        txtHelp.setFont(FSkin.getFont(12).getBaseFont());
        panel.add(new FLabel.Builder().text(t("lblHexInviteParty")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "span 2, h 22!");
        panel.add(new FScrollPane(txtParty, true), "span 2, h 96!");
        panel.add(new FLabel.Builder().text(t("lblHexInviteConnection")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "span 2, h 22!");
        panel.add(new FScrollPane(txtStatus, true), "span 2, h 130!");
        panel.add(new FScrollPane(txtHelp, true), "span 2, h 92!");

        final Timer timer = new Timer(1000, e -> fill());
        fill();
        timer.start();
        final FOptionPane pane = new FOptionPane(null, t("lblHexInviteTitle"), null, panel, Collections.singletonList(t("lblHexClose")), 0);
        try {
            pane.setVisible(true);
        } finally {
            timer.stop();
            pane.dispose();
        }
    }

    private void fill() {
        final int port = port();
        final List<HexInvite.Address> addresses = HexInvite.collectAddresses(externalAddress, port);
        final String newCode = HexInvite.encode(addresses);
        if (!newCode.equals(code)) {
            code = newCode;
            txtCode.setText(code);
        }

        final StringBuilder status = new StringBuilder();
        status.append(HostingServer.isHosting() ? t("lblHexServerRunning", String.valueOf(port)) : t("lblHexServerStarting")).append('\n');
        final Boolean upnp = FServerManager.getUpnpResult();
        if (!FServerManager.wasUpnpTried()) {
            status.append(t("lblHexUpnpOff")).append('\n');
        } else if (upnp == null) {
            status.append(t("lblHexUpnpPending")).append('\n');
        } else {
            status.append(t(upnp ? "lblHexUpnpOk" : "lblHexUpnpFailed", String.valueOf(port))).append('\n');
        }
        boolean hasVpn = false;
        for (final HexInvite.Address a : addresses) {
            status.append("  ").append(t("lblHexAddr" + a.kind)).append(": ").append(a.url).append('\n');
            hasVpn |= "VPN".equals(a.kind);
        }
        if (externalAddress == null) {
            status.append("  ").append(t("lblHexAddrWanUnknown")).append('\n');
        }
        txtStatus.setText(status.toString().trim());

        final StringBuilder party = new StringBuilder();
        final HexCampaign c = session.getCampaign();
        if (c != null) {
            for (final HexPlayer p : c.players) {
                party.append(p.name).append(" – ");
                if (p.name.equals(session.getHostHead())) {
                    party.append(t("lblHexPartyHost"));
                } else if (p.ai) {
                    party.append(t("lblHexCompanion"));
                } else if (session.getPresent().contains(p.name)) {
                    party.append(t("lblHexPartyConnected"));
                } else {
                    party.append(t("lblHexAway"));
                }
                party.append('\n');
            }
            if (c.players.size() < HexCampaign.MAX_HEADS) {
                party.append(t("lblHexPartyRoom", String.valueOf(HexCampaign.MAX_HEADS - c.players.size())));
            }
        }
        txtParty.setText(party.toString().trim());
        txtHelp.setText(t(hasVpn ? "lblHexInviteHelpVpn" : "lblHexInviteHelp", String.valueOf(port)));
    }
}
