package forge.screens.home.hexcrawl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexDungeon;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexRoom;
import forge.gamemodes.hexcrawl.HexSession;
import forge.item.IPaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.util.Localizer;

/**
 * The labyrinth of the dungeon the party is in, drawn where the hex map is: the rooms it has been in, those it
 * sees down its ways (with what waits in them), the rock beside them, darkness elsewhere. A click on a room next to the party's
 * votes for it; the way out below the entrance votes to leave. Above it, the dungeon's rule card, the buffs of
 * its shrines and the life the party has left.
 */
@SuppressWarnings("serial")
public class HexLabyrinthPanel extends JPanel {
    private static final int HEADER = 48;
    private static final int EXIT_H = 34;
    private static final Color WALL = new Color(0x2A2218);
    private static final Color DARK = new Color(0x0B0B0D);
    private static final Color ROCK = new Color(0x191715);
    private static final Color ROCK_LIGHT = new Color(0x2F2B27);
    private static final Color GOLD = new Color(0xE8C04A);
    private static final Color GOLD_LIGHT = new Color(0xFFE08A);

    private HexCampaign campaign;
    private Map<String, String> votes;
    private BiConsumer<Integer, Integer> onRoomClicked;
    private Runnable onExitClicked;
    private HexRoom hover;
    private boolean hoverExit;
    private boolean legendOpen = FModel.getPreferences().getPrefBoolean(FPref.HEXCRAWL_LEGEND_OPEN);
    private Rectangle exitBox = new Rectangle();
    private Rectangle ruleBox = new Rectangle();
    private Rectangle legendButton = new Rectangle();
    private Rectangle legendBox = new Rectangle();

    public HexLabyrinthPanel() {
        setOpaque(false);
        ToolTipManager.sharedInstance().registerComponent(this);
        final MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (legendButton.contains(e.getPoint())) {
                    toggleLegend();
                    return;
                }
                if (legendOpen && legendBox.contains(e.getPoint())) {
                    return;
                }
                if (exitBox.contains(e.getPoint())) {
                    if (onExitClicked != null) {
                        onExitClicked.run();
                    }
                    return;
                }
                final HexRoom r = roomAt(e.getX(), e.getY());
                if (r != null && onRoomClicked != null) {
                    onRoomClicked.accept(r.x, r.y);
                }
            }

            @Override
            public void mouseMoved(final MouseEvent e) {
                final boolean overLegend = legendButton.contains(e.getPoint()) || legendOpen && legendBox.contains(e.getPoint());
                final HexRoom r = overLegend ? null : roomAt(e.getX(), e.getY());
                final boolean exit = !overLegend && exitBox.contains(e.getPoint());
                if (r != hover || exit != hoverExit) {
                    hover = r;
                    hoverExit = exit;
                    repaint();
                }
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                hover = null;
                hoverExit = false;
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

    public void setVotes(final Map<String, String> votes0) {
        votes = votes0;
        repaint();
    }

    public void setOnRoomClicked(final BiConsumer<Integer, Integer> listener) {
        onRoomClicked = listener;
    }

    public void setOnExitClicked(final Runnable listener) {
        onExitClicked = listener;
    }

    public void toggleLegend() {
        legendOpen = !legendOpen;
        FModel.getPreferences().setPref(FPref.HEXCRAWL_LEGEND_OPEN, String.valueOf(legendOpen));
        FModel.getPreferences().save();
        repaint();
    }

    private HexDungeon dungeon() {
        return campaign == null ? null : campaign.dungeonHere();
    }

    /** The side of a room on the screen. */
    private int cell(final HexDungeon d) {
        final int w = getWidth() - 32;
        final int h = getHeight() - HEADER - EXIT_H - 16;
        return Math.max(24, Math.min(w / d.size, h / d.size));
    }

    private int originX(final HexDungeon d) {
        return (getWidth() - cell(d) * d.size) / 2;
    }

    private int originY() {
        return HEADER + 8;
    }

    private HexRoom roomAt(final int px, final int py) {
        final HexDungeon d = dungeon();
        if (d == null || d.rooms.isEmpty()) {
            return null;
        }
        final int c = cell(d);
        final int x = Math.floorDiv(px - originX(d), c);
        final int y = Math.floorDiv(py - originY(), c);
        return d.room(x, y);
    }

    private static Color blend(final Color a, final Color b, final double share) {
        return new Color((int) (a.getRed() * (1 - share) + b.getRed() * share),
                (int) (a.getGreen() * (1 - share) + b.getGreen() * share),
                (int) (a.getBlue() * (1 - share) + b.getBlue() * share));
    }

    private static String t(final String key, final Object... args) {
        return Localizer.getInstance().getMessage(key, args);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public String getToolTipText(final MouseEvent e) {
        final HexDungeon d = dungeon();
        if (d == null) {
            return null;
        }
        String text = null;
        if (legendButton.contains(e.getPoint())) {
            text = t("lblHexLegendButton");
        } else if (ruleBox.contains(e.getPoint()) && d.ruleCard != null) {
            final IPaperCard card = HexContent.get().findStartCard(d.ruleCard);
            text = card == null ? d.ruleCard : card.getName() + "\n" + card.getRules().getOracleText().replace("\\n", "\n");
        } else if (exitBox.contains(e.getPoint())) {
            text = t("lblHexLabyExitTip");
        } else {
            final HexRoom r = roomAt(e.getX(), e.getY());
            if (r != null && r.seen) {
                text = describe(d, r);
            }
        }
        return text == null ? null : "<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</html>";
    }

    /** What a room is and holds, as far as the party has seen it. */
    private String describe(final HexDungeon d, final HexRoom r) {
        final StringBuilder sb = new StringBuilder(t("lblHexRoom" + r.kind.name()));
        if (r.kind == HexRoom.Kind.TREASURE) {
            sb.append(" – ").append(r.looted ? t("lblHexRoomLooted") : t("lblHexTreasure" + r.content));
        } else if (r.kind == HexRoom.Kind.SHRINE) {
            sb.append(" – ").append(r.looted ? t("lblHexRoomUsed") : t("lblHexBuff" + r.content,
                    String.valueOf(HexCampaign.DUNGEON_SHRINE_LIFE), String.valueOf(HexCampaign.DUNGEON_WEAKEN_LIFE)));
        }
        if (r.guarded()) {
            sb.append('\n').append(t("lblHexEnemyMarker", r.guard.enemyName, String.valueOf(campaign.enemyLife(r.guard))));
            sb.append(HexCrawlPanel.scoutReport(campaign, r.guard));
        }
        if (r.x == d.x && r.y == d.y) {
            sb.append('\n').append(t("lblHexLegendParty"));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(final Graphics g0) {
        super.paintComponent(g0);
        final HexDungeon d = dungeon();
        if (d == null || d.rooms.isEmpty()) {
            return;
        }
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintHeader(g, d);

        final int c = cell(d);
        final int ox = originX(d);
        final int oy = originY();
        final Color tint = new Color(d.color.getRgb());
        final Color floor = blend(tint, new Color(0x8C8270), 0.55);
        g.setColor(DARK);
        g.fillRect(ox - 4, oy - 4, c * d.size + 8, c * d.size + 8);

        // the floors: lit where the party has been, dim where it sees, dark elsewhere
        for (final HexRoom r : d.rooms) {
            final int x = ox + r.x * c;
            final int y = oy + r.y * c;
            if (!r.seen) {
                continue;
            }
            final Color f = r.visited ? floor : blend(floor, Color.BLACK, 0.55);
            g.setColor(f);
            g.fillRect(x, y, c, c);
        }
        // the rock between the ways, where the party sees a room next to it
        for (final HexRoom r : d.rooms) {
            if (r.kind == HexRoom.Kind.ROCK && besideSeen(d, r)) {
                drawRock(g, ox + r.x * c, oy + r.y * c, c, r.x * 31 + r.y * 17);
            }
        }
        // the walls of the rooms the party knows, with a gap for every door
        g.setColor(WALL);
        g.setStroke(new BasicStroke(Math.max(3f, c / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        final int gap = c / 3;
        for (final HexRoom r : d.rooms) {
            if (!r.seen) {
                continue;
            }
            final int x = ox + r.x * c;
            final int y = oy + r.y * c;
            wall(g, (r.doors & HexDungeon.NORTH) != 0, x, y, x + c, y, gap);
            wall(g, (r.doors & HexDungeon.SOUTH) != 0, x, y + c, x + c, y + c, gap);
            wall(g, (r.doors & HexDungeon.WEST) != 0, x, y, x, y + c, gap);
            wall(g, (r.doors & HexDungeon.EAST) != 0, x + c, y, x + c, y + c, gap);
        }

        // where the party can go
        final boolean free = !campaign.isBusy();
        if (free) {
            g.setStroke(new BasicStroke(Math.max(2f, c / 30f)));
            for (final HexRoom n : d.neighbours(d.here())) {
                g.setColor(n == hover ? new Color(255, 255, 255, 240) : new Color(255, 255, 255, 110));
                g.drawRect(ox + n.x * c + 5, oy + n.y * c + 5, c - 10, c - 10);
            }
        }

        // what the rooms hold
        for (final HexRoom r : d.rooms) {
            if (r.seen) {
                paintRoom(g, d, r, ox + r.x * c, oy + r.y * c, c);
            }
        }

        // the way out, below the entrance
        final int ex = ox + d.entranceX * c;
        final int ey = oy + d.size * c + 4;
        exitBox = new Rectangle(ex + c / 4, ey, c / 2, EXIT_H - 6);
        final boolean canLeave = campaign.canLeaveLabyrinth();
        g.setColor(canLeave ? (hoverExit ? GOLD_LIGHT : GOLD) : new Color(0x6F6A5F));
        g.fillRoundRect(exitBox.x, exitBox.y, exitBox.width, exitBox.height, 10, 10);
        g.setColor(WALL);
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g.getFontMetrics();
        final String out = t("lblHexLabyExit");
        g.drawString(out, exitBox.x + (exitBox.width - fm.stringWidth(out)) / 2, exitBox.y + (exitBox.height + fm.getAscent()) / 2 - 2);

        // the party, and the votes
        final int pr = Math.max(8, c / 9);
        HexMapPanel.drawParty(g, ox + d.x * c + pr + 6, oy + d.y * c + c - pr - 6, pr);
        if (votes != null && !votes.isEmpty()) {
            final Map<String, List<String>> byKey = new LinkedHashMap<>();
            for (final Map.Entry<String, String> v : votes.entrySet()) {
                byKey.computeIfAbsent(v.getValue(), k -> new ArrayList<>()).add(v.getKey());
            }
            g.setFont(getFont().deriveFont(Font.BOLD, Math.max(10f, c * 0.12f)));
            final int br = Math.max(8, c / 10);
            for (final Map.Entry<String, List<String>> e : byKey.entrySet()) {
                int vx;
                int vy;
                if (HexSession.EXIT_KEY.equals(e.getKey())) {
                    vx = exitBox.x + exitBox.width + br + 4;
                    vy = exitBox.y + exitBox.height / 2;
                } else if (e.getKey().startsWith(HexSession.ROOM_KEY)) {
                    final String[] xy = e.getKey().substring(HexSession.ROOM_KEY.length()).split(",");
                    vx = ox + Integer.parseInt(xy[0]) * c + br + 6;
                    vy = oy + Integer.parseInt(xy[1]) * c + br + 6;
                } else {
                    continue;
                }
                for (final String voter : e.getValue()) {
                    final String txt = voter.isEmpty() ? "?" : voter.substring(0, 1).toUpperCase();
                    HexMapPanel.drawVote(g, vx, vy, br, HexCrawlPanel.headColor(seat(voter)), txt);
                    vx += br * 2 + 3;
                }
            }
        }
        paintLegend(g);
        g.dispose();
    }

    private int seat(final String head) {
        for (int i = 0; i < campaign.players.size(); i++) {
            if (campaign.players.get(i).name.equals(head)) {
                return i;
            }
        }
        return 0;
    }

    /** A square of rock with a room the party knows next to it. */
    private static boolean besideSeen(final HexDungeon d, final HexRoom rock) {
        for (final int door : HexDungeon.DOORS) {
            final HexRoom n = d.room(rock.x + HexDungeon.dx(door), rock.y + HexDungeon.dy(door));
            if (n != null && n.seen) {
                return true;
            }
        }
        return false;
    }

    /** Solid rock: a dark square with a few stones in it (placed by the seed, so they stay where they are). */
    private static void drawRock(final Graphics2D g, final int x, final int y, final int c, final int seed) {
        g.setColor(ROCK);
        g.fillRect(x, y, c, c);
        final java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < 5; i++) {
            final int w = c / 6 + rnd.nextInt(Math.max(1, c / 5));
            final int h = w * 2 / 3;
            final int sx = x + 4 + rnd.nextInt(Math.max(1, c - w - 8));
            final int sy = y + 4 + rnd.nextInt(Math.max(1, c - h - 8));
            g.setColor(ROCK_LIGHT);
            g.fillRoundRect(sx, sy, w, h, h, h);
            g.setColor(ROCK.darker());
            g.drawRoundRect(sx, sy, w, h, h, h);
        }
    }

    /** A wall from one corner to the other, with a gap in the middle where there is a door. */
    private static void wall(final Graphics2D g, final boolean door, final int x1, final int y1, final int x2, final int y2, final int gap) {
        if (!door) {
            g.drawLine(x1, y1, x2, y2);
            return;
        }
        final int mx = (x1 + x2) / 2;
        final int my = (y1 + y2) / 2;
        if (y1 == y2) {
            g.drawLine(x1, y1, mx - gap / 2, y1);
            g.drawLine(mx + gap / 2, y1, x2, y2);
        } else {
            g.drawLine(x1, y1, x1, my - gap / 2);
            g.drawLine(x1, my + gap / 2, x2, y2);
        }
    }

    /** A room's things: the entrance's stairs, a chest, a shrine, the boss's star, and the enemy in it. */
    private void paintRoom(final Graphics2D g, final HexDungeon d, final HexRoom r, final int x, final int y, final int c) {
        final int cx = x + c / 2;
        final int cy = y + c / 2;
        final boolean withGuard = r.guarded();
        final int thingX = withGuard ? x + c * 3 / 4 : cx; // the guard stands before what it guards
        switch (r.kind) {
            case ENTRANCE:
                drawStairs(g, cx, y + c - c / 5, c);
                break;
            case TREASURE:
                drawChest(g, thingX, cy + c / 8, c, r.looted ? null : r.content);
                break;
            case SHRINE:
                drawShrine(g, thingX, cy + c / 10, c, r.looted ? null : r.content);
                break;
            case BOSS:
                HexMapPanel.drawBoss(g, thingX, cy + c / 4, Math.max(6, c / 9), HexMapPanel.bossColor(!withGuard, d.color));
                break;
            default:
                break;
        }
        if (withGuard) {
            final HexEncounter enemy = r.guard;
            final BufferedImage sprite = HexSprites.get(enemy.enemyName);
            final int gx = r.kind == HexRoom.Kind.EMPTY || r.kind == HexRoom.Kind.ENTRANCE ? cx : x + c / 3;
            HexMapPanel.drawFigure(g, sprite, gx, cy, c * (r.kind == HexRoom.Kind.BOSS ? 0.62 : 0.5));
            HexMapPanel.drawLife(g, getFont(), campaign.enemyLife(enemy), x + c - Math.max(14, c / 6), y + Math.max(14, c / 6), c * 0.42);
        }
    }

    private static void drawStairs(final Graphics2D g, final int cx, final int bottom, final int c) {
        final int w = c / 3;
        final int step = Math.max(3, c / 22);
        for (int i = 0; i < 4; i++) {
            final int sw = w - i * w / 5;
            g.setColor(i % 2 == 0 ? new Color(0xBFB29A) : new Color(0xA39579));
            g.fillRect(cx - sw / 2, bottom - (i + 1) * step, sw, step);
        }
        g.setColor(WALL);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(cx - w / 2, bottom - 4 * step, w, 4 * step);
    }

    /** A chest; open and empty once looted (kind null), else with a mark of what is in it. */
    static void drawChest(final Graphics2D g, final int cx, final int cy, final int c, final String kind) {
        final int w = Math.max(14, c / 3);
        final int h = w * 2 / 3;
        final int x = cx - w / 2;
        final int y = cy - h / 2;
        g.setColor(kind == null ? new Color(0x5E5547) : new Color(0x8A5A2B));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(kind == null ? new Color(0x3A342C) : new Color(0x5A3A1A));
        g.fillRect(x, y + h / 3 - 1, w, 3); // the lid
        g.setColor(kind == null ? new Color(0x8A8A8A) : GOLD);
        g.fillRect(cx - 3, y + h / 3 - 3, 6, 7); // the lock
        g.setColor(WALL);
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(x, y, w, h, 6, 6);
        if (kind == null) {
            return;
        }
        // what is in it, on a little gold coin above
        final int r = Math.max(6, c / 14);
        final int mx = cx + w / 2;
        final int my = y - r / 2;
        g.setColor(GOLD);
        g.fillOval(mx - r, my - r, 2 * r, 2 * r);
        g.setColor(new Color(0x5A3E00));
        g.drawOval(mx - r, my - r, 2 * r, 2 * r);
        if ("CARD".equals(kind)) {
            g.drawRect(mx - r / 3, my - r / 2, r * 2 / 3, r);
        } else if ("RELIC".equals(kind)) {
            g.drawOval(mx - r / 2, my - r / 2, r, r);
        } else {
            g.fillOval(mx - r / 3, my - r / 3, r * 2 / 3, r * 2 / 3);
        }
    }

    /** A shrine: a stone pillar with a glowing orb (its colour tells its buff); grey once used (buff null). */
    static void drawShrine(final Graphics2D g, final int cx, final int cy, final int c, final String buff) {
        final int w = Math.max(10, c / 7);
        final int h = Math.max(14, c / 4);
        g.setColor(new Color(0x9C9384));
        g.fillRect(cx - w / 2, cy - h / 4, w, h);
        g.setColor(WALL);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRect(cx - w / 2, cy - h / 4, w, h);
        final Color glow = buff == null ? new Color(0x6F6A5F) : "LIFE".equals(buff) ? new Color(0xE0405A)
                : "HAND".equals(buff) ? new Color(0x4A8FE0) : "LAND".equals(buff) ? new Color(0x4FB35A) : new Color(0x9B4FD0);
        final int r = Math.max(6, c / 11);
        if (buff != null) {
            for (int i = 3; i >= 1; i--) {
                g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 40));
                g.fillOval(cx - r - i * 3, cy - h / 4 - r - i * 3 - r, 2 * (r + i * 3), 2 * (r + i * 3));
            }
        }
        g.setColor(glow);
        g.fillOval(cx - r, cy - h / 4 - 2 * r, 2 * r, 2 * r);
    }

    /** The dungeon's name, its rule card, the shrines' buffs and the party's life. */
    private void paintHeader(final Graphics2D g, final HexDungeon d) {
        final Font base = getFont();
        int x = 16;
        g.setFont(base.deriveFont(Font.BOLD, 17f));
        FontMetrics fm = g.getFontMetrics();
        final String title = t("lblHexLabyTitle", t(d.color.getLabelKey()));
        g.setColor(GOLD_LIGHT);
        g.drawString(title, x, 30);
        x += fm.stringWidth(title) + 18;
        g.setFont(base.deriveFont(Font.PLAIN, 12.5f));
        fm = g.getFontMetrics();
        final List<String> pills = new ArrayList<>();
        final IPaperCard rule = d.ruleCard == null ? null : HexContent.get().findStartCard(d.ruleCard);
        ruleBox = new Rectangle();
        if (rule != null) {
            pills.add(t("lblHexLabyRule", rule.getName()));
        }
        pills.add(t("lblHexLabyLife", String.valueOf(d.lifeLeft)));
        for (final String buff : d.buffs) {
            pills.add(t("lblHexBuff" + buff, String.valueOf(HexCampaign.DUNGEON_SHRINE_LIFE), String.valueOf(HexCampaign.DUNGEON_WEAKEN_LIFE)));
        }
        for (int i = 0; i < pills.size(); i++) {
            final String text = pills.get(i);
            final int w = fm.stringWidth(text) + 18;
            if (x + w > getWidth() - 12) {
                break;
            }
            g.setColor(i == 0 && rule != null ? new Color(0x3A2A4A) : new Color(0x3A3020));
            g.fillRoundRect(x, 14, w, 22, 22, 22);
            g.setColor(i == 0 && rule != null ? new Color(0xB48CE0) : new Color(0x7A6440));
            g.drawRoundRect(x, 14, w, 22, 22, 22);
            g.setColor(new Color(0xECE6D6));
            g.drawString(text, x + 9, 30);
            if (i == 0 && rule != null) {
                ruleBox = new Rectangle(x, 14, w, 22);
            }
            x += w + 8;
        }
    }

    /** The "?" coin, and the legend of the labyrinth's symbols when it is open. */
    private void paintLegend(final Graphics2D g) {
        final int r = 15;
        final int bx = getWidth() - r - 12;
        final int by = getHeight() - r - 12;
        legendButton = new Rectangle(bx - r, by - r, 2 * r, 2 * r);
        g.setColor(legendOpen ? GOLD_LIGHT : GOLD);
        g.fillOval(bx - r, by - r, 2 * r, 2 * r);
        g.setColor(new Color(0x5A3E00));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(bx - r, by - r, 2 * r, 2 * r);
        g.setFont(getFont().deriveFont(Font.BOLD, 18f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("?", bx - fm.stringWidth("?") / 2, by + fm.getAscent() / 2 - 2);
        legendBox = new Rectangle();
        if (!legendOpen) {
            return;
        }
        final String[] keys = { "lblHexLabyLegendEntrance", "lblHexLabyLegendTreasure", "lblHexLabyLegendShrine", "lblHexLabyLegendBoss",
            "lblHexLabyLegendGuard", "lblHexLegendParty", "lblHexLabyLegendRock", "lblHexLabyLegendDark" };
        final int row = 32;
        final int icon = 40;
        g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        fm = g.getFontMetrics();
        int textW = fm.stringWidth(t("lblHexLabyLegendKeys"));
        for (final String k : keys) {
            textW = Math.max(textW, fm.stringWidth(t(k)) + icon + 8);
        }
        final int w = 28 + textW;
        final int h = 12 + 22 + keys.length * row + 14 + 24;
        final int x = Math.max(4, getWidth() - w - 12);
        final int y = Math.max(4, by - r - 8 - h);
        legendBox = new Rectangle(x, y, w, h);
        g.setColor(new Color(16, 20, 24, 235));
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(new Color(0xC9A44C));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, w, h, 14, 14);
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g.setColor(GOLD_LIGHT);
        g.drawString(t("lblHexLegendTitle"), x + 14, y + 27);
        int ty = y + 35;
        final HexDungeon d = dungeon();
        for (final String k : keys) {
            final int cx = x + 14 + icon / 2;
            final int cy = ty + row / 2;
            final Graphics2D sg = (Graphics2D) g.create();
            switch (k) {
                case "lblHexLabyLegendEntrance": drawStairs(sg, cx, cy + 10, 60); break;
                case "lblHexLabyLegendTreasure": drawChest(sg, cx, cy + 3, 54, "CARD"); break;
                case "lblHexLabyLegendShrine": drawShrine(sg, cx, cy + 4, 60, "LIFE"); break;
                case "lblHexLabyLegendBoss": HexMapPanel.drawBoss(sg, cx, cy, 9, HexMapPanel.bossColor(false, d == null ? null : d.color)); break;
                case "lblHexLabyLegendGuard":
                    HexMapPanel.drawFigure(sg, null, cx - 4, cy + 2, 22);
                    HexMapPanel.drawLife(sg, getFont(), 20, cx + 10, cy - 6, 18);
                    break;
                case "lblHexLegendParty": HexMapPanel.drawParty(sg, cx, cy, 8); break;
                case "lblHexLabyLegendRock": drawRock(sg, cx - 14, cy - 14, 28, 7); break;
                default:
                    sg.setColor(DARK);
                    sg.fillRect(cx - 14, cy - 10, 28, 20);
                    sg.setColor(new Color(0x6F6A5F));
                    sg.drawRect(cx - 14, cy - 10, 28, 20);
                    break;
            }
            sg.dispose();
            g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            g.setColor(Color.WHITE);
            g.drawString(t(k), x + 14 + icon + 8, cy + fm.getAscent() / 2 - 2);
            ty += row;
        }
        g.setColor(GOLD_LIGHT);
        g.drawString(t("lblHexLabyLegendKeys"), x + 14, ty + 22);
    }
}
