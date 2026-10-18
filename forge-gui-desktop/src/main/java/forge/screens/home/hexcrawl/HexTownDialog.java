package forge.screens.home.hexcrawl;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.DefaultListModel;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexQuest;
import forge.gamemodes.hexcrawl.HexRelic;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.gamemodes.hexcrawl.HexShopType;
import forge.gamemodes.hexcrawl.HexTheme;
import forge.gui.CardPicturePanel;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.toolbox.FButton;
import forge.toolbox.FComboBox;
import forge.toolbox.FLabel;
import forge.toolbox.FList;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin;
import forge.toolbox.FTextArea;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * A town: one shop per kind of card (the cards are shown as pictures), a dealer in relics,
 * the notice board with tasks, and a counter where cards of the own deck and sideboard are sold.
 * Everybody trades for their own head; the host also looks after heads nobody plays.
 */
final class HexTownDialog {
    private static final String TAB_QUESTS = "QUESTS";
    private static final String TAB_SELL = "SELL";
    private static final String TAB_TRADE = "TRADE";
    private static final String PAGE_CARDS = "cards";
    private static final int TILE_W = 196;
    private static final int TILE_H = 274;

    private final Localizer loc = Localizer.getInstance();
    private final HexContent content = HexContent.get();
    private final HexSession session;
    private final List<String> heads = new ArrayList<>();

    private final FComboBox<String> cboPlayer;
    private final FLabel lblGold = new FLabel.Builder().fontSize(13).fontAlign(SwingConstants.LEFT).build();
    private final FLabel lblMessage = new FLabel.Builder().fontSize(12).fontAlign(SwingConstants.LEFT).build();
    private final Map<String, FButton> tabButtons = new LinkedHashMap<>();
    private final CardLayout pages = new CardLayout();
    private final JPanel body = new JPanel(pages);
    private String tab = HexShopType.CREATURE.name();

    // theme tabs: the town's theme, the random one and the two the head picked
    private final JPanel pnlThemes = new JPanel(new MigLayout("insets 0, gap 4, fillx"));
    private final FButton btnTownTheme = new FButton();
    private final FButton btnRandomTheme = new FButton();
    private final FButton[] btnPicked = { new FButton(), new FButton() };
    private final List<FComboBox<String>> cboPicked = new ArrayList<>();
    private final List<HexTheme> themeChoices = new ArrayList<>();
    /** The theme tab shown (a HexTheme id). */
    private String themeTab;
    private boolean fillingThemes;

    // card shops
    private final JPanel pnlTiles = new JPanel(new MigLayout("insets 0, gap 10", "", "[top]"));
    private final JPanel pnlBasics = new JPanel(new MigLayout("insets 0, gap 6"));
    private String tilesShown = "";

    // relic dealer
    private final DefaultListModel<String> relicOfferModel = new DefaultListModel<>();
    private final DefaultListModel<String> relicOwnModel = new DefaultListModel<>();
    private final FList<String> lstRelicOffers = new FList<>(relicOfferModel);
    private final FList<String> lstRelicOwn = new FList<>(relicOwnModel);
    private final List<String> relicOfferNames = new ArrayList<>();
    private final List<String> relicOwnNames = new ArrayList<>();
    private final FTextArea txtRelic = new FTextArea();
    private final CardPicturePanel picRelic = new CardPicturePanel();
    private final FButton btnBuyRelic = new FButton();
    private final FButton btnSellRelic = new FButton();

    // notice board
    private final DefaultListModel<String> questOfferModel = new DefaultListModel<>();
    private final DefaultListModel<String> questActiveModel = new DefaultListModel<>();
    private final FList<String> lstQuestOffers = new FList<>(questOfferModel);
    private final FList<String> lstQuestActive = new FList<>(questActiveModel);
    private final List<HexQuest> questOffers = new ArrayList<>();
    private final List<HexQuest> questActive = new ArrayList<>();
    private final FLabel lblQuestActive = new FLabel.Builder().fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build();
    private final FTextArea txtQuest = new FTextArea();
    private final FButton btnAccept = new FButton();

    // selling cards
    private final DefaultListModel<String> deckModel = new DefaultListModel<>();
    private final FList<String> lstDeck = new FList<>(deckModel);
    /** The rows of the sale list: deck cards first, then sideboard cards. */
    private final List<SaleRow> deckNames = new ArrayList<>();
    private final CardPicturePanel picSell = new CardPicturePanel();
    private final FButton btnSell = new FButton();

    // trading with team mates
    private final List<String> mates = new ArrayList<>();
    private final FComboBox<String> cboTradeTo = new FComboBox<>();
    private final forge.toolbox.FSpinner spnGold = new forge.toolbox.FSpinner.Builder().minValue(1).maxValue(100000).initialValue(10).build();
    private final FButton btnGiveGold = new FButton();
    private final DefaultListModel<String> tradeModel = new DefaultListModel<>();
    private final FList<String> lstTrade = new FList<>(tradeModel);
    private final List<SaleRow> tradeRows = new ArrayList<>();
    private final CardPicturePanel picTrade = new CardPicturePanel();
    private final FButton btnGiveCard = new FButton();

    HexTownDialog(final HexSession session0) {
        session = session0;
        final String me = session.getMyHead();
        for (final HexPlayer p : campaign().players) {
            if (session.mayActFor(me, p.name)) {
                heads.add(p.name);
            }
        }
        cboPlayer = new FComboBox<>(heads);
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    private HexCampaign campaign() {
        return session.getCampaign();
    }

    private HexPlayer head() {
        final HexCampaign c = campaign();
        return c == null || heads.isEmpty() ? null : c.player(heads.get(Math.max(0, cboPlayer.getSelectedIndex())));
    }

    /** Shows the town until it is closed. The given hook is told how to refresh the open dialog. */
    void show(final java.util.function.Consumer<Runnable> refreshHook) {
        if (heads.isEmpty() || campaign() == null || !campaign().inTown()) {
            return;
        }
        if (!session.isGuest()) {
            campaign().shop(); // makes sure the shelves are filled; guests get them from the host
        }
        final JPanel panel = new JPanel(new MigLayout("insets 0, gap 8, wrap 1", "[1030!, fill]"));
        panel.setOpaque(false);

        final JPanel top = new JPanel(new MigLayout("insets 0, gap 10", "[220!, fill][grow, fill]"));
        top.setOpaque(false);
        top.add(cboPlayer, "h 26!");
        top.add(lblGold, "h 26!");
        panel.add(top);

        panel.add(buildThemeTabs(), "hidemode 3");

        final JPanel tabs = new JPanel(new MigLayout("insets 0, gap 4, fillx"));
        tabs.setOpaque(false);
        for (final HexShopType type : HexShopType.values()) {
            addTab(tabs, type.name(), t(type.getLabelKey()));
        }
        addTab(tabs, TAB_QUESTS, t("lblHexTabQuests"));
        addTab(tabs, TAB_SELL, t("lblHexTabSell"));
        if (campaign().players.size() > 1) {
            addTab(tabs, TAB_TRADE, t("lblHexTabTrade"));
        }
        panel.add(tabs);

        body.setOpaque(false);
        body.add(buildCardsPage(), PAGE_CARDS);
        body.add(buildRelicPage(), HexShopType.RELIC.name());
        body.add(buildQuestPage(), TAB_QUESTS);
        body.add(buildSellPage(), TAB_SELL);
        body.add(buildTradePage(), TAB_TRADE);
        body.setPreferredSize(new Dimension(1030, 372));
        panel.add(body, "h 372!");
        panel.add(lblMessage, "h 22!");

        cboPlayer.addActionListener(e -> fill());
        fill();

        final FOptionPane pane = new FOptionPane(null, t("lblHexTown") + " " + campaign().tile().name, null, panel,
                Collections.singletonList(t("lblHexClose")), 0);
        refreshHook.accept(this::fill); // what others do arrives a moment later, with the next state from the host
        try {
            pane.setVisible(true);
        } finally {
            refreshHook.accept(null);
            pane.dispose();
        }
    }

    private void addTab(final JPanel tabs, final String key, final String text) {
        final FButton b = new FButton(text);
        b.setFont(FSkin.getFont(12));
        b.addActionListener(e -> {
            tab = key;
            lblMessage.setText("");
            fill();
        });
        tabButtons.put(key, b);
        tabs.add(b, "h 28!, growx, sg tab");
    }

    private boolean isCardShop() {
        return !TAB_QUESTS.equals(tab) && !TAB_SELL.equals(tab) && !TAB_TRADE.equals(tab) && !HexShopType.RELIC.name().equals(tab);
    }

    /** The offers of one shop of this town in the theme tab shown. Read only, so it is safe on a guest's copy too. */
    private List<HexShopOffer> offers(final HexShopType type) {
        final List<HexShopOffer> result = new ArrayList<>();
        final HexCampaign c = campaign();
        if (c == null || c.tile().poi != HexPoi.TOWN) {
            return result;
        }
        final List<HexShopOffer> stock = c.shops.get(c.tile().key());
        if (stock != null) {
            for (final HexShopOffer o : stock) {
                if (o.shop == type && (type == HexShopType.RELIC || java.util.Objects.equals(o.theme, themeTab))) {
                    result.add(o);
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ theme tabs

    private JPanel buildThemeTabs() {
        pnlThemes.setOpaque(false);
        themeChoices.clear();
        themeChoices.addAll(campaign().themeChoices());
        final List<String> labels = new ArrayList<>();
        labels.add(t("lblHexThemeChoose"));
        for (final HexTheme theme : themeChoices) {
            labels.add(theme.getLabel());
        }
        for (final FButton b : new FButton[] { btnTownTheme, btnRandomTheme, btnPicked[0], btnPicked[1] }) {
            b.setFont(FSkin.getFont(12));
        }
        btnTownTheme.addActionListener(e -> selectTheme(themeId(0)));
        btnRandomTheme.addActionListener(e -> selectTheme(themeId(1)));
        pnlThemes.add(btnTownTheme, "h 28!, growx, sg theme");
        pnlThemes.add(btnRandomTheme, "h 28!, growx, sg theme");
        for (int slot = 0; slot < HexCampaign.PICKED_THEMES; slot++) {
            final int s = slot;
            btnPicked[slot].addActionListener(e -> selectTheme(themeId(2 + s)));
            final FComboBox<String> cbo = new FComboBox<>(labels);
            cbo.setFont(FSkin.getFont(12));
            cbo.addActionListener(e -> onThemePicked(s, cbo.getSelectedIndex()));
            cboPicked.add(cbo);
            pnlThemes.add(btnPicked[slot], "h 28!, growx, sg theme");
            pnlThemes.add(cbo, "h 28!, w 150!");
        }
        return pnlThemes;
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
            case 1: return c.randomThemes.get(c.tile().key());
            default: return p == null ? null : p.themes[tabIndex - 2];
        }
    }

    private void selectTheme(final String id) {
        if (id != null) {
            themeTab = id;
            lblMessage.setText("");
            fill();
        }
    }

    private void onThemePicked(final int slot, final int index) {
        final HexPlayer p = head();
        if (fillingThemes || p == null || index <= 0 || index > themeChoices.size()) {
            return;
        }
        final HexTheme theme = themeChoices.get(index - 1);
        if (theme.id.equals(p.themes[slot])) {
            return;
        }
        if (session.isFightRunning() || !campaign().mayPickTheme(p.name, slot)) {
            lblMessage.setText(t("lblHexThemeLocked"));
        } else {
            lblMessage.setText("");
            themeTab = theme.id;
            session.pickTheme(p.name, slot, theme.id);
        }
        fill();
    }

    private void fillThemes(final HexPlayer p) {
        fillingThemes = true;
        try {
            final String[] ids = { themeId(0), themeId(1), themeId(2), themeId(3) };
            boolean shown = false;
            for (final String id : ids) {
                shown |= id != null && id.equals(themeTab);
            }
            if (!shown) {
                themeTab = ids[0]; // the tab shown is gone (a new random theme, another head): back to the town's
            }
            setThemeButton(btnTownTheme, "lblHexThemeTown", ids[0]);
            setThemeButton(btnRandomTheme, "lblHexThemeRandom", ids[1]);
            for (int slot = 0; slot < HexCampaign.PICKED_THEMES; slot++) {
                setThemeButton(btnPicked[slot], "lblHexThemePicked", ids[2 + slot]);
                final HexTheme picked = HexTheme.get(p.themes[slot]);
                final int index = picked == null ? 0 : themeChoices.indexOf(picked) + 1;
                cboPicked.get(slot).setSelectedIndex(Math.max(0, index));
                final boolean mayPick = !session.isFightRunning() && campaign().mayPickTheme(p.name, slot);
                cboPicked.get(slot).setEnabled(mayPick);
                cboPicked.get(slot).setToolTipText(mayPick ? t("lblHexThemePickHint") : t("lblHexThemeLocked"));
            }
        } finally {
            fillingThemes = false;
        }
    }

    private void setThemeButton(final FButton b, final String key, final String id) {
        final HexTheme theme = HexTheme.get(id);
        b.setText(t(key, theme == null ? "–" : theme.getLabel()));
        b.setEnabled(theme != null);
        b.setToggled(theme != null && theme.id.equals(themeTab));
    }

    // ------------------------------------------------------------------ refresh

    private void fill() {
        final HexCampaign c = campaign();
        final HexPlayer p = head();
        if (c == null || p == null) {
            return;
        }
        String purse = t("lblHexGoldAndCards", String.valueOf(p.gold), String.valueOf(p.deck.size()));
        if (!p.relics.isEmpty()) {
            purse += "  –  " + t("lblHexRelicsCarried", String.join(", ", p.relics));
        }
        lblGold.setText(purse);
        for (final Map.Entry<String, FButton> e : tabButtons.entrySet()) {
            e.getValue().setToggled(e.getKey().equals(tab));
        }
        fillThemes(p);
        pnlThemes.setVisible(isCardShop());
        if (isCardShop()) {
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

    private JPanel buildCardsPage() {
        final JPanel page = new JPanel(new MigLayout("insets 0, gap 6, wrap 1", "[grow, fill]", "[grow, top][]"));
        page.setOpaque(false);
        pnlTiles.setOpaque(false);
        page.add(pnlTiles, "h 334!");
        pnlBasics.setOpaque(false);
        pnlBasics.add(new FLabel.Builder().text(t("lblHexBasicLands")).fontAlign(SwingConstants.LEFT).build(), "h 26!");
        for (final HexColor color : HexColor.COLORS) {
            final FButton b = new FButton(color.getBasicLand() + " (" + HexCampaign.BASIC_LAND_PRICE + ")");
            b.setFont(FSkin.getFont(12));
            b.addActionListener(e -> {
                final HexPlayer p = head();
                if (p != null && p.gold < HexCampaign.BASIC_LAND_PRICE) {
                    lblMessage.setText(t("lblHexNotEnoughGold"));
                } else if (p != null) {
                    lblMessage.setText("");
                    session.buyLand(p.name, color);
                }
                fill();
            });
            pnlBasics.add(b, "h 26!, w 150!");
        }
        page.add(pnlBasics, "h 26!, hidemode 2");
        return page;
    }

    /** One tile per card on offer: its picture, its name and the button to buy it. */
    private void fillTiles(final HexShopType type) {
        pnlBasics.setVisible(type == HexShopType.LAND);
        final List<HexShopOffer> list = offers(type);
        final HexPlayer buyer = head();
        final StringBuilder shown = new StringBuilder(type.name()).append('/').append(themeTab).append('/').append(buyer == null ? "" : buyer.name);
        for (final HexShopOffer o : list) {
            shown.append('|').append(o.card).append('=').append(price(buyer, o));
        }
        if (shown.toString().equals(tilesShown)) {
            return; // nothing changed: keep the pictures as they are
        }
        tilesShown = shown.toString();
        pnlTiles.removeAll();
        if (list.isEmpty()) {
            pnlTiles.add(new FLabel.Builder().text(t("lblHexNothingOnOffer")).fontSize(13).fontAlign(SwingConstants.LEFT).build(), "h 30!");
        }
        for (final HexShopOffer o : list) {
            final JPanel tile = new JPanel(new MigLayout("insets 0, gap 3, wrap 1", "[" + TILE_W + "!, fill]"));
            tile.setOpaque(false);
            final CardPicturePanel pic = new CardPicturePanel();
            pic.setOpaque(false);
            final PaperCard card = content.getCard(o.card);
            if (card != null) {
                pic.setItem(card);
            }
            tile.add(pic, "h " + TILE_H + "!");
            tile.add(new FLabel.Builder().text(o.card).fontSize(12).fontAlign(SwingConstants.CENTER).build(), "h 20!");
            final FButton buy = new FButton(t("lblHexBuyFor", String.valueOf(price(buyer, o))));
            buy.setFont(FSkin.getFont(12));
            buy.addActionListener(e -> {
                final HexPlayer p = head();
                if (p == null) {
                    return;
                }
                if (p.gold < price(p, o)) {
                    lblMessage.setText(t("lblHexNotEnoughGold"));
                } else {
                    lblMessage.setText("");
                    session.buy(p.name, type, themeTab, o.card);
                }
                fill();
            });
            tile.add(buy, "h 28!");
            pnlTiles.add(tile);
        }
        pnlTiles.revalidate();
        pnlTiles.repaint();
    }

    // ------------------------------------------------------------------ relics

    private JPanel buildRelicPage() {
        final JPanel page = new JPanel(new MigLayout("insets 0, gap 8, wrap 3", "[380!, fill][380!, fill][grow, fill]"));
        page.setOpaque(false);
        lstRelicOffers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstRelicOwn.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        txtRelic.setFont(FSkin.getFont(12).getBaseFont());
        btnBuyRelic.setText(t("lblHexBuy"));
        btnSellRelic.setText(t("lblHexSell"));
        page.add(new FLabel.Builder().text(t("lblHexRelicOffers")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 22!");
        page.add(new FLabel.Builder().text(t("lblHexYourRelics")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 22!");
        picRelic.setOpaque(false);
        page.add(picRelic, "spany 4, h 340!, w 244!");
        page.add(new FScrollPane(lstRelicOffers, true), "h 140!");
        page.add(new FScrollPane(lstRelicOwn, true), "h 140!");
        page.add(btnBuyRelic, "h 28!");
        page.add(btnSellRelic, "h 28!");
        page.add(new FScrollPane(txtRelic, true), "span 2, h 126!");

        lstRelicOffers.addListSelectionListener(e -> {
            final int i = lstRelicOffers.getSelectedIndex();
            if (i >= 0 && i < relicOfferNames.size()) {
                lstRelicOwn.clearSelection();
                showRelic(relicOfferNames.get(i), true);
            }
        });
        lstRelicOwn.addListSelectionListener(e -> {
            final int i = lstRelicOwn.getSelectedIndex();
            if (i >= 0 && i < relicOwnNames.size()) {
                lstRelicOffers.clearSelection();
                showRelic(relicOwnNames.get(i), false);
            }
        });
        btnBuyRelic.addActionListener(e -> {
            final int i = lstRelicOffers.getSelectedIndex();
            final HexPlayer p = head();
            if (i < 0 || i >= relicOfferNames.size() || p == null) {
                return;
            }
            final HexRelic relic = content.getRelic(relicOfferNames.get(i));
            final HexRelic old = relic == null ? null : campaign().relicInSlot(p, relic.slot);
            final int refund = old == null ? 0 : content.relicSellPrice(old.name);
            if (relic == null || p.gold + refund < campaign().priceFor(p, relic.price)) {
                lblMessage.setText(t("lblHexNotEnoughGold"));
            } else {
                lblMessage.setText("");
                session.buy(p.name, HexShopType.RELIC, null, relic.name);
            }
            fill();
        });
        btnSellRelic.addActionListener(e -> {
            final int i = lstRelicOwn.getSelectedIndex();
            final HexPlayer p = head();
            if (i >= 0 && i < relicOwnNames.size() && p != null) {
                lblMessage.setText("");
                session.sellRelic(p.name, relicOwnNames.get(i));
            }
            fill();
        });
        return page;
    }

    private String slotName(final String slot) {
        return t("lblHexSlot" + slot);
    }

    private void fillRelics(final HexPlayer p) {
        final List<String> names = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        for (final HexShopOffer o : offers(HexShopType.RELIC)) {
            final HexRelic relic = content.getRelic(o.card);
            if (relic != null) {
                names.add(relic.name);
                lines.add(price(p, o) + "  –  " + relic.name + "   [" + slotName(relic.slot) + "]");
            }
        }
        setList(relicOfferModel, relicOfferNames, lines, names);
        final List<String> ownNames = new ArrayList<>();
        final List<String> ownLines = new ArrayList<>();
        for (final String name : p.relics) {
            final HexRelic relic = content.getRelic(name);
            ownNames.add(name);
            ownLines.add(name + (relic == null ? "" : "   [" + slotName(relic.slot) + "]") + "   (+" + content.relicSellPrice(name) + ")");
        }
        setList(relicOwnModel, relicOwnNames, ownLines, ownNames);
    }

    private void showRelic(final String name, final boolean offer) {
        final HexRelic relic = content.getRelic(name);
        if (relic == null) {
            txtRelic.setText(name);
            return;
        }
        final HexPlayer p = head();
        final StringBuilder sb = new StringBuilder(relic.name).append("  –  ").append(t("lblHexRelicSlot", slotName(relic.slot))).append('\n');
        sb.append(HexCrawlPanel.relicEffects(relic));
        if (relic.description != null && !relic.description.isEmpty()) {
            sb.append(relic.description).append('\n');
        }
        final HexRelic old = offer && p != null ? campaign().relicInSlot(p, relic.slot) : null;
        if (old != null) {
            sb.append(t("lblHexRelicReplaces", old.name, String.valueOf(content.relicSellPrice(old.name)))).append('\n');
        }
        txtRelic.setText(sb.toString());
        txtRelic.setCaretPosition(0);
        // the picture of what the relic puts into play, if it does
        final List<String> cards = relic.battlefield.isEmpty() ? relic.enemyBattlefield : relic.battlefield;
        final IPaperCard card = cards.isEmpty() ? null : content.findStartCard(cards.get(0));
        picRelic.setVisible(card != null);
        if (card != null) {
            picRelic.setItem(card);
        }
    }

    // ------------------------------------------------------------------ tasks

    private JPanel buildQuestPage() {
        final JPanel page = new JPanel(new MigLayout("insets 0, gap 8, wrap 2", "[505!, fill][505!, fill]"));
        page.setOpaque(false);
        lstQuestOffers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstQuestActive.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        txtQuest.setFont(FSkin.getFont(13).getBaseFont());
        btnAccept.setText(t("lblHexQuestAccept"));
        page.add(new FLabel.Builder().text(t("lblHexQuestOffers")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 22!");
        page.add(lblQuestActive, "h 22!");
        page.add(new FScrollPane(lstQuestOffers, true), "h 150!");
        page.add(new FScrollPane(lstQuestActive, true), "h 150!");
        page.add(btnAccept, "h 28!, wrap");
        page.add(new FScrollPane(txtQuest, true), "span 2, h 140!");

        lstQuestOffers.addListSelectionListener(e -> {
            final int i = lstQuestOffers.getSelectedIndex();
            if (i >= 0 && i < questOffers.size()) {
                lstQuestActive.clearSelection();
                txtQuest.setText(describe(questOffers.get(i)));
            }
        });
        lstQuestActive.addListSelectionListener(e -> {
            final int i = lstQuestActive.getSelectedIndex();
            if (i >= 0 && i < questActive.size()) {
                lstQuestOffers.clearSelection();
                txtQuest.setText(describe(questActive.get(i)));
            }
        });
        btnAccept.addActionListener(e -> {
            final int i = lstQuestOffers.getSelectedIndex();
            if (i < 0 || i >= questOffers.size()) {
                return;
            }
            if (campaign().quests.size() >= HexCampaign.MAX_ACTIVE_QUESTS) {
                lblMessage.setText(t("lblHexQuestsFull", String.valueOf(HexCampaign.MAX_ACTIVE_QUESTS)));
            } else {
                lblMessage.setText("");
                session.acceptQuest(questOffers.get(i).id);
            }
            fill();
        });
        return page;
    }

    private String describe(final HexQuest quest) {
        final HexCampaign c = campaign();
        final int far = HexMap.distance(quest.q, quest.r, c.partyQ, c.partyR);
        final forge.gamemodes.hexcrawl.HexEncounter enc = content.questEncounter(quest);
        return t("lblHexQuestDetail", quest.enemyName, String.valueOf(c.enemyLife(enc)), t(quest.color.getLabelKey()),
                String.valueOf(far), String.valueOf(c.gold(quest.gold))) + "\n\n" + HexCrawlPanel.enemyExtras(c, enc).trim();
    }

    private void fillQuests(final HexCampaign c) {
        final List<HexQuest> offers = c.questOffers.get(c.tile().key());
        final List<String> offerLines = new ArrayList<>();
        final List<HexQuest> newOffers = new ArrayList<>();
        if (offers != null) {
            for (final HexQuest quest : offers) {
                newOffers.add(quest);
                offerLines.add(t("lblHexQuestLine", quest.enemyName, String.valueOf(c.gold(quest.gold))));
            }
        }
        setList(questOfferModel, questOffers, offerLines, newOffers);
        final List<String> activeLines = new ArrayList<>();
        for (final HexQuest quest : c.quests) {
            activeLines.add(t("lblHexQuestLine", quest.enemyName, String.valueOf(c.gold(quest.gold))));
        }
        setList(questActiveModel, questActive, activeLines, new ArrayList<>(c.quests));
        lblQuestActive.setText(t("lblHexQuestActive", String.valueOf(c.quests.size()), String.valueOf(HexCampaign.MAX_ACTIVE_QUESTS)));
        if (offerLines.isEmpty() && activeLines.isEmpty()) {
            txtQuest.setText(t("lblHexNoQuests"));
        }
    }

    // ------------------------------------------------------------------ selling cards

    private JPanel buildSellPage() {
        final JPanel page = new JPanel(new MigLayout("insets 0, gap 8, wrap 2", "[520!, fill][grow, fill]"));
        page.setOpaque(false);
        lstDeck.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        btnSell.setText(t("lblHexSell"));
        page.add(new FLabel.Builder().text(t("lblHexYourDeck")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 22!");
        picSell.setOpaque(false);
        page.add(picSell, "spany 3, h 360!, w 258!, gapleft 40");
        page.add(new FScrollPane(lstDeck, true), "h 300!");
        page.add(btnSell, "h 28!");
        lstDeck.addListSelectionListener(e -> {
            final int i = lstDeck.getSelectedIndex();
            final PaperCard card = i >= 0 && i < deckNames.size() ? content.getCard(deckNames.get(i).card()) : null;
            if (card != null) {
                picSell.setItem(card);
                btnSell.setText(t("lblHexSellFor", String.valueOf(campaign().sellPriceFor(head(), card.getName()))));
            }
        });
        btnSell.addActionListener(e -> {
            final int i = lstDeck.getSelectedIndex();
            final HexPlayer p = head();
            if (i >= 0 && i < deckNames.size() && p != null) {
                final SaleRow row = deckNames.get(i);
                if (!row.sideboard() && p.deck.size() <= HexCampaign.MIN_DECK_SIZE) {
                    lblMessage.setText(t("lblHexDeckTooSmall", String.valueOf(HexCampaign.MIN_DECK_SIZE)));
                } else {
                    lblMessage.setText("");
                    session.sell(p.name, row.card(), row.sideboard());
                }
            }
            fill();
        });
        return page;
    }

    /** What the head pays for an offer (its discount perks included). */
    private int price(final HexPlayer p, final HexShopOffer o) {
        return p == null ? o.price : campaign().priceFor(p, o.price);
    }

    // ------------------------------------------------------------------ trading

    private JPanel buildTradePage() {
        final JPanel page = new JPanel(new MigLayout("insets 0, gap 8, wrap 2", "[520!, fill][grow, fill]"));
        page.setOpaque(false);
        final JPanel to = new JPanel(new MigLayout("insets 0, gap 8", "[][200!, fill][90!, fill][grow, fill]"));
        to.setOpaque(false);
        to.add(new FLabel.Builder().text(t("lblHexTradeTo")).fontAlign(SwingConstants.LEFT).build(), "h 26!");
        to.add(cboTradeTo, "h 26!");
        to.add(spnGold, "h 26!");
        btnGiveGold.setText(t("lblHexGiveGold"));
        to.add(btnGiveGold, "h 26!");
        page.add(to, "span 2");
        page.add(new FLabel.Builder().text(t("lblHexTradeCards")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 22!");
        picTrade.setOpaque(false);
        page.add(picTrade, "spany 3, h 320!, w 230!, gapleft 40");
        lstTrade.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        page.add(new FScrollPane(lstTrade, true), "h 260!");
        btnGiveCard.setText(t("lblHexGiveCard"));
        page.add(btnGiveCard, "h 28!");

        lstTrade.addListSelectionListener(e -> {
            final int i = lstTrade.getSelectedIndex();
            final PaperCard card = i >= 0 && i < tradeRows.size() ? content.getCard(tradeRows.get(i).card()) : null;
            if (card != null) {
                picTrade.setItem(card);
            }
        });
        btnGiveGold.addActionListener(e -> {
            final HexPlayer p = head();
            final String mate = (String) cboTradeTo.getSelectedItem();
            final int amount = ((Number) spnGold.getValue()).intValue();
            if (p == null || mate == null) {
                return;
            }
            if (p.gold < amount) {
                lblMessage.setText(t("lblHexNotEnoughGold"));
            } else {
                lblMessage.setText(t("lblHexGaveGold", String.valueOf(amount), mate));
                session.giveGold(p.name, mate, amount);
            }
            fill();
        });
        btnGiveCard.addActionListener(e -> {
            final HexPlayer p = head();
            final String mate = (String) cboTradeTo.getSelectedItem();
            final int i = lstTrade.getSelectedIndex();
            if (p == null || mate == null || i < 0 || i >= tradeRows.size()) {
                return;
            }
            final SaleRow row = tradeRows.get(i);
            if (!row.sideboard() && p.deck.size() <= HexCampaign.MIN_DECK_SIZE) {
                lblMessage.setText(t("lblHexDeckTooSmall", String.valueOf(HexCampaign.MIN_DECK_SIZE)));
            } else {
                lblMessage.setText(t("lblHexGaveCard", row.card(), mate));
                session.giveCard(p.name, mate, row.card(), row.sideboard());
            }
            fill();
        });
        return page;
    }

    private void fillTrade(final HexPlayer p) {
        final List<String> others = new ArrayList<>();
        for (final HexPlayer mate : campaign().players) {
            if (mate != p) {
                others.add(mate.name);
            }
        }
        if (!others.equals(mates)) {
            final Object selected = cboTradeTo.getSelectedItem();
            mates.clear();
            mates.addAll(others);
            cboTradeTo.removeAllItems();
            for (final String name : mates) {
                cboTradeTo.addItem(name);
            }
            if (selected != null && mates.contains(selected)) {
                cboTradeTo.setSelectedItem(selected);
            }
        }
        final List<String> lines = new ArrayList<>();
        final List<SaleRow> rows = new ArrayList<>();
        addSaleRows(p, p.deck, false, lines, rows, false);
        addSaleRows(p, p.sideboard, true, lines, rows, false);
        setList(tradeModel, tradeRows, lines, rows);
    }

    /** One row of the sale list: a card name and whether the copies are in the sideboard. */
    private record SaleRow(String card, boolean sideboard) { }

    private void fillDeck(final HexPlayer p) {
        final List<String> lines = new ArrayList<>();
        final List<SaleRow> rows = new ArrayList<>();
        addSaleRows(p, p.deck, false, lines, rows, true);
        addSaleRows(p, p.sideboard, true, lines, rows, true);
        setList(deckModel, deckNames, lines, rows);
    }

    private void addSaleRows(final HexPlayer p, final List<String> cards, final boolean sideboard, final List<String> lines,
            final List<SaleRow> rows, final boolean withPrice) {
        final TreeMap<String, Integer> counts = new TreeMap<>();
        for (final String card : cards) {
            counts.merge(card, 1, Integer::sum);
        }
        for (final Map.Entry<String, Integer> e : counts.entrySet()) {
            final PaperCard card = content.getCard(e.getKey());
            rows.add(new SaleRow(e.getKey(), sideboard));
            lines.add((sideboard ? t("lblHexSideboardMark") + " " : "") + e.getValue() + "x " + e.getKey()
                    + (card == null ? "" : "   (" + card.getRules().getType() + ")")
                    + (withPrice ? "   +" + campaign().sellPriceFor(p, e.getKey()) : ""));
        }
    }

    /** Replaces the rows of a list only if they changed, so that a selection survives a refresh. */
    private static <T> void setList(final DefaultListModel<String> model, final List<T> keys, final List<String> lines, final List<T> newKeys) {
        boolean same = lines.size() == model.getSize();
        for (int i = 0; same && i < lines.size(); i++) {
            same = lines.get(i).equals(model.get(i));
        }
        keys.clear();
        keys.addAll(newKeys);
        if (!same) {
            model.clear();
            for (final String line : lines) {
                model.addElement(line);
            }
        }
    }
}
