package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.GOLD;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT_BRIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.display;
import static forge.screens.home.hexcrawl.HexTownStyle.label;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

import forge.screens.home.hexcrawl.HexSidebarParts.Plate;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import net.miginfocom.swing.MigLayout;

/**
 * The town's keyboard shortcuts, two columns (key, what it does), over the window next to what was clicked; a click
 * anywhere or Esc closes it. The list is one text: "key = action · key = action · …".
 */
final class HexKeysPopup {
    private static final int GAP = 10;

    private final Plate plate;
    private final JComponent catcher = new JComponent() {
        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = HexTownStyle.smooth(g);
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
    };
    private JLayeredPane layer;

    HexKeysPopup(final Supplier<Palette> palette, final String title, final String table) {
        plate = new Plate(palette, new MigLayout("insets 16 18 16 18, gap 18 6, wrap 2", "[right][grow, fill]"));
        plate.add(label(title, display(18f), TEXT_BRIGHT), "span 2, left, gapbottom 6");
        for (final String entry : table.split(" · ")) {
            final int at = entry.indexOf(" = ");
            plate.add(label(at < 0 ? "" : entry.substring(0, at).trim(), body(Font.BOLD, 13f), GOLD));
            plate.add(label(at < 0 ? entry.trim() : entry.substring(at + 3).trim(), body(Font.PLAIN, 13f), TEXT_BRIGHT));
        }
        catcher.setLayout(null);
        final MouseAdapter closer = new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                close(); // a click anywhere, on the list too
            }
        };
        catcher.addMouseListener(closer);
        plate.addMouseListener(closer);
    }

    boolean isOpen() {
        return layer != null;
    }

    /** Opens the list beside the anchor (above it if there is no room below), inside the window. */
    void show(final JComponent anchor) {
        final JRootPane root = SwingUtilities.getRootPane(anchor);
        if (root == null) {
            return;
        }
        close();
        layer = root.getLayeredPane();
        final Point a = SwingUtilities.convertPoint(anchor.getParent(), anchor.getLocation(), layer);
        final int w = Math.min(plate.getPreferredSize().width, layer.getWidth() - 2 * GAP);
        final int h = Math.min(plate.getPreferredSize().height, layer.getHeight() - 2 * GAP);
        final int x = Math.max(GAP, Math.min(a.x, layer.getWidth() - GAP - w));
        final int y = Math.max(GAP, Math.min(a.y + anchor.getHeight() + 6, layer.getHeight() - GAP - h));
        catcher.setBounds(0, 0, layer.getWidth(), layer.getHeight());
        plate.setBounds(x, y, w, h);
        catcher.add(plate);
        layer.add(catcher, JLayeredPane.POPUP_LAYER);
        plate.validate();
        layer.repaint();
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
}
