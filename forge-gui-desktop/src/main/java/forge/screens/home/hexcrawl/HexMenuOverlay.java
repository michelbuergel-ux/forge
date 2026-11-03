package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.FAINT;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT;
import static forge.screens.home.hexcrawl.HexTownStyle.alpha;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.round;
import static forge.screens.home.hexcrawl.HexTownStyle.width;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import forge.screens.home.hexcrawl.HexSidebarParts.Heading;
import forge.screens.home.hexcrawl.HexSidebarParts.Plate;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import forge.screens.home.hexcrawl.HexTownStyle.PaintedButton;
import net.miginfocom.swing.MigLayout;

/**
 * A menu painted like the side panel of the hex map, opened over the map beside the panel: section headings and
 * rows to click. A click beside it, Escape, or leaving the screen closes it; a row's action runs once it is closed.
 */
final class HexMenuOverlay {
    private static final int W = 345;
    private static final int GAP = 10;

    private final Plate plate;
    private final Catcher catcher = new Catcher();
    private JLayeredPane layer;
    private final ComponentAdapter onResize = new ComponentAdapter() {
        @Override
        public void componentResized(final ComponentEvent e) {
            close();
        }
    };

    HexMenuOverlay(final Supplier<Palette> palette) {
        plate = new Plate(palette, new MigLayout("insets 16 14 14 14, gap 0, wrap 1, fillx, hidemode 3", "[grow, fill]"));
    }

    /** A section heading; the first one sits at the top, the others a little apart. */
    void heading(final String text) {
        plate.add(new Heading(text), plate.getComponentCount() == 0 ? "" : "gaptop 10");
    }

    /** A row: its text, a note on the right (or null), and what it does (it runs after the menu is closed). */
    void item(final String text, final String note, final boolean enabled, final Runnable action) {
        final Row row = new Row(text, note);
        row.setEnabled(enabled);
        row.addActionListener(e -> {
            close();
            SwingUtilities.invokeLater(action);
        });
        plate.add(row, "h 30!, gaptop 2");
    }

    /** A switch: the row shows its state on the right and stays open when clicked. */
    void toggle(final String text, final String on, final String off, final BooleanSupplier state, final Runnable flip) {
        final Row row = new Row(text, state.getAsBoolean() ? on : off);
        row.lit = state.getAsBoolean();
        row.addActionListener(e -> {
            flip.run();
            row.lit = state.getAsBoolean();
            row.note = row.lit ? on : off;
            row.repaint();
        });
        plate.add(row, "h 30!, gaptop 2");
    }

    boolean isOpen() {
        return layer != null;
    }

    /**
     * Opens the menu over the map, its right edge beside the side panel; its bottom at the anchor's bottom when the
     * anchor is low on the screen (the Kampagne button), else its top at the anchor's top (the campaign's name).
     */
    void show(final JComponent anchor, final JComponent sidePanel) {
        final JRootPane root = SwingUtilities.getRootPane(anchor);
        if (root == null) {
            return;
        }
        close();
        layer = root.getLayeredPane();
        final Point a = SwingUtilities.convertPoint(anchor.getParent(), anchor.getLocation(), layer);
        final Point s = SwingUtilities.convertPoint(sidePanel.getParent(), sidePanel.getLocation(), layer);
        final Dimension pref = plate.getPreferredSize();
        final int h = Math.min(pref.height, layer.getHeight() - 2 * GAP);
        final int x = Math.max(GAP, s.x - GAP - W);
        int y = a.y + anchor.getHeight() / 2 > layer.getHeight() / 2 ? a.y + anchor.getHeight() - h : a.y;
        y = Math.max(GAP, Math.min(y, layer.getHeight() - GAP - h));
        catcher.setBounds(0, 0, layer.getWidth(), layer.getHeight());
        plate.setBounds(x, y, W, h);
        catcher.add(plate);
        layer.add(catcher, JLayeredPane.POPUP_LAYER);
        layer.addComponentListener(onResize);
        plate.validate();
        layer.repaint();
    }

    void close() {
        if (layer == null) {
            return;
        }
        final JLayeredPane l = layer;
        layer = null;
        l.removeComponentListener(onResize);
        l.remove(catcher);
        l.repaint();
    }

    /** The whole window while the menu is open: a little darker, and a click on it closes the menu. */
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
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "hexMenuClose");
            getActionMap().put("hexMenuClose", new AbstractAction() {
                @Override
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    close();
                }
            });
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = HexTownStyle.smooth(g);
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRect(0, 0, getWidth(), getHeight());
            final Rectangle r = plate.getBounds();
            for (int i = 1; i <= 6; i++) { // a soft shadow under the plate
                g2.setColor(new Color(0, 0, 0, 22));
                g2.fill(round(r.x - i + 3, r.y - i + 5, r.width + 2 * i, r.height + 2 * i, 16 + i));
            }
            g2.dispose();
        }
    }

    /** One row of the menu: lit under the mouse, its note (a date, a state) on the right. */
    private static final class Row extends PaintedButton {
        private String note;
        private boolean lit;

        Row(final String text, final String note0) {
            super(text);
            note = note0;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width(this, getText(), body(Font.PLAIN, 14f)) + 40, 30);
        }

        @Override
        protected void paintButton(final Graphics2D g, final int w, final int h, final boolean hovered) {
            if (hovered) {
                g.setColor(new Color(255, 255, 255, 18));
                g.fill(round(0.5, 0.5, w - 1, h - 1, 8));
                g.setColor(alpha(GOLD, .45));
                g.draw(round(0.5, 0.5, w - 1, h - 1, 8));
            }
            g.setFont(body(Font.PLAIN, 14f));
            final FontMetrics fm = g.getFontMetrics();
            final int base = (h + fm.getAscent() - fm.getDescent()) / 2;
            int noteW = 0;
            if (note != null && !note.isEmpty()) {
                g.setFont(body(Font.BOLD, 12f));
                noteW = g.getFontMetrics().stringWidth(note);
                g.setColor(lit ? GOLD_LIGHT : FAINT);
                g.drawString(note, w - 10 - noteW, base);
                noteW += 12;
            }
            g.setFont(body(Font.PLAIN, 14f));
            g.setColor(hovered ? HexTownStyle.TEXT_BRIGHT : TEXT);
            g.drawString(HexSidebarParts.fit(g, getText(), w - 20 - noteW), 10, base);
        }
    }
}
