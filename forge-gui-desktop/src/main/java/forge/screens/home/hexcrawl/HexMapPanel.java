package forge.screens.home.hexcrawl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexQuest;
import forge.gamemodes.hexcrawl.HexTile;
import forge.util.Localizer;

/** Draws the hex map of a campaign and reports clicks on fields. */
@SuppressWarnings("serial")
public class HexMapPanel extends JPanel {
    private static final double SQRT3 = Math.sqrt(3);

    private HexCampaign campaign;
    private Consumer<HexTile> onTileClicked;
    private HexTile hover;
    private java.util.Map<String, String> votes;

    public HexMapPanel() {
        setOpaque(false);
        ToolTipManager.sharedInstance().registerComponent(this);
        final MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(final MouseEvent e) {
                final HexTile t = tileAt(e.getX(), e.getY());
                if (t != null && onTileClicked != null) {
                    onTileClicked.accept(t);
                }
            }
            @Override public void mouseMoved(final MouseEvent e) {
                final HexTile t = tileAt(e.getX(), e.getY());
                if (t != hover) {
                    hover = t;
                    repaint();
                }
            }
            @Override public void mouseExited(final MouseEvent e) {
                hover = null;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setCampaign(final HexCampaign campaign0) {
        campaign = campaign0;
        repaint();
    }

    /** Who wants to go where: head name to field key. */
    /** The seat of a head in the party (for its colour). */
    private int seat(final String head) {
        for (int i = 0; i < campaign.players.size(); i++) {
            if (campaign.players.get(i).name.equals(head)) {
                return i;
            }
        }
        return 0;
    }

    public void setVotes(final java.util.Map<String, String> votes0) {
        votes = votes0;
        repaint();
    }

    public void setOnTileClicked(final Consumer<HexTile> listener) {
        onTileClicked = listener;
    }

    private double hexSize() {
        if (campaign == null) {
            return 20;
        }
        final int n = campaign.map.radius * 2 + 1;
        final double byWidth = (getWidth() - 16) / (SQRT3 * (n + 0.5));
        final double byHeight = (getHeight() - 16) / (1.5 * n + 0.5);
        return Math.max(8, Math.min(byWidth, byHeight));
    }

    private double centerX(final HexTile t, final double size) {
        return getWidth() / 2.0 + size * SQRT3 * (t.q + t.r / 2.0);
    }

    private double centerY(final HexTile t, final double size) {
        return getHeight() / 2.0 + size * 1.5 * t.r;
    }

    /** The screen position of the middle of a field (used by automated UI tests as well). */
    public java.awt.Point centerOf(final HexTile t) {
        final double size = hexSize();
        return new java.awt.Point((int) Math.round(centerX(t, size)), (int) Math.round(centerY(t, size)));
    }

    public HexTile tileAt(final int x, final int y) {
        if (campaign == null) {
            return null;
        }
        final double size = hexSize();
        HexTile best = null;
        double bestDist = size * size * 0.8;
        for (final HexTile t : campaign.map.tiles.values()) {
            final double dx = x - centerX(t, size);
            final double dy = y - centerY(t, size);
            final double d = dx * dx + dy * dy;
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    private static Polygon hexagon(final double cx, final double cy, final double size) {
        final Polygon p = new Polygon();
        for (int i = 0; i < 6; i++) {
            final double angle = Math.PI / 180 * (60 * i - 30);
            p.addPoint((int) Math.round(cx + size * Math.cos(angle)), (int) Math.round(cy + size * Math.sin(angle)));
        }
        return p;
    }

    private static Color blend(final Color a, final Color b, final double share) {
        return new Color((int) (a.getRed() * (1 - share) + b.getRed() * share),
                (int) (a.getGreen() * (1 - share) + b.getGreen() * share),
                (int) (a.getBlue() * (1 - share) + b.getBlue() * share));
    }

    @Override
    public String getToolTipText(final MouseEvent e) {
        final HexTile t = tileAt(e.getX(), e.getY());
        if (t == null) {
            return null;
        }
        final Localizer loc = Localizer.getInstance();
        final StringBuilder sb = new StringBuilder(loc.getMessage(t.color.getLabelKey()));
        final forge.gamemodes.hexcrawl.HexBiomeRule rule = forge.gamemodes.hexcrawl.HexBiomeRule.forField(campaign, t);
        if (t.poi == HexPoi.TOWN) {
            sb.append(" – ").append(loc.getMessage("lblHexTown")).append(' ').append(t.name);
        } else if (t.poi == HexPoi.BOSS) {
            sb.append(" – ").append(t.name);
            if (t.cleared) {
                sb.append(" (").append(loc.getMessage("lblHexDefeated")).append(')');
            }
        }
        final HexQuest quest = campaign.questAt(t.q, t.r);
        final HexEncounter enemy = campaign.enemyAt(t);
        if (enemy != null) {
            final String life = String.valueOf(campaign.enemyLife(enemy));
            if (quest != null) {
                sb.append(" – ").append(loc.getMessage("lblHexQuestMarker", quest.enemyName, life, String.valueOf(campaign.gold(quest.gold))));
            } else if (t.poi == HexPoi.BOSS) {
                sb.append(" (").append(loc.getMessage("lblHexLifeShort", life)).append(')');
            } else {
                sb.append(" – ").append(loc.getMessage("lblHexEnemyMarker", enemy.enemyName, life));
            }
            if (enemy.roaming) {
                sb.append('\n').append(loc.getMessage("lblHexRoamer"));
            }
            sb.append(HexCrawlPanel.enemyExtras(campaign, enemy));
            // scouting: what the party knows of the enemy's deck before the fight
            sb.append(HexCrawlPanel.scoutReport(campaign, enemy));
        } else if (forge.gamemodes.hexcrawl.HexEvent.of(t.event) != null) {
            final forge.gamemodes.hexcrawl.HexEvent event = forge.gamemodes.hexcrawl.HexEvent.of(t.event);
            sb.append(" – ").append(event.getLabel()).append('\n').append(event.getDescription());
        }
        if (rule != null && t.poi != HexPoi.TOWN) {
            sb.append('\n').append(loc.getMessage(rule.getLabelKey()));
        }
        if (t.poi == HexPoi.TOWN && t.explored && t != campaign.tile() && !campaign.isAdjacent(t)) {
            sb.append('\n').append(loc.getMessage("lblHexFastTravelHint", String.valueOf(forge.gamemodes.hexcrawl.HexCampaign.FAST_TRAVEL_GOLD)));
        }
        final String text = sb.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html>" + text.replace("\n", "<br>") + "</html>";
    }

    @Override
    protected void paintComponent(final Graphics g0) {
        super.paintComponent(g0);
        if (campaign == null) {
            return;
        }
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        final double size = hexSize();
        final Color fog = new Color(0x15171A);
        final HexTile here = campaign.tile();

        for (final HexTile t : campaign.map.tiles.values()) {
            final double cx = centerX(t, size);
            final double cy = centerY(t, size);
            final Polygon hex = hexagon(cx, cy, size - 1);
            final Color base = new Color(t.color.getRgb());
            g.setColor(t.explored || t.scouted ? base : blend(base, fog, 0.42));
            g.fillPolygon(hex);
            g.setColor(new Color(0, 0, 0, 110));
            g.setStroke(new BasicStroke(1f));
            g.drawPolygon(hex);
        }

        // where the party can go
        if (!campaign.isBusy() && !campaign.won) {
            g.setStroke(new BasicStroke(Math.max(2f, (float) size / 9)));
            for (final HexTile t : campaign.map.neighbors(here)) {
                g.setColor(t == hover ? new Color(255, 255, 255, 240) : new Color(255, 255, 255, 120));
                g.drawPolygon(hexagon(centerX(t, size), centerY(t, size), size - 3));
            }
        }

        final Font small = getFont().deriveFont(Font.BOLD, (float) Math.max(9, size * 0.36));
        for (final HexTile t : campaign.map.tiles.values()) {
            if (t.poi == HexPoi.NONE) {
                continue;
            }
            final int cx = (int) Math.round(centerX(t, size));
            final int cy = (int) Math.round(centerY(t, size));
            final int s = (int) Math.round(size * 0.42);
            if (t.poi == HexPoi.TOWN) {
                // a little house
                final Polygon house = new Polygon(
                        new int[] { cx - s, cx - s, cx, cx + s, cx + s },
                        new int[] { cy + s, cy - s / 4, cy - s, cy - s / 4, cy + s }, 5);
                g.setColor(new Color(0xF6F1E4));
                g.fillPolygon(house);
                g.setColor(new Color(0x3A2E20));
                g.setStroke(new BasicStroke(1.6f));
                g.drawPolygon(house);
                g.fillRect(cx - s / 4, cy + s / 4, s / 2, s * 3 / 4);
            } else {
                // a star for a boss, grey once it is beaten
                final Polygon star = new Polygon();
                for (int i = 0; i < 10; i++) {
                    final double rad = (i % 2 == 0 ? 1.25 : 0.55) * s;
                    final double a = Math.PI / 5 * i - Math.PI / 2;
                    star.addPoint((int) Math.round(cx + rad * Math.cos(a)), (int) Math.round(cy + rad * Math.sin(a)));
                }
                g.setColor(t.cleared ? new Color(0x8A8A8A) : t.color == HexColor.COLORLESS ? new Color(0x7B2FBE) : new Color(0xB3121E));
                g.fillPolygon(star);
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(1.6f));
                g.drawPolygon(star);
            }
            if (t.name != null && size >= 16) {
                g.setFont(small);
                final FontMetrics fm = g.getFontMetrics();
                final int w = fm.stringWidth(t.name);
                final int tx = cx - w / 2;
                final int ty = (int) Math.round(cy + size * 0.95);
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRoundRect(tx - 3, ty - fm.getAscent(), w + 6, fm.getHeight(), 6, 6);
                g.setColor(Color.WHITE);
                g.drawString(t.name, tx, ty);
            }
        }

        // enemies in plain sight: their picture, and a heart with their life
        for (final HexTile t : campaign.map.tiles.values()) {
            final HexEncounter enemy = campaign.enemyAt(t);
            if (enemy == null) {
                continue;
            }
            final int cx = (int) Math.round(centerX(t, size));
            final int cy = (int) Math.round(centerY(t, size));
            final HexQuest quest = campaign.questAt(t.q, t.r);
            if (t.poi != HexPoi.BOSS) {
                final BufferedImage sprite = HexSprites.get(enemy.enemyName);
                if (enemy.roaming) {
                    // a hunter: a red ring under it
                    final int ring = (int) Math.round(size * 0.66);
                    g.setColor(new Color(0xC81E2D));
                    g.setStroke(new BasicStroke(Math.max(2.5f, (float) size / 8)));
                    g.drawOval(cx - ring, cy - ring, 2 * ring, 2 * ring);
                }
                if (quest != null) {
                    // a wanted enemy stands on a yellow diamond
                    final int s = (int) Math.round(size * 0.62);
                    final Polygon diamond = new Polygon(new int[] { cx, cx + s, cx, cx - s }, new int[] { cy - s, cy, cy + s, cy }, 4);
                    g.setColor(new Color(0xE0A100));
                    g.fillPolygon(diamond);
                    g.setColor(Color.BLACK);
                    g.setStroke(new BasicStroke(1.6f));
                    g.drawPolygon(diamond);
                }
                if (sprite != null) {
                    final double scale = Math.min(size * 1.15 / sprite.getHeight(), size * 1.25 / sprite.getWidth());
                    final int w = (int) Math.round(sprite.getWidth() * scale);
                    final int h = (int) Math.round(sprite.getHeight() * scale);
                    final Object before = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(sprite, cx - w / 2, cy - h / 2, w, h, null);
                    if (before != null) {
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, before);
                    }
                } else {
                    // no picture: crossed blades
                    final int s = (int) Math.round(size * 0.38);
                    g.setColor(new Color(0x2A0A0A));
                    g.setStroke(new BasicStroke(Math.max(2.5f, (float) size / 9)));
                    g.drawLine(cx - s, cy - s, cx + s, cy + s);
                    g.drawLine(cx - s, cy + s, cx + s, cy - s);
                }
                if (quest != null && size >= 16) {
                    g.setFont(small);
                    final FontMetrics fm = g.getFontMetrics();
                    final int w = fm.stringWidth(quest.enemyName);
                    final int tx = cx - w / 2;
                    final int ty = (int) Math.round(cy + size * 0.95);
                    g.setColor(new Color(0, 0, 0, 170));
                    g.fillRoundRect(tx - 3, ty - fm.getAscent(), w + 6, fm.getHeight(), 6, 6);
                    g.setColor(new Color(0xFFD98A));
                    g.drawString(quest.enemyName, tx, ty);
                }
            }
            drawLife(g, campaign.enemyLife(enemy), (int) Math.round(cx + size * 0.5), (int) Math.round(cy - size * 0.48), size);
        }

        // events in plain sight: a golden coin with a question mark
        for (final HexTile t : campaign.map.tiles.values()) {
            if (t.event == null || campaign.enemyAt(t) != null) {
                continue;
            }
            final int cx = (int) Math.round(centerX(t, size));
            final int cy = (int) Math.round(centerY(t, size));
            final int r = (int) Math.round(size * 0.42);
            g.setColor(new Color(0xE8C04A));
            g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            g.setColor(new Color(0x5A3E00));
            g.setStroke(new BasicStroke(Math.max(1.5f, (float) size / 14)));
            g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
            g.setFont(getFont().deriveFont(Font.BOLD, (float) Math.max(9, size * 0.6)));
            final FontMetrics fm = g.getFontMetrics();
            g.drawString("?", cx - fm.stringWidth("?") / 2, cy + fm.getAscent() / 2 - 1);
        }

        // votes: a badge with the number of heads that want to go to a field
        if (votes != null && !votes.isEmpty()) {
            // one coloured mark per voter (the head's colour and initial); a far target gets a dashed way to it
            final java.util.Map<String, java.util.List<String>> byField = new java.util.LinkedHashMap<>();
            for (final java.util.Map.Entry<String, String> v : votes.entrySet()) {
                byField.computeIfAbsent(v.getValue(), k -> new java.util.ArrayList<>()).add(v.getKey());
            }
            g.setFont(getFont().deriveFont(Font.BOLD, (float) Math.max(9, size * 0.34)));
            for (final java.util.Map.Entry<String, java.util.List<String>> e : byField.entrySet()) {
                final HexTile t = campaign.map.tiles.get(e.getKey());
                if (t == null) {
                    continue;
                }
                final int cx = (int) Math.round(centerX(t, size));
                final int cy = (int) Math.round(centerY(t, size));
                final java.util.List<String> voters = e.getValue();
                if (!campaign.isAdjacent(t)) {
                    final java.util.List<HexTile> way = campaign.canFastTravel(t) ? null : campaign.route(t);
                    g.setColor(HexCrawlPanel.headColor(seat(voters.get(0))));
                    g.setStroke(new BasicStroke(Math.max(2f, (float) size / 10), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            1f, new float[] { (float) size / 3, (float) size / 4 }, 0f));
                    int px = (int) Math.round(centerX(here, size));
                    int py = (int) Math.round(centerY(here, size));
                    if (way == null) {
                        g.drawLine(px, py, cx, cy);
                    } else {
                        for (final HexTile step : way) {
                            final int sx = (int) Math.round(centerX(step, size));
                            final int sy = (int) Math.round(centerY(step, size));
                            g.drawLine(px, py, sx, sy);
                            px = sx;
                            py = sy;
                        }
                    }
                }
                final int br = (int) Math.round(Math.max(6, size * 0.24));
                final double spread = voters.size() == 1 ? 0 : size * 0.32;
                for (int i = 0; i < voters.size(); i++) {
                    final double angle = Math.PI * 2 * i / voters.size() - Math.PI / 2;
                    final int vx = (int) Math.round(cx + Math.cos(angle) * spread);
                    final int vy = (int) Math.round(cy + Math.sin(angle) * spread);
                    g.setColor(HexCrawlPanel.headColor(seat(voters.get(i))));
                    g.fillOval(vx - br, vy - br, br * 2, br * 2);
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawOval(vx - br, vy - br, br * 2, br * 2);
                    final String txt = voters.get(i).isEmpty() ? "?" : voters.get(i).substring(0, 1).toUpperCase();
                    final FontMetrics fm = g.getFontMetrics();
                    g.drawString(txt, vx - fm.stringWidth(txt) / 2, vy + fm.getAscent() / 2 - 1);
                }
            }
        }

        // the party
        final boolean aside = here.poi != HexPoi.NONE || campaign.enemyAt(here) != null;
        final int px = (int) Math.round(centerX(here, size) - (aside ? size * 0.45 : 0));
        final int py = (int) Math.round(centerY(here, size) - (aside ? size * 0.45 : 0));
        final int pr = (int) Math.round(size * 0.3);
        g.setColor(new Color(0xFFD23F));
        g.fillOval(px - pr, py - pr, pr * 2, pr * 2);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(px - pr, py - pr, pr * 2, pr * 2);
        g.dispose();
    }

    /** A red heart with the life total of an enemy. */
    private void drawLife(final Graphics2D g, final int life, final int cx, final int cy, final double size) {
        final int r = (int) Math.round(Math.max(7, size * 0.3));
        final java.awt.geom.Path2D heart = new java.awt.geom.Path2D.Double();
        heart.moveTo(cx, cy + r);
        heart.curveTo(cx - r * 1.9, cy - r * 0.1, cx - r * 0.9, cy - r * 1.5, cx, cy - r * 0.55);
        heart.curveTo(cx + r * 0.9, cy - r * 1.5, cx + r * 1.9, cy - r * 0.1, cx, cy + r);
        heart.closePath();
        g.setColor(new Color(0xC81E2D));
        g.fill(heart);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(heart);
        final String txt = String.valueOf(life);
        g.setFont(getFont().deriveFont(Font.BOLD, (float) Math.max(9, r * (txt.length() > 2 ? 0.85 : 1.05))));
        final FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        g.drawString(txt, cx - fm.stringWidth(txt) / 2, cy + fm.getAscent() / 2 - 2);
    }

    /** Distance in fields between two tiles; kept here so the view does not need the map class. */
    public static int distance(final HexTile a, final HexTile b) {
        return HexMap.distance(a.q, a.r, b.q, b.r);
    }
}
