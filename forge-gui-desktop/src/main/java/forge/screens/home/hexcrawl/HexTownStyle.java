package forge.screens.home.hexcrawl;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.lang3.tuple.Pair;

import forge.ImageCache;
import forge.card.CardRarity;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gui.GuiBase;
import forge.item.PaperCard;
import forge.toolbox.FSkin;
import forge.util.ImageFetcher;
import net.miginfocom.swing.MigLayout;

/**
 * The look of the town window: the colours of the town, fantasy fonts and the parts that paint themselves
 * (banner, sidebar entries, theme pills, gold buttons, card tiles, notices).
 */
final class HexTownStyle {
    static final Color GOLD = new Color(0xe2b850);
    static final Color GOLD_LIGHT = new Color(0xf0cf78);
    static final Color GOLD_DEEP = new Color(0xc9962f);
    static final Color GOLD_EDGE = new Color(0x8a6a2a);
    static final Color GOLD_INK = new Color(0x1b1505);
    static final Color COIN_INK = new Color(0x7a5717);
    static final Color HEADING = new Color(0xc7a76b);
    static final Color TEXT = new Color(0xece6d6);
    static final Color TEXT_BRIGHT = new Color(0xf4ecd8);
    static final Color MUTED = new Color(0xb3ad9f);
    static final Color FAINT = new Color(0x9a9384);
    static final Color BAD = new Color(0xf0a58a);
    static final Color TOO_DEAR = new Color(0x8a8270);
    static final Color WINDOW_EDGE = new Color(0x6b5626);
    static final Color PARCHMENT = new Color(0xe7dcc0);
    static final Color PARCHMENT_RULE = new Color(0xb9a77a);
    static final Color PARCHMENT_INK = new Color(0x2b2116);
    static final Color PARCHMENT_SOFT = new Color(0x5a4a36);
    static final Color WANTED = new Color(0x8a2e1e);
    static final Color REWARD = new Color(0x6b4a10);

    private HexTownStyle() { }

    /** The colours of one town: its accent, the dark cloth of banner and sidebar, panels, light text and the ground. */
    record Palette(Color accent, Color dark, Color mid, Color light, Color ground) { }

    static Palette palette(final HexColor color) {
        switch (color) {
            case WHITE: return palette(0xd9c58a, 0x2a2618, 0x332e1e, 0xf3e8c6, 0x1a1812);
            case BLUE: return palette(0x4f8fd6, 0x13223a, 0x182b45, 0xbdd7f3, 0x10161f);
            case BLACK: return palette(0x9a6bc0, 0x1e1626, 0x271d31, 0xdcc8ec, 0x15111a);
            case RED: return palette(0xd0603f, 0x2c1712, 0x371d16, 0xf3c4b2, 0x1a110e);
            case GREEN: return palette(0x6aa863, 0x16241a, 0x1d2e22, 0xc3e2bb, 0x121a14);
            default: return palette(0xa7a9b4, 0x1e2026, 0x262930, 0xdfe0e6, 0x141518);
        }
    }

    private static Palette palette(final int accent, final int dark, final int mid, final int light, final int ground) {
        return new Palette(new Color(accent), new Color(dark), new Color(mid), new Color(light), new Color(ground));
    }

    static Color alpha(final Color c, final double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * a));
    }

    static Color brighter(final Color c, final double f) {
        return new Color(Math.min(255, (int) (c.getRed() * f)), Math.min(255, (int) (c.getGreen() * f)),
                Math.min(255, (int) (c.getBlue() * f)), c.getAlpha());
    }

    static Color rarityColor(final CardRarity rarity) {
        if (rarity == null) {
            return new Color(0x8b919a);
        }
        switch (rarity) {
            case Uncommon: return new Color(0xc4ccd8);
            case Rare: return GOLD;
            case MythicRare: return new Color(0xe0743a);
            case Special: return new Color(0xb58ad8);
            default: return new Color(0x8b919a);
        }
    }

    // ------------------------------------------------------------------ fonts

    private static Set<String> families;

    /** The first of the given font families this computer has; serif if none. */
    private static String family(final String... wanted) {
        if (families == null) {
            families = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        }
        for (final String name : wanted) {
            if (families.contains(name)) {
                return name;
            }
        }
        return Font.SERIF;
    }

    /** The carved capitals of headings and buttons. */
    static Font display(final float size) {
        return new Font(family("Cinzel", "Trajan Pro", "Palatino Linotype", "Book Antiqua", "Georgia"), Font.BOLD, 1).deriveFont(size);
    }

    /** The town's name in the banner. */
    static Font decorative(final float size) {
        return new Font(family("Cinzel Decorative", "Cinzel", "Trajan Pro", "Palatino Linotype", "Book Antiqua", "Georgia"), Font.BOLD, 1).deriveFont(size);
    }

    static Font body(final int style, final float size) {
        return FSkin.getFont().getBaseFont().deriveFont(style, size);
    }

    /** Letters set a little apart, as on a sign. */
    static Font tracked(final Font font, final float tracking) {
        return font.deriveFont(Map.of(TextAttribute.TRACKING, tracking));
    }

    static String caps(final String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    static JLabel label(final String text, final Font font, final Color color) {
        final JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** Text that wraps at the given width (in real pixels, unlike the widths of Swing's html). */
    static JComponent wrapped(final String text, final Font font, final Color color, final int width) {
        return new Prose(text, font, color, width);
    }

    private static final class Prose extends JComponent {
        private final List<String> lines = new ArrayList<>();
        private final int width;

        Prose(final String text, final Font font, final Color color, final int width0) {
            width = Math.max(40, width0);
            setFont(font);
            setForeground(color);
            final FontMetrics fm = getFontMetrics(font);
            for (final String paragraph : text.trim().split("\n")) {
                StringBuilder line = new StringBuilder();
                for (final String word : paragraph.trim().split(" +")) {
                    if (line.length() > 0 && fm.stringWidth(line + " " + word) > width) {
                        lines.add(line.toString());
                        line = new StringBuilder();
                    }
                    line.append(line.length() > 0 ? " " : "").append(word);
                }
                lines.add(line.toString());
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, lines.size() * getFontMetrics(getFont()).getHeight());
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            g2.setFont(getFont());
            g2.setColor(getForeground());
            final FontMetrics fm = g2.getFontMetrics();
            int y = fm.getAscent();
            for (final String line : lines) {
                g2.drawString(line, 0, y);
                y += fm.getHeight();
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ painting helpers

    static Graphics2D smooth(final Graphics g) {
        final Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g2;
    }

    static RoundRectangle2D round(final double x, final double y, final double w, final double h, final double arc) {
        return new RoundRectangle2D.Double(x, y, w, h, arc, arc);
    }

    /** A faint woven texture in the town's colour, for the cloth of banner and sidebar. */
    static void weave(final Graphics2D g, final int w, final int h, final Color base, final Palette p) {
        g.setColor(base);
        g.fillRect(0, 0, w, h);
        final Shape clip = g.getClip();
        g.clipRect(0, 0, w, h);
        g.setStroke(new BasicStroke(2f));
        g.setColor(alpha(p.accent(), .045));
        for (int x = -h; x < w; x += 9) {
            g.drawLine(x, h, x + h, 0);
        }
        g.setColor(new Color(0, 0, 0, 46));
        for (int x = -h; x < w; x += 9) {
            g.drawLine(x, 0, x + h, h);
        }
        g.setClip(clip);
    }

    static void drawCentered(final Graphics2D g, final String s, final Font font, final Color color, final double cx, final double cy) {
        g.setFont(font);
        g.setColor(color);
        final FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

    static int width(final JComponent c, final String s, final Font font) {
        return s == null || s.isEmpty() ? 0 : c.getFontMetrics(font).stringWidth(s);
    }

    /** A gold coin with a star, the size given, centred on (cx, cy). */
    static void coin(final Graphics2D g, final double cx, final double cy, final double size) {
        final double r = size / 2 - 1;
        g.setColor(GOLD_DEEP);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setStroke(new BasicStroke((float) Math.max(1, size / 18)));
        g.setColor(GOLD_LIGHT);
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        if (size >= 18) {
            final double ri = r * 0.68;
            g.setColor(COIN_INK);
            g.draw(new Ellipse2D.Double(cx - ri, cy - ri, 2 * ri, 2 * ri));
            g.fill(star(cx, cy, r * 0.48, r * 0.2));
        }
    }

    private static Shape star(final double cx, final double cy, final double outer, final double inner) {
        final Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            final double a = -Math.PI / 2 + i * Math.PI / 5;
            final double r = i % 2 == 0 ? outer : inner;
            if (i == 0) {
                p.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
            } else {
                p.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
            }
        }
        p.closePath();
        return p;
    }

    /** The small glyphs of the window, drawn in a box of 24 units like the icons of the design. */
    enum Glyph { NONE, CLOSE, RESTOCK, BELL, LEFT, RIGHT, NECK, BODY, BOOTS }

    static void glyph(final Graphics2D g0, final Glyph glyph, final double x, final double y, final double size, final Color color, final float stroke) {
        if (glyph == Glyph.NONE) {
            return;
        }
        final Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y);
        g.scale(size / 24, size / 24);
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        final Path2D.Double p = new Path2D.Double();
        switch (glyph) {
            case CLOSE:
                p.moveTo(6, 6); p.lineTo(18, 18); p.moveTo(18, 6); p.lineTo(6, 18);
                break;
            case RESTOCK:
                g.draw(new java.awt.geom.Arc2D.Double(4, 4, 16, 16, 15, 285, java.awt.geom.Arc2D.OPEN));
                p.moveTo(20, 4); p.lineTo(20, 11); p.lineTo(13, 11);
                break;
            case BELL:
                g.draw(new java.awt.geom.Arc2D.Double(6, 3, 12, 12, 0, 180, java.awt.geom.Arc2D.OPEN));
                p.moveTo(6, 9); p.lineTo(6, 12); p.lineTo(4, 16); p.lineTo(20, 16); p.lineTo(18, 12); p.lineTo(18, 9);
                g.draw(new java.awt.geom.Arc2D.Double(9, 16, 6, 6, 180, 180, java.awt.geom.Arc2D.OPEN));
                break;
            case LEFT: // a shield
                p.moveTo(12, 3); p.lineTo(19, 6); p.lineTo(19, 12); p.curveTo(19, 16.5, 16, 20, 12, 21);
                p.curveTo(8, 20, 5, 16.5, 5, 12); p.lineTo(5, 6); p.closePath();
                break;
            case RIGHT: // a sword
                p.moveTo(18, 3); p.lineTo(21, 3); p.lineTo(21, 6); p.lineTo(9, 18); p.lineTo(6, 15); p.closePath();
                p.moveTo(5, 13); p.lineTo(11, 19); p.moveTo(7, 17); p.lineTo(3, 21);
                break;
            case NECK: // an amulet
                p.moveTo(6, 3); p.lineTo(12, 12); p.lineTo(18, 3);
                g.draw(new Ellipse2D.Double(8, 12, 8, 8));
                break;
            case BODY: // a breastplate
                p.moveTo(4, 5); p.lineTo(9, 3); p.quadTo(12, 6, 15, 3); p.lineTo(20, 5); p.lineTo(19, 13);
                p.quadTo(18, 19, 12, 21); p.quadTo(6, 19, 5, 13); p.closePath();
                p.moveTo(12, 8); p.lineTo(12, 17);
                break;
            case BOOTS:
                p.moveTo(7, 3); p.lineTo(12, 3); p.lineTo(12, 12); p.lineTo(19, 15); p.lineTo(19, 20); p.lineTo(5, 20); p.lineTo(5, 3); p.closePath();
                break;
            default:
                break;
        }
        g.draw(p);
        g.dispose();
    }

    static Glyph slotGlyph(final String slot) {
        try {
            return Glyph.valueOf(caps(slot));
        } catch (final IllegalArgumentException | NullPointerException e) {
            return Glyph.NECK;
        }
    }

    // ------------------------------------------------------------------ panels

    /** The whole window: the ground of the town with a gold-edged frame. */
    static final class WindowPanel extends JPanel {
        private final Palette p;

        WindowPanel(final Palette p0, final MigLayout layout) {
            super(layout);
            p = p0;
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            g2.setColor(p.ground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }

        @Override
        protected void paintChildren(final Graphics g) {
            super.paintChildren(g);
            final Graphics2D g2 = smooth(g);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(WINDOW_EDGE);
            g2.draw(round(1, 1, getWidth() - 2, getHeight() - 2, 16));
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(alpha(GOLD, .35));
            g2.draw(round(5.5, 5.5, getWidth() - 11, getHeight() - 11, 12));
            g2.dispose();
        }
    }

    /** The banner of the town: dark woven cloth, an accent seam and the gold filigree under it. */
    static final class Banner extends JPanel {
        static final int CLOTH = 84;
        private final Palette p;

        Banner(final Palette p0, final MigLayout layout) {
            super(layout);
            p = p0;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            weave(g2, getWidth(), CLOTH, p.dark(), p);
            g2.setColor(p.accent());
            g2.fillRect(0, CLOTH, getWidth(), 3);
            // the filigree: two lines, two leaves and a diamond, in gold over the seam
            final double cx = getWidth() / 2.0;
            final double cy = CLOTH + 1.5;
            g2.setColor(p.ground());
            g2.fill(new Rectangle2D.Double(cx - 34, cy - 7, 68, 14));
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Line2D.Double(cx - 120, cy, cx - 32, cy));
            g2.draw(new java.awt.geom.Line2D.Double(cx + 32, cy, cx + 120, cy));
            for (final int side : new int[] { -1, 1 }) {
                final Path2D.Double leaf = new Path2D.Double();
                leaf.moveTo(cx + side * 32, cy);
                leaf.curveTo(cx + side * 24, cy - 7, cx + side * 16, cy - 7, cx + side * 10, cy);
                leaf.curveTo(cx + side * 16, cy + 7, cx + side * 24, cy + 7, cx + side * 32, cy);
                g2.draw(leaf);
            }
            final Path2D.Double diamond = new Path2D.Double();
            diamond.moveTo(cx, cy - 6);
            diamond.lineTo(cx + 6, cy);
            diamond.lineTo(cx, cy + 6);
            diamond.lineTo(cx - 6, cy);
            diamond.closePath();
            g2.fill(diamond);
            g2.dispose();
        }
    }

    /** The coat of arms of the town: a shield in its colours with a crest for its land. */
    static final class Shield extends JComponent {
        private final Palette p;
        private final HexColor color;

        Shield(final Palette p0, final HexColor color0) {
            p = p0;
            color = color0;
            setPreferredSize(new Dimension(46, 54));
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final Path2D.Double outer = new Path2D.Double();
            outer.moveTo(23, 2); outer.lineTo(42, 8); outer.lineTo(42, 24);
            outer.curveTo(42, 37, 34, 46, 23, 52); outer.curveTo(12, 46, 4, 37, 4, 24); outer.lineTo(4, 8); outer.closePath();
            g2.setColor(p.dark());
            g2.fill(outer);
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(outer);
            final Path2D.Double inner = new Path2D.Double();
            inner.moveTo(23, 7); inner.lineTo(37, 11.5); inner.lineTo(37, 24);
            inner.curveTo(37, 34, 31, 41, 23, 45.5); inner.curveTo(15, 41, 9, 34, 9, 24); inner.lineTo(9, 11.5); inner.closePath();
            g2.setColor(alpha(p.accent(), .85));
            g2.fill(inner);
            g2.setColor(GOLD_INK);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            final Path2D.Double crest = new Path2D.Double();
            switch (color) {
                case GREEN: // a tree
                    crest.moveTo(23, 16); crest.lineTo(23, 34); crest.moveTo(17, 22); crest.lineTo(23, 16); crest.lineTo(29, 22);
                    crest.moveTo(15, 30); crest.lineTo(31, 30);
                    break;
                case WHITE: // a sword of light
                    crest.moveTo(23, 14); crest.lineTo(23, 34); crest.moveTo(16, 21); crest.lineTo(30, 21); crest.moveTo(18, 34); crest.lineTo(28, 34);
                    break;
                case BLUE: // waves
                    for (final int y : new int[] { 22, 29 }) {
                        crest.moveTo(15, y); crest.curveTo(18, y - 3, 20, y - 3, 23, y); crest.curveTo(26, y + 3, 28, y + 3, 31, y);
                    }
                    break;
                case BLACK: // a drop
                    crest.moveTo(17, 20); crest.curveTo(17, 16, 20, 14, 23, 14); crest.curveTo(26, 14, 29, 16, 29, 20);
                    crest.curveTo(29, 24, 26, 25, 23, 29); crest.curveTo(20, 25, 17, 24, 17, 20); crest.closePath();
                    break;
                case RED: // a mountain
                    crest.moveTo(14, 32); crest.lineTo(23, 16); crest.lineTo(32, 32); crest.closePath();
                    crest.moveTo(19, 32); crest.lineTo(23, 25); crest.lineTo(27, 32);
                    break;
                default: // a cog
                    crest.append(new Ellipse2D.Double(17, 18, 12, 12), false);
                    crest.moveTo(23, 13); crest.lineTo(23, 16); crest.moveTo(23, 32); crest.lineTo(23, 35);
                    crest.moveTo(14, 24); crest.lineTo(17, 24); crest.moveTo(29, 24); crest.lineTo(32, 24);
                    break;
            }
            g2.draw(crest);
            g2.dispose();
        }
    }

    /** A label with a dark shadow under its letters, for the town's name. */
    static final class ShadowLabel extends JComponent {
        private String text;

        ShadowLabel(final String text0, final Font font, final Color color) {
            text = text0;
            setFont(font);
            setForeground(color);
        }

        void setText(final String text0) {
            text = text0;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            final FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(text) + 4, fm.getHeight() + 2);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            g2.setFont(getFont());
            final int base = g2.getFontMetrics().getAscent();
            g2.setColor(new Color(0, 0, 0, 128));
            g2.drawString(text, 0, base + 2);
            g2.setColor(getForeground());
            g2.drawString(text, 0, base);
            g2.dispose();
        }
    }

    /** The sidebar: the same cloth as the banner, a little lighter, with an accent edge. */
    static final class Sidebar extends JPanel {
        private final Palette p;

        Sidebar(final Palette p0, final MigLayout layout) {
            super(layout);
            p = p0;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            weave(g2, getWidth(), getHeight(), alpha(p.dark(), .85), p);
            g2.setColor(alpha(p.accent(), .3));
            g2.fillRect(getWidth() - 1, 0, 1, getHeight());
            g2.dispose();
        }
    }

    /** A panel with a double gold edge, for the preview and the relics. */
    static final class GildedPanel extends JPanel {
        private final Palette p;

        GildedPanel(final Palette p0, final MigLayout layout) {
            super(layout);
            p = p0;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            final int h = getHeight();
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fill(round(2, 6, w - 4, h - 6, 14));
            g2.setColor(alpha(p.mid(), .95));
            g2.fill(round(0, 0, w - 1, h - 4, 12));
            g2.setColor(alpha(p.accent(), .35));
            g2.draw(round(0.5, 0.5, w - 2, h - 5, 12));
            g2.setColor(alpha(GOLD, .35));
            g2.draw(round(5.5, 5.5, w - 12, h - 15, 8));
            g2.dispose();
        }
    }

    /** A notice nailed to the board: parchment with a ruled edge, set a little askew. */
    static final class Notice extends JPanel {
        Notice(final MigLayout layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            final int h = getHeight();
            g2.rotate(Math.toRadians(-0.4), w / 2.0, h / 2.0);
            g2.setColor(new Color(0, 0, 0, 50));
            g2.fill(round(4, 9, w - 8, h - 10, 8));
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fill(round(3, 7, w - 6, h - 9, 8));
            g2.setColor(PARCHMENT);
            g2.fill(round(3, 3, w - 6, h - 10, 6));
            g2.setColor(PARCHMENT_RULE);
            g2.draw(round(3.5, 3.5, w - 7, h - 11, 6));
            g2.draw(round(9.5, 9.5, w - 19, h - 23, 3));
            g2.dispose();
        }
    }

    /** A round badge with a glyph: the relics' and the notices' emblems. */
    static final class Badge extends JComponent {
        private final Glyph glyph;
        private final Color fill;
        private final Color edge;
        private final Color ink;
        private final float edgeWidth;

        Badge(final Glyph glyph0, final int size, final Color fill0, final Color edge0, final float edgeWidth0, final Color ink0) {
            glyph = glyph0;
            fill = fill0;
            edge = edge0;
            edgeWidth = edgeWidth0;
            ink = ink0;
            setPreferredSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final double s = Math.min(getWidth(), getHeight()) - edgeWidth - 1;
            final double o = (edgeWidth + 1) / 2;
            g2.setColor(fill);
            g2.fill(new Ellipse2D.Double(o, o, s, s));
            g2.setColor(new Color(0, 0, 0, 100));
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new Ellipse2D.Double(o + edgeWidth + 1, o + edgeWidth + 1, s - 2 * edgeWidth - 2, s - 2 * edgeWidth - 2));
            g2.setColor(edge);
            g2.setStroke(new BasicStroke(edgeWidth));
            g2.draw(new Ellipse2D.Double(o, o, s, s));
            final double gs = s * 0.5;
            glyph(g2, glyph, o + (s - gs) / 2, o + (s - gs) / 2, gs, ink, 1.6f);
            g2.dispose();
        }
    }

    /** The purse of the head shown: a coin and the gold. */
    static final class GoldChip extends JComponent {
        private String amount = "0";
        private final String word;

        GoldChip(final String word0) {
            word = word0;
        }

        void setAmount(final int gold) {
            amount = String.valueOf(gold);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(8 + 28 + 9 + width(this, amount, display(20f)) + 7 + width(this, word, body(Font.PLAIN, 12f)) + 16, 44);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth();
            final int h = getHeight();
            g2.setColor(new Color(0, 0, 0, 102));
            g2.fill(round(0.5, 0.5, w - 1, h - 1, h));
            g2.setColor(GOLD_EDGE);
            g2.draw(round(0.5, 0.5, w - 1, h - 1, h));
            coin(g2, 8 + 14, h / 2.0, 28);
            int x = 8 + 28 + 9;
            g2.setFont(display(20f));
            g2.setColor(GOLD_LIGHT);
            final FontMetrics fm = g2.getFontMetrics();
            final int base = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(amount, x, base);
            x += fm.stringWidth(amount) + 7;
            g2.setFont(body(Font.PLAIN, 12f));
            g2.setColor(new Color(0xc7b98f));
            g2.drawString(word, x, base);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ buttons

    /** A button that paints itself; hovering lights it up a little. */
    abstract static class PaintedButton extends JButton {
        protected boolean hover;
        protected int pressX;

        PaintedButton(final String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(final MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(final MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(final MouseEvent e) {
                    pressX = e.getX();
                }
            });
        }

        @Override
        protected final void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            if (!isEnabled()) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .45f));
            }
            paintButton(g2, getWidth(), getHeight(), hover && isEnabled());
            g2.dispose();
        }

        protected abstract void paintButton(Graphics2D g, int w, int h, boolean lit);
    }

    /** The buttons of the window: gold to pay, an outline for the rest, red-brown on a notice. */
    static final class GoldButton extends PaintedButton {
        enum Kind { GOLD, OUTLINE, NOTICE }

        private final Kind kind;
        private final Font font;
        private Glyph glyph = Glyph.NONE;
        private String suffix = "";
        /** Greyed but still clickable: the head cannot pay for it. */
        private boolean dear;
        /** Greyed and done: equipped, taken. */
        private boolean spent;

        GoldButton(final String text, final Kind kind0, final float fontSize) {
            super(text);
            kind = kind0;
            font = tracked(display(fontSize), 0.06f);
        }

        GoldButton withGlyph(final Glyph glyph0) {
            glyph = glyph0;
            return this;
        }

        void setSuffix(final String suffix0) {
            suffix = suffix0 == null ? "" : suffix0;
            repaint();
        }

        void setDear(final boolean dear0) {
            dear = dear0;
            repaint();
        }

        void setSpent(final boolean spent0) {
            spent = spent0;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            final String text = caps(getText());
            final int w = width(this, text, font) + (suffix.isEmpty() ? 0 : 8 + width(this, suffix, font))
                    + (glyph == Glyph.NONE ? 0 : 24) + 32;
            return new Dimension(Math.max(w, 40), 40);
        }

        @Override
        protected void paintButton(final Graphics2D g, final int w, final int h, final boolean lit) {
            final double arc = kind == Kind.NOTICE ? 6 : Math.min(12, h / 3.5);
            final RoundRectangle2D shape = round(0.5, 0.5, w - 1, h - 1, arc);
            Color ink;
            switch (kind) {
                case GOLD:
                    if (spent) {
                        g.setColor(new Color(0x5b6270));
                        g.fill(shape);
                    } else if (dear) {
                        g.setColor(TOO_DEAR);
                        g.fill(shape);
                    } else {
                        final double f = lit ? 1.1 : 1;
                        g.setPaint(new GradientPaint(0, 0, brighter(GOLD_LIGHT, f), 0, h, brighter(GOLD_DEEP, f)));
                        g.fill(shape);
                        g.setColor(new Color(255, 255, 255, 100));
                        g.drawLine((int) arc / 2, 2, w - (int) arc / 2, 2);
                    }
                    g.setColor(GOLD_LIGHT);
                    g.draw(shape);
                    ink = GOLD_INK;
                    break;
                case NOTICE:
                    if (!spent) {
                        g.setColor(lit ? new Color(0x8e4525) : new Color(0x7a3a1e));
                        g.fill(shape);
                    }
                    g.setColor(REWARD);
                    g.draw(shape);
                    ink = spent ? new Color(0x7a6a52) : TEXT_BRIGHT;
                    break;
                default:
                    g.setColor(lit ? new Color(0, 0, 0, 130) : new Color(0, 0, 0, 77));
                    g.fill(shape);
                    g.setColor(lit ? GOLD : GOLD_EDGE);
                    g.draw(shape);
                    ink = new Color(0xe8e0cc);
                    break;
            }
            final String text = caps(getText());
            g.setFont(font);
            final FontMetrics fm = g.getFontMetrics();
            final int textW = fm.stringWidth(text) + (suffix.isEmpty() ? 0 : 8 + fm.stringWidth(suffix));
            final int glyphW = glyph == Glyph.NONE ? 0 : (text.isEmpty() ? 18 : 24);
            int x = (w - textW - glyphW) / 2;
            if (glyph != Glyph.NONE) {
                glyph(g, glyph, x, (h - 18) / 2.0, 18, kind == Kind.OUTLINE ? GOLD_LIGHT : ink, 2f);
                x += glyphW;
            }
            final int base = (h + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(ink);
            g.drawString(text, x, base);
            if (!suffix.isEmpty()) {
                g.setColor(kind == Kind.OUTLINE ? GOLD_LIGHT : ink);
                g.drawString(suffix, x + fm.stringWidth(text) + 8, base);
            }
        }
    }

    /** An entry of the sidebar: a gem, the name and a count; the shop shown is lit in the town's colour. */
    static final class NavItem extends PaintedButton {
        private final Palette p;
        private final Color gem;
        private String count = "";
        private boolean active;

        NavItem(final String text, final Palette p0, final Color gem0) {
            super(text);
            p = p0;
            gem = gem0;
        }

        void setActive(final boolean active0) {
            active = active0;
            repaint();
        }

        void setCount(final String count0) {
            if (!count.equals(count0)) {
                count = count0;
                repaint();
            }
        }

        @Override
        protected void paintButton(final Graphics2D g, final int w, final int h, final boolean lit) {
            final RoundRectangle2D shape = round(0.5, 0.5, w - 1, h - 1, 8);
            if (active) {
                g.setColor(alpha(p.accent(), .16));
                g.fill(shape);
                g.setColor(alpha(p.accent(), .55));
                g.draw(shape);
            } else if (lit) {
                g.setColor(new Color(255, 255, 255, 13));
                g.fill(shape);
            }
            final double cx = 12 + 6.5;
            final double cy = h / 2.0;
            final Path2D.Double diamond = new Path2D.Double();
            diamond.moveTo(cx, cy - 6.5);
            diamond.lineTo(cx + 6.5, cy);
            diamond.lineTo(cx, cy + 6.5);
            diamond.lineTo(cx - 6.5, cy);
            diamond.closePath();
            if (active) {
                for (int i = 3; i >= 1; i--) {
                    g.setColor(alpha(gem, .12));
                    g.setStroke(new BasicStroke(i * 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(diamond);
                }
            }
            g.setColor(new Color(0, 0, 0, 102));
            g.setStroke(new BasicStroke(3f));
            g.draw(diamond);
            g.setColor(active ? gem : alpha(gem, .5));
            g.fill(diamond);
            final Font font = body(active ? Font.BOLD : Font.PLAIN, 14f);
            g.setFont(font);
            final FontMetrics fm = g.getFontMetrics();
            final int base = (h + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(active ? new Color(0xf8f2e2) : new Color(0xcbc5b6));
            g.drawString(getText(), 12 + 13 + 10, base);
            if (!count.isEmpty()) {
                g.setFont(body(Font.PLAIN, 12f));
                g.setColor(FAINT);
                g.drawString(count, w - 12 - g.getFontMetrics().stringWidth(count), base);
            }
        }
    }

    /** A theme tab: its kind in small gold capitals over the theme; the tab shown is lit and underlined. */
    static final class ThemePill extends PaintedButton {
        private final Palette p;
        private final String kind;
        private boolean active;
        private boolean arrow;

        ThemePill(final String kind0, final Palette p0) {
            super("");
            kind = caps(kind0);
            p = p0;
        }

        void set(final String label, final boolean active0, final boolean arrow0) {
            setText(label);
            active = active0;
            arrow = arrow0;
            revalidate();
            repaint();
        }

        /** Whether the last press hit the arrow, which opens the list of themes. */
        boolean pressedArrow() {
            return arrow && pressX >= getWidth() - 28;
        }

        @Override
        public Dimension getPreferredSize() {
            final int w = Math.max(width(this, kind, tracked(display(10.5f), 0.12f)), width(this, getText(), body(Font.BOLD, 14f)) + (arrow ? 18 : 0));
            return new Dimension(w + 32, 46);
        }

        @Override
        protected void paintButton(final Graphics2D g, final int w, final int h, final boolean lit) {
            final RoundRectangle2D shape = round(0.5, 0.5, w - 1, h - 1, 10);
            g.setColor(active ? alpha(p.accent(), lit ? .3 : .22) : new Color(0, 0, 0, lit ? 90 : 64));
            g.fill(shape);
            if (active) {
                final Shape clip = g.getClip();
                g.clip(shape);
                g.setColor(p.accent());
                g.fillRect(0, h - 3, w, 3);
                g.setClip(clip);
            }
            g.setColor(active ? p.accent() : alpha(GOLD, lit ? .45 : .25));
            g.draw(shape);
            g.setFont(tracked(display(10.5f), 0.12f));
            g.setColor(HEADING);
            g.drawString(kind, 16, 18);
            g.setFont(body(Font.BOLD, 14f));
            g.setColor(new Color(0xf2ead6));
            g.drawString(getText(), 16, 36);
            if (arrow) { // a small triangle pointing down: the list of themes
                final double x = 16 + g.getFontMetrics().stringWidth(getText()) + 9;
                final Path2D.Double tri = new Path2D.Double();
                tri.moveTo(x, 28.5);
                tri.lineTo(x + 9, 28.5);
                tri.lineTo(x + 4.5, 33.5);
                tri.closePath();
                g.setColor(GOLD_LIGHT);
                g.fill(tri);
            }
        }
    }

    /** One of a group of pills (heads, team mates): the chosen one is gold. */
    static final class Pill extends PaintedButton {
        private boolean active;

        Pill(final String text) {
            super(text);
        }

        void setActive(final boolean active0) {
            active = active0;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width(this, getText(), body(Font.BOLD, 14f)) + 32, 36);
        }

        @Override
        protected void paintButton(final Graphics2D g, final int w, final int h, final boolean lit) {
            if (active) {
                g.setColor(GOLD);
                g.fill(round(0, 0, w, h, h));
            } else if (lit) {
                g.setColor(new Color(255, 255, 255, 20));
                g.fill(round(0, 0, w, h, h));
            }
            drawCentered(g, getText(), body(Font.BOLD, 14f), active ? GOLD_INK : new Color(0xe8e0cc), w / 2.0, h / 2.0);
        }
    }

    /** The dark rounded tray the pills sit in. */
    static final class PillTray extends JPanel {
        PillTray() {
            super(new MigLayout("insets 4, gap 4"));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int h = getHeight();
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(round(0.5, 0.5, getWidth() - 1, h - 1, h));
            g2.setColor(alpha(GOLD, .35));
            g2.draw(round(0.5, 0.5, getWidth() - 1, h - 1, h));
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ cards

    /** The picture of a card, loaded (and fetched if missing) like the other card pictures of Forge, and scaled smoothly. */
    static final class CardImage implements ImageFetcher.Callback {
        private final PaperCard card;
        private final JComponent owner;
        private BufferedImage original;
        private BufferedImage scaled;

        CardImage(final PaperCard card0, final JComponent owner0) {
            card = card0;
            owner = owner0;
            load(true);
        }

        PaperCard card() {
            return card;
        }

        private void load(final boolean fetch) {
            final Pair<BufferedImage, Boolean> info = ImageCache.getCardOriginalImageInfo(card.getImageKey(false), true);
            original = info.getLeft();
            scaled = null;
            if (fetch && (ImageCache.isDefaultImage(original) || Boolean.TRUE.equals(info.getRight()))) {
                GuiBase.getInterface().getImageFetcher().fetchImage(card.getImageKey(false), this);
            }
        }

        @Override
        public void onImageFetched() {
            load(false);
            owner.repaint();
        }

        /** Draws the picture into the box, with rounded corners. */
        void paint(final Graphics2D g, final double x, final double y, final double w, final double h, final double arc) {
            if (original == null) {
                return;
            }
            final double scale = g.getTransform().getScaleX();
            final int pw = (int) Math.round(w * scale);
            final int ph = (int) Math.round(h * scale);
            if (scaled == null || scaled.getWidth() != pw || scaled.getHeight() != ph) {
                scaled = scale(original, pw, ph);
            }
            final Shape clip = g.getClip();
            g.clip(round(x, y, w, h, arc));
            g.drawImage(scaled, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
            g.setClip(clip);
        }

        /** Halves the picture step by step before the last step, so that small pictures stay sharp. */
        private static BufferedImage scale(final BufferedImage src, final int w, final int h) {
            BufferedImage img = src;
            int cw = src.getWidth();
            int ch = src.getHeight();
            do {
                cw = Math.max(w, cw / 2);
                ch = Math.max(h, ch / 2);
                final BufferedImage step = new BufferedImage(Math.max(1, cw), Math.max(1, ch), BufferedImage.TYPE_INT_ARGB);
                final Graphics2D g = step.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(img, 0, 0, step.getWidth(), step.getHeight(), null);
                g.dispose();
                img = step;
            } while (cw > w || ch > h);
            return img;
        }
    }

    /** Paints a card face: picture, rarity edge, and the name on a dark plate while there is no picture. */
    static void paintFace(final Graphics2D g, final CardImage image, final Color edge, final double x, final double y,
            final double w, final double h, final double arc) {
        g.setColor(new Color(0x22262c));
        g.fill(round(x, y, w, h, arc));
        if (image != null) {
            image.paint(g, x, y, w, h, arc);
        }
        g.setStroke(new BasicStroke(2f));
        g.setColor(edge);
        g.draw(round(x + 1, y + 1, w - 2, h - 2, arc));
    }

    /** A big card face, for the preview. */
    static final class CardFace extends JComponent {
        private CardImage image;
        private Color edge = GOLD_EDGE;

        void setCard(final PaperCard card) {
            if (card == null) {
                image = null;
            } else if (image == null || image.card() != card) {
                image = new CardImage(card, this);
                edge = rarityColor(card.getRarity());
            }
            repaint();
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            if (image != null) {
                paintFace(g2, image, edge, 0, 0, getWidth(), getHeight(), 14);
            } else {
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fill(round(0, 0, getWidth(), getHeight(), 14));
            }
            g2.dispose();
        }
    }

    /** What a card tile does when it is pointed at or clicked. */
    interface TileListener {
        void hovered();
        void clicked();
    }

    /** Base of the card tiles: hovering lifts the tile, a click acts. */
    abstract static class Tile extends JComponent {
        protected final CardImage image;
        protected boolean hover;
        protected boolean faded;

        Tile(final PaperCard card, final TileListener listener) {
            image = card == null ? null : new CardImage(card, this);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(final MouseEvent e) {
                    hover = true;
                    repaint();
                    listener.hovered();
                }

                @Override
                public void mouseExited(final MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        listener.clicked();
                    }
                }
            });
        }

        @Override
        protected final void paintComponent(final Graphics g) {
            final Graphics2D g2 = smooth(g);
            if (faded) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .45f));
            }
            paintTile(g2, getWidth(), getHeight());
            g2.dispose();
        }

        protected abstract void paintTile(Graphics2D g, int w, int h);
    }

    /** A card on offer: the card face (nothing covers it), and under it the price and how many the deck has. */
    static final class ShopTile extends Tile {
        static final int LIFT = 5;
        static final int STRIP = 30;
        private final Color edge;
        private final String price;
        private final String inDeck;

        ShopTile(final PaperCard card, final int w, final String price0, final String inDeck0, final boolean dear, final TileListener listener) {
            super(card, listener);
            edge = rarityColor(card == null ? null : card.getRarity());
            price = price0;
            inDeck = inDeck0;
            faded = dear;
            final int faceH = (int) Math.round(w * 88 / 63.0);
            setPreferredSize(new Dimension(w, LIFT + faceH + 8 + STRIP));
        }

        @Override
        protected void paintTile(final Graphics2D g, final int w, final int h) {
            final int faceH = h - LIFT - 8 - STRIP;
            final int top = hover ? 0 : LIFT;
            if (hover) {
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(round(3, top + 10, w - 6, faceH, 14));
                g.setColor(new Color(0, 0, 0, 50));
                g.fill(round(1, top + 6, w - 2, faceH, 14));
            }
            paintFace(g, image, edge, 0, top, w, faceH, 12);
            if (hover) {
                g.setStroke(new BasicStroke(1f));
                g.setColor(alpha(GOLD, .55));
                g.draw(round(-0.5 + 1, top + 0.5, w - 2, faceH - 1, 12));
            }
            // the strip under the face: a gold price pill on the left, the copies in the deck on the right
            final int sy = LIFT + faceH + 8;
            final Font priceFont = display(13f);
            g.setFont(priceFont);
            final FontMetrics fm = g.getFontMetrics();
            final int pw = 11 + 13 + 5 + fm.stringWidth(price) + 11;
            final RoundRectangle2D pill = round(0.5, sy + 2.5, pw, STRIP - 5, STRIP - 5);
            if (faded) {
                g.setColor(TOO_DEAR);
            } else {
                g.setPaint(new GradientPaint(0, sy, GOLD_LIGHT, 0, sy + STRIP, GOLD_DEEP));
            }
            g.fill(pill);
            g.setColor(GOLD_LIGHT);
            g.draw(pill);
            g.setColor(COIN_INK);
            g.fill(new Ellipse2D.Double(11, sy + STRIP / 2.0 - 5.5, 11, 11));
            g.setColor(GOLD_INK);
            g.drawString(price, 11 + 13 + 5, sy + (STRIP + fm.getAscent() - fm.getDescent()) / 2);
            if (inDeck != null) {
                final Font f = body(Font.BOLD, 11f);
                g.setFont(f);
                final FontMetrics fm2 = g.getFontMetrics();
                final int cw = fm2.stringWidth(inDeck) + 18;
                final RoundRectangle2D chip = round(w - cw - 0.5, sy + 4.5, cw, STRIP - 9, STRIP - 9);
                g.setColor(new Color(0, 0, 0, 102));
                g.fill(chip);
                g.setColor(new Color(255, 255, 255, 36));
                g.draw(chip);
                g.setColor(new Color(0xe6e0d2));
                g.drawString(inDeck, (float) (w - cw + 9 - 0.5), sy + (STRIP + fm2.getAscent() - fm2.getDescent()) / 2f);
            }
        }
    }

    /** A card of the own deck at the counter (to sell or to give): face, name, copies and what it brings. */
    static final class CounterTile extends Tile {
        private final Palette p;
        private final String name;
        private final String count;
        private final String price;

        CounterTile(final PaperCard card, final String name0, final int w, final Palette p0, final String count0, final String price0,
                final TileListener listener) {
            super(card, listener);
            p = p0;
            name = name0;
            count = count0;
            price = price0;
            final int faceH = (int) Math.round((w - 16) * 88 / 63.0);
            setPreferredSize(new Dimension(w, 3 + 8 + faceH + 6 + 18 + 4 + 16 + 8));
        }

        @Override
        protected void paintTile(final Graphics2D g, final int w, final int h) {
            final int top = hover ? 0 : 3;
            final int ph = h - 3;
            final RoundRectangle2D shape = round(0.5, top + 0.5, w - 1, ph - 1, 12);
            g.setColor(p.mid());
            g.fill(shape);
            g.setColor(hover ? p.accent() : alpha(p.accent(), .35));
            g.draw(shape);
            final int faceW = w - 16;
            final int faceH = (int) Math.round(faceW * 88 / 63.0);
            paintFace(g, image, rarityColor(image == null ? null : image.card().getRarity()), 8, top + 8, faceW, faceH, 8);
            int y = top + 8 + faceH + 6;
            g.setFont(body(Font.BOLD, 12f));
            g.setColor(new Color(0xece8df));
            final FontMetrics fm = g.getFontMetrics();
            String shown = name;
            if (fm.stringWidth(shown) > w - 16) {
                while (shown.length() > 1 && fm.stringWidth(shown + "…") > w - 16) {
                    shown = shown.substring(0, shown.length() - 1);
                }
                shown += "…";
            }
            g.drawString(shown, 8, y + fm.getAscent());
            y += 18 + 4;
            g.setFont(body(Font.PLAIN, 11f));
            g.setColor(MUTED);
            g.drawString(count, 8, y + g.getFontMetrics().getAscent());
            if (price != null) {
                g.setFont(body(Font.BOLD, 13f));
                g.setColor(GOLD_LIGHT);
                g.drawString(price, w - 8 - g.getFontMetrics().stringWidth(price), y + g.getFontMetrics().getAscent() - 1);
            }
        }
    }
}
