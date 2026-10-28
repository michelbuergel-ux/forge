package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_EDGE;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.HEADING;
import static forge.screens.home.hexcrawl.HexTownStyle.MUTED;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT_BRIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.caps;
import static forge.screens.home.hexcrawl.HexTownStyle.decorative;
import static forge.screens.home.hexcrawl.HexTownStyle.display;
import static forge.screens.home.hexcrawl.HexTownStyle.label;
import static forge.screens.home.hexcrawl.HexTownStyle.tracked;
import static forge.screens.home.hexcrawl.HexTownStyle.wrapped;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;

import forge.card.CardRules;
import forge.deckchooser.FDeckViewer;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexDeckStats;
import forge.gamemodes.hexcrawl.HexDeckStats.Advice;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexRelic;
import forge.gamemodes.hexcrawl.HexSession;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.screens.home.hexcrawl.HexDeckParts.CardRow;
import forge.screens.home.hexcrawl.HexDeckParts.ColourBars;
import forge.screens.home.hexcrawl.HexDeckParts.ColourHeader;
import forge.screens.home.hexcrawl.HexDeckParts.ColourRow;
import forge.screens.home.hexcrawl.HexDeckParts.CurveChart;
import forge.screens.home.hexcrawl.HexDeckParts.DeckBanner;
import forge.screens.home.hexcrawl.HexDeckParts.RelicBox;
import forge.screens.home.hexcrawl.HexDeckParts.RowListener;
import forge.screens.home.hexcrawl.HexDeckParts.Sigil;
import forge.screens.home.hexcrawl.HexDeckParts.StatTile;
import forge.screens.home.hexcrawl.HexDeckParts.Tab;
import forge.screens.home.hexcrawl.HexDeckParts.TintBox;
import forge.screens.home.hexcrawl.HexDeckParts.TipIcon;
import forge.screens.home.hexcrawl.HexTownStyle.Badge;
import forge.screens.home.hexcrawl.HexTownStyle.CardFace;
import forge.screens.home.hexcrawl.HexTownStyle.GildedPanel;
import forge.screens.home.hexcrawl.HexTownStyle.GoldButton;
import forge.screens.home.hexcrawl.HexTownStyle.Glyph;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import forge.screens.home.hexcrawl.HexTownStyle.Pill;
import forge.screens.home.hexcrawl.HexTownStyle.PillTray;
import forge.screens.home.hexcrawl.HexTownStyle.ShadowLabel;
import forge.screens.home.hexcrawl.HexTownStyle.WindowPanel;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import forge.view.FDialog;
import net.miginfocom.swing.MigLayout;

/**
 * The deck and the sideboard of the heads, in the colours of the deck: cards move between them at any time
 * outside a fight. Beside them the card pointed at, the numbers of the deck, what its colours need and what
 * its lands make, the mana curve and advice; a tab shows the relics the head wears. Everybody can look at
 * every head; changes are made only for the own head (and by the host for heads nobody plays).
 */
final class HexDeckDialog {
    private static final String TAB_SIDE = "side";
    private static final String TAB_RELICS = "relics";
    private static final String[] SLOTS = { "Head", "Left", "Right", "Neck", "Body", "Boots", HexContent.AVATAR_SLOT };
    private static final String[] COLOR_KEYS = { "lblWhite", "lblBlue", "lblBlack", "lblRed", "lblGreen" };
    /** The colours of the gems and bars: white, blue, black, red, green. */
    private static final Color[] GEMS = { new Color(0xe8dcae), new Color(0x4f8fd6), new Color(0x9a6bc0), new Color(0xd9603f), new Color(0x6aa863) };
    private static final Color COLOURLESS_GEM = new Color(0xa7a9b4);

    private final Localizer loc = Localizer.getInstance();
    private final HexContent content = HexContent.get();
    private final HexSession session;
    private final List<String> heads = new ArrayList<>();
    private int headIndex;

    private FDialog dialog;
    private final JPanel host = new JPanel(new BorderLayout());
    private int width;
    private int height;
    /** The deck's main colours the window is painted in; the window is built again when they change. */
    private String themeKey;
    private Palette palette;

    // the parts of the window, made anew with each theme
    private Sigil sigil;
    private JLabel lblSubtitle;
    private final List<Pill> headPills = new ArrayList<>();
    private JLabel lblDeckCount;
    private JPanel pnlDeck;
    private final List<CardRow> rows = new ArrayList<>();
    private final List<String> rowNames = new ArrayList<>();
    private Tab tabSide;
    private Tab tabRelics;
    private final CardLayout sidePages = new CardLayout();
    private JPanel sideCards;
    private JPanel pnlSide;
    private final List<Pill> filterPills = new ArrayList<>();
    private JPanel pnlRelics;
    private JPanel relicSummary;
    private CardFace preview;
    private final StatTile[] tiles = new StatTile[4];
    private ColourBars bars;
    private CurveChart chart;
    private JPanel pnlAdvice;
    private int adviceW;
    private int relicTextW;
    private final JLabel lblMessage = label(" ", body(Font.PLAIN, 13f), MUTED);
    private String listsShown = "";
    private String relicsShown = "";
    private String adviceShown = "";

    private String tab = TAB_SIDE;
    private String filter = "all";
    private String hovered;
    private String message = "";
    private boolean messageBad;

    HexDeckDialog(final HexSession session0) {
        session = session0;
        for (final HexPlayer p : campaign().players) {
            heads.add(p.name);
        }
        headIndex = Math.max(0, heads.indexOf(session.getMyHead()));
        host.setOpaque(false);
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    private HexCampaign campaign() {
        return session.getCampaign();
    }

    private HexPlayer head() {
        final HexCampaign c = campaign();
        return c == null || heads.isEmpty() ? null : c.player(heads.get(Math.min(headIndex, heads.size() - 1)));
    }

    private boolean mayEdit(final HexPlayer p) {
        return p != null && !session.isFightRunning() && !campaign().deckLocked() && session.mayActFor(session.getMyHead(), p.name);
    }

    private static JPanel transparent(final MigLayout layout) {
        final JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static FScrollPane scroller(final JComponent view) {
        final FScrollPane scroll = new FScrollPane(view, false, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    private static JLabel heading(final String text) {
        return label(caps(text), tracked(display(12f), 0.2f), HEADING);
    }

    private String colourName(final int color) {
        return t(COLOR_KEYS[color]);
    }

    /** Shows the window until it is closed. The given hook is told how to refresh the open window. */
    void show(final Consumer<Runnable> refreshHook) {
        if (heads.isEmpty() || campaign() == null) {
            return;
        }
        final Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog = new FDialog(true, false, "0");
        dialog.getTitleBar().setVisible(false);
        dialog.add(build(Math.min(1440, screen.width - 40), Math.min(920, screen.height - 40)), "w 100%!, h 100%!");
        dialog.setSize(width, height);
        refreshHook.accept(this::fill); // a guest's change arrives a moment later, with the next state from the host
        try {
            dialog.setVisible(true);
        } finally {
            refreshHook.accept(null);
            dialog.dispose();
        }
    }

    /** The whole window, laid out for the given size and filled; it stays the same panel when the theme changes. */
    JPanel build(final int width0, final int height0) {
        width = width0;
        height = height0;
        rebuild(HexDeckStats.of(head().deck, content));
        return host;
    }

    // ------------------------------------------------------------------ theme

    private String themeKey(final HexDeckStats stats) {
        return stats.mainColors().toString();
    }

    private Palette paletteOf(final List<Integer> mains) {
        if (mains.isEmpty()) {
            return HexTownStyle.palette(HexColor.COLORLESS);
        }
        return mains.size() <= 2 ? HexTownStyle.palette(HexColor.COLORS[mains.get(0)]) : HexTownStyle.multicolour();
    }

    /** Builds the window in the colours of the deck shown. */
    private void rebuild(final HexDeckStats stats) {
        themeKey = themeKey(stats);
        final List<Integer> mains = stats.mainColors();
        palette = paletteOf(mains);
        rows.clear();
        rowNames.clear();
        headPills.clear();
        filterPills.clear();
        listsShown = "";
        relicsShown = "";
        adviceShown = "";

        final int deckW = Math.round(width * 0.3f);
        final int sideW = Math.round(width * 0.285f);
        final int rightW = width - deckW - sideW - 2 * 22 - 2 * 18;
        adviceW = rightW - 36 - 24 - 12 - 24;
        relicTextW = sideW - 32 - 44 - 14 - 28 - 16;

        final WindowPanel root = new WindowPanel(palette, new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[][grow, fill][]"));
        root.add(buildBanner(mains), "h " + (DeckBanner.CLOTH + 10) + "!");
        final JPanel body = transparent(new MigLayout("insets 8 22 0 22, gap 18, fill",
                "[" + deckW + "!, fill][" + sideW + "!, fill][grow, fill]", "[grow, fill]"));
        body.add(buildDeckPanel(), "wmin 0, hmin 0");
        body.add(buildSidePanel(), "wmin 0, hmin 0");
        body.add(buildRightColumn(rightW), "wmin 0, hmin 0");
        root.add(body, "wmin 0, hmin 0");
        final JPanel footer = transparent(new MigLayout("insets 0 26 0 26, gap 0, fill"));
        footer.add(lblMessage, "growx, aligny center");
        root.add(footer, "h 40!");
        host.removeAll();
        host.add(root, BorderLayout.CENTER);
        fill();
        host.revalidate();
        host.repaint();
    }

    private JPanel buildBanner(final List<Integer> mains) {
        final DeckBanner banner = new DeckBanner(new MigLayout("insets 0 26 10 22, gap 18, aligny center", "[][]push[][]", "[" + DeckBanner.CLOTH + "!]"));
        final List<Color> darks = new ArrayList<>();
        final List<Color> accents = new ArrayList<>();
        for (final int m : mains) {
            darks.add(HexTownStyle.palette(HexColor.COLORS[m]).dark());
            accents.add(HexTownStyle.palette(HexColor.COLORS[m]).accent());
        }
        if (mains.isEmpty()) {
            darks.add(palette.dark());
            accents.add(palette.accent());
        }
        banner.setColors(palette, darks, accents);
        sigil = new Sigil();
        banner.add(sigil, "h 44!");
        final JPanel names = transparent(new MigLayout("insets 0, gap 2, wrap 1"));
        names.add(new ShadowLabel(t("lblHexDeckWindowTitle"), decorative(27f), TEXT_BRIGHT));
        lblSubtitle = label(" ", tracked(display(13f), 0.15f), palette.light());
        names.add(lblSubtitle);
        banner.add(names);
        final PillTray tray = new PillTray();
        for (int i = 0; i < heads.size(); i++) {
            final int index = i;
            final HexPlayer p = campaign().player(heads.get(i));
            final Pill pill = new Pill(heads.get(i) + (p != null && p.ai ? " · " + t("lblHexCompanion") : ""));
            pill.addActionListener(e -> selectHead(index));
            headPills.add(pill);
            tray.add(pill, "h 36!");
        }
        banner.add(tray);
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

    private JPanel buildDeckPanel() {
        final GildedPanel panel = new GildedPanel(palette, new MigLayout("insets 16 14 14 18, gap 0 6, wrap 1, fill", "[grow, fill]", "[][grow, fill]"));
        final JPanel top = transparent(new MigLayout("insets 0, gap 10, aligny bottom", "[][]push[]"));
        top.add(label(t("lblHexDeckList"), display(20f), TEXT_BRIGHT));
        lblDeckCount = label(" ", body(Font.PLAIN, 13f), MUTED);
        top.add(lblDeckCount, "gapbottom 3");
        final GoldButton pictures = new GoldButton(t("lblHexAsPictures"), GoldButton.Kind.OUTLINE, 11.5f);
        pictures.addActionListener(e -> {
            final HexPlayer p = head();
            if (p != null) {
                FDeckViewer.show(content.toDeck(p.name, p.deck));
            }
        });
        top.add(pictures, "h 32!");
        panel.add(top);
        pnlDeck = transparent(new MigLayout("insets 0 0 0 4, gap 0 2, wrap 1, fillx", "[grow, fill]"));
        panel.add(scroller(pnlDeck), "wmin 0, hmin 0");
        return panel;
    }

    private JPanel buildSidePanel() {
        final GildedPanel panel = new GildedPanel(palette, new MigLayout("insets 14 14 14 18, gap 0 10, wrap 1, fill", "[grow, fill]", "[][grow, fill]"));
        final JPanel tabs = transparent(new MigLayout("insets 0, gap 6"));
        tabSide = new Tab(t("lblHexSideboardTab"), palette);
        tabRelics = new Tab(t("lblHexRelicsTab"), palette);
        tabSide.addActionListener(e -> selectTab(TAB_SIDE));
        tabRelics.addActionListener(e -> selectTab(TAB_RELICS));
        tabs.add(tabSide, "h 40!");
        tabs.add(tabRelics, "h 40!");
        panel.add(tabs);

        sideCards = new JPanel(sidePages);
        sideCards.setOpaque(false);
        final JPanel side = transparent(new MigLayout("insets 0, gap 0 10, wrap 1, fill", "[grow, fill]", "[][][grow, fill]"));
        side.add(label(t("lblHexSideNote"), body(Font.PLAIN, 12f), HexTownStyle.FAINT));
        final JPanel filters = transparent(new MigLayout("insets 0, gap 6, wrap 3"));
        final String[][] choices = { { "all", "lblHexFilterAll" }, { "creature", "lblHexFilterCreatures" }, { "spell", "lblHexFilterSpells" },
                { "land", "lblHexFilterLands" }, { "fits", "lblHexFilterFits" } };
        for (final String[] choice : choices) {
            final Pill pill = new Pill(t(choice[1]));
            pill.putClientProperty("filter", choice[0]);
            pill.addActionListener(e -> {
                filter = choice[0];
                fill();
            });
            filterPills.add(pill);
            filters.add(pill, "h 30!");
        }
        side.add(filters);
        pnlSide = transparent(new MigLayout("insets 0 0 0 4, gap 0 2, wrap 1, fillx", "[grow, fill]"));
        side.add(scroller(pnlSide), "wmin 0, hmin 0");
        sideCards.add(side, TAB_SIDE);

        final JPanel relics = transparent(new MigLayout("insets 0, gap 0 10, wrap 1, fill", "[grow, fill]", "[][grow, fill]"));
        relicSummary = new TintBox(palette, new MigLayout("insets 10 14 10 14, gap 0 4, wrap 1", "[grow, fill]"));
        relics.add(relicSummary);
        pnlRelics = transparent(new MigLayout("insets 0 0 0 4, gap 0 10, wrap 1, fillx", "[grow, fill]"));
        relics.add(scroller(pnlRelics), "wmin 0, hmin 0");
        sideCards.add(relics, TAB_RELICS);
        panel.add(sideCards, "wmin 0, hmin 0");
        return panel;
    }

    private JPanel buildRightColumn(final int rightW) {
        final JPanel column = transparent(new MigLayout("insets 0, gap 0 14, wrap 1, fill", "[grow, fill]", "[][][][grow, fill]"));
        final JPanel top = transparent(new MigLayout("insets 0, gap 14", "[196!][grow, fill]", "[274!]"));
        preview = new CardFace();
        top.add(preview, "w 196!, h 274!");
        final JPanel grid = transparent(new MigLayout("insets 0, gap 10, wrap 2, fill", "[grow, fill, sg t][grow, fill, sg t]", "[grow, fill][grow, fill]"));
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = new StatTile(palette);
            grid.add(tiles[i]);
        }
        top.add(grid, "wmin 0");
        column.add(top);

        // the colours under a fixed heading: three rows at most, the others scroll
        final GildedPanel colours = new GildedPanel(palette, new MigLayout("insets 12 18 14 18, gap 0 6, wrap 1, fill", "[grow, fill]"));
        colours.add(new ColourHeader(t("lblHexColoursTitle"), t("lblHexNeededBy"), t("lblHexMadeBy")), "h 22!");
        bars = new ColourBars();
        colours.add(scroller(bars), "h " + (4 + ColourBars.ROW) + ":pref:" + (4 + 3 * ColourBars.ROW) + ", wmin 0");
        column.add(colours);

        final GildedPanel curve = new GildedPanel(palette, new MigLayout("insets 12 18 14 18, gap 0, fill", "[grow, fill]"));
        chart = new CurveChart(palette);
        curve.add(chart, "hmin " + chart.getPreferredSize().height);
        column.add(curve);

        final GildedPanel advice = new GildedPanel(palette, new MigLayout("insets 12 18 14 18, gap 0 10, wrap 1, fill", "[grow, fill]", "[][grow, fill]"));
        advice.add(heading(t("lblHexAdviceTitle")));
        pnlAdvice = transparent(new MigLayout("insets 0 0 0 4, gap 0 9, wrap 1, fillx", "[grow, fill]"));
        advice.add(scroller(pnlAdvice), "wmin 0, hmin 0");
        column.add(advice, "hmin 110");
        return column;
    }

    private void selectHead(final int index) {
        if (index != headIndex) {
            headIndex = index;
            hovered = null;
            message = "";
            fill();
        }
    }

    private void selectTab(final String tab0) {
        tab = tab0;
        fill();
    }

    // ------------------------------------------------------------------ refresh

    private void fill() {
        final HexPlayer p = head();
        if (p == null) {
            return;
        }
        final HexDeckStats stats = HexDeckStats.of(p.deck, content);
        if (!themeKey(stats).equals(themeKey)) {
            rebuild(stats); // other main colours: the window in its new colours (it fills itself)
            return;
        }
        final boolean editable = mayEdit(p);
        for (int i = 0; i < headPills.size(); i++) {
            headPills.get(i).setActive(i == headIndex);
        }
        fillSigil(stats, p);
        lblDeckCount.setText(t("lblHexCardsCount", String.valueOf(p.deck.size())));
        tabSide.set(String.valueOf(p.sideboard.size()), TAB_SIDE.equals(tab));
        tabRelics.set(p.relics.size() + "/" + SLOTS.length, TAB_RELICS.equals(tab));
        sidePages.show(sideCards, tab);
        for (final Pill pill : filterPills) {
            pill.setActive(filter.equals(pill.getClientProperty("filter")));
        }
        fillLists(p, stats, editable);
        fillRelics(p);
        fillNumbers(stats);
        fillAdvice(p, stats, editable);
        showPreview();
        if (!editable && message.isEmpty()) {
            lblMessage.setText(session.isFightRunning() ? t("lblHexNoChangesInFight")
                    : campaign().deckLocked() ? t("lblHexDeckLocked") : t("lblHexViewOnly", p.name));
            lblMessage.setForeground(MUTED);
        } else {
            lblMessage.setText(message.isEmpty() ? t("lblHexDeckHint") : message);
            lblMessage.setForeground(messageBad ? HexDeckParts.BAD : message.isEmpty() ? HexTownStyle.FAINT : palette.light());
        }
        host.revalidate();
        host.repaint();
    }

    private void fillSigil(final HexDeckStats stats, final HexPlayer p) {
        final List<Integer> colors = stats.deckColors();
        final List<Integer> mains = stats.mainColors();
        final List<Color> gems = new ArrayList<>();
        final List<Boolean> small = new ArrayList<>();
        for (final int c : colors) {
            gems.add(GEMS[c]);
            small.add(!mains.contains(c));
        }
        if (gems.isEmpty()) {
            gems.add(COLOURLESS_GEM);
            small.add(false);
        }
        sigil.set(gems, small);
        String words;
        if (mains.size() >= 3) {
            words = t("lblHexColoursMany" + mains.size());
        } else if (mains.isEmpty()) {
            words = t("lblHexColourless");
        } else {
            final List<String> names = new ArrayList<>();
            for (final int c : mains) {
                names.add(colourName(c));
            }
            words = String.join(" & ", names);
            for (final int c : colors) {
                if (!mains.contains(c)) {
                    words += " · " + t("lblHexSplash", colourName(c));
                }
            }
        }
        lblSubtitle.setText(caps(p.name + " · " + words + " · " + t("lblHexCardsCount", String.valueOf(p.deck.size()))));
    }

    /** One entry per card name with its copies, in the order of the list. */
    private static TreeMap<String, Integer> counts(final List<String> cards) {
        final TreeMap<String, Integer> counts = new TreeMap<>();
        for (final String card : cards) {
            counts.merge(card, 1, Integer::sum);
        }
        return counts;
    }

    private static int manaValue(final PaperCard card) {
        return card == null ? 99 : Math.max(0, card.getRules().getManaCost().getCMC());
    }

    private static String kind(final PaperCard card) {
        if (card == null) {
            return "spell";
        }
        final CardRules rules = card.getRules();
        return rules.getType().isLand() ? "land" : rules.getType().isCreature() ? "creature" : "spell";
    }

    /** Whether no land of the deck makes a colour of the card. */
    private static boolean unsupported(final PaperCard card, final HexDeckStats stats) {
        if (card == null || card.getRules().getType().isLand()) {
            return false;
        }
        final byte[] bits = { forge.card.MagicColor.WHITE, forge.card.MagicColor.BLUE, forge.card.MagicColor.BLACK,
                forge.card.MagicColor.RED, forge.card.MagicColor.GREEN };
        for (int i = 0; i < bits.length; i++) {
            if (card.getRules().getColor().hasAnyColor(bits[i]) && stats.sources[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private void fillLists(final HexPlayer p, final HexDeckStats stats, final boolean editable) {
        final String shown = p.name + '|' + p.deck + '|' + p.sideboard + '|' + filter + '|' + editable + '|' + palette;
        if (shown.equals(listsShown)) {
            return;
        }
        listsShown = shown;
        rows.clear();
        rowNames.clear();
        final Comparator<Map.Entry<String, Integer>> byCost = Comparator
                .comparingInt((Map.Entry<String, Integer> e) -> manaValue(content.getCard(e.getKey())))
                .thenComparing(Map.Entry::getKey);

        // the deck, by kind
        pnlDeck.removeAll();
        final TreeMap<String, Integer> deck = counts(p.deck);
        final String[][] groups = { { "creature", "lblHexGroupCreatures" }, { "spell", "lblHexGroupSpells" }, { "land", "lblHexGroupLands" } };
        for (final String[] group : groups) {
            final List<Map.Entry<String, Integer>> entries = new ArrayList<>();
            int copies = 0;
            for (final Map.Entry<String, Integer> e : deck.entrySet()) {
                if (group[0].equals(kind(content.getCard(e.getKey())))) {
                    entries.add(e);
                    copies += e.getValue();
                }
            }
            if (entries.isEmpty()) {
                continue;
            }
            entries.sort(byCost);
            pnlDeck.add(new HexSidebarParts.Heading(t(group[1]) + "   " + copies), "gaptop 8, gapbottom 2");
            for (final Map.Entry<String, Integer> e : entries) {
                final String name = e.getKey();
                final PaperCard card = content.getCard(name);
                addRow(pnlDeck, name, new CardRow(palette, true, card, name, e.getValue(), unsupported(card, stats), null, true, editable,
                        t("lblHexMoveToSide", name), t("lblHexNoSourceTip"), listener(name, true)));
            }
        }

        // the sideboard, the cards that suit the deck first
        pnlSide.removeAll();
        final List<Map.Entry<String, Integer>> side = new ArrayList<>();
        for (final Map.Entry<String, Integer> e : counts(p.sideboard).entrySet()) {
            final PaperCard card = content.getCard(e.getKey());
            final boolean fits = card == null || stats.fits(card.getRules());
            if ("all".equals(filter) || ("fits".equals(filter) ? fits : filter.equals(kind(card)))) {
                side.add(e);
            }
        }
        side.sort(Comparator.comparing((Map.Entry<String, Integer> e) -> {
            final PaperCard card = content.getCard(e.getKey());
            return card != null && !stats.fits(card.getRules());
        }).thenComparing(byCost));
        for (final Map.Entry<String, Integer> e : side) {
            final String name = e.getKey();
            final PaperCard card = content.getCard(name);
            final boolean fits = card == null || stats.fits(card.getRules());
            addRow(pnlSide, name, new CardRow(palette, false, card, name, e.getValue(), false, t(fits ? "lblHexFits" : "lblHexOffColour"), fits,
                    editable, t("lblHexMoveToDeck", name), null, listener(name, false)));
        }
        if (side.isEmpty()) {
            pnlSide.add(label(t("lblHexNothingHere"), body(Font.PLAIN, 13f), HexTownStyle.FAINT), "gaptop 12");
        }
        pnlDeck.revalidate();
        pnlSide.revalidate();
        if (hovered == null || (!p.deck.contains(hovered) && !p.sideboard.contains(hovered))) {
            hovered = null;
            for (final String name : rowNames) {
                if (hovered == null && "creature".equals(kind(content.getCard(name)))) {
                    hovered = name;
                }
            }
            if (hovered == null && !rowNames.isEmpty()) {
                hovered = rowNames.get(0);
            }
        }
    }

    private void addRow(final JPanel panel, final String name, final CardRow row) {
        rows.add(row);
        rowNames.add(name);
        panel.add(row, "h 34!");
    }

    private RowListener listener(final String name, final boolean toSideboard) {
        return new RowListener() {
            @Override
            public void hovered() {
                hovered = name;
                showPreview();
            }

            @Override
            public void move() {
                HexDeckDialog.this.move(name, toSideboard);
            }
        };
    }

    private void showPreview() {
        preview.setCard(hovered == null ? null : content.getCard(hovered));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setSelected(rowNames.get(i).equals(hovered));
        }
    }

    // ------------------------------------------------------------------ moving cards

    private void setMessage(final String text, final boolean bad) {
        message = text;
        messageBad = bad;
    }

    private void move(final String card, final boolean toSideboard) {
        final HexPlayer p = head();
        if (p == null) {
            return;
        }
        if (session.isFightRunning()) {
            setMessage(t("lblHexNoChangesInFight"), true);
        } else if (campaign().deckLocked()) {
            setMessage(t("lblHexDeckLocked"), true);
        } else if (!mayEdit(p)) {
            setMessage(t("lblHexViewOnly", p.name), true);
        } else if (toSideboard && p.deck.size() <= HexCampaign.MIN_DECK_SIZE) {
            setMessage(t("lblHexDeckTooSmall", String.valueOf(HexCampaign.MIN_DECK_SIZE)), true);
        } else {
            session.moveCard(p.name, card, toSideboard);
            setMessage(t(toSideboard ? "lblHexMovedSide" : "lblHexMovedDeck", card), false);
        }
        fill();
    }

    /** The fix of a piece of advice: lands from the sideboard in first (the deck never drops below its minimum), then the others out. */
    private void applyFix(final Advice advice) {
        final HexPlayer p = head();
        if (p == null || !mayEdit(p) || !advice.hasFix()) {
            return;
        }
        for (int i = 0; i < advice.count; i++) {
            session.moveCard(p.name, advice.landIn, false);
        }
        if (advice.landOut != null && advice.kind != Advice.Kind.FEW_LANDS) {
            for (int i = 0; i < advice.count; i++) {
                session.moveCard(p.name, advice.landOut, true);
            }
            setMessage(t("lblHexSwapped", String.valueOf(advice.count), advice.landOut, advice.landIn), false);
        } else {
            setMessage(t("lblHexMovedDeck", advice.landIn), false);
        }
        fill();
    }

    // ------------------------------------------------------------------ relics

    private void fillRelics(final HexPlayer p) {
        final String shown = p.name + '|' + p.relics + '|' + p.trophies + '|' + mayEdit(p);
        if (shown.equals(relicsShown)) {
            return;
        }
        relicsShown = shown;
        int life = 0;
        int hand = 0;
        int inPlay = 0;
        int enemyLife = 0;
        pnlRelics.removeAll();
        for (final String slot : SLOTS) {
            HexRelic relic = null;
            for (final String name : p.relics) {
                final HexRelic r = content.getRelic(name);
                if (r != null && slot.equalsIgnoreCase(r.slot)) {
                    relic = r;
                }
            }
            // the pictures of what the relic puts into play, if it does: pointing at one shows it big
            final List<IPaperCard> pictures = relic == null ? new ArrayList<>() : content.relicPictures(relic, 2);
            final RelicBox box = new RelicBox(palette, relic != null, new MigLayout("insets 12 14 12 14, gap 14", "[44!][grow, fill][]", "[top]"));
            box.add(new Badge(HexTownStyle.slotGlyph(slot), 44, new Color(0x2a2416), relic != null ? GOLD_EDGE : new Color(0x4a4436), 2f,
                    relic != null ? GOLD_LIGHT : new Color(0x6f6a5f)), "w 44!, h 44!");
            final JPanel text = transparent(new MigLayout("insets 0, gap 0 3, wrap 1"));
            text.add(label(caps(t("lblHexSlot" + slot)), tracked(display(10.5f), 0.16f), HEADING));
            if (relic == null) {
                text.add(label(t("lblHexSlotEmpty"), body(Font.ITALIC, 13f), HexTownStyle.FAINT));
            } else {
                text.add(wrapped(relic.name, display(15.5f), TEXT_BRIGHT, relicTextW - 50 * pictures.size()));
                String effects = HexCrawlPanel.relicEffects(relic, campaign().cycle);
                if (relic.description != null && !relic.description.isEmpty()) {
                    effects += relic.description;
                }
                if (!effects.trim().isEmpty()) {
                    text.add(wrapped(effects, body(Font.PLAIN, 12.5f), new Color(0xd6d0c2), relicTextW - 50 * pictures.size()));
                }
                life += relic.life;
                hand += relic.hand;
                inPlay += relic.battlefield.size() + relic.randomPerCycle * campaign().cycle;
                enemyLife += relic.enemyLife;
            }
            box.add(text, "wmin 0");
            if (!pictures.isEmpty()) {
                final JPanel thumbs = transparent(new MigLayout("insets 0, gap 6"));
                for (final IPaperCard card : pictures) {
                    thumbs.add(HexTownStyle.thumbnail(card), "w 44!, h 61!");
                }
                box.add(thumbs);
            }
            pnlRelics.add(box);
        }
        if (!p.trophies.isEmpty()) {
            pnlRelics.add(buildTrophies(p), "gaptop 4");
        }
        pnlRelics.add(wrapped(t("lblHexRelicHint"), body(Font.PLAIN, 12f), HexTownStyle.FAINT, relicTextW + 58), "gaptop 2");
        final List<String> parts = new ArrayList<>();
        if (life != 0) {
            parts.add(t("lblHexSumLife", signed(life)));
        }
        if (hand != 0) {
            parts.add(t("lblHexSumHand", signed(hand)));
        }
        if (inPlay != 0) {
            parts.add(t("lblHexSumPlay", String.valueOf(inPlay)));
        }
        if (enemyLife != 0) {
            parts.add(t("lblHexSumEnemy", signed(enemyLife)));
        }
        relicSummary.removeAll();
        relicSummary.add(label(caps(t("lblHexEveryFight")), tracked(display(11f), 0.16f), HEADING));
        relicSummary.add(wrapped(parts.isEmpty() ? t("lblHexNoRelics") : String.join(" · ", parts), body(Font.PLAIN, 13f),
                new Color(0xece6d6), relicTextW + 58));
        pnlRelics.revalidate();
        relicSummary.revalidate();
    }

    private static String signed(final int n) {
        return n > 0 ? "+" + n : "−" + Math.abs(n);
    }

    /** The trophies of the bosses the head has beaten: the one worn is lit, a click wears another (or takes it off). */
    private JPanel buildTrophies(final HexPlayer p) {
        final JPanel box = new TintBox(palette, new MigLayout("insets 10 14 12 14, gap 6 6, wrap 1", "[grow, fill]"));
        box.add(label(caps(t("lblHexTrophies")), tracked(display(11f), 0.16f), HEADING));
        final boolean editable = mayEdit(p);
        final HexRelic worn = campaign().relicInSlot(p, HexCampaign.TROPHY_SLOT);
        for (final Map.Entry<String, Integer> e : p.trophies.entrySet()) {
            final HexColor color;
            try {
                color = HexColor.valueOf(e.getKey());
            } catch (final IllegalArgumentException ex) {
                continue;
            }
            final HexRelic trophy = content.trophy(color, e.getValue());
            if (trophy == null) {
                continue;
            }
            final boolean on = worn != null && worn.name.equals(trophy.name);
            final GoldButton wear = new GoldButton(on ? t("lblHexTrophyWorn", trophy.name) : trophy.name,
                    on ? GoldButton.Kind.GOLD : GoldButton.Kind.OUTLINE, 12.5f);
            wear.setToolTipText(HexCrawlPanel.relicEffects(trophy, campaign().cycle).trim().replace("\n", " · "));
            wear.setEnabled(editable);
            wear.addActionListener(ev -> wearTrophy(p, on ? null : color.name(), worn, on ? null : trophy));
            box.add(wear, "h 32!");
        }
        return box;
    }

    private void wearTrophy(final HexPlayer p, final String color, final HexRelic worn, final HexRelic trophy) {
        if (!mayEdit(p)) {
            return;
        }
        if (trophy != null && worn != null && worn.trophy == null && !forge.toolbox.FOptionPane.showConfirmDialog(
                t("lblHexTrophySellsRelic", trophy.name, worn.name, String.valueOf(content.relicSellPrice(worn.name))), p.name)) {
            return; // a relic bought for the slot would be sold to make room
        }
        session.wearTrophy(p.name, color);
        setMessage(trophy == null ? t("lblHexTrophyOff") : t("lblHexTrophyOn", trophy.name), false);
        fill();
    }

    // ------------------------------------------------------------------ numbers and advice

    private String decimal(final double value) {
        return String.format(Locale.ROOT, "%.2f", value).replace(".", t("lblHexDecimalPoint"));
    }

    private void fillNumbers(final HexDeckStats stats) {
        final int low = stats.recommendedLow();
        final int high = stats.recommendedHigh();
        final int min = HexCampaign.MIN_DECK_SIZE;
        tiles[0].set(t("lblHexTileCards"), String.valueOf(stats.cards),
                stats.cards > min ? t("lblHexOverMinimum", String.valueOf(stats.cards - min), String.valueOf(min)) : t("lblHexMinimumCards", String.valueOf(min)),
                stats.cards > min + 2 ? HexDeckParts.WARN : HexDeckParts.OK);
        tiles[1].set(t("lblHexTileLands"), String.valueOf(stats.lands), t("lblHexAdvisedLands", String.valueOf(low), String.valueOf(high)),
                stats.lands < low || stats.lands > high + 1 ? HexDeckParts.WARN : HexDeckParts.OK);
        tiles[2].set(t("lblHexTileCreatures"), String.valueOf(stats.creatures), t("lblHexMostDecks"),
                stats.creatures < 12 ? HexDeckParts.WARN : HexDeckParts.OK);
        tiles[3].set(t("lblHexTileAverage"), decimal(stats.averageManaValue()), t("lblHexSpellsOnly"),
                stats.averageManaValue() > 3.6 ? HexDeckParts.WARN : HexDeckParts.OK);

        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (stats.symbols[i] > 0 || stats.sources[i] > 0) {
                order.add(i);
            }
        }
        order.sort(Comparator.comparingInt((Integer i) -> -stats.symbols[i]).thenComparingInt(i -> -stats.sources[i]));
        final List<ColourRow> colourRows = new ArrayList<>();
        for (final int i : order) {
            final int wanted = stats.wantedSources(i);
            final boolean shortOf = stats.sources[i] < wanted - 1 || (stats.symbols[i] > 0 && stats.sources[i] == 0);
            final Color color = shortOf ? (stats.sources[i] == 0 ? HexDeckParts.BAD : HexDeckParts.WARN) : wanted > 0 ? HexDeckParts.OK : MUTED;
            colourRows.add(new ColourRow(GEMS[i], colourName(i), stats.share(i), stats.lands == 0 ? 0 : (double) stats.sources[i] / stats.lands,
                    t("lblHexSymbolsOn", String.valueOf(stats.symbols[i]), String.valueOf(stats.colors[i])),
                    wanted > 0 ? t("lblHexLandsNeed", String.valueOf(stats.sources[i]), String.valueOf(wanted))
                            : t("lblHexLandsNotNeeded", String.valueOf(stats.sources[i])), color));
        }
        bars.set(t("lblHexNoColours"), colourRows);
        chart.set(t("lblHexCurveTitle"), t("lblHexCurveCreatures"), t("lblHexCurveOther"), stats.curve, stats.curveCreatures);
    }

    private String adviceText(final Advice a, final HexDeckStats stats) {
        final int[] n = a.numbers;
        String text;
        switch (a.kind) {
            case NO_SOURCE:
            case FEW_SOURCES:
                text = a.kind == Advice.Kind.NO_SOURCE
                        ? t("lblHexAdviceNoSource", colourName(a.color), String.valueOf(n[0]))
                        : t("lblHexAdviceFewSources", colourName(a.color), String.valueOf(n[0]), String.valueOf(n[1]), String.valueOf(n[2]));
                if (!a.hasFix()) {
                    text += " " + t("lblHexAdviceBuy", HexColor.COLORS[a.color].getBasicLand(), String.valueOf(HexCampaign.BASIC_LAND_PRICE));
                }
                return text;
            case FEW_LANDS:
                return t("lblHexAdviceFewLands", String.valueOf(n[0]), decimal(stats.averageManaValue()), String.valueOf(n[1]), String.valueOf(n[2]));
            case MANY_LANDS:
                return t("lblHexAdviceManyLands", String.valueOf(n[0]), String.valueOf(n[1]), String.valueOf(n[2]));
            case FEW_CREATURES:
                text = t("lblHexAdviceFewCreatures", String.valueOf(n[0]));
                return n[1] > 0 ? text + " " + t("lblHexAdviceSpareCreatures", String.valueOf(n[1])) : text;
            case FEW_CHEAP:
                return t("lblHexAdviceFewCheap", String.valueOf(n[0]));
            case OVER_MINIMUM:
                return t("lblHexAdviceOverMinimum", String.valueOf(n[0]));
            default:
                return t("lblHexAdviceSolid");
        }
    }

    private void fillAdvice(final HexPlayer p, final HexDeckStats stats, final boolean editable) {
        final List<Advice> advice = stats.advice(p.deck, p.sideboard, content);
        final List<Advice> shownAdvice = advice.subList(0, Math.min(4, advice.size()));
        final StringBuilder key = new StringBuilder(p.name).append(editable);
        for (final Advice a : shownAdvice) {
            key.append('|').append(adviceText(a, stats)).append(a.landIn).append(a.count);
        }
        if (key.toString().equals(adviceShown)) {
            return;
        }
        adviceShown = key.toString();
        pnlAdvice.removeAll();
        for (final Advice a : shownAdvice) {
            final boolean fix = editable && a.hasFix();
            final JPanel row = transparent(new MigLayout("insets 0, gap 12 6, wrap 2", "[24!][grow, fill]"));
            row.add(new TipIcon(a.level), "w 24!, h 24!, aligny top, gaptop 2");
            row.add(wrapped(adviceText(a, stats), body(Font.PLAIN, 13f), new Color(0xe2dccd), adviceW), "wmin 0");
            if (fix) { // the fix under the text, where a long land name has room
                final String label = a.kind == Advice.Kind.FEW_LANDS ? t("lblHexAdviceAddLand", a.landIn)
                        : t("lblHexAdviceSwap", String.valueOf(a.count), a.landOut, a.landIn);
                final GoldButton button = new GoldButton(label, GoldButton.Kind.GOLD, 11.5f);
                button.addActionListener(e -> applyFix(a));
                row.add(button, "skip 1, h 34!, growx 0, alignx left");
            }
            pnlAdvice.add(row);
        }
        pnlAdvice.revalidate();
    }
}
