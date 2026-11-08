package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.FAINT;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT_BRIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.alpha;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.display;
import static forge.screens.home.hexcrawl.HexTownStyle.label;
import static forge.screens.home.hexcrawl.HexTownStyle.round;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import forge.gamemodes.hexcrawl.HexTheme;
import forge.screens.home.hexcrawl.HexSidebarParts.Heading;
import forge.screens.home.hexcrawl.HexSidebarParts.Plate;
import forge.screens.home.hexcrawl.HexTownStyle.PaintedButton;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * The list of the themes a head may pick for one of its shop sections, opened over the town under what was clicked:
 * a search field, then the themes in their groups (worlds, ways to play, kinds of cards, tribes), alphabetical, each
 * with the number of cards the town has in it. A click picks one; Enter picks the first one found; a click beside
 * it or Escape closes it.
 */
final class HexThemePicker {
    private static final int W = 940;
    private static final int GAP = 10;
    private static final int COLUMNS = 3;

    private final Localizer loc = Localizer.getInstance();
    private final Plate plate;
    private final Catcher catcher = new Catcher();
    private final JPanel list = new JPanel(new MigLayout("insets 0 0 4 4, gap 6 4, wrap " + COLUMNS + ", fillx", "[grow, fill, sg c]".repeat(COLUMNS)));
    private final JTextField search;
    private final List<HexTheme> themes;
    private final String current;
    private final ToIntFunction<HexTheme> count;
    private final Consumer<HexTheme> onPick;
    private final List<HexTheme> shown = new ArrayList<>();
    private JLayeredPane layer;

    HexThemePicker(final Supplier<Palette> palette, final String title, final List<HexTheme> themes0, final String current0,
            final ToIntFunction<HexTheme> count0, final Consumer<HexTheme> onPick0) {
        themes = new ArrayList<>(themes0);
        final Collator collator = Collator.getInstance();
        themes.sort((a, b) -> collator.compare(a.getLabel(), b.getLabel()));
        current = current0;
        count = count0;
        onPick = onPick0;
        search = new HexTownStyle.SearchField(loc.getMessage("lblHexThemeSearch"));
        plate = new Plate(palette, new MigLayout("insets 18 18 16 18, gap 0 12, wrap 1, fill", "[grow, fill]", "[][grow, fill]"));
        final JPanel top = new JPanel(new MigLayout("insets 0, gap 16, fillx", "[][grow, fill]"));
        top.setOpaque(false);
        top.add(label(title, display(19f), TEXT_BRIGHT));
        top.add(search, "h 32!");
        plate.add(top);
        list.setOpaque(false);
        final FScrollPane scroll = new FScrollPane(list, false, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        plate.add(scroll, "wmin 0, hmin 0");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(final DocumentEvent e) { fillList(); }
            @Override public void removeUpdate(final DocumentEvent e) { fillList(); }
            @Override public void changedUpdate(final DocumentEvent e) { fillList(); }
        });
        search.addActionListener(e -> {
            if (!shown.isEmpty()) {
                pick(shown.get(0));
            }
        });
        fillList();
    }

    /** Does the theme match what was typed (its name, or its key: "token" finds the tokens in any language)? */
    private static boolean matches(final HexTheme theme, final String query) {
        if (query.isEmpty()) {
            return true;
        }
        return theme.getLabel().toLowerCase(Locale.ROOT).contains(query) || theme.id.toLowerCase(Locale.ROOT).contains(query);
    }

    private void fillList() {
        final String query = search.getText().trim().toLowerCase(Locale.ROOT);
        list.removeAll();
        shown.clear();
        final Map<HexTheme.Group, List<HexTheme>> groups = new EnumMap<>(HexTheme.Group.class);
        for (final HexTheme theme : themes) {
            if (matches(theme, query)) {
                groups.computeIfAbsent(theme.group(), g -> new ArrayList<>()).add(theme);
            }
        }
        for (final Map.Entry<HexTheme.Group, List<HexTheme>> group : groups.entrySet()) {
            list.add(new Heading(loc.getMessage(group.getKey().labelKey())), (shown.isEmpty() ? "" : "newline, gaptop 10, ") + "span, growx, h 22!");
            for (final HexTheme theme : group.getValue()) {
                shown.add(theme);
                final Row row = new Row(theme.getLabel(), String.valueOf(count.applyAsInt(theme)), theme.id.equals(current));
                row.addActionListener(e -> pick(theme));
                list.add(row, "h 32!, wmin 0");
            }
        }
        if (shown.isEmpty()) {
            list.add(label(loc.getMessage("lblHexThemeNoMatch"), body(Font.PLAIN, 14f), FAINT), "span");
        }
        list.revalidate();
        list.repaint();
    }

    private void pick(final HexTheme theme) {
        close();
        SwingUtilities.invokeLater(() -> onPick.accept(theme));
    }

    /** Opens the list under the anchor, as wide as it can be beside the window's edge. */
    void show(final JComponent anchor) {
        final JRootPane root = SwingUtilities.getRootPane(anchor);
        if (root == null) {
            return;
        }
        close();
        layer = root.getLayeredPane();
        final Point a = SwingUtilities.convertPoint(anchor.getParent(), anchor.getLocation(), layer);
        final int w = Math.min(W, layer.getWidth() - 2 * GAP);
        final int x = Math.max(GAP, Math.min(a.x, layer.getWidth() - GAP - w));
        int y = a.y + anchor.getHeight() + 6;
        final int h = Math.min(Math.max(plate.getPreferredSize().height, 240), layer.getHeight() - GAP - y);
        if (h < 240) { // no room below: over the anchor instead
            y = Math.max(GAP, a.y - 6 - 520);
        }
        catcher.setBounds(0, 0, layer.getWidth(), layer.getHeight());
        plate.setBounds(x, y, w, h < 240 ? Math.min(520, layer.getHeight() - GAP - y) : h);
        catcher.add(plate);
        layer.add(catcher, JLayeredPane.POPUP_LAYER);
        plate.validate();
        layer.repaint();
        search.requestFocusInWindow();
    }

    void close() {
        if (layer == null) {
            return;
        }
        final JLayeredPane l = layer;
        layer = null;
        l.remove(catcher);
        l.repaint();
    }

    /** The whole window while the list is open: a little darker; a click on it closes the list. */
    private final class Catcher extends JComponent {
        Catcher() {
            setLayout(null);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(final MouseEvent e) {
                    if (!plate.getBounds().contains(e.getPoint())) {
                        close();
                    }
                }
            });
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "hexPickerClose");
            getActionMap().put("hexPickerClose", new AbstractAction() {
                @Override
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    close();
                }
            });
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = HexTownStyle.smooth(g);
            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillRect(0, 0, getWidth(), getHeight());
            final Rectangle r = plate.getBounds();
            for (int i = 1; i <= 6; i++) { // a soft shadow under the plate
                g2.setColor(new Color(0, 0, 0, 22));
                g2.fill(round(r.x - i + 3, r.y - i + 5, r.width + 2 * i, r.height + 2 * i, 16 + i));
            }
            g2.dispose();
        }
    }

    /** One theme: its name, the town's number of cards in it on the right; the theme picked now in gold. */
    private static final class Row extends PaintedButton {
        private final String note;
        private final boolean chosen;

        Row(final String text, final String note0, final boolean chosen0) {
            super(text);
            note = note0;
            chosen = chosen0;
            setToolTipText(text);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 32);
        }

        @Override
        protected void paintButton(final Graphics2D g, final int w, final int h, final boolean hovered) {
            if (hovered || chosen) {
                g.setColor(chosen ? alpha(GOLD, .16) : new Color(255, 255, 255, 18));
                g.fill(round(0.5, 0.5, w - 1, h - 1, 8));
                g.setColor(alpha(GOLD, chosen ? .7 : .45));
                g.draw(round(0.5, 0.5, w - 1, h - 1, 8));
            }
            g.setFont(body(Font.BOLD, 12f));
            final int noteW = g.getFontMetrics().stringWidth(note);
            final int base = (h + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2;
            g.setColor(chosen ? GOLD_LIGHT : FAINT);
            g.drawString(note, w - 10 - noteW, base);
            g.setFont(body(chosen ? Font.BOLD : Font.PLAIN, 14f));
            final FontMetrics fm = g.getFontMetrics();
            String text = getText();
            final int room = w - 10 - noteW - 12 - 10;
            if (fm.stringWidth(text) > room) {
                while (text.length() > 1 && fm.stringWidth(text + "...") > room) {
                    text = text.substring(0, text.length() - 1);
                }
                text = text.trim() + "...";
            }
            g.setColor(chosen ? GOLD_LIGHT : TEXT);
            g.drawString(text, 10, (h + fm.getAscent() - fm.getDescent()) / 2);
        }
    }
}
