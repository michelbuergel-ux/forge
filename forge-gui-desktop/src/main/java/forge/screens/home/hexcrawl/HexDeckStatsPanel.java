package forge.screens.home.hexcrawl;

import forge.gamemodes.hexcrawl.HexDeckStats;
import forge.toolbox.FSkin;
import forge.util.Localizer;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

/** The deck's mana curve as bars, with card types and colours next to it. */
class HexDeckStatsPanel extends JComponent {
    private static final String[] CURVE_LABELS = { "0", "1", "2", "3", "4", "5", "6+" };
    private static final String[] COLOR_SYMBOLS = { "W", "U", "B", "R", "G" };
    private static final Color[] COLOR_SHADES = {
            new Color(0xE8E2C4), new Color(0x4F8BD6), new Color(0x8A7F8C), new Color(0xD9533C), new Color(0x4FA65B) };
    private static final Color BAR = new Color(0xC9A227);

    private HexDeckStats stats;

    void setStats(final HexDeckStats stats0) {
        stats = stats0;
        repaint();
    }

    @Override
    protected void paintComponent(final Graphics g0) {
        if (stats == null) {
            return;
        }
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        final Color text = FSkin.getColor(FSkin.Colors.CLR_TEXT).getColor();
        final Font font = FSkin.getFont(12).getBaseFont();
        g.setFont(font);
        final FontMetrics fm = g.getFontMetrics();
        final Localizer loc = Localizer.getInstance();

        // mana curve
        final int chartWidth = 300;
        final int top = 4;
        final int bottom = getHeight() - fm.getHeight() - 2;
        int max = 1;
        for (final int n : stats.curve) {
            max = Math.max(max, n);
        }
        final int slot = chartWidth / CURVE_LABELS.length;
        for (int i = 0; i < CURVE_LABELS.length; i++) {
            final int h = (bottom - top - fm.getHeight()) * stats.curve[i] / max;
            final int x = i * slot + 6;
            g.setColor(BAR);
            g.fillRect(x, bottom - h, slot - 12, h);
            g.setColor(text);
            final String count = String.valueOf(stats.curve[i]);
            g.drawString(count, x + (slot - 12 - fm.stringWidth(count)) / 2, bottom - h - 2);
            g.drawString(CURVE_LABELS[i], x + (slot - 12 - fm.stringWidth(CURVE_LABELS[i])) / 2, bottom + fm.getAscent());
        }

        // types, average mana value and colours
        int x = chartWidth + 30;
        int y = top + fm.getAscent();
        g.setColor(text);
        g.drawString(loc.getMessage("lblHexStatsTypes", String.valueOf(stats.lands), String.valueOf(stats.creatures),
                String.valueOf(stats.otherSpells)), x, y);
        y += fm.getHeight() + 4;
        g.drawString(loc.getMessage("lblHexStatsAverage", String.format(Locale.ROOT, "%.2f", stats.averageManaValue())), x, y);
        y += fm.getHeight() + 8;
        for (int i = 0; i < COLOR_SYMBOLS.length; i++) {
            if (stats.colors[i] == 0) {
                continue;
            }
            g.setColor(COLOR_SHADES[i]);
            g.fillOval(x, y - fm.getAscent() + 1, fm.getAscent(), fm.getAscent());
            g.setColor(text);
            final String label = COLOR_SYMBOLS[i] + " " + stats.colors[i];
            g.drawString(label, x + fm.getAscent() + 4, y);
            x += fm.getAscent() + fm.stringWidth(label) + 16;
        }
        g.dispose();
    }
}
