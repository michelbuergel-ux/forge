package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.BAD;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_EDGE;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.HEADING;
import static forge.screens.home.hexcrawl.HexTownStyle.MUTED;
import static forge.screens.home.hexcrawl.HexTownStyle.PARCHMENT_INK;
import static forge.screens.home.hexcrawl.HexTownStyle.PARCHMENT_SOFT;
import static forge.screens.home.hexcrawl.HexTownStyle.REWARD;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT_BRIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.WANTED;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.caps;
import static forge.screens.home.hexcrawl.HexTownStyle.decorative;
import static forge.screens.home.hexcrawl.HexTownStyle.display;
import static forge.screens.home.hexcrawl.HexTownStyle.label;
import static forge.screens.home.hexcrawl.HexTownStyle.tracked;
import static forge.screens.home.hexcrawl.HexTownStyle.wrapped;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexQuest;
import forge.gamemodes.hexcrawl.HexRelic;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.gamemodes.hexcrawl.HexShopType;
import forge.gamemodes.hexcrawl.HexTheme;
import forge.gamemodes.hexcrawl.HexTile;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.screens.home.hexcrawl.HexTownStyle.Badge;
import forge.screens.home.hexcrawl.HexTownStyle.Banner;
import forge.screens.home.hexcrawl.HexTownStyle.CardFace;
import forge.screens.home.hexcrawl.HexTownStyle.CounterTile;
import forge.screens.home.hexcrawl.HexTownStyle.GildedPanel;
import forge.screens.home.hexcrawl.HexTownStyle.Glyph;
import forge.screens.home.hexcrawl.HexTownStyle.GoldButton;
import forge.screens.home.hexcrawl.HexTownStyle.GoldChip;
import forge.screens.home.hexcrawl.HexTownStyle.NavItem;
import forge.screens.home.hexcrawl.HexTownStyle.Notice;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import forge.screens.home.hexcrawl.HexTownStyle.Pill;
import forge.screens.home.hexcrawl.HexTownStyle.PillTray;
import forge.screens.home.hexcrawl.HexTownStyle.ShadowLabel;
import forge.screens.home.hexcrawl.HexTownStyle.Shield;
import forge.screens.home.hexcrawl.HexTownStyle.ShopTile;
import forge.screens.home.hexcrawl.HexTownStyle.Sidebar;
import forge.screens.home.hexcrawl.HexTownStyle.ThemePill;
import forge.screens.home.hexcrawl.HexTownStyle.TileListener;
import forge.screens.home.hexcrawl.HexTownStyle.WindowPanel;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSpinner;
import forge.util.Localizer;
import forge.view.FDialog;
import net.miginfocom.swing.MigLayout;

/**
 * A town, in its own colours: a banner with the town's arms, the merchants and the rest of the town in a sidebar,
 * one shop per kind of card (the cards shown as pictures, with a big preview), a dealer in relics, the notice board
 * with tasks, and a counter where cards of the own deck and sideboard are sold or given to team mates.
 * Everybody trades for their own head; the host also looks after heads nobody plays.
 */
final class HexTownDialog {
    private static final String TAB_QUESTS = "QUESTS";
    private static final String TAB_SELL = "SELL";
    private static final String TAB_TRADE = "TRADE";
    private static final String PAGE_CARDS = "cards";
    private static final int SIDEBAR_W = 228;
    private static final int PREVIEW_W = 290;
    private static final int COLUMNS = 4;
    private static final int COUNTER_COLUMNS = 6;
    private static final int GAP = 18;

    private final Localizer loc = Localizer.getInstance();
    private final HexContent content = HexContent.get();
    private final HexSession session;
    private final List<String> heads = new ArrayList<>();
    private int headIndex;

    private Palette palette;
    private FDialog dialog;
    /** Width of the pages next to the sidebar, and of what sits in their columns. */
    private int pageW;
    private int tileW;
    private int counterW;
    private int relicW;
    private int questW;

    private final List<Pill> headPills = new ArrayList<>();
    /** The others: who is in the shops too, and who wants to move on. */
    private final JLabel lblParty = new JLabel(" ");
    /** Names what a vote is for (see HexCrawlPanel.describeVote). */
    private Function<String, String> voteNames = key -> "?";
    private GoldChip goldChip;
    private final JLabel lblMessage = new JLabel(" ");
    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private final CardLayout pages = new CardLayout();
    private final JPanel body = new JPanel(pages);
    private String tab = HexShopType.CREATURE.name();

    // the sections of a shop: the town's theme, the random one and the two the head picked (HexCampaign.TAB_TOWN,
    // TAB_RANDOM, TAB_PICKED + slot); one chip each over the page jumps to it, lit while it is in view
    private static final int SECTIONS = HexCampaign.TAB_PICKED + HexCampaign.PICKED_THEMES;
    private final ThemePill[] themePills = new ThemePill[SECTIONS];
    private GoldButton btnRestock;
    /** The section the page shows at its top (its chip is lit). */
    private int sectionInView = HexCampaign.TAB_TOWN;
    /** A section to scroll to once the page is built again (after a theme was picked), or -1. */
    private int scrollToSection = -1;
    private final Map<Integer, JComponent> sectionHeads = new LinkedHashMap<>();
    private FScrollPane tilesScroll;
    /** A picked tab whose new theme a guest has asked for, until the host's answer arrives. */
    private int pendingPickTab = -1;

    // card shops
    private final JPanel pnlTiles = transparent(new MigLayout("insets 0 2 6 2, gap " + GAP + " 10, wrap " + COLUMNS, "", "[top]"));
    private final JPanel pnlBasics = transparent(new MigLayout("insets 0, gap 8, aligny center"));
    private String tilesShown = "";
    private final CardFace previewFace = new CardFace();
    private final JLabel lblPreviewInfo = label(" ", body(Font.PLAIN, 12f), MUTED);
    private GoldButton btnPreviewBuy;
    private HexShopType previewType;
    private int previewTab;

    /** Where bought cards go: the deck played or the sideboard (remembered while the program runs). */
    private static boolean buyToSideboard;
    private final List<Pill> buyIntoPills = new ArrayList<>();
    /** The last purchase, while it can still be undone (sold back at what it cost); null if none. */
    private record LastBuy(String head, String card, int price, boolean toSideboard) { }
    private LastBuy lastBuy;
    private GoldButton btnUndo;

    /** The search over all merchants and sections of the town; while it holds text the page shows what it finds. */
    private final HexTownStyle.SearchField search = new HexTownStyle.SearchField(Localizer.getInstance().getMessage("lblHexTownSearch"));
    private HexShopOffer previewOffer;

    // relic dealer
    private JPanel pnlRelics;
    private String relicsShown = "";

    // notice board
    private JPanel pnlQuests;
    private final JLabel lblQuestActive = label(" ", body(Font.PLAIN, 13f), MUTED);
    private String questsShown = "";

    // selling cards
    private JPanel pnlSale;
    private String saleShown = "";

    /** Which cards the sell and trade pages show: all, the deck's or the sideboard's. */
    private static final String SOURCE_ALL = "all";
    private static final String SOURCE_DECK = "deck";
    private static final String SOURCE_SIDEBOARD = "sideboard";
    private String source = SOURCE_ALL;
    private final List<Pill> salePills = new ArrayList<>();
    private final List<Pill> tradePills = new ArrayList<>();

    // trading with team mates
    private final PillTray mateTray = new PillTray();
    private final List<Pill> matePills = new ArrayList<>();
    private final List<String> mates = new ArrayList<>();
    private String mate;
    private final FSpinner spnGold = new FSpinner.Builder().minValue(1).maxValue(100000).initialValue(10).build();
    private JPanel pnlTrade;
    private String tradeShown = "";

    HexTownDialog(final HexSession session0) {
        session = session0;
        final String me = session.getMyHead();
        for (final HexPlayer p : campaign().players) {
            if (session.mayActFor(me, p.name)) {
                heads.add(p.name);
            }
        }
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    /** How votes are named in the window. */
    HexTownDialog describingVotes(final Function<String, String> names) {
        voteNames = names;
        return this;
    }

    private HexCampaign campaign() {
        return session.getCampaign();
    }

    private HexPlayer head() {
        final HexCampaign c = campaign();
        return c == null || heads.isEmpty() ? null : c.player(heads.get(Math.min(headIndex, heads.size() - 1)));
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

    /** How wide the shop's scroll bar is (the skin's). */
    private static int scrollBarWidth() {
        return Math.max(12, scroller(new JPanel()).getVerticalScrollBar().getPreferredSize().width);
    }

    private static JLabel title(final String text) {
        return label(text, display(24f), TEXT_BRIGHT);
    }

    private static JLabel heading(final String text) {
        return label(caps(text), tracked(display(12f), 0.2f), HEADING);
    }

    /** The others: who is in the shops too, and who wants to move on (with the vote's clock). */
    private void fillParty() {
        final String me = session.getMyHead();
        final List<String> parts = new ArrayList<>();
        final List<String> shoppers = new ArrayList<>();
        for (final String name : session.getShopping()) {
            if (!name.equals(me)) {
                shoppers.add(name);
            }
        }
        if (!shoppers.isEmpty()) {
            parts.add(t("lblHexAlsoInShops", String.join(", ", shoppers)));
        }
        final Map<String, String> votes = session.getVotes();
        for (final Map.Entry<String, String> vote : votes.entrySet()) {
            if (!vote.getKey().equals(me)) { // one's own vote one knows
                parts.add(t("lblHexWantsToMove", vote.getKey(), voteNames.apply(vote.getValue())));
            }
        }
        String text = String.join("   \u00b7   ", parts);
        final int seconds = session.voteSecondsLeft(System.currentTimeMillis());
        if (!votes.isEmpty() && seconds >= 0) {
            text += "   " + t("lblHexTownVoteClock", String.valueOf(seconds));
        }
        lblParty.setText(text.isEmpty() ? " " : text);
        lblParty.setForeground(votes.isEmpty() ? GOLD_LIGHT : new Color(0xF0A040));
    }

    private void setMessage(final String text, final boolean bad) {
        lastBuy = null; // any other news: the last purchase is no longer undone from here
        if (btnUndo != null) {
            btnUndo.setVisible(false);
        }
        lblMessage.setText(text == null || text.isEmpty() ? " " : text);
        lblMessage.setForeground(bad ? BAD : palette.light());
    }

    /** Shows the town until it is closed. The given hook is told how to refresh the open window. */
    void show(final Consumer<Runnable> refreshHook) {
        if (heads.isEmpty() || campaign() == null || !campaign().inTown()) {
            return;
        }
        final Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        final int width = Math.min(1440, screen.width - 40); // as big as the deck window
        final int height = Math.min(920, screen.height - 40);
        dialog = new FDialog(true, false, "0");
        dialog.getTitleBar().setVisible(false);
        dialog.add(build(width, height), "w 100%!, h 100%!");
        dialog.setSize(width, height);

        refreshHook.accept(this::fill); // what others do arrives a moment later, with the next state from the host
        final javax.swing.Timer clock = new javax.swing.Timer(1000, e -> fillParty()); // the vote's clock runs down
        clock.start();
        try {
            dialog.setVisible(true);
        } finally {
            clock.stop();
            refreshHook.accept(null);
            dialog.dispose();
        }
    }

    /** The whole window, laid out for the given size and filled. */
    JPanel build(final int width, final int height) {
        if (!session.isGuest()) {
            campaign().shop(); // makes sure the shelves are filled; guests get them from the host
        }
        final HexTile town = campaign().tile();
        palette = HexTownStyle.palette(town.color);
        pageW = width - SIDEBAR_W - 2 * 24 - 16; // the page's padding and room for a scroll bar
        // the tiles' room: the page without its padding, the preview and its gap, the tiles' insets, the scroll bar
        final int tilesRoom = width - SIDEBAR_W - 4 - 2 * 24 - 20 - PREVIEW_W - 4 - scrollBarWidth() - 6;
        tileW = Math.max(100, Math.min(200, (tilesRoom - (COLUMNS - 1) * GAP) / COLUMNS));
        counterW = (pageW - (COUNTER_COLUMNS - 1) * 14) / COUNTER_COLUMNS;
        relicW = (pageW - 2 * GAP) / 3;
        questW = (pageW - GAP) / 2;

        final WindowPanel root = new WindowPanel(palette, new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[][grow, fill]"));
        root.add(buildBanner(town), "h " + (Banner.CLOTH + 10) + "!");
        final JPanel center = transparent(new MigLayout("insets 0 2 2 2, gap 0, fill", "[" + SIDEBAR_W + "!, fill][grow, fill]", "[grow, fill]"));
        center.add(buildSidebar());
        final JPanel main = transparent(new MigLayout("insets 6 24 10 24, gap 0, wrap 1, fill", "[grow, fill]", "[][grow, fill][]"));
        lblParty.setFont(body(Font.BOLD, 13f));
        lblParty.setForeground(GOLD_LIGHT);
        main.add(lblParty, "h 22!, gapbottom 4");
        body.setOpaque(false);
        body.add(buildCardsPage(height), PAGE_CARDS);
        body.add(buildRelicPage(), HexShopType.RELIC.name());
        body.add(buildQuestPage(), TAB_QUESTS);
        body.add(buildSellPage(), TAB_SELL);
        body.add(buildTradePage(), TAB_TRADE);
        main.add(body, "wmin 0, hmin 0");
        lblMessage.setFont(body(Font.PLAIN, 13f));
        final JPanel messageRow = transparent(new MigLayout("insets 0, gap 12, fillx, hidemode 3", "[grow, fill][]", "[26!]"));
        messageRow.add(lblMessage, "aligny center");
        btnUndo = new GoldButton(t("lblHexUndo"), GoldButton.Kind.OUTLINE, 11.5f);
        btnUndo.addActionListener(e -> undoLastBuy());
        btnUndo.setVisible(false);
        messageRow.add(btnUndo, "h 26!");
        main.add(messageRow, "gaptop 6");
        center.add(main, "wmin 0, hmin 0");
        root.add(center, "wmin 0, hmin 0");
        setMessage("", false);
        fill();
        return root;
    }

    // ------------------------------------------------------------------ banner and sidebar

    private JPanel buildBanner(final HexTile town) {
        final Banner banner = new Banner(palette, new MigLayout("insets 0 28 10 22, gap 18, aligny center", "[][]push[][][]", "[" + Banner.CLOTH + "!]"));
        banner.add(new Shield(palette, town.color), "w 46!, h 54!");
        final JPanel names = transparent(new MigLayout("insets 0, gap 2, wrap 1"));
        names.add(new ShadowLabel(town.name == null ? t("lblHexTown") : town.name, decorative(28f), TEXT_BRIGHT));
        final HexTheme theme = HexTheme.get(town.theme);
        String subtitle = t("lblHexTownKind" + town.color.getSymbol());
        if (theme != null) {
            subtitle = t("lblHexTownRenowned", subtitle, theme.getName());
        }
        names.add(label(caps(subtitle), tracked(display(13f), 0.15f), palette.light()));
        if (town.freed) {
            // the people the party freed sell cheaper for the rest of the cycle
            names.add(label(t("lblHexTownFreed", String.valueOf(HexCampaign.SIEGE_DISCOUNT_PERCENT)), body(Font.BOLD, 12f), GOLD_LIGHT));
        }
        banner.add(names);

        final PillTray tray = new PillTray();
        for (int i = 0; i < heads.size(); i++) {
            final int index = i;
            final Pill pill = new Pill(heads.get(i));
            pill.addActionListener(e -> selectHead(index));
            headPills.add(pill);
            tray.add(pill, "h 36!");
        }
        banner.add(tray);
        goldChip = new GoldChip(t("lblHexGoldWord"));
        banner.add(goldChip, "h 44!");
        final GoldButton close = new GoldButton("", GoldButton.Kind.OUTLINE, 13f).withGlyph(Glyph.CLOSE);
        close.setToolTipText(t("lblHexLeaveTown"));
        close.addActionListener(e -> dialog.setVisible(false));
        banner.add(close, "w 44!, h 44!");

        // the banner carries the window around
        final MouseAdapter drag = new MouseAdapter() {
            private Point start;
            private Point origin;

            @Override
            public void mousePressed(final MouseEvent e) {
                start = e.getLocationOnScreen();
                origin = dialog.getLocation();
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

    private void selectHead(final int index) {
        if (index != headIndex) {
            headIndex = index;
            previewOffer = null;
            setMessage("", false);
            fill();
        }
    }

    private JPanel buildSidebar() {
        final Sidebar side = new Sidebar(palette, new MigLayout("insets 14 12 14 12, gap 0, wrap 1, fillx", "[grow, fill]"));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(final javax.swing.event.DocumentEvent e) { onSearch(); }
            @Override public void removeUpdate(final javax.swing.event.DocumentEvent e) { onSearch(); }
            @Override public void changedUpdate(final javax.swing.event.DocumentEvent e) { onSearch(); }
        });
        search.setToolTipText(t("lblHexTownSearchHint"));
        side.add(search, "h 32!, gapbottom 14");
        side.add(heading(t("lblHexMerchants")), "gapleft 12, gapbottom 6");
        for (final HexShopType type : HexShopType.values()) {
            if (type != HexShopType.RELIC) {
                addNav(side, type.name(), t(type.getLabelKey()), palette.accent());
            }
        }
        side.add(heading(t("lblHexTheTown")), "gapleft 12, gaptop 12, gapbottom 6");
        addNav(side, HexShopType.RELIC.name(), t("lblHexRelicDealerNav"), GOLD);
        addNav(side, TAB_QUESTS, t("lblHexQuestOffers"), GOLD);
        addNav(side, TAB_SELL, t("lblHexTabSell"), GOLD);
        if (campaign().players.size() > 1) {
            addNav(side, TAB_TRADE, t("lblHexTabTrade"), GOLD);
        }
        return side;
    }

    private void addNav(final JPanel side, final String key, final String text, final Color gem) {
        final NavItem item = new NavItem(text, palette, gem);
        item.addActionListener(e -> {
            tab = key;
            setMessage("", false);
            clearSearch();
            fill();
        });
        navItems.put(key, item);
        side.add(item, "h 44!, gapbottom 4");
    }

    private String query() {
        return search.getText().trim();
    }

    private boolean searching() {
        return !query().isEmpty();
    }

    /** Empties the search field (and so the page shows the merchant chosen again). */
    private void clearSearch() {
        if (!search.getText().isEmpty()) {
            search.setText("");
        }
    }

    private void onSearch() {
        if (searching() && !isCardShop()) {
            tab = HexShopType.CREATURE.name(); // the search shows cards: away from the notice board or the counter
        }
        fill();
    }

    private boolean isCardShop() {
        return !TAB_QUESTS.equals(tab) && !TAB_SELL.equals(tab) && !TAB_TRADE.equals(tab) && !HexShopType.RELIC.name().equals(tab);
    }

    /** The offers of one shop of this town in one section (theme tab). Read only, so it is safe on a guest's copy too. */
    private List<HexShopOffer> offers(final HexShopType type, final int themeTab) {
        final List<HexShopOffer> result = new ArrayList<>();
        final HexCampaign c = campaign();
        final HexPlayer p = head();
        if (c == null || p == null || c.tile().poi != HexPoi.TOWN) {
            return result;
        }
        final String tabKey = HexCampaign.tabKey(p.name, themeTab);
        final List<HexShopOffer> stock = c.shops.get(c.tile().key());
        if (stock != null) {
            for (final HexShopOffer o : stock) {
                if (o.shop == type && (type == HexShopType.RELIC ? HexCampaign.relicTabKey(p.name).equals(o.tab) : tabKey.equals(o.tab))) {
                    result.add(o);
                }
            }
        }
        return result;
    }

    private String navCount(final HexCampaign c, final String key) {
        if (TAB_SELL.equals(key) || TAB_TRADE.equals(key)) {
            return "";
        }
        if (TAB_QUESTS.equals(key)) {
            final List<HexQuest> offers = c.questOffers.get(c.tile().key());
            return String.valueOf(offers == null ? 0 : offers.size());
        }
        if (HexShopType.RELIC.name().equals(key)) {
            return String.valueOf(offers(HexShopType.RELIC, 0).size());
        }
        return ""; // a card shop: always a full page
    }

    // ------------------------------------------------------------------ theme tabs

    private JPanel buildThemeTabs() {
        final JPanel row = transparent(new MigLayout("insets 0, gap 8, aligny center", "", "[46!]"));
        for (int i = 0; i < themePills.length; i++) {
            final int tabIndex = i;
            final String kind = i == HexCampaign.TAB_TOWN ? t("lblHexThemeKindTown")
                    : i == HexCampaign.TAB_RANDOM ? t("lblHexThemeKindRandom") : t("lblHexThemeKindYours");
            final ThemePill pill = new ThemePill(kind, palette);
            pill.addActionListener(e -> onThemePill(tabIndex, pill));
            themePills[i] = pill;
            row.add(pill, "h 46!");
        }
        btnRestock = new GoldButton(t("lblHexRestockShort"), GoldButton.Kind.OUTLINE, 13f).withGlyph(Glyph.RESTOCK);
        btnRestock.setSuffix(String.valueOf(campaign().townPrice(HexCampaign.RESTOCK_PRICE)));
        btnRestock.setToolTipText(t("lblHexRestockHint"));
        btnRestock.addActionListener(e -> onRestock());
        row.add(btnRestock, "pushx, alignx right, h 42!");
        return row;
    }

    private void onThemePill(final int tabIndex, final ThemePill pill) {
        if (tabIndex >= HexCampaign.TAB_PICKED && (themeId(tabIndex) == null || pill.pressedArrow())) {
            openThemeMenu(tabIndex - HexCampaign.TAB_PICKED, pill);
        } else if (searching()) {
            scrollToSection = tabIndex; // back to the merchant's page, at this section
            clearSearch();
        } else {
            scrollTo(tabIndex);
        }
    }

    /** The themes a head may pick for one of its sections: grouped, with a search field, under what was clicked. */
    private void openThemeMenu(final int slot, final JComponent anchor) {
        final HexPlayer p = head();
        if (p == null) {
            return;
        }
        if (session.isFightRunning() || !campaign().mayPickTheme(p.name, slot)) {
            setMessage(t("lblHexThemeLocked"), true);
            return;
        }
        if (t("lblHexThemeLocked").equals(lblMessage.getText())) {
            setMessage("", false); // said of another section
        }
        final HexColor color = campaign().tile().color;
        new HexThemePicker(() -> palette, t("lblHexThemePickTitle", String.valueOf(slot + 1)), campaign().themeChoices(), p.themes[slot],
                theme -> content.themeCardCount(color, theme), theme -> onThemePicked(slot, theme)).show(anchor);
    }

    /** A head pays for new shelves in every tab of the town (and new relics). */
    private void onRestock() {
        final HexPlayer p = head();
        if (p == null) {
            return;
        }
        if (p.gold < campaign().townPrice(HexCampaign.RESTOCK_PRICE)) {
            setMessage(t("lblHexNotEnoughGold"), true);
        } else if (FOptionPane.showConfirmDialog(t("lblHexRestockConfirm", p.name, String.valueOf(campaign().townPrice(HexCampaign.RESTOCK_PRICE))), t("lblHexTown"))) {
            setMessage("", false);
            previewOffer = null;
            session.restock(p.name);
        }
        fill();
    }

    /** The theme of a tab: 0 the town's, 1 the random one, 2 and 3 the ones the head picked; null if none. */
    private String themeId(final int tabIndex) {
        final HexCampaign c = campaign();
        final HexPlayer p = head();
        if (c == null || c.tile().poi != HexPoi.TOWN) {
            return null;
        }
        switch (tabIndex) {
            case 0: return c.tile().theme;
            case 1: return HexCampaign.ANY_CARDS;
            default: return p == null ? null : p.themes[tabIndex - 2];
        }
    }

    /** Scrolls the page so that the section starts at its top. */
    private void scrollTo(final int section) {
        final JComponent head = sectionHeads.get(section);
        if (head == null || tilesScroll == null) {
            return;
        }
        final int y = section == HexCampaign.TAB_TOWN ? 0 : head.getY();
        tilesScroll.getViewport().setViewPosition(new Point(0, Math.max(0, Math.min(y,
                pnlTiles.getHeight() - tilesScroll.getViewport().getHeight()))));
        sectionInView = section;
        fillThemes(head());
    }

    /** Where a section starts on the page (the first one at the very top). */
    private int sectionTop(final int section) {
        final JComponent head = sectionHeads.get(section);
        return head == null || section == HexCampaign.TAB_TOWN ? 0 : head.getY();
    }

    /** Scrolls the page to a section and so far down in it, but not past its end. */
    private void scrollInto(final int section, final int down) {
        if (tilesScroll == null || !sectionHeads.containsKey(section)) {
            return;
        }
        final int top = sectionTop(section);
        int end = pnlTiles.getHeight();
        for (final Map.Entry<Integer, JComponent> e : sectionHeads.entrySet()) {
            if (e.getKey() > section) {
                end = e.getValue().getY();
                break;
            }
        }
        final int y = top + Math.min(down, Math.max(0, end - top - 160)); // some of the section stays in sight
        tilesScroll.getViewport().setViewPosition(new Point(0, Math.max(0, Math.min(y,
                pnlTiles.getHeight() - tilesScroll.getViewport().getHeight()))));
        sectionInView = section;
        spySection();
        fillThemes(head());
    }

    /** Which section is at the top of the page: the last one whose heading has scrolled past the top. */
    private void spySection() {
        if (tilesScroll == null || sectionHeads.isEmpty()) {
            return;
        }
        final int top = tilesScroll.getViewport().getViewPosition().y + 40;
        final boolean atEnd = top - 40 + tilesScroll.getViewport().getHeight() >= pnlTiles.getHeight() - 2;
        int in = HexCampaign.TAB_TOWN;
        for (final Map.Entry<Integer, JComponent> e : sectionHeads.entrySet()) {
            if (e.getValue().getY() <= top) {
                in = e.getKey();
            }
        }
        if (atEnd && top > 40) { // scrolled to the end: the last section, even when it is too short to reach the top
            for (final Integer key : sectionHeads.keySet()) {
                in = key;
            }
        }
        if (in != sectionInView) {
            sectionInView = in;
            fillThemes(head());
        }
    }

    private void onThemePicked(final int slot, final HexTheme theme) {
        final HexPlayer p = head();
        if (p == null || theme == null) {
            return;
        }
        if (theme.id.equals(p.themes[slot])) {
            scrollTo(HexCampaign.TAB_PICKED + slot);
            return;
        }
        if (session.isFightRunning() || !campaign().mayPickTheme(p.name, slot)) {
            setMessage(t("lblHexThemeLocked"), true);
        } else {
            setMessage("", false);
            pendingPickTab = HexCampaign.TAB_PICKED + slot;
            scrollToSection = pendingPickTab;
            previewOffer = null;
            session.pickTheme(p.name, slot, theme.id);
        }
        fill();
    }

    private void fillThemes(final HexPlayer p) {
        if (p == null) {
            return;
        }
        final String[] ids = { themeId(0), themeId(1), themeId(2), themeId(3) };
        if (pendingPickTab >= 0 && pendingPickTab < ids.length && ids[pendingPickTab] != null) {
            pendingPickTab = -1;
        }
        for (int i = 0; i < themePills.length; i++) {
            if (i == HexCampaign.TAB_RANDOM) { // no theme: any cards of the town
                themePills[i].set(t("lblHexThemeAnyCards"), i == sectionInView && !searching(), false);
                themePills[i].setEnabled(true);
                themePills[i].setToolTipText(t("lblHexThemeAnyCardsHint"));
                continue;
            }
            final HexTheme theme = HexTheme.get(ids[i]);
            final boolean picked = i >= HexCampaign.TAB_PICKED;
            String text = theme == null ? (picked ? t("lblHexThemeChooseShort") : "–") : theme.getName();
            if (theme == null && i == pendingPickTab) {
                text = "…";
            }
            themePills[i].set(text, i == sectionInView && !searching(), picked);
            themePills[i].setEnabled(theme != null || picked);
            if (picked) {
                final boolean mayPick = !session.isFightRunning() && campaign().mayPickTheme(p.name, i - HexCampaign.TAB_PICKED);
                themePills[i].setToolTipText(mayPick ? t("lblHexThemePickHint") : t("lblHexThemeLocked"));
            } else {
                themePills[i].setToolTipText(theme == null ? null : theme.getLabel());
            }
        }
        btnRestock.setEnabled(!session.isFightRunning());
    }

    // ------------------------------------------------------------------ refresh

    private void fill() {
        final HexCampaign c = campaign();
        final HexPlayer p = head();
        if (c == null || p == null) {
            return;
        }
        if (!c.inTown() && dialog != null && dialog.isVisible()) {
            dialog.setVisible(false); // the party moved on: the shops are behind it
            return;
        }
        fillParty();
        for (int i = 0; i < headPills.size(); i++) {
            headPills.get(i).setActive(i == headIndex);
        }
        goldChip.setAmount(p.gold);
        String purse = t("lblHexGoldAndCards", String.valueOf(p.gold), String.valueOf(p.deck.size()));
        if (!p.relics.isEmpty()) {
            purse += "  –  " + t("lblHexRelicsCarried", String.join(", ", p.relics));
        }
        goldChip.setToolTipText(purse);
        fillThemes(p);
        for (final Map.Entry<String, NavItem> e : navItems.entrySet()) {
            e.getValue().setActive(e.getKey().equals(tab));
            e.getValue().setCount(navCount(c, e.getKey()));
        }
        if (searching()) {
            for (final NavItem item : navItems.values()) {
                item.setActive(false);
            }
            fillSearch(query());
            pages.show(body, PAGE_CARDS);
        } else if (isCardShop()) {
            fillTiles(HexShopType.valueOf(tab));
            pages.show(body, PAGE_CARDS);
        } else {
            if (HexShopType.RELIC.name().equals(tab)) {
                fillRelics(p);
            } else if (TAB_QUESTS.equals(tab)) {
                fillQuests(c);
            } else if (TAB_TRADE.equals(tab)) {
                fillTrade(p);
            } else {
                fillDeck(p);
            }
            pages.show(body, tab);
        }
        body.revalidate();
        body.repaint();
    }

    // ------------------------------------------------------------------ card shops

    private JPanel buildCardsPage(final int height) {
        final JPanel page = transparent(new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[]14[grow, fill]"));
        page.add(buildThemeTabs(), "h 48!");
        final JPanel lower = transparent(new MigLayout("insets 0, gap 20, fill", "[grow, fill][" + PREVIEW_W + "!, fill]", "[grow]"));
        final JPanel left = transparent(new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[grow, fill][]"));
        tilesScroll = scroller(pnlTiles);
        tilesScroll.getViewport().addChangeListener(e -> spySection());
        left.add(tilesScroll, "wmin 0, hmin 0");
        pnlBasics.add(label(t("lblHexBasicLands"), body(Font.PLAIN, 13f), MUTED));
        for (final HexColor color : HexColor.COLORS) {
            final GoldButton b = new GoldButton(color.getBasicLand(), GoldButton.Kind.OUTLINE, 12f);
            b.setSuffix(String.valueOf(campaign().townPrice(HexCampaign.BASIC_LAND_PRICE)));
            b.addActionListener(e -> {
                final HexPlayer p = head();
                if (p != null && p.gold < campaign().townPrice(HexCampaign.BASIC_LAND_PRICE)) {
                    setMessage(t("lblHexNotEnoughGold"), true);
                } else if (p != null) {
                    setMessage(t("lblHexBoughtCard", p.name, color.getBasicLand(), String.valueOf(campaign().townPrice(HexCampaign.BASIC_LAND_PRICE))), false);
                    session.buyLand(p.name, color);
                }
                fill();
            });
            pnlBasics.add(b, "h 34!");
        }
        left.add(pnlBasics, "hidemode 3, gaptop 8");
        lower.add(left, "grow, wmin 0, hmin 0");
        final JPanel side = new JPanel(new java.awt.BorderLayout()); // the preview as high as it needs, at the top
        side.setOpaque(false);
        side.add(buildPreview(height), java.awt.BorderLayout.NORTH);
        lower.add(side, "growy");
        page.add(lower, "wmin 0, hmin 0");
        return page;
    }

    /** The big picture of the card pointed at, what it is, and the button to buy it. */
    private JPanel buildPreview(final int height) {
        final GildedPanel panel = new GildedPanel(palette, new MigLayout("insets 18 18 24 18, gap 0, wrap 1", "[grow, fill]"));
        // the card as big as the panel's width allows, unless the window is too low for it
        final int faceH = Math.min((int) Math.round((PREVIEW_W - 36) * 88 / 63.0), height - 94 - 16 - 30 - 62 - 140);
        final int faceW = (int) Math.round(faceH * 63 / 88.0);
        panel.add(previewFace, "w " + faceW + "!, h " + faceH + "!, alignx center");
        panel.add(lblPreviewInfo, "gaptop 12");
        btnPreviewBuy = new GoldButton(t("lblHexBuy"), GoldButton.Kind.GOLD, 15f);
        btnPreviewBuy.addActionListener(e -> {
            if (previewOffer != null) {
                buy(previewType, previewTab, previewOffer);
            }
        });
        panel.add(btnPreviewBuy, "h 48!, gaptop 12");
        final JPanel into = transparent(new MigLayout("insets 0, gap 10, aligny center", "[][]", "[36!]"));
        into.add(label(t("lblHexBuyInto"), body(Font.PLAIN, 13f), MUTED));
        final PillTray tray = new PillTray();
        for (final boolean side : new boolean[] { false, true }) {
            final Pill pill = new Pill(t(side ? "lblHexBuyIntoSide" : "lblHexBuyIntoDeck"));
            pill.addActionListener(e -> {
                buyToSideboard = side;
                fillBuyInto();
            });
            buyIntoPills.add(pill);
            tray.add(pill, "h 28!");
        }
        into.add(tray);
        panel.add(into, "gaptop 10, alignx center");
        fillBuyInto();
        return panel;
    }

    private void fillBuyInto() {
        for (int i = 0; i < buyIntoPills.size(); i++) {
            buyIntoPills.get(i).setActive((i == 1) == buyToSideboard);
        }
    }

    /** The name of a section: its kind (the town's, any cards, yours) and its theme. */
    private String sectionTitle(final int section) {
        if (section == HexCampaign.TAB_RANDOM) {
            return t("lblHexThemeKindRandom") + " · " + t("lblHexThemeAnyCards");
        }
        final HexTheme theme = HexTheme.get(themeId(section));
        final String kind = t(section == HexCampaign.TAB_TOWN ? "lblHexThemeKindTown" : "lblHexThemeKindYours");
        if (theme == null) {
            return kind + " · " + (section == pendingPickTab ? "…" : t("lblHexThemeChooseShort"));
        }
        return kind + " · " + theme.getName();
    }

    /** The heading of a section; a section of the head's own has a button to pick (another) theme. */
    private JComponent sectionHead(final int section) {
        final JPanel row = transparent(new MigLayout("insets 0, gap 12, fillx", "[grow, fill][]", "[30!]"));
        row.add(new HexSidebarParts.Heading(sectionTitle(section)), "aligny center");
        if (section >= HexCampaign.TAB_PICKED) {
            final int slot = section - HexCampaign.TAB_PICKED;
            final boolean has = themeId(section) != null;
            final HexPlayer p = head();
            final boolean mayPick = p != null && !session.isFightRunning() && campaign().mayPickTheme(p.name, slot);
            final GoldButton change = new GoldButton(t(!mayPick ? "lblHexThemeLockedShort" : has ? "lblHexThemeChange" : "lblHexThemeChooseShort"),
                    GoldButton.Kind.OUTLINE, 11.5f);
            if (!mayPick) { // locked until the next fight: a padlock, and no click
                change.withGlyph(HexTownStyle.Glyph.LOCK);
                change.setEnabled(false);
            }
            change.setToolTipText(mayPick ? t("lblHexThemePickHint") : t("lblHexThemeLocked"));
            change.addActionListener(e -> openThemeMenu(slot, change));
            row.add(change, "h 28!");
        }
        return row;
    }

    /**
     * The page of one shop: every section under its heading, one after the other (the town's theme, any cards, the
     * head's two themes). Pointing at a card shows it big, a click buys it; a tile shows the price and the copies owned.
     */
    private void fillTiles(final HexShopType type) {
        pnlBasics.setVisible(type == HexShopType.LAND);
        final HexPlayer buyer = head();
        final List<List<HexShopOffer>> sections = new ArrayList<>();
        final StringBuilder shown = new StringBuilder(type.name()).append('/').append(buyer.name).append('/').append(buyer.gold)
                .append('/').append(buyer.deck.size()).append('/').append(buyer.sideboard.size()).append('/').append(pendingPickTab)
                .append('/').append(session.isFightRunning());
        for (int section = 0; section < SECTIONS; section++) {
            final List<HexShopOffer> list = offers(type, section);
            sections.add(list);
            shown.append('#').append(themeId(section));
            if (section >= HexCampaign.TAB_PICKED) {
                shown.append('/').append(campaign().mayPickTheme(buyer.name, section - HexCampaign.TAB_PICKED));
            }
            for (final HexShopOffer o : list) {
                shown.append('|').append(o.card).append('=').append(price(buyer, o));
            }
        }
        // the card shown big: the one pointed at last, while it is still on offer; else the first one of the page
        HexShopOffer preview = null;
        int previewSection = 0;
        for (int section = 0; section < SECTIONS && preview == null; section++) {
            for (final HexShopOffer o : sections.get(section)) {
                if (previewOffer != null && type == previewType && section == previewTab && o.card.equals(previewOffer.card)) {
                    preview = o;
                    previewSection = section;
                }
            }
        }
        for (int section = 0; section < SECTIONS && preview == null; section++) {
            if (!sections.get(section).isEmpty()) {
                preview = sections.get(section).get(0);
                previewSection = section;
            }
        }
        showPreview(type, previewSection, preview);
        if (!shown.toString().equals(tilesShown)) {
            final boolean newShop = !tilesShown.startsWith(type.name() + "/");
            // another merchant: the same section, as far down in it as before (where the new one's section reaches)
            int keep = -1;
            int keepOffset = 0;
            if (newShop && !tilesShown.isEmpty() && !tilesShown.startsWith("?") && tilesScroll != null
                    && sectionHeads.containsKey(sectionInView)) {
                keep = sectionInView;
                keepOffset = Math.max(0, tilesScroll.getViewport().getViewPosition().y - sectionTop(sectionInView));
            }
            final int keepSection = keep;
            final int keepDown = keepOffset;
            tilesShown = shown.toString();
            pnlTiles.removeAll();
            sectionHeads.clear();
            for (int section = 0; section < SECTIONS; section++) {
                final int tab = section;
                final JComponent head = sectionHead(section);
                sectionHeads.put(section, head);
                pnlTiles.add(head, (section == 0 ? "" : "newline, gaptop 18, ") + "span, growx, gapbottom 2");
                final List<HexShopOffer> list = sections.get(section);
                if (themeId(section) == null) {
                    // a section of the head's own without a theme yet: what it is for
                    pnlTiles.add(wrapped(t(section == pendingPickTab ? "lblHexThemeComing" : "lblHexThemeInvite"), body(Font.PLAIN, 14f), MUTED,
                            pageW - PREVIEW_W - 60), "span, gapleft 4");
                    continue;
                }
                if (list.isEmpty()) {
                    // a theme that sells only fitting cards may simply have none of this kind here
                    final HexTheme theme = HexTheme.get(themeId(section));
                    pnlTiles.add(label(t(theme != null && theme.isStrict() ? "lblHexNothingInTheme" : "lblHexNothingOnOffer"),
                            body(Font.PLAIN, 14f), MUTED), "span, gapleft 4");
                }
                for (final HexShopOffer o : list) {
                    addTile(buyer, type, tab, o);
                }
            }
            pnlTiles.revalidate();
            pnlTiles.repaint();
            final int target = scrollToSection;
            scrollToSection = -1;
            if (newShop || target >= 0) { // another shop starts at its top; a theme just picked is shown
                SwingUtilities.invokeLater(() -> {
                    if (tilesScroll != null) {
                        tilesScroll.validate(); // the page at its new height first
                    }
                    pnlTiles.validate();
                    if (target >= 0) {
                        scrollTo(target);
                    } else if (keepSection >= 0) {
                        scrollInto(keepSection, keepDown);
                    } else if (tilesScroll != null) {
                        tilesScroll.getViewport().setViewPosition(new Point(0, 0));
                    }
                });
            }
        }
    }

    /** One card on offer: its picture (pointing at it shows it big, a click buys it), its price and the copies in the deck. */
    private void addTile(final HexPlayer buyer, final HexShopType type, final int section, final HexShopOffer o) {
        final int price = price(buyer, o);
        final int inDeck = Collections.frequency(buyer.deck, o.card);
        pnlTiles.add(new ShopTile(content.getCard(o.card, o.theme), tileW, String.valueOf(price),
                inDeck > 0 ? t("lblHexInDeck", String.valueOf(inDeck)) : null, buyer.gold < price, new TileListener() {
                    @Override
                    public void hovered() {
                        showPreview(type, section, o);
                    }

                    @Override
                    public void clicked() {
                        buy(type, section, o);
                    }
                }));
    }

    /** One offer found by the search: where it is sold (merchant and section) and the offer. */
    private record Found(HexShopType type, int section, HexShopOffer offer) { }

    /**
     * What the search finds: every card of every merchant and section of this head's shelves that matches, under one
     * heading per merchant; a card sold in two sections once.
     */
    private void fillSearch(final String query) {
        pnlBasics.setVisible(false);
        final HexPlayer buyer = head();
        final Map<HexShopType, List<Found>> byShop = new LinkedHashMap<>();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        final StringBuilder shown = new StringBuilder("?").append(query).append('/').append(buyer.name).append('/').append(buyer.gold)
                .append('/').append(buyer.deck.size()).append('/').append(buyer.sideboard.size());
        for (final HexShopType type : HexShopType.CARD_SHOPS) {
            for (int section = 0; section < SECTIONS; section++) {
                for (final HexShopOffer o : offers(type, section)) {
                    if (forge.gamemodes.hexcrawl.HexCardSearch.matches(content.getCard(o.card), query) && seen.add(type + "|" + o.card)) {
                        byShop.computeIfAbsent(type, k -> new ArrayList<>()).add(new Found(type, section, o));
                        shown.append('|').append(o.card).append('=').append(price(buyer, o));
                    }
                }
            }
        }
        // the card shown big: the one pointed at last, while the search still finds it; else the first one found
        Found preview = null;
        Found firstFound = null;
        for (final List<Found> list : byShop.values()) {
            for (final Found f : list) {
                firstFound = firstFound == null ? f : firstFound;
                if (previewOffer != null && f.type() == previewType && f.section() == previewTab && f.offer().card.equals(previewOffer.card)) {
                    preview = f;
                }
            }
        }
        preview = preview == null ? firstFound : preview;
        if (preview == null) {
            showPreview(null, 0, null);
        } else {
            showPreview(preview.type(), preview.section(), preview.offer());
        }
        if (shown.toString().equals(tilesShown)) {
            return;
        }
        final boolean newSearch = !tilesShown.startsWith("?");
        tilesShown = shown.toString();
        pnlTiles.removeAll();
        sectionHeads.clear();
        boolean first = true;
        for (final Map.Entry<HexShopType, List<Found>> e : byShop.entrySet()) {
            pnlTiles.add(new HexSidebarParts.Heading(t(e.getKey().getLabelKey())), (first ? "" : "newline, gaptop 18, ") + "span, growx, h 30!, gapbottom 2");
            first = false;
            for (final Found f : e.getValue()) {
                addTile(buyer, f.type(), f.section(), f.offer());
            }
        }
        if (byShop.isEmpty()) {
            pnlTiles.add(label(t("lblHexTownSearchNone"), body(Font.PLAIN, 14f), MUTED), "span, gapleft 4");
        }
        pnlTiles.revalidate();
        pnlTiles.repaint();
        if (newSearch && tilesScroll != null) {
            SwingUtilities.invokeLater(() -> tilesScroll.getViewport().setViewPosition(new Point(0, 0)));
        }
    }

    private void showPreview(final HexShopType type, final int section, final HexShopOffer o) {
        previewType = type;
        previewTab = section;
        previewOffer = o;
        final HexPlayer p = head();
        if (o == null || p == null) {
            previewFace.setCard(null);
            lblPreviewInfo.setText(" ");
            btnPreviewBuy.setText(t("lblHexBuy"));
            btnPreviewBuy.setEnabled(false);
            return;
        }
        final PaperCard card = content.getCard(o.card, o.theme);
        previewFace.setCard(card);
        final int sideboard = Collections.frequency(p.sideboard, o.card);
        String info = t("lblHexPreviewInfo", rarityName(card), String.valueOf(Collections.frequency(p.deck, o.card)));
        if (sideboard > 0) {
            info += " · " + t("lblHexPreviewSideboard", String.valueOf(sideboard));
        }
        lblPreviewInfo.setText(info);
        final int price = price(p, o);
        btnPreviewBuy.setText(t("lblHexBuyForGold", String.valueOf(price)));
        btnPreviewBuy.setEnabled(true);
        btnPreviewBuy.setDear(p.gold < price);
    }

    private String rarityName(final PaperCard card) {
        if (card == null || card.getRarity() == null) {
            return "";
        }
        switch (card.getRarity()) {
            case Common: case Uncommon: case Rare: case MythicRare: case Special: case BasicLand:
                return t("lblHexRarity" + card.getRarity().name());
            default:
                return card.getRarity().getLongName();
        }
    }

    private void buy(final HexShopType type, final int section, final HexShopOffer o) {
        final HexPlayer p = head();
        if (p == null) {
            return;
        }
        final int price = price(p, o);
        if (p.gold < price) {
            setMessage(t("lblHexNotEnoughGold"), true);
        } else {
            setMessage(t(buyToSideboard ? "lblHexBoughtCardSide" : "lblHexBoughtCard", p.name, o.card, String.valueOf(price)), false);
            session.buy(p.name, type, section, o.card, buyToSideboard);
            lastBuy = new LastBuy(p.name, o.card, price, buyToSideboard);
            btnUndo.setVisible(true);
        }
        fill();
    }

    /** The last purchase sold back at what it cost (the campaign does that for cards bought during this visit). */
    private void undoLastBuy() {
        final LastBuy last = lastBuy;
        final HexPlayer p = last == null ? null : campaign().player(last.head());
        if (p == null) {
            return;
        }
        final boolean possible = last.toSideboard() ? p.sideboard.contains(last.card())
                : p.deck.contains(last.card()) && p.deck.size() > HexCampaign.MIN_DECK_SIZE;
        if (!possible) {
            setMessage(t("lblHexUndoFailed", last.card()), true);
        } else {
            session.sell(p.name, last.card(), last.toSideboard());
            setMessage(t("lblHexUndone", last.card(), String.valueOf(last.price())), false);
        }
        fill();
    }

    /** What the head pays for an offer (its discount perks included). */
    private int price(final HexPlayer p, final HexShopOffer o) {
        return p == null ? o.price : campaign().priceFor(p, o.price);
    }

    // ------------------------------------------------------------------ relics

    private JPanel buildRelicPage() {
        final JPanel page = transparent(new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[]14[grow, fill]"));
        page.add(title(t("lblHexRelicDealer")));
        final String col = "[" + relicW + "!, fill]";
        pnlRelics = transparent(new MigLayout("insets 0 0 8 0, gap " + GAP, col + col + col, "[top]"));
        page.add(scroller(pnlRelics), "wmin 0, hmin 0");
        return page;
    }

    private String slotName(final String slot) {
        return t("lblHexSlot" + slot);
    }

    private void fillRelics(final HexPlayer p) {
        final List<HexShopOffer> offers = offers(HexShopType.RELIC, 0);
        final StringBuilder shown = new StringBuilder(p.name).append('/').append(p.gold).append('/').append(p.relics);
        for (final HexShopOffer o : offers) {
            shown.append('|').append(o.card).append('=').append(price(p, o));
        }
        if (shown.toString().equals(relicsShown)) {
            return;
        }
        relicsShown = shown.toString();
        pnlRelics.removeAll();
        int count = 0;
        for (final HexShopOffer o : offers) {
            final HexRelic relic = content.getRelic(o.card);
            if (relic != null) {
                pnlRelics.add(relicPanel(p, relic, o), "growy" + (++count % 3 == 0 ? ", wrap" : ""));
            }
        }
        if (count == 0) {
            pnlRelics.add(label(t("lblHexNothingOnOffer"), body(Font.PLAIN, 14f), MUTED), "span 3, wrap");
        } else if (count % 3 != 0) {
            pnlRelics.add(new JLabel(), "span, wrap, h 0!");
        }
        if (!p.relics.isEmpty()) {
            pnlRelics.add(heading(t("lblHexYourRelics")), "span 3, wrap, gaptop 10");
            count = 0;
            for (final String name : p.relics) {
                final HexRelic relic = content.getRelic(name);
                if (relic != null) {
                    pnlRelics.add(relicPanel(p, relic, null), "growy" + (++count % 3 == 0 ? ", wrap" : ""));
                }
            }
        }
        pnlRelics.revalidate();
        pnlRelics.repaint();
    }

    /** A relic in a gilded panel: its emblem, name and slot, what it does, and the button to buy or sell it. */
    private JPanel relicPanel(final HexPlayer p, final HexRelic relic, final HexShopOffer offer) {
        final HexRelic old = offer != null ? campaign().relicInSlot(p, relic.slot) : null;
        final GildedPanel panel = new GildedPanel(palette, new MigLayout("insets 20 20 24 20, gap 0, wrap 1, fill", "[grow, fill]",
                old != null ? "[][][grow, top][]" : "[][grow, top][]"));
        // the pictures of what the relic puts into play, if it does: pointing at one shows it big
        final List<IPaperCard> pictures = content.relicPictures(relic, 2);
        final JPanel top = transparent(new MigLayout("insets 0, gap 12, aligny center", "[52!][grow, fill][]"));
        top.add(new Badge(HexTownStyle.slotGlyph(relic.slot), 52, new Color(0x2a2416), GOLD_EDGE, 2f, GOLD_LIGHT), "w 52!, h 52!");
        final JPanel names = transparent(new MigLayout("insets 0, gap 2, wrap 1"));
        names.add(wrapped(relic.name, display(16f), TEXT_BRIGHT, relicW - 40 - 52 - 12 - 12 - 50 * pictures.size()));
        names.add(label(t("lblHexRelicSlot", slotName(relic.slot)), body(Font.PLAIN, 12f), MUTED));
        top.add(names, "wmin 0");
        if (!pictures.isEmpty()) {
            final JPanel thumbs = transparent(new MigLayout("insets 0, gap 6"));
            for (final IPaperCard card : pictures) {
                thumbs.add(HexTownStyle.thumbnail(card), "w 44!, h 61!");
            }
            top.add(thumbs);
        }
        panel.add(top);
        if (old != null) { // the head wears a relic in this slot already: buying swaps them (a trophy is only put away)
            panel.add(new HexTownStyle.NoteBox(old.trophy != null ? t("lblHexRelicPutsAwayTrophy", old.name)
                    : t("lblHexRelicReplaces", old.name, String.valueOf(content.relicSellPrice(old.name))),
                    HexDeckParts.WARN, relicW - 40), "gaptop 12, wmin 0");
        }

        final StringBuilder sb = new StringBuilder(HexCrawlPanel.relicEffects(relic, campaign().cycle));
        if (relic.description != null && !relic.description.isEmpty()) {
            sb.append(relic.description).append('\n');
        }
        panel.add(wrapped(sb.toString(), body(Font.PLAIN, 13f), new Color(0xd6d0c2), relicW - 40 - 8), "gaptop 12, wmin 0");

        final GoldButton button;
        if (offer != null) {
            final int price = price(p, offer);
            final int refund = old == null || old.trophy != null ? 0 : content.relicSellPrice(old.name);
            button = new GoldButton(t("lblHexBuyForGold", String.valueOf(price)), GoldButton.Kind.GOLD, 13f);
            button.setDear(p.gold + refund < price);
            button.addActionListener(e -> buyRelic(relic));
        } else if (relic.trophy != null) {
            // a boss's trophy is not for sale: it is put on and taken off in the deck window
            button = new GoldButton(t("lblHexTrophyNotForSale"), GoldButton.Kind.OUTLINE, 13f);
            button.setEnabled(false);
        } else {
            button = new GoldButton(t("lblHexSellFor", String.valueOf(content.relicSellPrice(relic.name))), GoldButton.Kind.OUTLINE, 13f);
            button.addActionListener(e -> {
                final HexPlayer seller = head();
                if (seller != null) {
                    setMessage(t("lblHexSoldCard", seller.name, relic.name, String.valueOf(content.relicSellPrice(relic.name))), false);
                    session.sellRelic(seller.name, relic.name);
                }
                fill();
            });
        }
        panel.add(button, "h 44!, gaptop 14");
        return panel;
    }

    private void buyRelic(final HexRelic relic) {
        final HexPlayer p = head();
        if (p == null) {
            return;
        }
        final HexRelic old = campaign().relicInSlot(p, relic.slot);
        final int refund = old == null || old.trophy != null ? 0 : content.relicSellPrice(old.name);
        final int price = campaign().priceFor(p, relic.price);
        if (p.gold + refund < price) {
            setMessage(t("lblHexNotEnoughGold"), true);
        } else {
            setMessage(t("lblHexBoughtCard", p.name, relic.name, String.valueOf(price)), false);
            session.buy(p.name, HexShopType.RELIC, -1, relic.name);
        }
        fill();
    }

    // ------------------------------------------------------------------ tasks

    private JPanel buildQuestPage() {
        final JPanel page = transparent(new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[]14[grow, fill]"));
        final JPanel top = transparent(new MigLayout("insets 0, gap 16, aligny bottom"));
        top.add(title(t("lblHexNoticeBoard")));
        top.add(lblQuestActive, "gapbottom 4");
        page.add(top);
        final String col = "[" + questW + "!, fill]";
        pnlQuests = transparent(new MigLayout("insets 4 0 8 4, gap " + GAP, col + col, "[top]"));
        page.add(scroller(pnlQuests), "wmin 0, hmin 0");
        return page;
    }

    private String describe(final HexQuest quest) {
        final HexCampaign c = campaign();
        final int far = HexMap.distance(quest.q, quest.r, c.partyQ, c.partyR);
        final HexEncounter enc = content.questEncounter(quest);
        return t("lblHexQuestDetail", quest.enemyName, String.valueOf(c.enemyLife(enc)), t(quest.color.getLabelKey()),
                String.valueOf(far), String.valueOf(c.gold(quest.gold))) + "\n\n" + HexCrawlPanel.enemyExtras(c, enc, false).trim();
    }

    private void fillQuests(final HexCampaign c) {
        lblQuestActive.setText(t("lblHexQuestActive", String.valueOf(c.quests.size()), String.valueOf(HexCampaign.MAX_ACTIVE_QUESTS)));
        final List<HexQuest> offers = c.questOffers.get(c.tile().key());
        final StringBuilder shown = new StringBuilder().append(c.partyQ).append(',').append(c.partyR);
        if (offers != null) {
            for (final HexQuest quest : offers) {
                shown.append("|o").append(quest.id);
            }
        }
        for (final HexQuest quest : c.quests) {
            shown.append("|a").append(quest.id);
        }
        if (shown.toString().equals(questsShown)) {
            return;
        }
        questsShown = shown.toString();
        pnlQuests.removeAll();
        int count = 0;
        if (offers != null) {
            for (final HexQuest quest : offers) {
                pnlQuests.add(notice(c, quest, true), "growy" + (++count % 2 == 0 ? ", wrap" : ""));
            }
        }
        if (count == 0) {
            pnlQuests.add(label(t("lblHexNoQuests"), body(Font.PLAIN, 14f), MUTED), "span 2, wrap");
        } else if (count % 2 != 0) {
            pnlQuests.add(new JLabel(), "wrap, h 0!");
        }
        if (!c.quests.isEmpty()) {
            pnlQuests.add(heading(t("lblHexQuestActive", String.valueOf(c.quests.size()), String.valueOf(HexCampaign.MAX_ACTIVE_QUESTS))),
                    "span 2, wrap, gaptop 10");
            count = 0;
            for (final HexQuest quest : c.quests) {
                pnlQuests.add(notice(c, quest, false), "growy" + (++count % 2 == 0 ? ", wrap" : ""));
            }
        }
        pnlQuests.revalidate();
        pnlQuests.repaint();
    }

    /** A task on the notice board: who is wanted, where, and the bounty. */
    private JPanel notice(final HexCampaign c, final HexQuest quest, final boolean offer) {
        // the bounty and the button on the right, as wide as they need (the German label is long)
        final JLabel reward = label(t("lblHexGoldAmount", String.valueOf(c.gold(quest.gold))), display(19f), REWARD);
        final GoldButton take = new GoldButton(offer ? t("lblHexQuestAccept") : t("lblHexQuestTaken"), GoldButton.Kind.NOTICE, 12f);
        final int rightW = Math.max(reward.getPreferredSize().width, take.getPreferredSize().width);
        final Notice notice = new Notice(new MigLayout("insets 22 24 28 24, gap 14, aligny center", "[56!][grow, fill][" + rightW + "!, right]"));
        notice.add(new Badge(Glyph.BELL, 56, new Color(0x3a2a22), new Color(0x7a4a2a), 3f, new Color(0xe7b48f)), "w 56!, h 56!");
        final HexEncounter enc = content.questEncounter(quest);
        final int textW = questW - 48 - 56 - 28 - rightW - 8; // what is left for the text
        final JPanel text = transparent(new MigLayout("insets 0, gap 3, wrap 1"));
        text.add(label(caps(t(quest.archenemy ? "lblHexWantedArchenemy" : "lblHexWanted")), tracked(display(12f), 0.25f), WANTED));
        text.add(wrapped(quest.enemyName, display(19f), PARCHMENT_INK, textW));
        final int far = HexMap.distance(quest.q, quest.r, c.partyQ, c.partyR);
        text.add(wrapped(t("lblHexQuestWhere", t(quest.color.getLabelKey()), String.valueOf(far), String.valueOf(c.enemyLife(enc))),
                body(Font.PLAIN, 13f), PARCHMENT_SOFT, textW));
        // what the enemy starts with and the like; not its followers, every enemy has as many
        final String extras = HexCrawlPanel.enemyExtras(c, enc, false).trim();
        if (!extras.isEmpty()) {
            text.add(wrapped(extras, body(Font.PLAIN, 12f), PARCHMENT_SOFT, textW));
        }
        notice.add(text, "wmin 0");
        final JPanel right = transparent(new MigLayout("insets 0, gap 8, wrap 1", "[right]"));
        right.add(reward);
        if (offer) {
            take.addActionListener(e -> {
                if (campaign().quests.size() >= HexCampaign.MAX_ACTIVE_QUESTS) {
                    setMessage(t("lblHexQuestsFull", String.valueOf(HexCampaign.MAX_ACTIVE_QUESTS)), true);
                } else {
                    setMessage("", false);
                    session.acceptQuest(quest.id);
                }
                fill();
            });
        } else {
            take.setSpent(true);
            take.setCursor(Cursor.getDefaultCursor());
        }
        right.add(take, "h 40!, w " + take.getPreferredSize().width + "!");
        notice.add(right);
        notice.setToolTipText(html(describe(quest)));
        return notice;
    }

    private static String html(final String text) {
        return "<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</html>";
    }

    // ------------------------------------------------------------------ selling cards

    private JPanel buildSellPage() {
        final JPanel page = transparent(new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[]14[grow, fill]"));
        final JPanel top = transparent(new MigLayout("insets 0, gap 16, aligny bottom, fillx", "[][]push[]"));
        top.add(title(t("lblHexTabSell")));
        top.add(label(t("lblHexSellHint", String.valueOf(HexCampaign.MIN_DECK_SIZE)), body(Font.PLAIN, 13f), MUTED), "gapbottom 4");
        top.add(sourcePills(salePills));
        page.add(top);
        pnlSale = transparent(new MigLayout("insets 0 0 8 0, gap 14, wrap " + COUNTER_COLUMNS, "", "[top]"));
        page.add(scroller(pnlSale), "wmin 0, hmin 0");
        return page;
    }

    /** One row of the counter: a card name and whether the copies are in the sideboard. */
    private record SaleRow(String card, boolean sideboard, int copies) { }

    private List<SaleRow> saleRows(final HexPlayer p) {
        final List<SaleRow> rows = new ArrayList<>();
        for (final boolean sideboard : new boolean[] { false, true }) {
            final TreeMap<String, Integer> counts = new TreeMap<>();
            for (final String card : sideboard ? p.sideboard : p.deck) {
                counts.merge(card, 1, Integer::sum);
            }
            for (final Map.Entry<String, Integer> e : counts.entrySet()) {
                rows.add(new SaleRow(e.getKey(), sideboard, e.getValue()));
            }
        }
        return rows;
    }

    /** The tray of pills that shows all cards, the deck's or the sideboard's (one per page, the choice is shared). */
    private PillTray sourcePills(final List<Pill> pills) {
        final PillTray tray = new PillTray();
        final String[][] choices = { { SOURCE_ALL, "lblHexFilterAll" }, { SOURCE_DECK, "lblHexDeckList" }, { SOURCE_SIDEBOARD, "lblHexSideboardTab" } };
        for (final String[] choice : choices) {
            final Pill pill = new Pill(t(choice[1]));
            pill.putClientProperty("source", choice[0]);
            pill.addActionListener(e -> {
                source = choice[0];
                fill();
            });
            pills.add(pill);
            tray.add(pill, "h 30!");
        }
        return tray;
    }

    /** The cards of the counter the filter lets through. */
    private List<SaleRow> shownRows(final HexPlayer p, final List<Pill> pills) {
        for (final Pill pill : pills) {
            pill.setActive(source.equals(pill.getClientProperty("source")));
        }
        final List<SaleRow> rows = new ArrayList<>();
        for (final SaleRow row : saleRows(p)) {
            if (SOURCE_ALL.equals(source) || row.sideboard() == SOURCE_SIDEBOARD.equals(source)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private String copies(final SaleRow row) {
        return t(row.sideboard() ? "lblHexCountSideboard" : "lblHexCountDeck", String.valueOf(row.copies()));
    }

    private void fillDeck(final HexPlayer p) {
        final List<SaleRow> rows = shownRows(p, salePills);
        final String shown = p.name + '/' + rows;
        if (shown.equals(saleShown)) {
            return;
        }
        saleShown = shown;
        pnlSale.removeAll();
        for (final SaleRow row : rows) {
            // a card bought during this visit goes back for what it cost
            final int price = campaign().sellBackPrice(p, row.card());
            final boolean back = !row.sideboard() && campaign().isBoughtHere(p, row.card());
            final CounterTile tile = new CounterTile(content.getCard(row.card()), row.card(), counterW, palette,
                    back ? copies(row) + " \u00b7 " + t("lblHexSellBackMark") : copies(row), "+" + price,
                    new TileListener() {
                        @Override
                        public void hovered() { }

                        @Override
                        public void clicked() {
                            sell(row);
                        }
                    });
            tile.setToolTipText(row.card());
            pnlSale.add(tile);
        }
        pnlSale.revalidate();
        pnlSale.repaint();
    }

    private void sell(final SaleRow row) {
        final HexPlayer p = head();
        if (p == null) {
            return;
        }
        if (!row.sideboard() && p.deck.size() <= HexCampaign.MIN_DECK_SIZE) {
            setMessage(t("lblHexDeckTooSmall", String.valueOf(HexCampaign.MIN_DECK_SIZE)), true);
        } else {
            final boolean back = !row.sideboard() && campaign().isBoughtHere(p, row.card());
            setMessage(t(back ? "lblHexSoldBack" : "lblHexSoldCard", p.name, row.card(),
                    String.valueOf(back ? campaign().sellBackPrice(p, row.card()) : campaign().sellPriceFor(p, row.card()))), false);
            session.sell(p.name, row.card(), row.sideboard());
        }
        fill();
    }

    // ------------------------------------------------------------------ trading

    /** The relics on the trade page, and what they showed last. */
    private JPanel pnlTradeRelics;
    private String tradeRelicsShown;

    private JPanel buildTradePage() {
        final JPanel page = transparent(new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[]14[]8[]8[]12[grow, fill]"));
        page.add(title(t("lblHexTabTrade")));
        final JPanel row = transparent(new MigLayout("insets 0, gap 10, aligny center"));
        row.add(label(t("lblHexTradeTo"), body(Font.PLAIN, 14f), MUTED));
        row.add(mateTray);
        if (spnGold.getEditor() instanceof javax.swing.JSpinner.DefaultEditor editor) { // the amount in gold on dark, like the purse
            final javax.swing.JFormattedTextField field = editor.getTextField();
            field.setBackground(palette.mid());
            field.setForeground(GOLD_LIGHT);
            field.setCaretColor(GOLD_LIGHT);
            field.setFont(display(15f));
            field.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        }
        spnGold.setBorder(BorderFactory.createLineBorder(GOLD_EDGE));
        row.add(spnGold, "gapleft 24, w 100!, h 36!");
        final GoldButton btnGiveGold = new GoldButton(t("lblHexGiveGold"), GoldButton.Kind.GOLD, 13f);
        btnGiveGold.addActionListener(e -> {
            final HexPlayer p = head();
            final int amount = ((Number) spnGold.getValue()).intValue();
            if (p == null || mate == null) {
                return;
            }
            if (p.gold < amount) {
                setMessage(t("lblHexNotEnoughGold"), true);
            } else {
                setMessage(t("lblHexGaveGold", String.valueOf(amount), mate), false);
                session.giveGold(p.name, mate, amount);
            }
            fill();
        });
        row.add(btnGiveGold, "h 40!");
        page.add(row);
        // the relics the head wears: one may go to the mate if the mate's slot for it is free
        pnlTradeRelics = transparent(new MigLayout("insets 0, gap 10, aligny center, wrap 4"));
        page.add(pnlTradeRelics, "gaptop 4");
        final JPanel hint = transparent(new MigLayout("insets 0, gap 16, aligny center, fillx", "[]push[]"));
        hint.add(label(t("lblHexTradeHint"), body(Font.PLAIN, 13f), MUTED));
        hint.add(sourcePills(tradePills));
        page.add(hint);
        pnlTrade = transparent(new MigLayout("insets 0 0 8 0, gap 14, wrap " + COUNTER_COLUMNS, "", "[top]"));
        page.add(scroller(pnlTrade), "wmin 0, hmin 0");
        return page;
    }

    /** A button per relic the head wears: given to the chosen mate if the mate's slot for it is free. */
    private void fillTradeRelics(final HexPlayer p) {
        final HexPlayer taker = mate == null ? null : campaign().player(mate);
        final String shown = p.name + '/' + mate + '/' + p.relics + '/' + (taker == null ? "" : taker.relics);
        if (shown.equals(tradeRelicsShown)) {
            return;
        }
        tradeRelicsShown = shown;
        pnlTradeRelics.removeAll();
        if (!p.relics.isEmpty() && taker != null) {
            pnlTradeRelics.add(label(t("lblHexGiveRelicTo", taker.name), body(Font.PLAIN, 14f), MUTED), "span, wrap");
        }
        for (final String name : p.relics) {
            final HexRelic relic = content.getRelic(name);
            if (relic == null || relic.trophy != null) {
                continue; // a boss's trophy stays with whoever beat the boss
            }
            final boolean can = taker != null && campaign().canGiveRelic(p.name, taker.name, name);
            final GoldButton button = new GoldButton(name, can ? GoldButton.Kind.GOLD : GoldButton.Kind.OUTLINE, 13f);
            button.setEnabled(can);
            final HexRelic worn = taker == null ? null : campaign().relicInSlot(taker, relic.slot);
            button.setToolTipText(can ? t("lblHexGiveRelicHint", name, taker.name, slotName(relic.slot))
                    : worn != null ? t("lblHexGiveRelicTaken", taker == null ? "" : taker.name, slotName(relic.slot), worn.name)
                    : t("lblHexGiveRelicNo"));
            button.addActionListener(e -> {
                if (mate != null && campaign().canGiveRelic(p.name, mate, name)) {
                    setMessage(t("lblHexGaveRelic", name, mate), false);
                    session.giveRelic(p.name, mate, name);
                    fill();
                }
            });
            pnlTradeRelics.add(button, "h 36!");
        }
        pnlTradeRelics.revalidate();
        pnlTradeRelics.repaint();
    }

    private void fillTrade(final HexPlayer p) {
        final List<String> others = new ArrayList<>();
        for (final HexPlayer other : campaign().players) {
            if (other != p) {
                others.add(other.name);
            }
        }
        if (!others.equals(mates)) {
            mates.clear();
            mates.addAll(others);
            mateTray.removeAll();
            matePills.clear();
            for (final String name : mates) {
                final Pill pill = new Pill(name);
                pill.addActionListener(e -> {
                    mate = name;
                    fill();
                });
                matePills.add(pill);
                mateTray.add(pill, "h 36!");
            }
            mateTray.revalidate();
        }
        if (mate == null || !mates.contains(mate)) {
            mate = mates.isEmpty() ? null : mates.get(0);
        }
        for (int i = 0; i < matePills.size(); i++) {
            matePills.get(i).setActive(mates.get(i).equals(mate));
        }
        fillTradeRelics(p);
        final List<SaleRow> rows = shownRows(p, tradePills);
        final String shown = p.name + '/' + rows;
        if (shown.equals(tradeShown)) {
            return;
        }
        tradeShown = shown;
        pnlTrade.removeAll();
        for (final SaleRow row : rows) {
            final CounterTile tile = new CounterTile(content.getCard(row.card()), row.card(), counterW, palette, copies(row), null,
                    new TileListener() {
                        @Override
                        public void hovered() { }

                        @Override
                        public void clicked() {
                            give(row);
                        }
                    });
            tile.setToolTipText(row.card());
            pnlTrade.add(tile);
        }
        pnlTrade.revalidate();
        pnlTrade.repaint();
    }

    private void give(final SaleRow row) {
        final HexPlayer p = head();
        if (p == null || mate == null) {
            return;
        }
        if (!row.sideboard() && p.deck.size() <= HexCampaign.MIN_DECK_SIZE) {
            setMessage(t("lblHexDeckTooSmall", String.valueOf(HexCampaign.MIN_DECK_SIZE)), true);
        } else {
            setMessage(t("lblHexGaveCard", row.card(), mate), false);
            session.giveCard(p.name, mate, row.card(), row.sideboard());
        }
        fill();
    }
}
