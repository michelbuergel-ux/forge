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
    /** Fields are drawn as big as on a map of this radius filling the panel; a bigger map scrolls instead. */
    private static final int FIT_RADIUS = 6;
    /** How much of the way to its goal the view glides per frame. */
    private static final double GLIDE = 0.16;

    private HexCampaign campaign;
    private Consumer<HexTile> onTileClicked;
    private HexTile hover;
    private java.util.Map<String, String> votes;

    // the view: the map point in its middle, in units of a field's size (see centerX)
    private double camX;
    private double camY;
    /** Where the view glides to: the party's field, or where the player dragged it. */
    private double goalX;
    private double goalY;
    private final javax.swing.Timer glide = new javax.swing.Timer(15, e -> glideStep());
    /** The map and the party's field shown last: a new map jumps to the party, a step glides after it. */
    private String shownMap;
    private String shownParty;
    private java.awt.Point dragFrom;
    private boolean dragged;

    public HexMapPanel() {
        setOpaque(false);
        ToolTipManager.sharedInstance().registerComponent(this);
        final MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(final MouseEvent e) {
                if (dragged) {
                    return;
                }
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
            // a map bigger than the panel: drag it (or turn the wheel, with Shift sideways) to look around
            @Override public void mousePressed(final MouseEvent e) {
                dragFrom = e.getPoint();
                dragged = false;
            }
            @Override public void mouseDragged(final MouseEvent e) {
                if (dragFrom == null) {
                    return;
                }
                final int dx = e.getX() - dragFrom.x;
                final int dy = e.getY() - dragFrom.y;
                if (!dragged && dx * dx + dy * dy < 25) {
                    return; // a shaky click, not a drag
                }
                dragged = true;
                setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                final double size = hexSize();
                panBy(-dx / size, -dy / size);
                dragFrom = e.getPoint();
            }
            @Override public void mouseReleased(final MouseEvent e) {
                dragFrom = null;
                setCursor(java.awt.Cursor.getDefaultCursor());
            }
            @Override public void mouseWheelMoved(final java.awt.event.MouseWheelEvent e) {
                final double step = e.getPreciseWheelRotation() * 1.5;
                if (e.isShiftDown()) {
                    panBy(step, 0);
                } else {
                    panBy(0, step);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void setCampaign(final HexCampaign campaign0) {
        campaign = campaign0;
        if (campaign == null) {
            shownMap = null;
            shownParty = null;
            glide.stop();
            repaint();
            return;
        }
        // the campaign object is new after every sync on a guest: tell the maps apart by name and cycle
        final String mapKey = campaign.name + "#" + campaign.cycle;
        final String partyKey = campaign.partyQ + "," + campaign.partyR;
        final HexTile here = campaign.tile();
        if (here != null && !mapKey.equals(shownMap)) {
            goalX = camX = mapX(here);
            goalY = camY = mapY(here);
            glide.stop();
        } else if (here != null && !partyKey.equals(shownParty)) {
            goalX = mapX(here);
            goalY = mapY(here);
            glide.start();
        }
        shownMap = mapKey;
        shownParty = partyKey;
        repaint();
    }

    private static double mapX(final HexTile t) {
        return SQRT3 * (t.q + t.r / 2.0);
    }

    private static double mapY(final HexTile t) {
        return 1.5 * t.r;
    }

    /** How far the view's middle may move from the map's middle, sideways: no further than the map's edge. */
    private double limitX(final double size) {
        final double half = (getWidth() / 2.0 - 8) / size;
        return campaign == null ? 0 : Math.max(0, SQRT3 * (campaign.map.radius + 0.5) - half);
    }

    /** The same up and down, with room for the names below the outer fields. */
    private double limitY(final double size) {
        final double half = (getHeight() / 2.0 - 8) / size;
        return campaign == null ? 0 : Math.max(0, 1.5 * campaign.map.radius + 1.6 - half);
    }

    private static double clamp(final double v, final double limit) {
        return Math.max(-limit, Math.min(limit, v));
    }

    private void panBy(final double dx, final double dy) {
        if (campaign == null) {
            return;
        }
        glide.stop();
        final double size = hexSize();
        goalX = camX = clamp(clamp(camX, limitX(size)) + dx, limitX(size));
        goalY = camY = clamp(clamp(camY, limitY(size)) + dy, limitY(size));
        repaint();
    }

    private void glideStep() {
        if (campaign == null) {
            glide.stop();
            return;
        }
        final double size = hexSize();
        final double gx = clamp(goalX, limitX(size));
        final double gy = clamp(goalY, limitY(size));
        camX = clamp(camX, limitX(size));
        camY = clamp(camY, limitY(size));
        camX += (gx - camX) * GLIDE;
        camY += (gy - camY) * GLIDE;
        if (Math.abs(gx - camX) < 0.01 && Math.abs(gy - camY) < 0.01) {
            camX = gx;
            camY = gy;
            glide.stop();
        }
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
        // as big as a small map needs to fill the panel; a bigger map does not shrink the fields, it scrolls
        final int n = Math.min(campaign.map.radius, FIT_RADIUS) * 2 + 1;
        final double byWidth = (getWidth() - 16) / (SQRT3 * (n + 0.5));
        final double byHeight = (getHeight() - 16) / (1.5 * n + 0.5);
        return Math.max(8, Math.min(byWidth, byHeight));
    }

    private double centerX(final HexTile t, final double size) {
        return getWidth() / 2.0 + size * (mapX(t) - clamp(camX, limitX(size)));
    }

    private double centerY(final HexTile t, final double size) {
        return getHeight() / 2.0 + size * (mapY(t) - clamp(camY, limitY(size)));
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
                sb.append(" – ").append(loc.getMessage(quest.archenemy ? "lblHexQuestMarkerArchenemy" : "lblHexQuestMarker",
                        quest.enemyName, life, String.valueOf(campaign.gold(quest.gold))));
            } else if (enemy.siege) {
                sb.append('\n').append(loc.getMessage("lblHexSiegeMarker", enemy.enemyName, life,
                        String.valueOf(forge.gamemodes.hexcrawl.HexCampaign.SIEGE_DISCOUNT_PERCENT)));
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
        if (t.freed) {
            sb.append('\n').append(loc.getMessage("lblHexTownFreed", String.valueOf(forge.gamemodes.hexcrawl.HexCampaign.SIEGE_DISCOUNT_PERCENT)));
        }
        if (t.poi == HexPoi.TOWN && t.explored && t.siege == null && t != campaign.tile() && !campaign.isAdjacent(t)) {
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
            final int cx = (int) Math.round(centerX(t, size) + (t.siege != null ? size * 0.2 : 0)); // room for a besieger before the gates
            final int cy = (int) Math.round(centerY(t, size));
            final int s = (int) Math.round(size * 0.42);
            if (t.poi == HexPoi.TOWN) {
                // a little house
                final Polygon house = new Polygon(
                        new int[] { cx - s, cx - s, cx, cx + s, cx + s },
                        new int[] { cy + s, cy - s / 4, cy - s, cy - s / 4, cy + s }, 5);
                if (t.siege != null) {
                    // besieged: a glowing red edge
                    g.setColor(new Color(200, 30, 45, 150));
                    g.setStroke(new BasicStroke(Math.max(3f, (float) size / 6)));
                    g.drawPolygon(house);
                }
                g.setColor(t.siege != null ? new Color(0xD9C9B4) : new Color(0xF6F1E4));
                g.fillPolygon(house);
                g.setColor(new Color(0x3A2E20));
                g.setStroke(new BasicStroke(1.6f));
                g.drawPolygon(house);
                g.fillRect(cx - s / 4, cy + s / 4, s / 2, s * 3 / 4);
                if (t.siege != null) {
                    // and flames on the roof
                    for (int i = -1; i <= 1; i++) {
                        final int fx = cx + i * s * 2 / 3;
                        final int fy = cy - s / 4 - (i == 0 ? s * 3 / 4 : 0);
                        final int fh = (int) Math.round(s * (i == 0 ? 0.9 : 0.7));
                        final Polygon flame = new Polygon(
                                new int[] { fx - fh / 3, fx - fh / 6, fx, fx + fh / 6, fx + fh / 3 },
                                new int[] { fy, fy - fh * 2 / 3, fy - fh, fy - fh * 2 / 3, fy }, 5);
                        g.setColor(new Color(0xF2741B));
                        g.fillPolygon(flame);
                        g.setColor(new Color(0xFFD23F));
                        g.fillOval(fx - fh / 8, fy - fh / 2, fh / 4, fh / 2);
                    }
                }
                if (t.freed) {
                    drawDiscount(g, cx + s, cy - s, size);
                }
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
                final int tx = (int) Math.round(centerX(t, size)) - w / 2;
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
            // a besieger's heart on its own side, so the flames on the roof show
            final int heartX = (int) Math.round(centerX(t, size) + size * (t.poi == HexPoi.TOWN ? -0.62 : 0.5));
            final int heartY = (int) Math.round(centerY(t, size) - size * 0.48);
            int cx = (int) Math.round(centerX(t, size));
            int cy = (int) Math.round(centerY(t, size));
            double figure = 1;
            final HexQuest quest = campaign.questAt(t.q, t.r);
            if (t.poi != HexPoi.BOSS) {
                final BufferedImage sprite = HexSprites.get(enemy.enemyName);
                if (t.poi == HexPoi.TOWN) {
                    // a besieger stands before the gates, a little smaller, so the burning house and the name show
                    cx -= (int) Math.round(size * 0.42);
                    cy += (int) Math.round(size * 0.06);
                    figure = 0.8;
                }
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
                    final double scale = figure * Math.min(size * 1.15 / sprite.getHeight(), size * 1.25 / sprite.getWidth());
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
            drawLife(g, campaign.enemyLife(enemy), heartX, heartY, size);
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
    /** A freed town's mark: a gold coin with its discount. */
    private void drawDiscount(final Graphics2D g, final int cx, final int cy, final double size) {
        final int r = (int) Math.round(Math.max(8, size * 0.36));
        g.setColor(new Color(0xE8C04A));
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(new Color(0x5A3E00));
        g.setStroke(new BasicStroke(1.4f));
        g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
        final String text = "-" + forge.gamemodes.hexcrawl.HexCampaign.SIEGE_DISCOUNT_PERCENT + "%";
        g.setFont(getFont().deriveFont(Font.BOLD, (float) Math.max(7, r * 0.78)));
        final FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

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
