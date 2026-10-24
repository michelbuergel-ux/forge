package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.FAINT;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.HEADING;
import static forge.screens.home.hexcrawl.HexTownStyle.MUTED;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT_BRIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.caps;
import static forge.screens.home.hexcrawl.HexTownStyle.decorative;
import static forge.screens.home.hexcrawl.HexTownStyle.display;
import static forge.screens.home.hexcrawl.HexTownStyle.label;
import static forge.screens.home.hexcrawl.HexTownStyle.tracked;
import static forge.screens.home.hexcrawl.HexTownStyle.wrapped;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexCycleRecord;
import forge.gamemodes.hexcrawl.HexDefeat;
import forge.gamemodes.hexcrawl.HexHeadStats;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.item.PaperCard;
import forge.screens.home.hexcrawl.HexSidebarParts.Sprite;
import forge.screens.home.hexcrawl.HexTownStyle.Banner;
import forge.screens.home.hexcrawl.HexTownStyle.GildedPanel;
import forge.screens.home.hexcrawl.HexTownStyle.GoldButton;
import forge.screens.home.hexcrawl.HexTownStyle.Glyph;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import forge.screens.home.hexcrawl.HexTownStyle.ShadowLabel;
import forge.screens.home.hexcrawl.HexTownStyle.WindowPanel;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import forge.view.FDialog;
import net.miginfocom.swing.MigLayout;

/**
 * The chronicle of the campaign: what every head did in this cycle (with the whole campaign beneath),
 * and the enemies the party has beaten. Comes up on its own after the victory over the last enemy,
 * and from the side panel at any time.
 */
final class HexStatsDialog {
    private static final int DEFEATED_W = 380;

    private final Localizer loc = Localizer.getInstance();
    private final HexCampaign campaign;
    private final boolean victory;
    private final Palette palette = HexTownStyle.palette(HexColor.COLORLESS);
    private FDialog dialog;

    /** @param victory0 shown after the victory over the last enemy (another title) */
    HexStatsDialog(final HexCampaign campaign0, final boolean victory0) {
        campaign = campaign0;
        victory = victory0;
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    private static JPanel transparent(final MigLayout layout) {
        final JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static FScrollPane scroller(final JComponent view) {
        final FScrollPane scroll = new FScrollPane(view, false, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    private static JLabel heading(final String text) {
        return label(caps(text), tracked(display(12f), 0.2f), HEADING);
    }

    /** Shows the window until it is closed. */
    void show() {
        if (campaign == null) {
            return;
        }
        final Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        final int width = Math.min(1440, screen.width - 40);
        final int height = Math.min(920, screen.height - 40);
        dialog = new FDialog(true, false, "0");
        dialog.getTitleBar().setVisible(false);
        dialog.add(build(width, height), "w 100%!, h 100%!");
        dialog.setSize(width, height);
        try {
            dialog.setVisible(true);
        } finally {
            dialog.dispose();
        }
    }

    /** The whole window, laid out for the given size. */
    JPanel build(final int width, final int height) {
        final WindowPanel root = new WindowPanel(palette, new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[][grow, fill][]"));
        root.add(buildBanner(), "h " + (Banner.CLOTH + 10) + "!");
        final JPanel body = transparent(new MigLayout("insets 8 22 0 22, gap 18, fill", "[grow, fill][" + DEFEATED_W + "!, fill]", "[grow, fill]"));
        body.add(buildHeads(), "wmin 0, hmin 0");
        body.add(buildDefeated(), "wmin 0, hmin 0");
        root.add(body, "wmin 0, hmin 0");
        final JPanel footer = transparent(new MigLayout("insets 0 26 0 26, gap 0, fill"));
        if (campaign.cycle > 1) {
            footer.add(label(t("lblHexChronicleTotalNote"), body(Font.ITALIC, 12f), FAINT), "aligny center");
        }
        root.add(footer, "h 34!");
        root.setPreferredSize(new java.awt.Dimension(width, height));
        return root;
    }

    private JPanel buildBanner() {
        final Banner banner = new Banner(palette, new MigLayout("insets 0 26 10 22, gap 18, aligny center", "[]push[]", "[" + Banner.CLOTH + "!]"));
        final JPanel names = transparent(new MigLayout("insets 0, gap 2, wrap 1"));
        final String title = victory ? t("lblHexChronicleTitleVictory", HexContent.get().getBossName(HexColor.COLORLESS)) : t("lblHexChronicleTitle");
        names.add(new ShadowLabel(title, decorative(27f), TEXT_BRIGHT));
        names.add(label(t("lblHexChronicleSubtitle", String.valueOf(campaign.cycle),
                String.valueOf(campaign.cycleBattles(forge.gamemodes.hexcrawl.HexBattle.Result.WON)),
                String.valueOf(campaign.cycleBattles(forge.gamemodes.hexcrawl.HexBattle.Result.LOST)),
                String.valueOf(campaign.cycleBattles(forge.gamemodes.hexcrawl.HexBattle.Result.FLED)),
                String.valueOf(campaign.cycleQuests())), tracked(display(13f), 0.15f), palette.light()));
        banner.add(names);
        final GoldButton close = new GoldButton("", GoldButton.Kind.OUTLINE, 13f).withGlyph(Glyph.CLOSE);
        close.setToolTipText(t("lblHexClose"));
        close.addActionListener(e -> {
            if (dialog != null) {
                dialog.setVisible(false);
            }
        });
        banner.add(close, "w 44!, h 44!");
        // the banner carries the window around
        final MouseAdapter drag = new MouseAdapter() {
            private Point start;
            private Point origin;

            @Override
            public void mousePressed(final MouseEvent e) {
                if (dialog != null) {
                    start = e.getLocationOnScreen();
                    origin = dialog.getLocation();
                }
            }

            @Override
            public void mouseDragged(final MouseEvent e) {
                if (start != null) {
                    final Point now = e.getLocationOnScreen();
                    dialog.setLocation(origin.x + now.x - start.x, origin.y + now.y - start.y);
                }
            }
        };
        banner.addMouseListener(drag);
        banner.addMouseMotionListener(drag);
        return banner;
    }

    // ------------------------------------------------------------------ the heads

    /** One line of the table: its label, and for every head the number of this cycle and of the whole campaign. */
    private record Row(String key, Function<HexHeadStats, Integer> value) { }

    private JPanel buildHeads() {
        final GildedPanel panel = new GildedPanel(palette, new MigLayout("insets 18 20 22 20, gap 0, wrap 1, fill", "[grow, fill]", "[][grow, fill]"));
        panel.add(heading(t("lblHexChronicleHeads")), "gapbottom 10");
        final List<HexPlayer> heads = campaign.players;
        final StringBuilder cols = new StringBuilder("[250!]");
        for (int i = 0; i < heads.size(); i++) {
            cols.append("[grow, fill, 110:150:n]");
        }
        final boolean totals = campaign.cycle > 1;
        final JPanel table = transparent(new MigLayout("insets 0, gapx 14, gapy 0, wrap " + (heads.size() + 1), cols.toString()));
        table.add(new JLabel());
        for (final HexPlayer p : heads) {
            final JPanel name = transparent(new MigLayout("insets 0, gap 0, wrap 1"));
            name.add(label(p.name, display(16f), GOLD_LIGHT));
            name.add(label(p.ai ? t("lblHexCompanion") : " ", body(Font.PLAIN, 11f), FAINT));
            table.add(name, "gapbottom 8");
        }
        final List<Row> rows = List.of(
                new Row("lblHexChronicleFights", s -> s.fights),
                new Row("lblHexChronicleMvp", s -> s.fightsMvp),
                new Row("lblHexChronicleDamage", s -> s.damage),
                new Row("lblHexChronicleDrawn", s -> s.cardsDrawn),
                new Row("lblHexChronicleLife", s -> s.lifeGained),
                new Row("lblHexChronicleSpells", s -> s.spells),
                new Row("lblHexChronicleLands", s -> s.lands),
                new Row("lblHexChronicleGoldEarned", s -> s.goldEarned),
                new Row("lblHexChronicleGoldSpent", s -> s.goldSpent),
                new Row("lblHexChronicleBought", s -> s.cardsBought),
                new Row("lblHexChronicleSold", s -> s.cardsSold),
                new Row("lblHexChronicleRelics", s -> s.relicsBought));
        for (final Row row : rows) {
            addRow(table, t(row.key()), heads, p -> cell(String.valueOf(row.value().apply(p.stats)),
                    totals ? String.valueOf(row.value().apply(p.totalStats)) : null));
            if ("lblHexChronicleGoldSpent".equals(row.key())) {
                // what the head has had all in all: spent and still in the purse
                addRow(table, t("lblHexChronicleGoldNow"), heads, p -> cell(String.valueOf(p.gold), null));
                addRow(table, t("lblHexChronicleGoldTotal"), heads, p -> cell(String.valueOf(p.stats.goldSpent + p.gold),
                        totals ? String.valueOf(p.totalStats.goldSpent + p.gold) : null));
            }
        }
        addRow(table, t("lblHexChronicleBestFight"), heads, p -> cell(String.valueOf(p.stats.bestFight),
                p.stats.bestFightEnemy == null ? null : t("lblHexChronicleAgainst", p.stats.bestFightEnemy)));
        addRow(table, t("lblHexChronicleFavourite"), heads, p -> favourite(p.stats));
        final FScrollPane scroll = scroller(table);
        panel.add(scroll, "wmin 0, hmin 0");
        return panel;
    }

    private void addRow(final JPanel table, final String text, final List<HexPlayer> heads, final Function<HexPlayer, JComponent> value) {
        table.add(label(text, body(Font.PLAIN, 13f), MUTED), "gaptop 4, gapbottom 4, aligny center");
        for (final HexPlayer p : heads) {
            table.add(value.apply(p), "gaptop 4, gapbottom 4, aligny center");
        }
    }

    /** A number of this cycle, and small beside it the whole campaign's (or a note). */
    private static JComponent cell(final String big, final String small) {
        final JPanel cell = transparent(new MigLayout("insets 0, gap 10"));
        cell.add(label(big, body(Font.BOLD, 15f), TEXT_BRIGHT));
        if (small != null) {
            cell.add(label(small, body(Font.PLAIN, 11f), FAINT));
        }
        return cell;
    }

    private JComponent favourite(final HexHeadStats stats) {
        final String name = stats.favouriteCard();
        if (name == null) {
            return cell("–", null);
        }
        final JPanel cell = transparent(new MigLayout("insets 0, gap 8", "[][grow]"));
        final PaperCard card = HexContent.get().getCard(name);
        if (card != null) {
            cell.add(HexTownStyle.thumbnail(card), "w 54!, h 75!, spany 2");
        }
        cell.add(wrapped(name, body(Font.BOLD, 13f), TEXT, 120), "wrap");
        cell.add(label(t("lblHexChronicleDamageOf", String.valueOf(stats.favouriteCardDamage())), body(Font.PLAIN, 11f), FAINT), "aligny top");
        return cell;
    }

    // ------------------------------------------------------------------ the enemies

    private JPanel buildDefeated() {
        final GildedPanel panel = new GildedPanel(palette, new MigLayout("insets 18 20 22 20, gap 0, wrap 1, fill", "[grow, fill]", "[][][grow, fill]"));
        panel.add(heading(t("lblHexChronicleDefeated")));
        panel.add(label(t("lblHexChronicleDefeatedCount", String.valueOf(campaign.defeated.size()), String.valueOf(campaign.defeatedTotal)),
                body(Font.PLAIN, 12f), MUTED), "gaptop 4, gapbottom 10");
        final int textW = DEFEATED_W - 40 - 48 - 30;
        final JPanel list = transparent(new MigLayout("insets 0 0 0 8, gap 0, wrap 1", "[grow, fill]"));
        final List<HexDefeat> bosses = new ArrayList<>();
        final List<HexDefeat> special = new ArrayList<>();
        final List<String> ordinary = new ArrayList<>();
        for (final HexDefeat d : campaign.defeated) {
            if (d.kind == HexDefeat.Kind.ARCHMAGE || d.kind == HexDefeat.Kind.BOSS) {
                bosses.add(d);
            } else if (d.kind == HexDefeat.Kind.TASK || d.kind == HexDefeat.Kind.ARCHENEMY) {
                special.add(d);
            } else {
                ordinary.add(d.name);
            }
        }
        bosses.sort((a, b) -> Boolean.compare(b.kind == HexDefeat.Kind.BOSS, a.kind == HexDefeat.Kind.BOSS)); // the last enemy first
        if (campaign.defeated.isEmpty()) {
            list.add(label(t("lblHexChronicleNone"), body(Font.ITALIC, 13f), FAINT));
        }
        if (!bosses.isEmpty()) {
            list.add(label(caps(t("lblHexChronicleBosses")), tracked(display(11f), 0.15f), palette.light()), "gapbottom 6");
            for (final HexDefeat d : bosses) {
                list.add(enemy(d, 48, textW), "gapbottom 8");
            }
        }
        if (!special.isEmpty()) {
            list.add(label(caps(t("lblHexChronicleSpecial")), tracked(display(11f), 0.15f), palette.light()), "gaptop 8, gapbottom 6");
            for (final HexDefeat d : special) {
                list.add(enemy(d, 36, textW + 12), "gapbottom 6");
            }
        }
        if (!ordinary.isEmpty()) {
            list.add(label(caps(t("lblHexChronicleOrdinary", String.valueOf(ordinary.size()))), tracked(display(11f), 0.15f), palette.light()),
                    "gaptop 8, gapbottom 4");
            list.add(wrapped(String.join(", ", ordinary), body(Font.PLAIN, 12f), MUTED, DEFEATED_W - 70));
        }
        if (!campaign.cycleHistory.isEmpty()) {
            list.add(label(caps(t("lblHexChronicleCycles")), tracked(display(11f), 0.15f), palette.light()), "gaptop 14, gapbottom 4");
            for (final HexCycleRecord r : campaign.cycleHistory) {
                list.add(wrapped(t("lblHexChronicleCycleLine", String.valueOf(r.cycle), String.valueOf(r.battlesWon), String.valueOf(r.defeated),
                        String.valueOf(r.questsDone)), body(Font.PLAIN, 12f), MUTED, DEFEATED_W - 70), "gapbottom 2");
            }
        }
        final FScrollPane scroll = new FScrollPane(list, false, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, "wmin 0, hmin 0");
        return panel;
    }

    private JComponent enemy(final HexDefeat d, final int size, final int textW) {
        final java.awt.image.BufferedImage sprite = HexSprites.get(d.name);
        final JPanel row = transparent(new MigLayout("insets 0, gap 10", sprite == null ? "[grow]" : "[" + size + "!][grow]"));
        if (sprite != null) {
            row.add(new Sprite(sprite, size), "w " + size + "!, h " + size + "!, spany 2, aligny center");
        }
        row.add(wrapped(d.name, body(Font.BOLD, 13f), TEXT_BRIGHT, textW), "wrap");
        final String kind = t("lblHexChronicleKind" + d.kind.name().charAt(0) + d.kind.name().substring(1).toLowerCase());
        final String colour = d.color == null || d.kind != HexDefeat.Kind.ARCHMAGE ? "" : " · " + t(d.color.getLabelKey());
        final String turns = d.turns > 0 ? " · " + t("lblHexChronicleTurns", String.valueOf(d.turns)) : "";
        row.add(label(kind + colour + turns, body(Font.PLAIN, 11f), FAINT), "aligny top");
        return row;
    }
}
