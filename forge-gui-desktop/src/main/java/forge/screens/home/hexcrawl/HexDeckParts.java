package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.FAINT;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_EDGE;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_INK;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.HEADING;
import static forge.screens.home.hexcrawl.HexTownStyle.MUTED;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT;
import static forge.screens.home.hexcrawl.HexTownStyle.alpha;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.caps;
import static forge.screens.home.hexcrawl.HexTownStyle.display;
import static forge.screens.home.hexcrawl.HexTownStyle.round;
import static forge.screens.home.hexcrawl.HexTownStyle.smooth;
import static forge.screens.home.hexcrawl.HexTownStyle.tracked;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

import forge.card.mana.ManaCost;
import forge.item.PaperCard;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import forge.toolbox.CardFaceSymbols;
import net.miginfocom.swing.MigLayout;

/**
 * The painted parts of the deck window: the banner and gems in the deck's colours, the rows of the deck and the
 * sideboard, tabs, the number tiles, the colour bars, the mana curve, the advice icons and the relic slots.
 */
final class HexDeckParts {
    static final Color OK = new Color(0x8fd18b);
    static final Color WARN = new Color(0xf0a058);
    static final Color BAD = new Color(0xf07a6a);
    static final Color HINT = new Color(0x9cb8d8);

    private HexDeckParts() { }

    private static float[] fractions(final int n) {
        final float[] f = new float[n];
        for (int i = 0; i < n; i++) {
            f[i] = n == 1 ? 0 : i / (float) (n - 1);
        }
        return f;
    }

    /** Paints the colours side by side, blending into each other; one colour fills the whole box. */
    private static void blend(final Graphics2D g, final List<Color> colors, final int x, final int y, final int w, final int h) {
        if (colors.size() == 1) {
            g.setColor(colors.get(0));
        } else {
            g.setPaint(new LinearGradientPaint(x + w * 0.3f, 0, x + w, 0, fractions(colors.size()), colors.toArray(new Color[0])));
        }
        g.fillRect(x, y, w, h);
    }

    /** The banner of the deck: the dark cloth of each of its main colours blending into the next, with a seam of their accents. */
    static final class DeckBanner extends JPanel {
        static final int CLOTH = 84;
        private Palette p;
        private List<Color> darks = new ArrayList<>();
        private List<Color> accents = new ArrayList<>();

        DeckBanner(final MigLayout layout) {
            super(layout);
            setOpaque(false);
        }

        void setColors(final Palette p0, final List<Color> darks0, final List<Color> accents0) {
            p = p0;
            darks = darks0;
            accents = accents0;
            repaint();
        }

        @Override
        protected void paintComponent(final Graphics g) {
            if (p == null) {
                return;
            }
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            blend(g2, darks, 0, 0, w, CLOTH);
            HexTownStyle.weaveLines(g2, w, CLOTH, p);
            blend(g2, accents, 0, CLOTH, w, 3);
            HexTownStyle.filigree(g2, w / 2.0, CLOTH + 1.5, p.ground());
            g2.dispose();
        }
    }

    /** The deck's colours as round gems, overlapping; a splash is a smaller gem. */
    static final class Sigil extends JComponent {
        private final List<Color> colors = new ArrayList<>();
        private final List<Boolean> small = new ArrayList<>();

        void set(final List<Color> colors0, final List<Boolean> small0) {
            colors.clear();
            colors.addAll(colors0);
            small.clear();
            small.addAll(small0);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            int w = 4;
            for (int i = 0; i < colors.size(); i++) {
                w += (small.get(i) ? 28 : 40) - (i > 0 ? 12 : 0);
            }
            return new Dimension(Math.max(44, w), 44);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            double x = 2;
            for (int i = 0; i < colors.size(); i++) {
                final double size = small.get(i) ? 28 : 40;
                final double y = (getHeight() - size) / 2;
                final Ellipse2D.Double gem = new Ellipse2D.Double(x, y, size, size);
                g2.setColor(new Color(0, 0, 0, 120));
                g2.fill(new Ellipse2D.Double(x, y + 2, size, size));
                g2.setPaint(new RadialGradientPaint((float) (x + size * 0.35), (float) (y + size * 0.3), (float) size * 0.75f,
                        new float[] { 0f, 0.55f, 1f }, new Color[] { new Color(255, 255, 255, 140), colors.get(i), colors.get(i).darker() }));
                g2.fill(gem);
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(GOLD);
                g2.draw(gem);
                x += size - 12;
            }
            g2.dispose();
        }
    }

    /** What a card row does when it is pointed at or its arrow is clicked. */
    interface RowListener {
        void hovered();
        void move();
    }

    /**
     * One card of the deck or the sideboard: copies, name, mana cost and the arrow that moves one copy across
     * (on the right in the deck, on the left in the sideboard). Deck rows warn when no land makes a colour of
     * the card; sideboard rows say whether the card suits the deck's colours.
     */
    static final class CardRow extends JComponent {
        private final Palette p;
        private final boolean inDeck;
        private final ManaCost cost;
        private final String name;
        private final String qty;
        private final boolean warn;
        private final String fitLabel;
        private final boolean fits;
        private final boolean editable;
        private final String moveTip;
        private final String warnTip;
        private boolean selected;
        private boolean hover;
        private boolean overArrow;

        CardRow(final Palette p0, final boolean inDeck0, final PaperCard card, final String name0, final int copies, final boolean warn0,
                final String fitLabel0, final boolean fits0, final boolean editable0, final String moveTip0, final String warnTip0,
                final RowListener listener) {
            p = p0;
            inDeck = inDeck0;
            cost = card == null ? null : card.getRules().getManaCost();
            name = name0;
            qty = copies + "×";
            warn = warn0;
            fitLabel = fitLabel0;
            fits = fits0;
            editable = editable0;
            moveTip = moveTip0;
            warnTip = warnTip0;
            setPreferredSize(new Dimension(10, 34));
            final MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(final MouseEvent e) {
                    hover = true;
                    listener.hovered();
                    repaint();
                }

                @Override
                public void mouseExited(final MouseEvent e) {
                    hover = false;
                    overArrow = false;
                    repaint();
                }

                @Override
                public void mouseMoved(final MouseEvent e) {
                    final boolean over = arrowAt(e.getX());
                    if (over != overArrow) {
                        overArrow = over;
                        setCursor(over && editable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                        repaint();
                    }
                    setToolTipText(over ? moveTip : warn ? warnTip : null);
                }

                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && arrowAt(e.getX()) && editable) {
                        listener.move();
                    } else {
                        listener.hovered();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void setSelected(final boolean selected0) {
            if (selected != selected0) {
                selected = selected0;
                repaint();
            }
        }

        private int arrowX() {
            return inDeck ? getWidth() - 34 : 4;
        }

        private boolean arrowAt(final int x) {
            return x >= arrowX() - 2 && x <= arrowX() + 32;
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            final int h = getHeight();
            if (selected || hover) {
                g2.setColor(selected ? alpha(p.accent(), .14) : new Color(255, 255, 255, 15));
                g2.fill(round(0, 1, w, h - 2, 8));
            }
            // the arrow button
            final int ax = arrowX();
            final RoundRectangle2D button = round(ax + 0.5, 2.5, 29, 29, 8);
            final java.awt.Composite composite = g2.getComposite();
            if (!editable) {
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, .3f));
            }
            g2.setColor(alpha(p.accent(), overArrow && editable ? .4 : inDeck ? .12 : .22));
            g2.fill(button);
            g2.setColor(alpha(p.accent(), overArrow && editable ? .9 : .5));
            g2.draw(button);
            g2.setColor(new Color(0xf4ecd8));
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            final Path2D.Double chevron = new Path2D.Double();
            final double cx = ax + 15;
            final double dir = inDeck ? 1 : -1;
            chevron.moveTo(cx - 3 * dir, 11);
            chevron.lineTo(cx + 3 * dir, 17);
            chevron.lineTo(cx - 3 * dir, 23);
            g2.draw(chevron);
            g2.setComposite(composite);

            // copies
            final int qx = inDeck ? 6 : 42;
            final RoundRectangle2D chip = round(qx + 0.5, 6.5, 32, 21, 6);
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(chip);
            g2.setColor(new Color(255, 255, 255, 26));
            g2.draw(chip);
            g2.setFont(body(Font.BOLD, 12f));
            final FontMetrics qm = g2.getFontMetrics();
            g2.setColor(GOLD_LIGHT);
            g2.drawString(qty, qx + 16 - qm.stringWidth(qty) / 2f, 21.5f);

            // the right end: the mana cost (and in the sideboard whether the card fits)
            int right = inDeck ? ax - 8 : w - 6;
            if (!inDeck) {
                g2.setFont(body(Font.BOLD, 11f));
                g2.setColor(fits ? p.light() : new Color(0x8e887b));
                final int fw = g2.getFontMetrics().stringWidth(fitLabel);
                g2.drawString(fitLabel, right - fw, 21);
                right -= Math.max(fw, 52) + 10;
            }
            if (cost != null && !cost.isNoCost()) {
                final int size = 16;
                final int mw = cost.getGlyphCount() * (size + 1);
                CardFaceSymbols.draw(g2, cost, right - mw, (h - size) / 2, size);
                right -= mw + 8;
            }

            // the name
            final int nx = inDeck ? 46 : 82;
            g2.setFont(body(Font.PLAIN, 13.5f));
            g2.setColor(inDeck || fits ? TEXT : new Color(0xa39d8f));
            final String shown = HexSidebarParts.fit(g2, name, right - nx - (warn ? 24 : 0));
            g2.drawString(shown, nx, 22);
            if (warn) {
                final double wx = nx + g2.getFontMetrics().stringWidth(shown) + 6;
                g2.setColor(WARN);
                g2.fill(new Ellipse2D.Double(wx, 8, 18, 18));
                g2.setColor(GOLD_INK);
                g2.setFont(body(Font.BOLD, 12f));
                g2.drawString("!", (float) (wx + 9 - g2.getFontMetrics().stringWidth("!") / 2.0), 22);
            }
            g2.dispose();
        }
    }

    /** A tab over the sideboard: its name and a count; the tab shown is lit and underlined in the deck's colour. */
    static final class Tab extends HexTownStyle.PaintedButton {
        private final Palette p;
        private String count = "";
        private boolean active;

        Tab(final String text, final Palette p0) {
            super(text);
            p = p0;
        }

        void set(final String count0, final boolean active0) {
            count = count0;
            active = active0;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(HexTownStyle.width(this, getText(), display(15f)) + HexTownStyle.width(this, count, body(Font.BOLD, 12f)) + 36, 40);
        }

        @Override
        protected void paintButton(final Graphics2D g, final int w, final int h, final boolean lit) {
            final RoundRectangle2D shape = round(0.5, 0.5, w - 1, h - 1, 10);
            g.setColor(active ? alpha(p.accent(), lit ? .3 : .22) : new Color(0, 0, 0, lit ? 90 : 64));
            g.fill(shape);
            if (active) {
                final java.awt.Shape clip = g.getClip();
                g.clip(shape);
                g.setColor(p.accent());
                g.fillRect(0, h - 3, w, 3);
                g.setClip(clip);
            }
            g.setColor(active ? p.accent() : alpha(GOLD, lit ? .45 : .25));
            g.draw(shape);
            g.setFont(display(15f));
            final FontMetrics fm = g.getFontMetrics();
            final int base = (h + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(new Color(0xf2ead6));
            g.drawString(getText(), 14, base);
            g.setFont(body(Font.BOLD, 12f));
            g.setColor(MUTED);
            g.drawString(count, 14 + fm.stringWidth(getText()) + 8, base);
        }
    }

    /** One number at a glance: what it is, the number, and a note with a dot (green when fine, orange when not). */
    static final class StatTile extends JComponent {
        private final Palette p;
        private String label = "";
        private String value = "";
        private String note = "";
        private Color dot = OK;

        StatTile(final Palette p0) {
            p = p0;
            setPreferredSize(new Dimension(140, 112));
        }

        void set(final String label0, final String value0, final String note0, final Color dot0) {
            label = caps(label0);
            value = value0;
            note = note0;
            dot = dot0;
            setToolTipText(note);
            repaint();
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            final int h = getHeight();
            g2.setColor(new Color(0, 0, 0, 72));
            g2.fill(round(0.5, 0.5, w - 1, h - 1, 12));
            g2.setColor(alpha(p.accent(), .3));
            g2.draw(round(0.5, 0.5, w - 1, h - 1, 12));
            final int top = (h - 84) / 2;
            g2.setFont(tracked(display(11f), 0.16f));
            g2.setColor(HEADING);
            g2.drawString(HexSidebarParts.fit(g2, label, w - 28), 16, top + 14);
            g2.setFont(display(30f));
            g2.setColor(GOLD_LIGHT);
            g2.drawString(value, 16, top + 52);
            g2.setColor(dot);
            g2.fill(new Ellipse2D.Double(15, top + 69, 8, 8));
            g2.setFont(body(Font.PLAIN, 12f));
            g2.setColor(MUTED);
            g2.drawString(HexSidebarParts.fit(g2, note, w - 36), 29, top + 77);
            g2.dispose();
        }
    }

    /** One colour of the deck: what share of the mana symbols needs it, what share of the lands makes it. */
    record ColourRow(Color gem, String name, double need, double make, String needLabel, String makeLabel, Color makeColor) { }

    /** Paints a section heading with a legend of two bars on the right. */
    private static void header(final Graphics2D g, final int w, final String title, final String legendA, final Color fillA,
            final String legendB, final Color fillB, final boolean outlineB) {
        g.setFont(tracked(display(12f), 0.2f));
        g.setColor(HEADING);
        g.drawString(caps(title), 0, 15);
        g.setFont(body(Font.PLAIN, 11.5f));
        final FontMetrics fm = g.getFontMetrics();
        int x = w - fm.stringWidth(legendB);
        g.setColor(MUTED);
        g.drawString(legendB, x, 15);
        x -= 20;
        if (outlineB) {
            g.setColor(fillB);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(round(x + 0.75, 7.75, 13.5, 6.5, 4));
        } else {
            g.setColor(fillB);
            g.fill(round(x, 6, 14, 10, 3));
        }
        x -= 14 + fm.stringWidth(legendA);
        g.setColor(MUTED);
        g.drawString(legendA, x, 15);
        g.setColor(fillA);
        g.fill(round(x - 20, outlineB ? 7.5 : 6, 14, outlineB ? 7 : 10, outlineB ? 4 : 3));
    }

    /** The heading of the colours: its title and the legend of the two bars (it stays put while the rows scroll). */
    static final class ColourHeader extends JComponent {
        private final String title;
        private final String needLegend;
        private final String makeLegend;

        ColourHeader(final String title0, final String needLegend0, final String makeLegend0) {
            title = title0;
            needLegend = needLegend0;
            makeLegend = makeLegend0;
            setPreferredSize(new Dimension(200, 22));
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            header(g2, getWidth(), title, needLegend, new Color(0xc7c0ae), makeLegend, new Color(0xc7c0ae), true);
            g2.dispose();
        }
    }

    /** The colours: for each, the bar of what the spells need and the outlined bar of what the lands make. */
    static final class ColourBars extends JComponent {
        static final int ROW = 31;
        private final List<ColourRow> rows = new ArrayList<>();
        private String empty = "";

        void set(final String empty0, final List<ColourRow> rows0) {
            empty = empty0;
            rows.clear();
            rows.addAll(rows0);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 4 + Math.max(1, rows.size()) * ROW);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            if (rows.isEmpty()) {
                g2.setFont(body(Font.PLAIN, 13f));
                g2.setColor(new Color(0xd6d0c2));
                g2.drawString(empty, 0, 22);
            }
            final int labelW = 168;
            final int barX = 104;
            final int barW = Math.max(40, w - barX - labelW - 12);
            int y = 4;
            for (final ColourRow r : rows) {
                final Path2D.Double gem = new Path2D.Double();
                gem.moveTo(7, y + 6);
                gem.lineTo(13, y + 12);
                gem.lineTo(7, y + 18);
                gem.lineTo(1, y + 12);
                gem.closePath();
                g2.setColor(r.gem());
                g2.fill(gem);
                g2.setFont(body(Font.BOLD, 13.5f));
                g2.setColor(TEXT);
                g2.drawString(HexSidebarParts.fit(g2, r.name(), barX - 30), 22, y + 17);
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fill(round(barX, y + 3, barW, 8, 8));
                g2.fill(round(barX, y + 14, barW, 8, 8));
                g2.setColor(r.gem());
                g2.fill(round(barX, y + 3, Math.max(0, barW * r.need()), 8, 8));
                if (r.make() > 0) {
                    g2.setColor(alpha(r.gem(), .2));
                    g2.fill(round(barX, y + 14, barW * Math.min(1, r.make()), 8, 8));
                    g2.setColor(r.gem());
                    g2.setStroke(new BasicStroke(1.6f));
                    g2.draw(round(barX + 0.8, y + 14.8, Math.max(1, barW * Math.min(1, r.make()) - 1.6), 6.4, 6));
                }
                g2.setFont(body(Font.PLAIN, 11.5f));
                g2.setColor(MUTED);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(r.needLabel(), w - fm.stringWidth(r.needLabel()), y + 11);
                g2.setFont(body(Font.BOLD, 11.5f));
                g2.setColor(r.makeColor());
                fm = g2.getFontMetrics();
                g2.drawString(r.makeLabel(), w - fm.stringWidth(r.makeLabel()), y + 24);
                y += ROW;
            }
            g2.dispose();
        }
    }

    /** The mana curve: one column per mana value, creatures at the bottom in the deck's colour, other spells above. */
    static final class CurveChart extends JComponent {
        private final Palette p;
        private int[] all = new int[7];
        private int[] creatures = new int[7];
        private String title = "";
        private String creatureLegend = "";
        private String otherLegend = "";

        CurveChart(final Palette p0) {
            p = p0;
            setPreferredSize(new Dimension(200, 124));
        }

        void set(final String title0, final String creatureLegend0, final String otherLegend0, final int[] all0, final int[] creatures0) {
            title = title0;
            creatureLegend = creatureLegend0;
            otherLegend = otherLegend0;
            all = all0.clone();
            creatures = creatures0.clone();
            repaint();
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            final Color other = alpha(p.light(), .55);
            header(g2, w, title, creatureLegend, p.accent(), otherLegend, other, false);
            int max = 1;
            for (final int n : all) {
                max = Math.max(max, n);
            }
            final int gap = 10;
            final double colW = (w - gap * 6) / 7.0;
            final int base = getHeight() - 20;
            // the tallest bar ends under the heading, with room for its number above it
            final double barMax = Math.max(8, base - 22 - 18);
            for (int i = 0; i < 7; i++) {
                final double x = i * (colW + gap);
                final double hc = creatures[i] * barMax / max;
                final double ho = (all[i] - creatures[i]) * barMax / max;
                if (hc > 0) {
                    g2.setColor(p.accent());
                    g2.fill(round(x, base - hc, colW, hc, 6));
                }
                if (ho > 0) {
                    g2.setColor(other);
                    g2.fill(round(x, base - hc - ho, colW, ho + (hc > 0 ? 3 : 0), 6));
                    if (hc > 0) {
                        g2.setColor(p.accent());
                        g2.fill(new java.awt.geom.Rectangle2D.Double(x, base - hc, colW, Math.min(hc, 4)));
                    }
                }
                g2.setFont(body(Font.BOLD, 12f));
                g2.setColor(TEXT);
                if (all[i] > 0) {
                    final String n = String.valueOf(all[i]);
                    g2.drawString(n, (float) (x + colW / 2 - g2.getFontMetrics().stringWidth(n) / 2.0), (float) (base - hc - ho - 4));
                }
                g2.setFont(display(12f));
                g2.setColor(MUTED);
                final String label = i == 6 ? "6+" : String.valueOf(i);
                g2.drawString(label, (float) (x + colW / 2 - g2.getFontMetrics().stringWidth(label) / 2.0), base + 15);
            }
            g2.dispose();
        }
    }

    /** The round mark of a piece of advice: a problem, a warning, a hint or all is well. */
    static final class TipIcon extends JComponent {
        private final int level;

        TipIcon(final int level0) {
            level = level0;
            setPreferredSize(new Dimension(24, 24));
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            g2.setColor(level == 0 ? BAD : level == 1 ? WARN : level == 2 ? HINT : OK);
            g2.fill(new Ellipse2D.Double(0, 0, 24, 24));
            g2.setColor(GOLD_INK);
            if (level == 3) {
                g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                final Path2D.Double check = new Path2D.Double();
                check.moveTo(7, 12.5);
                check.lineTo(10.5, 16);
                check.lineTo(17, 8.5);
                g2.draw(check);
            } else {
                HexTownStyle.drawCentered(g2, level == 2 ? "i" : "!", body(Font.BOLD, 14f), GOLD_INK, 12, 12);
            }
            g2.dispose();
        }
    }

    /** One relic slot: a dark box with the relic, or a dashed outline while the slot is empty. */
    static final class RelicBox extends JPanel {
        private final Palette p;
        private final boolean worn;

        RelicBox(final Palette p0, final boolean worn0, final MigLayout layout) {
            super(layout);
            p = p0;
            worn = worn0;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final RoundRectangle2D shape = round(0.5, 0.5, getWidth() - 1, getHeight() - 1, 10);
            if (worn) {
                g2.setColor(new Color(0, 0, 0, 72));
                g2.fill(shape);
                g2.setColor(alpha(p.accent(), .35));
                g2.draw(shape);
            } else {
                g2.setColor(new Color(255, 255, 255, 46));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] { 4f, 4f }, 0f));
                g2.draw(shape);
            }
            g2.dispose();
        }
    }

    /** A soft panel in the deck's colour, for the summary of the relics. */
    static final class TintBox extends JPanel {
        private final Palette p;

        TintBox(final Palette p0, final MigLayout layout) {
            super(layout);
            p = p0;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final RoundRectangle2D shape = round(0.5, 0.5, getWidth() - 1, getHeight() - 1, 10);
            g2.setColor(alpha(p.accent(), .12));
            g2.fill(shape);
            g2.setColor(alpha(p.accent(), .4));
            g2.draw(shape);
            g2.dispose();
        }
    }

    /** Muted text for an empty slot. */
    static final Color EMPTY_SLOT = FAINT;
    /** The ring of a relic's badge. */
    static final Color BADGE_EDGE = GOLD_EDGE;
}
