package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.FAINT;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_INK;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.HEADING;
import static forge.screens.home.hexcrawl.HexTownStyle.MUTED;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT;
import static forge.screens.home.hexcrawl.HexTownStyle.alpha;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.caps;
import static forge.screens.home.hexcrawl.HexTownStyle.coin;
import static forge.screens.home.hexcrawl.HexTownStyle.display;
import static forge.screens.home.hexcrawl.HexTownStyle.round;
import static forge.screens.home.hexcrawl.HexTownStyle.smooth;
import static forge.screens.home.hexcrawl.HexTownStyle.tracked;
import static forge.screens.home.hexcrawl.HexTownStyle.weave;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.JPanel;

import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import net.miginfocom.swing.MigLayout;

/**
 * The painted parts of the side panel of the hex map: the plate it sits on (in the colours of the region the
 * party is in), section headings, one row per head of the party, the archmages still to beat and the enemy ahead.
 */
final class HexSidebarParts {
    private HexSidebarParts() { }

    /** The plate of the side panel: the cloth of the region the party stands in, with a gold edge. */
    static final class Plate extends JPanel {
        private final Supplier<Palette> palette;

        Plate(final Supplier<Palette> palette0, final MigLayout layout) {
            super(layout);
            palette = palette0;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final Palette p = palette.get();
            final int w = getWidth();
            final int h = getHeight();
            final RoundRectangle2D shape = round(0, 0, w - 1, h - 1, 16);
            final Shape clip = g2.getClip();
            g2.clip(shape);
            weave(g2, w, h, p.dark(), p);
            // a little darker towards the bottom, where the journal is
            g2.setPaint(new java.awt.GradientPaint(0, h / 3f, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 90)));
            g2.fillRect(0, 0, w, h);
            g2.setClip(clip);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(HexTownStyle.WINDOW_EDGE);
            g2.draw(round(1, 1, w - 3, h - 3, 16));
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(alpha(GOLD, .3));
            g2.draw(round(5.5, 5.5, w - 12, h - 12, 12));
            g2.setColor(p.accent());
            g2.fillRect(18, 3, w - 36, 2);
            g2.dispose();
        }
    }

    /** The heading of a section: small gold capitals, then a fine gold rule to the edge. */
    static final class Heading extends JComponent {
        private final String text;

        Heading(final String text0) {
            text = caps(text0);
            setPreferredSize(new Dimension(10, 22));
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            g2.setFont(tracked(display(11.5f), 0.2f));
            final FontMetrics fm = g2.getFontMetrics();
            final int base = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(HEADING);
            g2.drawString(text, 0, base);
            final int x = fm.stringWidth(text) + 10;
            if (x < getWidth()) {
                g2.setPaint(new java.awt.GradientPaint(x, 0, alpha(GOLD, .45), getWidth(), 0, alpha(GOLD, 0)));
                g2.fillRect(x, getHeight() / 2, getWidth() - x, 1);
            }
            g2.dispose();
        }
    }

    /** One head of the party: its colour, name, gold, what it carries and what it is doing right now. */
    static final class PartyRow extends JComponent {
        private String name = "";
        private String tag = "";
        private String gold = "";
        private String details = "";
        private String status = "";
        private Color statusColor = MUTED;
        private Color headColor = GOLD;
        private boolean mine;

        void set(final String name0, final String tag0, final int gold0, final String details0, final String status0,
                final Color statusColor0, final Color headColor0, final boolean mine0) {
            name = name0;
            tag = tag0 == null ? "" : tag0;
            gold = String.valueOf(gold0);
            details = details0;
            status = status0 == null ? "" : status0;
            statusColor = statusColor0;
            headColor = headColor0;
            mine = mine0;
            setToolTipText(status.isEmpty() ? null : status);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, status.isEmpty() ? 50 : 68);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            final int h = getHeight();
            final RoundRectangle2D shape = round(0.5, 0.5, w - 1, h - 1, 10);
            g2.setColor(new Color(0, 0, 0, mine ? 90 : 70));
            g2.fill(shape);
            final Shape clip = g2.getClip();
            g2.clip(shape);
            g2.setColor(headColor);
            g2.fillRect(0, 0, 4, h);
            g2.setClip(clip);
            g2.setColor(mine ? alpha(GOLD, .45) : new Color(255, 255, 255, 18));
            g2.draw(shape);

            // the purse on the right
            g2.setFont(display(15f));
            final FontMetrics gm = g2.getFontMetrics();
            final int goldW = gm.stringWidth(gold);
            final int goldX = w - 12 - goldW;
            g2.setColor(GOLD_LIGHT);
            g2.drawString(gold, goldX, 22);
            coin(g2, goldX - 11, 22 - (gm.getAscent() - gm.getDescent()) / 2.0, 15);

            final int textW = goldX - 22 - 16;
            g2.setFont(body(Font.BOLD, 14f));
            g2.setColor(TEXT);
            final String shownName = fit(g2, name, textW);
            g2.drawString(shownName, 16, 21);
            if (!tag.isEmpty()) {
                final int x = 16 + g2.getFontMetrics().stringWidth(shownName) + 7;
                g2.setFont(body(Font.PLAIN, 11.5f));
                g2.setColor(FAINT);
                g2.drawString(fit(g2, tag, textW - x + 16), x, 21);
            }
            g2.setFont(body(Font.PLAIN, 12f));
            g2.setColor(MUTED);
            g2.drawString(fit(g2, details, w - 28), 16, 39);
            if (!status.isEmpty()) {
                g2.setFont(body(Font.BOLD, 12f));
                g2.setColor(statusColor);
                g2.drawString(fit(g2, status, w - 28), 16, 57);
            }
            g2.dispose();
        }
    }

    /** The text, shortened with an ellipsis to fit the width. */
    static String fit(final Graphics2D g, final String text, final int width) {
        final FontMetrics fm = g.getFontMetrics();
        if (text == null || fm.stringWidth(text) <= width) {
            return text == null ? "" : text;
        }
        String s = text;
        while (s.length() > 1 && fm.stringWidth(s + "…") > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    /** The archmages of the five colours: a gem each, lit with a check once beaten, the name under it. */
    static final class BossTrack extends JComponent {
        private String[] names = new String[0];
        private Color[] colors = new Color[0];
        private boolean[] beaten = new boolean[0];

        void set(final String[] names0, final Color[] colors0, final boolean[] beaten0, final String beatenWord) {
            names = names0;
            colors = colors0;
            beaten = beaten0;
            final StringBuilder tip = new StringBuilder("<html>");
            for (int i = 0; i < names.length; i++) {
                tip.append(names[i]).append(beaten[i] ? " – " + beatenWord : "").append(i < names.length - 1 ? "<br>" : "");
            }
            setToolTipText(tip.append("</html>").toString());
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, 54);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int n = names.length;
            if (n == 0) {
                g2.dispose();
                return;
            }
            final double colW = getWidth() / (double) n;
            for (int i = 0; i < n; i++) {
                final double cx = colW * i + colW / 2;
                final double cy = 16;
                final double r = 11;
                final Path2D.Double gem = new Path2D.Double();
                gem.moveTo(cx, cy - r);
                gem.lineTo(cx + r, cy);
                gem.lineTo(cx, cy + r);
                gem.lineTo(cx - r, cy);
                gem.closePath();
                if (beaten[i]) {
                    for (int k = 3; k >= 1; k--) {
                        g2.setColor(alpha(colors[i], .14));
                        g2.setStroke(new BasicStroke(k * 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.draw(gem);
                    }
                    g2.setColor(colors[i]);
                    g2.fill(gem);
                    g2.setColor(GOLD_LIGHT);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(gem);
                    g2.setColor(GOLD_INK);
                    g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    final Path2D.Double check = new Path2D.Double();
                    check.moveTo(cx - 4.5, cy + 0.5);
                    check.lineTo(cx - 1.2, cy + 3.8);
                    check.lineTo(cx + 4.8, cy - 3.2);
                    g2.draw(check);
                } else {
                    g2.setColor(new Color(0, 0, 0, 110));
                    g2.fill(gem);
                    g2.setColor(alpha(colors[i], .8));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(gem);
                }
                g2.setFont(body(beaten[i] ? Font.BOLD : Font.PLAIN, 10.5f));
                g2.setColor(beaten[i] ? TEXT : FAINT);
                final String shown = fit(g2, names[i], (int) colW - 2);
                g2.drawString(shown, (float) (cx - g2.getFontMetrics().stringWidth(shown) / 2.0), 46);
            }
            g2.dispose();
        }
    }

    /** The panel of the enemy ahead: dark red, with the enemy's figure from the map. */
    static final class Threat extends JPanel {
        Threat(final MigLayout layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            final int h = getHeight();
            g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x4a1d16), 0, h, new Color(0x2a1210)));
            g2.fill(round(0.5, 0.5, w - 1, h - 1, 12));
            g2.setColor(new Color(0xa0412c));
            g2.draw(round(0.5, 0.5, w - 1, h - 1, 12));
            g2.setColor(alpha(GOLD, .25));
            g2.draw(round(4.5, 4.5, w - 10, h - 10, 9));
            g2.dispose();
        }
    }

    /** A pixel figure of the map, enlarged without blurring. */
    static final class Sprite extends JComponent {
        private final BufferedImage image;

        Sprite(final BufferedImage image0, final int size) {
            image = image0;
            setPreferredSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(final Graphics g) {
            if (image == null) {
                return;
            }
            final Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            final double scale = Math.min(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
            final int w = (int) (image.getWidth() * scale);
            final int h = (int) (image.getHeight() * scale);
            g2.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            g2.dispose();
        }
    }
}
