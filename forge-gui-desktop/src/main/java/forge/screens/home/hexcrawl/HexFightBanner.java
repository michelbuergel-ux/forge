package forge.screens.home.hexcrawl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.ToolTipManager;

import forge.gamemodes.hexcrawl.HexFightInfo;
import forge.game.card.CardView;
import forge.util.Localizer;

/**
 * The strip above the match screen in a campaign fight: the enemy and what kind of fight it is, the life bar of
 * the enemy team (with a boss's half-life mark, where it wakes), the rules of the fight as pills, and on the
 * right what the team mates did last. Pointing at a team mate's action shows its card.
 */
@SuppressWarnings("serial")
public class HexFightBanner extends JPanel {
    public static final int HEIGHT = 58;
    /** How long a team mate's action stays in the strip. */
    private static final long ACTION_MILLIS = 20000;
    private static final int ACTIONS = 3;
    private static final int LOG_W = 360;

    private static final Color GOLD = new Color(0xE8C04A);
    private static final Color GOLD_LIGHT = new Color(0xFFE08A);
    private static final Color TEXT = new Color(0xECE6D6);
    private static final Color MUTED = new Color(0xA89F8C);

    private final HexFightInfo info;
    private final Consumer<CardView> showCard;
    private int life;
    private boolean awakened;

    /** A team mate's action: what to say, the card, and when it happened. */
    private static final class Action {
        final String text;
        final CardView card;
        final long at;

        Action(final String text0, final CardView card0) {
            text = text0;
            card = card0;
            at = System.currentTimeMillis();
        }
    }

    private final List<Action> actions = new ArrayList<>();
    private final List<Rectangle> actionBoxes = new ArrayList<>();
    private final Timer fade = new Timer(500, e -> fadeStep());

    public HexFightBanner(final HexFightInfo info0, final Consumer<CardView> showCard0) {
        info = info0;
        showCard = showCard0;
        life = info0.startLife;
        setOpaque(true);
        setPreferredSize(new Dimension(10, HEIGHT));
        setMinimumSize(new Dimension(10, HEIGHT));
        if (info0.scout != null) {
            setToolTipText("<html>" + escape(info0.scout).replace("\n", "<br>") + "</html>");
        }
        ToolTipManager.sharedInstance().registerComponent(this);
        final MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(final MouseEvent e) {
                final Action a = actionAt(e.getX(), e.getY());
                if (a != null && a.card != null) {
                    showCard.accept(a.card);
                }
            }
        };
        addMouseMotionListener(mouse);
    }

    private static String escape(final String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The enemy team's life now, and whether its boss has woken (its phase two). */
    public void setEnemy(final int life0, final boolean awakened0) {
        if (life0 != life || awakened0 != awakened) {
            life = life0;
            awakened = awakened0;
            repaint();
        }
    }

    /** A team mate cast something or played a land: newest on top, gone after a while. */
    public void addTeamAction(final String text, final CardView card) {
        actions.add(0, new Action(text, card));
        while (actions.size() > ACTIONS) {
            actions.remove(actions.size() - 1);
        }
        fade.start();
        repaint();
    }

    private void fadeStep() {
        final long now = System.currentTimeMillis();
        actions.removeIf(a -> now - a.at > ACTION_MILLIS);
        if (actions.isEmpty()) {
            fade.stop();
        }
        repaint();
    }

    private Action actionAt(final int x, final int y) {
        for (int i = 0; i < actionBoxes.size() && i < actions.size(); i++) {
            if (actionBoxes.get(i).contains(x, y)) {
                return actions.get(i);
            }
        }
        return null;
    }

    @Override
    public String getToolTipText(final MouseEvent e) {
        final Action a = actionAt(e.getX(), e.getY());
        return a != null ? null : super.getToolTipText(e);
    }

    @Override
    protected void paintComponent(final Graphics g0) {
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        final int w = getWidth();
        final int h = getHeight();
        g.setPaint(new GradientPaint(0, 0, new Color(0x2A2016), 0, h, new Color(0x15110C)));
        g.fillRect(0, 0, w, h);
        g.setColor(GOLD);
        g.fillRect(0, h - 2, w, 2);
        final Localizer loc = Localizer.getInstance();
        final Font base = getFont();

        // the enemy: its picture, its name and the kind of fight
        int x = 12;
        final BufferedImage sprite = HexSprites.get(info.enemyName);
        if (sprite != null) {
            final double scale = Math.min(44.0 / sprite.getHeight(), 44.0 / sprite.getWidth());
            final int sw = (int) Math.round(sprite.getWidth() * scale);
            final int sh = (int) Math.round(sprite.getHeight() * scale);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(sprite, x + (44 - sw) / 2, (h - sh) / 2, sw, sh, null);
            x += 52;
        }
        g.setFont(base.deriveFont(Font.BOLD, 16f));
        FontMetrics fm = g.getFontMetrics();
        final int nameW = Math.min(260, fm.stringWidth(info.enemyName));
        g.setColor(GOLD_LIGHT);
        g.drawString(clip(info.enemyName, fm, 260), x, 25);
        g.setFont(base.deriveFont(Font.PLAIN, 12f));
        fm = g.getFontMetrics();
        final int kindW = Math.min(260, fm.stringWidth(info.kind));
        g.setColor(MUTED);
        g.drawString(clip(info.kind, fm, 260), x, 43);
        x += Math.max(nameW, kindW) + 24;

        // the enemy team's life
        final int logX = w - LOG_W - 12;
        final int barW = Math.max(160, Math.min(420, logX - x - 24));
        final int barY = 10;
        final int barH = 18;
        final double share = info.startLife <= 0 ? 0 : Math.max(0, Math.min(1, life / (double) info.startLife));
        g.setColor(new Color(0x3A1414));
        g.fillRoundRect(x, barY, barW, barH, 8, 8);
        final Color fill = awakened ? new Color(0x8E3BD1) : new Color(0xC81E2D);
        g.setPaint(new GradientPaint(x, barY, fill.brighter(), x, barY + barH, fill.darker()));
        g.fillRoundRect(x, barY, (int) Math.round(barW * share), barH, 8, 8);
        if (info.phaseCard != null) {
            // where the boss wakes: at half its life
            final int mx = x + barW / 2;
            g.setColor(awakened ? new Color(0xD9B8FF) : GOLD_LIGHT);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(mx, barY - 3, mx, barY + barH + 3);
        }
        g.setColor(new Color(0x5A3E00));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(x, barY, barW, barH, 8, 8);
        g.setFont(base.deriveFont(Font.BOLD, 12f));
        fm = g.getFontMetrics();
        String lifeText = loc.getMessage("lblHexBannerLife", String.valueOf(life), String.valueOf(info.startLife));
        if (awakened) {
            lifeText += "  ·  " + loc.getMessage("lblHexBannerAwakened");
        } else if (info.phaseCard != null) {
            lifeText += "  ·  " + loc.getMessage("lblHexBannerWakes", String.valueOf(info.startLife / 2));
        }
        g.setColor(Color.WHITE);
        g.drawString(clip(lifeText, fm, barW - 12), x + 8, barY + barH / 2 + fm.getAscent() / 2 - 2);

        // the rules of the fight, as pills under the bar
        g.setFont(base.deriveFont(Font.PLAIN, 11f));
        fm = g.getFontMetrics();
        int px = x;
        final int py = barY + barH + 6;
        for (final String pill : info.pills) {
            final String text = clip(pill, fm, 300);
            final int pw = fm.stringWidth(text) + 14;
            if (px + pw > x + barW) {
                break;
            }
            g.setColor(new Color(0x3A3020));
            g.fillRoundRect(px, py, pw, 16, 16, 16);
            g.setColor(new Color(0x7A6440));
            g.drawRoundRect(px, py, pw, 16, 16, 16);
            g.setColor(TEXT);
            g.drawString(text, px + 7, py + 12);
            px += pw + 6;
        }

        // what the team mates did last
        actionBoxes.clear();
        if (!actions.isEmpty()) {
            g.setFont(base.deriveFont(Font.PLAIN, 12f));
            fm = g.getFontMetrics();
            final long now = System.currentTimeMillis();
            int ay = 6;
            for (final Action a : actions) {
                final float age = Math.min(1f, (now - a.at) / (float) ACTION_MILLIS);
                final int alpha = (int) (255 * (1f - age * age * 0.75f));
                g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), alpha));
                g.fillOval(logX, ay + 5, 6, 6);
                g.setColor(new Color(TEXT.getRed(), TEXT.getGreen(), TEXT.getBlue(), alpha));
                g.drawString(clip(a.text, fm, LOG_W - 12), logX + 12, ay + 12);
                actionBoxes.add(new Rectangle(logX, ay, LOG_W, 16));
                ay += 16;
            }
        }
        g.dispose();
    }

    /** The text, cut with an ellipsis where it would be wider than this. */
    private static String clip(final String text, final FontMetrics fm, final int width) {
        if (text == null || fm.stringWidth(text) <= width) {
            return text == null ? "" : text;
        }
        String s = text;
        while (s.length() > 1 && fm.stringWidth(s + "…") > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}
