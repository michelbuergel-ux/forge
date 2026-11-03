package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.BAD;
import static forge.screens.home.hexcrawl.HexTownStyle.FAINT;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_EDGE;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.MUTED;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT;
import static forge.screens.home.hexcrawl.HexTownStyle.alpha;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.decorative;
import static forge.screens.home.hexcrawl.HexTownStyle.label;
import static forge.screens.home.hexcrawl.HexTownStyle.smooth;
import static forge.screens.home.hexcrawl.HexTownStyle.weave;
import static forge.screens.home.hexcrawl.HexTownStyle.wrapped;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexCampaignIO;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexDifficulty;
import forge.gamemodes.hexcrawl.HexInvite;
import forge.gamemodes.net.server.HostingServer;
import forge.screens.home.hexcrawl.HexSidebarParts.Heading;
import forge.screens.home.hexcrawl.HexSidebarParts.Plate;
import forge.screens.home.hexcrawl.HexTownStyle.GoldButton;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import forge.screens.home.hexcrawl.HexTownStyle.Pill;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * The page of a new campaign, shown in place of the map: its name, how many companions the AI plays, how much gold
 * there is, and whether friends play along. With friends the invitation code is on the page at once (it needs only
 * this computer's addresses), so it can be sent while the rest is chosen; the server starts with the campaign.
 */
final class HexNewCampaignPage extends JPanel {
    /** What the page was filled in with. */
    record Settings(String name, int companions, int goldMultiplier, HexDifficulty difficulty, boolean host) { }

    private static final int PLATE_W = 640;
    private static final int TEXT_W = PLATE_W - 64;

    private final Localizer loc = Localizer.getInstance();
    private final Palette palette = HexTownStyle.palette(HexColor.COLORLESS);
    private final JTextField txtName = field(false);
    private final JPanel nameNote = holder();
    private final List<Pill> companionPills = new ArrayList<>();
    private final List<Pill> goldPills = new ArrayList<>();
    private final List<Pill> difficultyPills = new ArrayList<>();
    private final JPanel difficultyNote = holder();
    private final Pill pillAlone;
    private final Pill pillHost;
    private final JPanel hostingNote = holder();
    private final JPanel hostSection = transparent(new MigLayout("insets 0, gap 0, wrap 1, fillx, hidemode 3", "[grow, fill]"));
    private final JTextField txtCode = field(true);
    private final GoldButton btnCopy;
    private final JPanel roomNote = holder();
    private final JPanel addressBox = holder();
    private final JPanel helpBox = holder();
    private final GoldButton btnStart;
    private final Timer timer = new Timer(1000, e -> {
        if (isShowing()) {
            fillHosting();
        }
    });

    private int companions;
    private int goldChoice;
    private HexDifficulty difficulty = HexDifficulty.NORMAL;
    private boolean host;
    private String code;
    private Consumer<Settings> onStart;
    private Runnable onCancel;

    HexNewCampaignPage() {
        super(new BorderLayout());
        setOpaque(false);

        final Plate plate = new Plate(() -> palette, new MigLayout("insets 30 32 26 32, gap 0, wrap 1, fillx, hidemode 3", "[grow, fill]"));
        plate.add(label(t("lblHexNewCampaign"), decorative(26f), GOLD_LIGHT));
        plate.add(wrapped(t("lblHexSetupIntro"), body(Font.PLAIN, 13f), MUTED, TEXT_W), "gaptop 4");

        plate.add(new Heading(t("lblHexCampaignName")), "gaptop 20");
        plate.add(txtName, "h 36!, gaptop 6");
        plate.add(nameNote, "gaptop 4");
        txtName.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(final DocumentEvent e) { update(); }
            @Override public void removeUpdate(final DocumentEvent e) { update(); }
            @Override public void changedUpdate(final DocumentEvent e) { update(); }
        });
        txtName.addActionListener(e -> start());

        plate.add(new Heading(t("lblHexSetupCompanions")), "gaptop 18");
        final JPanel companionRow = row();
        for (int i = 0; i < HexCampaign.MAX_HEADS; i++) {
            final int n = i;
            final Pill pill = new Pill(i == 0 ? t("lblHexSetupNone") : String.valueOf(i));
            pill.addActionListener(e -> { companions = n; update(); });
            companionPills.add(pill);
            companionRow.add(pill, "h 34!, wmin 56");
        }
        plate.add(companionRow, "gaptop 8, growx 0");
        plate.add(wrapped(t("lblHexSetupCompanionsHint"), body(Font.PLAIN, 12f), FAINT, TEXT_W), "gaptop 6");

        plate.add(new Heading(t("lblHexDifficulty")), "gaptop 18");
        final JPanel difficultyRow = row();
        for (final HexDifficulty d : HexDifficulty.values()) {
            final Pill pill = new Pill(t(d.key()));
            pill.addActionListener(e -> { difficulty = d; update(); });
            difficultyPills.add(pill);
            difficultyRow.add(pill, "h 34!, wmin 56");
        }
        plate.add(difficultyRow, "gaptop 8, growx 0");
        plate.add(difficultyNote, "gaptop 6");

        plate.add(new Heading(t("lblHexSetupGold")), "gaptop 18");
        final JPanel goldRow = row();
        for (int i = 0; i < HexCampaign.GOLD_MULTIPLIERS.length; i++) {
            final int n = i;
            final int m = HexCampaign.GOLD_MULTIPLIERS[i];
            final Pill pill = new Pill(m == 1 ? t("lblHexGoldNormal") : m + "x");
            pill.addActionListener(e -> { goldChoice = n; update(); });
            goldPills.add(pill);
            goldRow.add(pill, "h 34!, wmin 56");
        }
        plate.add(goldRow, "gaptop 8, growx 0");
        plate.add(wrapped(t("lblHexSetupGoldHint"), body(Font.PLAIN, 12f), FAINT, TEXT_W), "gaptop 6");

        plate.add(new Heading(t("lblHexSetupFriends")), "gaptop 18");
        final JPanel friendsRow = row();
        pillAlone = new Pill(t("lblHexSetupAlone"));
        pillHost = new Pill(t("lblHexInvite"));
        pillAlone.addActionListener(e -> { host = false; update(); });
        pillHost.addActionListener(e -> { host = true; update(); });
        friendsRow.add(pillAlone, "h 34!");
        friendsRow.add(pillHost, "h 34!");
        plate.add(friendsRow, "gaptop 8, growx 0");
        plate.add(hostingNote, "gaptop 6");

        // friends: the code to send now, the room in the party, how this computer is reached
        hostSection.add(wrapped(t("lblHexSetupInviteSteps"), body(Font.PLAIN, 13f), TEXT, TEXT_W), "gaptop 12");
        final JPanel codeRow = transparent(new MigLayout("insets 0, gap 8", "[grow, fill][]"));
        txtCode.setToolTipText(t("lblHexInviteCode"));
        codeRow.add(txtCode, "h 36!");
        btnCopy = new GoldButton(t("lblHexCopy"), GoldButton.Kind.OUTLINE, 11f);
        btnCopy.addActionListener(e -> {
            if (code != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
                btnCopy.setText(t("lblHexCopied"));
            }
        });
        codeRow.add(btnCopy, "h 36!, w 110!");
        hostSection.add(label(t("lblHexInviteCode"), body(Font.BOLD, 12f), MUTED), "gaptop 10");
        hostSection.add(codeRow, "gaptop 4");
        hostSection.add(roomNote, "gaptop 8");
        hostSection.add(label(t("lblHexInviteConnection"), body(Font.BOLD, 12f), MUTED), "gaptop 10");
        hostSection.add(addressBox, "gaptop 4");
        hostSection.add(helpBox, "gaptop 6");
        plate.add(hostSection);

        final JPanel buttons = transparent(new MigLayout("insets 0, gap 10", "[grow][][]"));
        final GoldButton btnCancel = new GoldButton(t("lblCancel"), GoldButton.Kind.OUTLINE, 12f);
        btnCancel.addActionListener(e -> cancel());
        btnStart = new GoldButton(t("lblHexSetupStart"), GoldButton.Kind.GOLD, 13f);
        btnStart.addActionListener(e -> start());
        buttons.add(new JPanel() { { setOpaque(false); } });
        buttons.add(btnCancel, "h 42!, wmin 130");
        buttons.add(btnStart, "h 42!, wmin 200");
        plate.add(buttons, "gaptop 24");

        // centred on the page; it scrolls when the window is low
        final Centre centre = new Centre();
        centre.add(plate, "w " + PLATE_W + "!");
        final FScrollPane scroll = new FScrollPane(centre, false, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    void setOnStart(final Consumer<Settings> onStart0) {
        onStart = onStart0;
    }

    void setOnCancel(final Runnable onCancel0) {
        onCancel = onCancel0;
    }

    /** Fills the page anew: a free name, no companions, normal gold, and friends only if this computer hosts already. */
    void open(final String defaultName) {
        txtName.setText(defaultName);
        companions = 0;
        goldChoice = 0;
        difficulty = HexDifficulty.NORMAL;
        host = HostingServer.isHosting();
        code = null;
        btnCopy.setText(t("lblHexCopy"));
        HexInviteDialog.lookUpExternalAddress();
        update();
        timer.start();
        SwingUtilities.invokeLater(() -> {
            txtName.requestFocusInWindow();
            txtName.selectAll();
        });
    }

    /** The page is left: nothing runs on for it. */
    void close() {
        timer.stop();
    }

    private void start() {
        if (!btnStart.isEnabled() || onStart == null) {
            return;
        }
        close();
        onStart.accept(new Settings(txtName.getText().trim(), companions, HexCampaign.GOLD_MULTIPLIERS[goldChoice], difficulty, host));
    }

    private void cancel() {
        close();
        if (onCancel != null) {
            onCancel.run();
        }
    }

    /** The choices lit, the notes under them, and whether the campaign can start like this. */
    private void update() {
        for (int i = 0; i < companionPills.size(); i++) {
            companionPills.get(i).setActive(i == companions);
        }
        for (int i = 0; i < goldPills.size(); i++) {
            goldPills.get(i).setActive(i == goldChoice);
        }
        for (int i = 0; i < difficultyPills.size(); i++) {
            difficultyPills.get(i).setActive(HexDifficulty.values()[i] == difficulty);
        }
        setText(difficultyNote, describe(difficulty), body(Font.PLAIN, 12f), FAINT);
        final boolean hosting = HostingServer.isHosting();
        pillAlone.setActive(!host);
        pillHost.setActive(host);
        pillAlone.setEnabled(!hosting); // the server runs already: its guests play the new campaign too
        setText(hostingNote, hosting ? t("lblHexSetupHosting") : null, body(Font.PLAIN, 12f), FAINT);

        final String name = txtName.getText().trim();
        final boolean taken = HexCampaignIO.exists(name);
        setText(nameNote, taken ? t("lblHexSetupNameTaken", name) : null, body(Font.PLAIN, 12f), BAD);

        final int room = HexCampaign.MAX_HEADS - 1 - companions;
        hostSection.setVisible(host);
        if (host) {
            setText(roomNote, room > 0 ? t("lblHexPartyRoom", String.valueOf(room)) : t("lblHexSetupNoRoom"),
                    body(room > 0 ? Font.PLAIN : Font.BOLD, 12.5f), room > 0 ? TEXT : BAD);
            fillHosting();
        }
        btnStart.setEnabled(!name.isEmpty() && !taken && (!host || room > 0));
        revalidate();
        repaint();
    }

    /** The code and the addresses as they are now (the internet address comes in after a moment). */
    private void fillHosting() {
        if (!host) {
            return;
        }
        final List<HexInvite.Address> addresses = HexInviteDialog.addresses();
        final String newCode = HexInvite.encode(addresses);
        if (!newCode.equals(code)) {
            code = newCode;
            txtCode.setText(code);
            txtCode.setCaretPosition(0);
            btnCopy.setText(t("lblHexCopy"));
        }
        final StringBuilder lines = new StringBuilder();
        boolean hasVpn = false;
        for (final HexInvite.Address a : addresses) {
            lines.append(t("lblHexAddr" + a.kind)).append(": ").append(a.url).append('\n');
            hasVpn |= "VPN".equals(a.kind);
        }
        if (!HexInviteDialog.externalKnown()) {
            lines.append(t("lblHexAddrWanUnknown"));
        }
        setText(addressBox, lines.toString().trim(), body(Font.PLAIN, 12.5f), TEXT);
        setText(helpBox, t(hasVpn ? "lblHexInviteHelpVpn" : "lblHexInviteHelp", String.valueOf(HexInviteDialog.port())),
                body(Font.PLAIN, 11.5f), FAINT);
    }

    @Override
    protected void paintComponent(final Graphics g) {
        final Graphics2D g2 = smooth(g);
        weave(g2, getWidth(), getHeight(), palette.ground(), palette);
        g2.dispose();
    }

    /** What a level changes, in one line (Normal: the game as it was before the levels). */
    static String describe(final HexDifficulty d) {
        final Localizer l = Localizer.getInstance();
        final List<String> parts = new ArrayList<>();
        if (d.enemyLifePercent != 100) {
            final int change = d.enemyLifePercent - 100;
            parts.add(l.getMessage("lblHexDiffEnemyLife", (change > 0 ? "+" : "\u2212") + Math.abs(change)));
        }
        if (d.dangerShift > 0) {
            parts.add(l.getMessage("lblHexDiffDecksStronger"));
        } else if (d.dangerShift < 0) {
            parts.add(l.getMessage("lblHexDiffDecksWeaker"));
        }
        if (d.startAdvantages > 0) {
            parts.add(l.getMessage("lblHexDiffAdvantages", String.valueOf(d.startAdvantages)));
        }
        parts.add(l.getMessage("lblHexDiffTeamLife", String.valueOf(d.teamLife)));
        parts.add(d.cardsLost == 0 ? l.getMessage("lblHexDiffNoLoss")
                : l.getMessage("lblHexDiffLoss", String.valueOf(d.cardsLost), String.valueOf(d.lossMaxPrice)));
        parts.add(d.dungeonHeal == 0 ? l.getMessage("lblHexDiffNoHeal") : l.getMessage("lblHexDiffHeal", String.valueOf(d.dungeonHeal)));
        return (d == HexDifficulty.NORMAL ? l.getMessage("lblHexDiffNormal") + " " : "") + String.join("  \u00b7  ", parts);
    }

    // ------------------------------------------------------------------ parts

    private static JPanel transparent(final MigLayout layout) {
        final JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    /** A row of choices on a dark track, so the ones not chosen still look like something to click. */
    private static JPanel row() {
        final JPanel track = new JPanel(new MigLayout("insets 3, gap 4")) {
            @Override
            protected void paintComponent(final Graphics g) {
                final Graphics2D g2 = smooth(g);
                final int h = getHeight();
                g2.setColor(new Color(0, 0, 0, 80));
                g2.fill(HexTownStyle.round(0.5, 0.5, getWidth() - 1, h - 1, h));
                g2.setColor(alpha(GOLD, .22));
                g2.draw(HexTownStyle.round(0.5, 0.5, getWidth() - 1, h - 1, h));
                g2.dispose();
            }
        };
        track.setOpaque(false);
        return track;
    }

    private static JPanel holder() {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        return panel;
    }

    /** Wrapped text in its holder, replaced when it changes; no text hides the holder. */
    private static void setText(final JPanel box, final String text, final Font font, final Color color) {
        final boolean has = text != null && !text.trim().isEmpty();
        final String key = has ? text + '|' + color.getRGB() + '|' + font.getStyle() : "";
        box.setVisible(has);
        if (key.equals(box.getClientProperty("shown"))) {
            return;
        }
        box.putClientProperty("shown", key);
        box.removeAll();
        if (has) {
            box.add(wrapped(text, font, color, TEXT_W));
        }
        box.revalidate();
    }

    /** A text field on the dark cloth, with a gold edge; the code's is read-only. */
    private static JTextField field(final boolean readOnly) {
        final JTextField f = new JTextField();
        f.setFont(readOnly ? new Font(Font.MONOSPACED, Font.BOLD, 14) : body(Font.PLAIN, 15f));
        f.setForeground(TEXT);
        f.setBackground(new Color(0x15130f));
        f.setCaretColor(GOLD_LIGHT);
        f.setSelectionColor(alpha(GOLD, .45));
        f.setSelectedTextColor(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(GOLD_EDGE),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        f.setEditable(!readOnly);
        return f;
    }

    /** Holds the plate in the middle of the page, as wide as the page and at least as high. */
    private static final class Centre extends JPanel implements Scrollable {
        Centre() {
            super(new MigLayout("insets 24, fill", "[center]", "[center]"));
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(final Rectangle visible, final int orientation, final int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(final Rectangle visible, final int orientation, final int direction) {
            return visible.height - 24;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }
}
