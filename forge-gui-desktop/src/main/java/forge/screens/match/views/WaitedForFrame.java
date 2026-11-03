package forge.screens.match.views;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

/** Team play and Hex Campaign fights: the pulsing amber frame and hourglass on the avatar of a player the game waits for. */
final class WaitedForFrame {
    private static final Color WAIT_AMBER = new Color(255, 190, 40);

    private WaitedForFrame() { }

    /** Draws the frame around the avatar's bounds r. */
    static void paint(final Graphics g0, final Rectangle r) {
        final java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        // the frame breathes, so the eye finds it
        final double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 260.0);
        for (int i = 0; i < 4; i++) {
            final int alpha = (int) Math.round((150 + 105 * pulse) * (1 - i * 0.22));
            g.setColor(new Color(WAIT_AMBER.getRed(), WAIT_AMBER.getGreen(), WAIT_AMBER.getBlue(), Math.max(0, alpha)));
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawRoundRect(r.x + i, r.y + i, r.width - 1 - 2 * i, r.height - 1 - 2 * i, 8, 8);
        }
        // an hourglass in the top right corner
        final int s = Math.max(12, Math.min(r.width, r.height) / 4);
        final int bx = r.x + r.width - s - 4;
        final int by = r.y + 4;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillOval(bx - 3, by - 3, s + 6, s + 6);
        g.setColor(WAIT_AMBER);
        final java.awt.geom.Path2D glass = new java.awt.geom.Path2D.Double();
        glass.moveTo(bx + s * 0.22, by + s * 0.15);
        glass.lineTo(bx + s * 0.78, by + s * 0.15);
        glass.lineTo(bx + s * 0.5, by + s * 0.5);
        glass.lineTo(bx + s * 0.78, by + s * 0.85);
        glass.lineTo(bx + s * 0.22, by + s * 0.85);
        glass.lineTo(bx + s * 0.5, by + s * 0.5);
        glass.closePath();
        g.fill(glass);
        g.dispose();
    }
}
