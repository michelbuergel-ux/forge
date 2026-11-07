package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.BAD;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
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

import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import forge.Singletons;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexCampaignIO;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexFight;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexNet;
import forge.gamemodes.hexcrawl.HexPerk;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexQuest;
import forge.gamemodes.hexcrawl.HexRelic;
import forge.gamemodes.hexcrawl.HexReward;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexTile;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.event.HexStateEvent;
import forge.gamemodes.net.server.FServerManager;
import forge.gamemodes.net.server.HostingServer;
import forge.gamemodes.net.server.ServerGameLobby;
import forge.gui.CardPicturePanel;
import forge.gui.framework.EDocID;
import forge.gui.framework.FScreen;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.home.CHomeUI;
import forge.toolbox.FLabel;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import forge.screens.home.hexcrawl.HexSidebarParts.BossTrack;
import forge.screens.home.hexcrawl.HexSidebarParts.Heading;
import forge.screens.home.hexcrawl.HexSidebarParts.PartyRow;
import forge.screens.home.hexcrawl.HexSidebarParts.Plate;
import forge.screens.home.hexcrawl.HexSidebarParts.Sprite;
import forge.screens.home.hexcrawl.HexSidebarParts.Threat;
import forge.screens.home.hexcrawl.HexTownStyle.GoldButton;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import net.miginfocom.swing.MigLayout;

/**
 * The hex campaign screen: the map on the left, the party, the actions and the journal on the
 * right. The computer that owns the campaign can invite friends over the network; every person
 * then sees this screen for the same campaign, votes where to go, confirms fights, picks their
 * own reward and trades for their own head.
 */
@SuppressWarnings("serial")
public class HexCrawlPanel extends JPanel implements HexSession.Listener {
    private final Localizer loc = Localizer.getInstance();
    private final HexMapPanel mapPanel = new HexMapPanel();
    /** Inside a dungeon its labyrinth takes the hex map's place. */
    private final HexLabyrinthPanel labyrinthPanel = new HexLabyrinthPanel();
    private final java.awt.CardLayout mapCards = new java.awt.CardLayout();
    private final JPanel mapArea = new JPanel(mapCards);
    /** Width of the side panel, and of the text in its scrolling middle part. */
    private static final int SIDE_W = 350;
    private static final int MIDDLE_W = SIDE_W - 32 - 18;
    /** The cards an event gives, shown a little smaller than the rewards. */
    private static final int GIFT_W = 200;
    private static final int GIFT_H = 280;
    private final JLabel lblTitle = label(" ", decorative(22f), TEXT_BRIGHT);
    private final JLabel lblPlace = label(" ", tracked(display(11.5f), 0.12f), MUTED);
    private final GoldButton btnFight = new GoldButton("", GoldButton.Kind.GOLD, 15f);
    /** Into the dungeon the party stands before, or out of it (at its entrance). */
    private final GoldButton btnDungeon = new GoldButton("", GoldButton.Kind.GOLD, 14f);
    private final GoldButton btnNewGamePlus = new GoldButton("", GoldButton.Kind.GOLD, 13f);
    private final GoldButton btnRewards = new GoldButton("", GoldButton.Kind.GOLD, 13f);
    private final GoldButton btnPerk = new GoldButton("", GoldButton.Kind.GOLD, 13f);
    private final GoldButton btnEvent = new GoldButton("", GoldButton.Kind.GOLD, 13f);
    private final GoldButton btnShop = new GoldButton("", GoldButton.Kind.GOLD, 14f);
    private final GoldButton btnDecks = new GoldButton("", GoldButton.Kind.OUTLINE, 13f);
    private final GoldButton btnStats = new GoldButton("", GoldButton.Kind.OUTLINE, 13f);
    /** "Always OK my partner" in the fights, switched here as well (a saved setting of this computer). */
    private final GoldButton btnAutoOk = new GoldButton("", GoldButton.Kind.OUTLINE, 11f);
    /** New, load, export, import and joining a friend: one menu instead of a row of buttons. */
    private final GoldButton btnMenu = new GoldButton("", GoldButton.Kind.OUTLINE, 11f).withGlyph(HexTownStyle.Glyph.MENU);
    private final GoldButton btnInvite = new GoldButton("", GoldButton.Kind.OUTLINE, 11f);
    private final GoldButton btnLeave = new GoldButton("", GoldButton.Kind.OUTLINE, 11f);
    /** Who is connected to whom: shown to the host and to guests. */
    private final JLabel lblConnection = label(" ", body(java.awt.Font.BOLD, 12f), MUTED);
    /** The side panel: the campaign menu opens beside it, over the map. */
    private final Plate sidePanel = new Plate(this::palette, new MigLayout());
    /** The campaign menu (see openMenu), open or not. */
    private HexMenuOverlay menu = new HexMenuOverlay(this::palette);
    /** The page of a new campaign, in place of the map while it is filled in. */
    private final HexNewCampaignPage setupPage = new HexNewCampaignPage();
    private boolean settingUp;

    // the side panel's parts: the enemy ahead, the vote, the party, the archmages, tasks, the journal
    private final JPanel threatBox = box();
    private final JPanel voteBox = box();
    private final Heading hdParty = new Heading(t("lblHexPartyTitle"));
    private final JPanel pnlParty = transparent(new MigLayout("insets 0, gap 0 6, wrap 1, fillx", "[grow, fill]"));
    private final List<PartyRow> partyRows = new ArrayList<>();
    private final Heading hdBosses = new Heading(t("lblHexArchmagesTitle"));
    private final BossTrack bossTrack = new BossTrack();
    private final JLabel lblRecord = label(" ", body(java.awt.Font.PLAIN, 12f), MUTED);
    private final Heading hdTasks = new Heading(t("lblHexTasksTitle"));
    private final JPanel tasksBox = box();
    private final Heading hdCycle = new Heading(t("lblHexCycleHeading"));
    private final JPanel cycleBox = box();
    private final Heading hdBoosts = new Heading(t("lblHexBoostsHeading"));
    private final JPanel boostsBox = box();
    private final Heading hdJournal = new Heading(t("lblHexJournalTitle"));
    private final JPanel pnlJournal = transparent(new MigLayout("insets 0, gap 0 7, wrap 1", "[" + MIDDLE_W + "!]"));
    private String journalShown = "";

    private HexSession session;
    private boolean rewardPromptOpen;
    /** The fight whose boss intro this screen has shown already. */
    private String bossIntroShown;
    /** The cycle whose victory this screen has asked about New Game Plus already. */
    private int newGamePlusPromptedCycle;
    private boolean perkPromptOpen;
    private boolean eventPromptOpen;
    /** The last event (see HexCampaign.eventSerial) already shown on this screen. */
    private int shownEventSerial = -1;
    /** The last cards this person's head got for one of its own (see HexPlayer.giftSerial) already shown. */
    private int shownGiftSerial = -1;
    /** The last fight summary this screen has shown (-1: none yet, take the campaign's as seen). */
    private int shownSummarySerial = -1;
    /** The last freed town this screen thanked the party for (see {@link #showThanksIfNew()}). */
    private int shownFreedSerial = -1;
    /** Host or single player: a fight of the campaign is on the match screen (until its result is closed). */
    private volatile boolean fightOnScreen;
    private Runnable shopRefresher;
    private Runnable deckRefresher;
    private int ticks;
    private final Timer hostTimer = new Timer(1500, e -> hostTick());
    /** Keeps the connection line up to date (a lost connection sends no message). */
    /** While a vote is open: the countdown on the side panel, once a second. */
    private final Timer voteTimer = new Timer(1000, e -> {
        if (isShowing() && session != null && session.voteSecondsLeft(System.currentTimeMillis()) >= 0) {
            refresh();
        }
    });
    /** The host picks a leader whose vote decides where the party goes. */
    private final forge.toolbox.FComboBox<String> cboLeader = new forge.toolbox.FComboBox<>();
    private final List<String> leaderChoices = new ArrayList<>();
    private boolean fillingLeader;

    /** One colour per head (by seat), on the map's vote marks and in the party panel. */
    private static final java.awt.Color[] HEAD_COLORS = {
        new java.awt.Color(0x1E88E5), new java.awt.Color(0xE53935), new java.awt.Color(0x43A047), new java.awt.Color(0xFB8C00) };

    static java.awt.Color headColor(final int seat) {
        return HEAD_COLORS[Math.max(0, seat) % HEAD_COLORS.length];
    }

    private final Timer connectionTimer = new Timer(2000, e -> {
        if (isShowing() && (HexNet.isGuest() || HostingServer.isHosting())) {
            showConnection(isGuest() || HexNet.isGuest());
        }
    });

    public HexCrawlPanel() {
        super(new MigLayout("insets 10, gap 10, fill", "[grow, fill][" + SIDE_W + "!, fill]", "[grow, fill]"));
        setOpaque(false);
        mapArea.setOpaque(false);
        mapArea.add(mapPanel, "hex");
        mapArea.add(labyrinthPanel, "labyrinth");
        mapArea.add(setupPage, "setup");
        add(mapArea, "grow");

        final Plate side = sidePanel;
        side.setLayout(new MigLayout("insets 18 16 14 16, gap 0, wrap 1, fillx, hidemode 3",
                "[grow, fill]"));
        btnRewards.setText(t("lblHexRewards"));
        btnPerk.setText(t("lblHexChoosePerk"));
        btnEvent.setText(t("lblHexEventChooseButton"));
        btnShop.setText(t("lblHexShop"));
        btnDecks.setText(t("lblHexDecks"));
        btnStats.setText(t("lblHexChronicle"));
        btnInvite.setText(t("lblHexInvite"));
        btnMenu.setText(t("lblHexCampaignMenu"));
        side.add(lblTitle);
        // a click on the campaign's name: rename it (the host) or export it, without more buttons in the sidebar
        lblTitle.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(final java.awt.event.MouseEvent e) {
                if (lblTitle.contains(e.getPoint()) && getCampaign() != null) {
                    openMenu(lblTitle);
                }
            }
        });
        side.add(lblPlace, "gaptop 2");
        side.add(lblConnection, "gaptop 6");

        // what there is to do right now, the most pressing first
        final JPanel actions = transparent(new MigLayout("insets 0, gap 0 8, wrap 1, fillx, hidemode 3", "[grow, fill]"));
        actions.add(threatBox);
        actions.add(btnFight, "h 46!");
        actions.add(btnDungeon, "h 42!");
        actions.add(btnRewards, "h 40!");
        actions.add(btnPerk, "h 40!");
        actions.add(btnEvent, "h 40!");
        actions.add(btnNewGamePlus, "h 40!");
        actions.add(voteBox);
        actions.add(btnShop, "h 44!");
        actions.add(btnDecks, "h 38!");
        actions.add(btnStats, "h 34!");
        side.add(actions, "gaptop 12");

        // the party and the campaign so far, then the journal: scrolls when there is a lot of it
        final JPanel middle = transparent(new MigLayout("insets 0 0 0 4, gap 0, wrap 1, hidemode 3", "[" + MIDDLE_W + "!, fill]"));
        middle.add(hdParty);
        middle.add(cboLeader, "h 28!, gaptop 4");
        middle.add(pnlParty, "gaptop 6");
        middle.add(btnAutoOk, "h 28!, gaptop 6");
        middle.add(hdBosses, "gaptop 12");
        middle.add(bossTrack, "gaptop 2");
        middle.add(lblRecord, "gaptop 2");
        middle.add(hdTasks, "gaptop 12");
        middle.add(tasksBox, "gaptop 2");
        middle.add(hdCycle, "gaptop 12");
        middle.add(cycleBox, "gaptop 2");
        middle.add(hdBoosts, "gaptop 12");
        middle.add(boostsBox, "gaptop 2");
        middle.add(hdJournal, "gaptop 12");
        middle.add(pnlJournal, "gaptop 4");
        final FScrollPane scroll = new FScrollPane(middle, false, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        side.add(scroll, "gaptop 14, grow, push, h 120:120:n"); // whatever room the rest leaves

        // campaigns and friends
        final JPanel footer = transparent(new MigLayout("insets 0, gap 0 6, wrap 1, hidemode 3", "[grow, fill]"));
        footer.add(new Heading(t("lblHexFilesTitle")));
        footer.add(btnMenu, "h 32!");
        footer.add(btnInvite, "h 32!");
        footer.add(btnLeave, "h 32!");
        side.add(footer, "gaptop 10");
        add(side, "grow");

        mapPanel.setOnTileClicked(this::onTileClicked);
        mapPanel.setOnTilePinged(t -> {
            if (session != null) {
                session.ping(t.q, t.r);
            }
        });
        labyrinthPanel.setOnRoomClicked(this::onRoomClicked);
        labyrinthPanel.setOnExitClicked(this::onExitClicked);
        btnDungeon.addActionListener(e -> {
            final HexCampaign c = getCampaign();
            if (c != null && c.inLabyrinth()) {
                onExitClicked();
            } else if (c != null) {
                onTileClicked(c.tile());
            }
        });
        bindKeys();
        btnFight.addActionListener(e -> {
            if (session != null && session.isFightRunning()) {
                resetFight(); // the host only: the button is off for the guests while a fight runs
            } else if (session != null) {
                session.readyToFight();
            }
        });
        btnNewGamePlus.addActionListener(e -> newGamePlus());
        cboLeader.addActionListener(e -> {
            final int i = cboLeader.getSelectedIndex();
            if (!fillingLeader && session != null && i >= 0) {
                session.setLeader(i == 0 ? null : leaderChoices.get(i));
            }
        });
        btnRewards.addActionListener(e -> promptReward());
        btnPerk.addActionListener(e -> promptPerk());
        btnEvent.addActionListener(e -> promptEvent());
        btnShop.addActionListener(e -> openShop());
        btnDecks.addActionListener(e -> showDeck());
        btnStats.addActionListener(e -> showStats(false));
        btnAutoOk.setToolTipText(t("lblAlwaysOkPartnerHint"));
        btnAutoOk.addActionListener(e -> {
            FModel.getPreferences().setPref(FPref.YIELD_AUTO_OK_PARTNER, String.valueOf(!alwaysOkPartner()));
            FModel.getPreferences().save();
            showAutoOk();
        });
        btnInvite.addActionListener(e -> invite());
        btnLeave.setText(t("lblHexLeave"));
        btnLeave.addActionListener(e -> leaveHost());
        btnMenu.addActionListener(e -> openMenu(btnMenu));
        setupPage.setOnStart(this::createCampaign);
        setupPage.setOnCancel(this::endSetup);

        // a reward waiting for us is offered as soon as this screen is visible again after a fight
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) {
                menu.close();
            }
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()
                    && !fightOnScreen && !HexNet.isCampaignFight()) {
                forge.gamemodes.hexcrawl.HexMusic.leave(); // another part of Forge: its own music again
            }
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                forge.gamemodes.hexcrawl.HexMusic.enter();
                forge.gamemodes.hexcrawl.HexMusic.forget(); // whatever played before (a fight, the menus) is over
                forge.gamemodes.hexcrawl.HexMusic.playMap(getCampaign());
                if (fightOnScreen) {
                    // the match was closed from elsewhere (a guest left the result screen first):
                    // take our own result screen away as well, it would cover the map
                    fightOnScreen = false;
                    forge.gui.SOverlayUtils.hideOverlay();
                }
                SwingUtilities.invokeLater(this::promptRewardIfWaiting);
            }
        });

        HexNet.setHostListener((userName, action) -> {
            final HexSession s = session;
            if (s != null && !s.isGuest()) {
                s.handle(userName, action);
            }
        });
        HexNet.setGuestListener(state -> SwingUtilities.invokeLater(() -> onStateFromHost(state)));
        connectionTimer.start();
        voteTimer.start();
        HexNet.setFightInfo(() -> fightInfo(getCampaign()));
        HexNet.setFightHooks(
                () -> fightOnScreen || (session != null && session.isGuest() && HexNet.isGuest() && session.getCampaign() != null),
                () -> SwingUtilities.invokeLater(() -> {
                    fightOnScreen = false;
                    if (getCampaign() != null && !isShowing()) {
                        CHomeUI.SINGLETON_INSTANCE.itemClick(EDocID.HOME_HEXCRAWL);
                    }
                    refresh();
                    promptRewardIfWaiting();
                }));
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    public HexSession getSession() {
        return session;
    }

    public HexCampaign getCampaign() {
        return session == null ? null : session.getCampaign();
    }

    public HexMapPanel getMapPanel() {
        return mapPanel;
    }

    private boolean isGuest() {
        return session != null && session.isGuest();
    }

    private boolean isHosting() {
        return session != null && !session.isGuest() && HostingServer.isHosting() && FServerManager.getInstance().getLobby() != null;
    }

    /** Opens the campaign played last, if there is one. */
    public void loadLastCampaign() {
        if (session != null || HexNet.isGuest()) {
            return;
        }
        final List<String> saved = HexCampaignIO.list();
        if (!saved.isEmpty()) {
            try {
                final HexCampaign c = HexCampaignIO.load(saved.get(0));
                if (c != null) {
                    setSession(new HexSession(c));
                }
            } catch (final RuntimeException e) {
                e.printStackTrace(); // a broken save must not block the screen
            }
        }
        refresh();
    }

    private void setSession(final HexSession session0) {
        session = session0;
        if (session != null) {
            session.setListener(this);
        }
        // events that happened before this screen knew the campaign are not shown again
        shownEventSerial = session == null || session.getCampaign() == null ? -1 : session.getCampaign().eventSerial;
        shownGiftSerial = -1;
        shownSummarySerial = session == null || session.getCampaign() == null || session.getCampaign().lastFight == null
                ? -1 : session.getCampaign().lastFight.serial;
        shownFreedSerial = session == null || session.getCampaign() == null ? -1 : session.getCampaign().freedSerial;
        refresh();
    }

    // ------------------------------------------------------------------ session events

    @Override
    public void sessionChanged() {
        final HexSession s = session;
        if (s != null && !s.isGuest() && s.getCampaign() != null) {
            try {
                HexCampaignIO.save(s.getCampaign());
            } catch (final RuntimeException e) {
                e.printStackTrace();
            }
            broadcast();
        }
        SwingUtilities.invokeLater(() -> {
            refresh();
            showEventIfNew();
            showGiftIfNew();
            promptRewardIfWaiting();
            promptPerkIfWaiting();
            promptEventIfWaiting();
        });
    }

    /** The party came across an event: everybody sees what happened, once. */
    /**
     * After a fight, once this screen is back: what every head did in it, one column each with the
     * card that dealt the most damage, and the best of them marked. Once per fight.
     */
    private void showSummaryIfNew() {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || !isShowing() || campaign.lastFight == null) {
            return;
        }
        final forge.gamemodes.hexcrawl.HexFightSummary s = campaign.lastFight;
        if (shownSummarySerial < 0 && isGuest()) {
            shownSummarySerial = s.serial; // a guest's first state: nothing to repeat
            return;
        }
        if (s.serial <= shownSummarySerial) {
            return;
        }
        shownSummarySerial = s.serial;
        final JPanel panel = new JPanel(new net.miginfocom.swing.MigLayout("insets 0, gap 16", "", "[top]"));
        panel.setOpaque(false);
        for (final forge.gamemodes.hexcrawl.HexFightSummary.Head h : s.heads) {
            final JPanel col = new JPanel(new net.miginfocom.swing.MigLayout("insets 0, gap 2, wrap 1", "[160!, fill]"));
            col.setOpaque(false);
            final boolean mvp = h.name.equals(s.mvp) && s.heads.size() > 1;
            col.add(new FLabel.Builder().text((mvp ? "\u2605 " : "") + h.name).fontSize(14).fontStyle(java.awt.Font.BOLD)
                    .fontAlign(SwingConstants.LEFT).build(), "h 22!");
            if (mvp) {
                col.add(new FLabel.Builder().text(t("lblHexSummaryMvp")).fontSize(12).fontAlign(SwingConstants.LEFT).build(), "h 18!");
            }
            col.add(new FLabel.Builder().text(t("lblHexSummaryDamage", String.valueOf(h.damage))).fontSize(12).fontAlign(SwingConstants.LEFT).build(), "h 18!");
            col.add(new FLabel.Builder().text(t("lblHexSummarySpells", String.valueOf(h.spells), String.valueOf(h.lands))).fontSize(12)
                    .fontAlign(SwingConstants.LEFT).build(), "h 18!");
            final PaperCard top = h.topCard == null ? null : HexContent.get().getCard(h.topCard);
            if (top != null) {
                col.add(new FLabel.Builder().text(t("lblHexSummaryTopCard", String.valueOf(h.topCardDamage))).fontSize(12)
                        .fontAlign(SwingConstants.LEFT).build(), "h 18!");
                final CardPicturePanel pic = new CardPicturePanel();
                pic.setOpaque(false);
                pic.setItem(top);
                col.add(pic, "h 224!, w 160!");
            }
            if ("LOST".equals(s.result)) {
                // what the defeat cost this head (on the easy level nothing, on the hardest two cards)
                final forge.gamemodes.hexcrawl.HexDifficulty level = getCampaign() == null
                        ? forge.gamemodes.hexcrawl.HexDifficulty.NORMAL : getCampaign().difficulty();
                final List<String> lostNames = h.lostCards();
                final String none = level.cardsLost == 0 ? t("lblHexSummaryNoLossLevel", t(level.key()))
                        : t("lblHexSummaryNothingLost", String.valueOf(level.lossMaxPrice));
                col.add(new FLabel.Builder().text(lostNames.isEmpty() ? none : t("lblHexSummaryLostCard", String.join(", ", lostNames)))
                        .fontSize(12).fontStyle(java.awt.Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 18!, gaptop 6");
                for (final String name : lostNames) {
                    final PaperCard lost = HexContent.get().getCard(name);
                    if (lost == null) {
                        continue;
                    }
                    final CardPicturePanel lostPic = new CardPicturePanel();
                    lostPic.setOpaque(false);
                    lostPic.setItem(lost);
                    col.add(lostPic, "h 224!, w 160!");
                }
            }
            panel.add(col);
        }
        final String title = t("lblHexSummary_" + s.result, s.enemyName, String.valueOf(s.turns));
        FOptionPane.showOptionDialog(null, title, null, panel, Arrays.asList(t("lblHexClose")), 0);
    }

    /** The party freed a besieged town: its people thank it, once on every screen. */
    private void showThanksIfNew() {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || !isShowing() || session.isFightRunning()) {
            return;
        }
        if (shownFreedSerial < 0 && isGuest()) {
            shownFreedSerial = campaign.freedSerial; // a guest's first state: nothing to repeat
            return;
        }
        if (campaign.freedSerial <= shownFreedSerial) {
            return;
        }
        shownFreedSerial = campaign.freedSerial;
        new HexThanksDialog(campaign, campaign.freedTown, campaign.freedFrom).show();
    }

    private void showEventIfNew() {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || !isShowing()) {
            return;
        }
        if (shownEventSerial < 0) {
            shownEventSerial = campaign.eventSerial; // a guest's first state: nothing to repeat
            return;
        }
        if (campaign.eventSerial > shownEventSerial && campaign.lastEvent != null) {
            shownEventSerial = campaign.eventSerial;
            final List<String> cards = campaign.lastEventCards;
            if (cards.isEmpty()) {
                FOptionPane.showMessageDialog(campaign.lastEvent, t("lblHexEventTitle"));
                return;
            }
            // the cards the heads found, each under its head's name
            final JPanel pictures = new JPanel(new MigLayout("insets 0, gap 12"));
            pictures.setOpaque(false);
            for (int i = 0; i < cards.size(); i++) {
                final String owner = i < campaign.lastEventOwners.size() ? campaign.lastEventOwners.get(i) : "";
                pictures.add(HexRewardCards.captioned(owner, HexRewardCards.card(HexContent.get().getCard(cards.get(i)), null, false),
                        GIFT_W, GIFT_H));
            }
            FOptionPane.showOptionDialog(campaign.lastEvent, t("lblHexEventTitle"), null, pictures, Arrays.asList(t("lblHexClose")), 0);
        }
    }

    /** This person's head's last trade at an event (a caravan, a ruined shrine): what it gave and what it got, once. */
    private void showGiftIfNew() {
        final HexCampaign campaign = getCampaign();
        final HexPlayer mine = campaign == null || session == null ? null : campaign.player(session.getMyHead());
        if (mine == null || !isShowing()) {
            return;
        }
        if (shownGiftSerial < 0) {
            shownGiftSerial = mine.giftSerial; // the first state: nothing to repeat
            return;
        }
        if (mine.giftSerial <= shownGiftSerial) {
            return;
        }
        shownGiftSerial = mine.giftSerial;
        final List<JComponent> got = new ArrayList<>();
        final String message;
        if (mine.giftRelic != null) {
            // a relic has no card: its name on a blank card, its effects in the text
            got.add(HexRewardCards.card(null, mine.giftRelic, false));
            final HexRelic relic = HexContent.get().getRelic(mine.giftRelic);
            message = t("lblHexTradeShrineRelic", mine.name, String.valueOf(mine.giftGiven), mine.giftRelic)
                    + (relic == null ? "" : "\n" + relicEffects(relic, campaign.cycle).trim());
        } else if (mine.giftGold > 0) {
            got.add(HexRewardCards.card(HexRewardCards.goldToken(), t("lblHexTakeGold", String.valueOf(mine.giftGold)), false));
            message = t("lblHexTradeShrineGold", mine.name, String.valueOf(mine.giftGiven), String.valueOf(mine.giftGold));
        } else {
            for (final String card : mine.gifts) {
                got.add(HexRewardCards.card(HexContent.get().getCard(card), null, false));
            }
            message = t("lblHexGiftReceived", mine.name, String.valueOf(mine.giftGiven));
        }
        if (got.isEmpty()) {
            return;
        }
        // the card given, an arrow, and what came back for it
        final JPanel pictures = new JPanel(new MigLayout("insets 0, gap 12", "", "[center]"));
        pictures.setOpaque(false);
        final PaperCard given = mine.giftGiven == null ? null : HexContent.get().getCard(mine.giftGiven);
        if (given != null) {
            pictures.add(HexRewardCards.captioned(t("lblHexTradeGave"), HexRewardCards.card(given, null, false), GIFT_W, GIFT_H));
            pictures.add(label("\u2192", display(40f), GOLD_LIGHT));
        }
        final JPanel received = new JPanel(new MigLayout("insets 0, gap 12"));
        received.setOpaque(false);
        for (final JComponent card : got) {
            received.add(card, "w " + GIFT_W + "!, h " + GIFT_H + "!");
        }
        final JPanel column = new JPanel(new MigLayout("insets 0, gap 0, wrap 1"));
        column.setOpaque(false);
        column.add(label(caps(t("lblHexTradeGot")), tracked(display(12f), 0.16f), GOLD_LIGHT), "alignx center");
        column.add(received);
        pictures.add(column);
        FOptionPane.showOptionDialog(message, t("lblHexEventTitle"), null, pictures, Arrays.asList(t("lblHexClose")), 0);
    }

    private void broadcast() {
        final HexSession s = session;
        if (s != null && !s.isGuest() && HostingServer.isHosting()) {
            FServerManager.getInstance().broadcast(new HexStateEvent(s.toStateJson()));
        }
    }

    @Override
    public void startFight() {
        final HexSession s = session;
        if (s == null || s.isGuest()) {
            return;
        }
        // like the lobby's start button: the match screen has to be built on the screen thread,
        // and never on the network thread that delivered the last player's action
        // the campaign is saved with the pending fight, so a crash in the fight leads back to it
        try {
            HexCampaignIO.save(s.getCampaign());
        } catch (final RuntimeException e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            try {
                showBossIntroIfNew(); // the archmage speaks before the fight begins
                fightOnScreen = true;
                if (isHosting()) {
                    HexBattle.startHosted(s, FServerManager.getInstance().getLobby(), s::fightFinished, s::fightAborted);
                } else {
                    final HostedMatch match = HexBattle.start(s.getCampaign(), s::fightFinished, s::fightAborted);
                    match.setOnMatchOver(() -> SwingUtilities.invokeLater(() -> {
                        refresh();
                        promptRewardIfWaiting();
                    }));
                }
            } catch (final RuntimeException e) {
                e.printStackTrace();
                fightOnScreen = false;
                s.fightAborted();
            }
        });
    }

    /**
     * Before a fight against an archmage or Emrakul: a short scene with the boss, its life and
     * its words (the same line for everybody, chosen when the fight came up). Once per fight.
     */
    private void showBossIntroIfNew() {
        final HexCampaign campaign = getCampaign();
        final HexEncounter enc = campaign == null ? null : campaign.pendingEncounter;
        if (enc == null || !enc.boss) {
            return;
        }
        final String key = enc.q + "," + enc.r + "," + campaign.fights() + "," + campaign.cycle;
        if (key.equals(bossIntroShown)) {
            return;
        }
        bossIntroShown = key;
        final javax.swing.JLabel picture = new javax.swing.JLabel();
        final java.awt.image.BufferedImage sprite = HexSprites.get(enc.enemyName);
        if (sprite != null) {
            final int size = 160;
            picture.setIcon(new javax.swing.ImageIcon(sprite.getScaledInstance(size, size * sprite.getHeight() / Math.max(1, sprite.getWidth()),
                    java.awt.Image.SCALE_FAST)));
        }
        final String words = enc.intro == null ? "" : "„" + enc.intro + "“\n\n";
        FOptionPane.showOptionDialog(words + t("lblHexBossIntro", enc.enemyName, String.valueOf(campaign.enemyLife(enc))) + enemyExtras(campaign, enc),
                enc.enemyName, null, picture, Arrays.asList(t("lblHexBossIntroFight")), 0);
    }

    private void onStateFromHost(final HexStateEvent state) {
        if (session == null || !session.isGuest()) {
            setSession(HexSession.forGuest());
        }
        session.applyState(state.getStateJson(), state.getYourHead());
        if (session.isFightRunning()) {
            SwingUtilities.invokeLater(this::showBossIntroIfNew); // the host's fight is starting
        }
        // bring the campaign to the front while we are on the home screen (not in the middle of a match)
        if (Singletons.getControl().getCurrentScreen() == FScreen.HOME_SCREEN && !isShowing()) {
            CHomeUI.SINGLETON_INSTANCE.itemClick(EDocID.HOME_HEXCRAWL);
        }
    }

    /** Host: keeps the list of connected guests up to date and makes room for more of them. */
    private void hostTick() {
        if (session != null && !session.isGuest()) {
            session.tick(System.currentTimeMillis()); // a vote whose time is up
        }
        if (!isHosting()) {
            return;
        }
        final ServerGameLobby lobby = FServerManager.getInstance().getLobby();
        final List<String> guests = new ArrayList<>();
        boolean open = false;
        for (int i = 0; i < lobby.getNumberOfSlots(); i++) {
            final LobbySlot slot = lobby.getSlot(i);
            if (slot.getType() == LobbySlotType.REMOTE) {
                guests.add(slot.getName());
            } else if (slot.getType() == LobbySlotType.OPEN) {
                open = true;
            }
        }
        if (!open && lobby.getNumberOfSlots() < HexCampaign.MAX_HEADS && !session.isFightRunning()) {
            lobby.addSlot();
        }
        final boolean changed = session.setGuests(guests);
        // also reaches people who reconnected; not during a fight, when the table needs the line
        // and the campaign does not change
        if (!changed && ++ticks % 4 == 0 && !session.isFightRunning()) {
            broadcast();
        }
        if (btnInvite.isVisible()) {
            refresh();
        }
    }

    // ------------------------------------------------------------------ display

    private static JPanel transparent(final MigLayout layout) {
        final JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    /** A place for a piece of wrapped text, replaced when the text changes. */
    private static JPanel box() {
        final JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setOpaque(false);
        return panel;
    }

    private static void setText(final JPanel box, final String text, final java.awt.Font font, final java.awt.Color color, final int width) {
        final boolean has = text != null && !text.trim().isEmpty();
        final String key = has ? text + '|' + color.getRGB() + '|' + width : "";
        box.setVisible(has);
        if (key.equals(box.getClientProperty("shown"))) {
            return;
        }
        box.putClientProperty("shown", key);
        box.removeAll();
        if (has) {
            box.add(wrapped(text, font, color, width));
        }
        box.revalidate();
    }

    /** The colours of the region the party stands in, for the side panel. */
    private Palette palette() {
        final HexCampaign campaign = getCampaign();
        return HexTownStyle.palette(campaign == null || campaign.tile() == null ? HexColor.COLORLESS : campaign.tile().color);
    }

    public void refresh() {
        final HexCampaign campaign = getCampaign();
        if (campaign != null) {
            HexNet.setFleePrice(campaign.gold(forge.gamemodes.hexcrawl.HexFight.FLEE_GOLD));
            if (isShowing()) {
                forge.gamemodes.hexcrawl.HexMusic.playMap(campaign); // a new region, a town
            }
            if (campaign.pendingEncounter != null) {
                HexNet.setFightMusic(forge.gamemodes.hexcrawl.HexMusic.fightPlaylist(campaign, campaign.pendingEncounter));
            }
        }
        mapPanel.setCampaign(campaign);
        mapPanel.setVotes(session == null ? null : session.getVotes());
        mapPanel.setPings(session == null ? null : session.getPings());
        labyrinthPanel.setCampaign(campaign);
        labyrinthPanel.setVotes(session == null ? null : session.getVotes());
        final boolean has = campaign != null;
        final boolean guest = isGuest() || HexNet.isGuest();
        if (settingUp && guest) { // joined a friend's campaign meanwhile
            endSetup();
            return;
        }
        mapCards.show(mapArea, settingUp ? "setup" : campaign != null && campaign.inLabyrinth() ? "labyrinth" : "hex");
        final Palette palette = palette();
        lblPlace.setForeground(palette.light());
        btnInvite.setVisible(has && !guest);
        btnInvite.setText(t(HostingServer.isHosting() ? "lblHexInviteShow" : "lblHexInvite"));
        btnLeave.setVisible(HexNet.isGuest());
        showConnection(guest);
        btnDecks.setVisible(has);
        btnStats.setVisible(has);
        btnAutoOk.setVisible(has && getCampaign().players.size() > 1);
        showAutoOk();
        btnNewGamePlus.setVisible(has && !guest && getCampaign().won);
        if (has) {
            btnNewGamePlus.setText(t("lblHexNewGamePlus", String.valueOf(getCampaign().cycle)));
        }
        if (!has) {
            btnFight.setVisible(false);
            btnDungeon.setVisible(false);
            btnRewards.setVisible(false);
            btnPerk.setVisible(false);
            btnEvent.setVisible(false);
            btnShop.setVisible(false);
            threatBox.setVisible(false);
            voteBox.setVisible(false);
            for (final JComponent c : new JComponent[] { hdParty, cboLeader, pnlParty, hdBosses, bossTrack, lblRecord, hdTasks, tasksBox,
                    hdCycle, cycleBox, hdBoosts, boostsBox }) {
                c.setVisible(false);
            }
            lblTitle.setText(t("lblHexCampaign"));
            lblPlace.setText(caps(t(guest ? "lblHexWaitingForHost" : "lblHexNoCampaign")));
            fillJournal(Collections.singletonList(t("lblHexIntro")));
            revalidate();
            repaint();
            return;
        }
        final String me = session.getMyHead();
        final Set<String> present = session.getPresent();
        final Set<String> ready = session.getFightReady();
        final Map<String, String> votes = session.getVotes();
        int humans = 0;
        for (final HexPlayer p : campaign.players) {
            if (!session.isAiHead(p.name)) {
                humans++;
            }
        }

        // what there is to do now
        fillThreat(campaign);
        btnFight.setVisible(campaign.pendingEncounter != null);
        // a dungeon: in, when the party stands before it; out, at its entrance (both are votes, like a step)
        final boolean leave = campaign.canLeaveLabyrinth();
        final boolean enter = campaign.canEnterLabyrinth();
        btnDungeon.setVisible(leave || enter);
        if (leave || enter) {
            final String mine = votes.get(me);
            final boolean voted = leave ? HexSession.EXIT_KEY.equals(mine) : campaign.tile().key().equals(mine);
            btnDungeon.setText(t(voted ? "lblHexDungeonVoted" : leave ? "lblHexDungeonLeave" : "lblHexDungeonEnter"));
            btnDungeon.setEnabled(!voted);
        }
        if (session.isFightRunning()) {
            btnFight.setText(t("lblHexFightRunning"));
            // the host may set a fight back that hangs (its end never came), see resetFight
            btnFight.setEnabled(!session.isGuest());
            btnFight.setToolTipText(session.isGuest() ? null : t("lblHexResetFightHint"));
        } else if (ready.contains(me)) {
            btnFight.setToolTipText(null);
            btnFight.setText(t("lblHexFightWaiting", String.valueOf(ready.size()), String.valueOf(humans)));
            btnFight.setEnabled(false);
        } else {
            btnFight.setToolTipText(null);
            btnFight.setText(t("lblHexFight"));
            btnFight.setEnabled(true);
        }
        btnRewards.setVisible(campaign.pendingEncounter == null && campaign.pendingReward(me) != null);
        final HexPlayer mine = campaign.player(me);
        btnPerk.setVisible(mine != null && !mine.perkOffer.isEmpty() && campaign.pendingReward(me) == null);
        btnEvent.setVisible(mine != null && mine.eventOffer != null);
        btnShop.setVisible(campaign.inTown());

        lblTitle.setText(campaign.cycle > 1 ? campaign.name + "  –  " + t("lblHexCycle", String.valueOf(campaign.cycle - 1)) : campaign.name);
        lblTitle.setToolTipText(lblTitle.getText() + " – " + t("lblHexTitleHint"));
        lblTitle.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        final HexTile here = campaign.tile();
        String place = t(here.color.getLabelKey());
        if (here.poi == HexPoi.TOWN) {
            place += " · " + t("lblHexTown") + " " + here.name;
        } else if (here.poi == HexPoi.BOSS) {
            place += " · " + here.name;
        }
        lblPlace.setText(caps(place));

        // the vote, or how the campaign stands
        final boolean myVoteMissing = !votes.isEmpty() && !votes.containsKey(me) && present.contains(me) && humans > 1;
        final java.awt.Color orange = new java.awt.Color(0xF5A55A);
        if (campaign.won) {
            setText(voteBox, t("lblHexJournalVictory") + "\n" + t(guest ? "lblHexNewGamePlusWait" : "lblHexNewGamePlusHint"),
                    body(java.awt.Font.BOLD, 12.5f), GOLD_LIGHT, SIDE_W - 32);
        } else if (myVoteMissing) {
            setText(voteBox, t("lblHexYouHaveNotVoted", String.valueOf(Math.max(0, session.voteSecondsLeft(System.currentTimeMillis())))),
                    body(java.awt.Font.BOLD, 12.5f), orange, SIDE_W - 32);
        } else if (campaign.pendingEncounter == null && humans > 1 && !campaign.isBusy()) {
            final int secs = session.voteSecondsLeft(System.currentTimeMillis());
            setText(voteBox, secs >= 0
                    ? t("lblHexVoteCountdown", String.valueOf(votes.size()), String.valueOf(humans), String.valueOf(secs))
                    : t("lblHexVoteHint", String.valueOf(votes.size()), String.valueOf(humans)),
                    body(java.awt.Font.PLAIN, 12.5f), palette.light(), SIDE_W - 32);
        } else {
            setText(voteBox, null, null, MUTED, 0);
        }

        // the party
        final java.util.Set<String> shopping = session.getShopping();
        // a guest with another program can't play the fights: named so in the party
        final java.util.Set<String> otherBuild = guest ? java.util.Collections.emptySet() : session.guestsWithOtherBuild();
        hdParty.setVisible(true);
        pnlParty.setVisible(true);
        fillLeader(campaign, humans, guest);
        while (partyRows.size() < campaign.players.size()) {
            final PartyRow row = new PartyRow();
            partyRows.add(row);
            pnlParty.add(row);
        }
        while (partyRows.size() > campaign.players.size()) {
            pnlParty.remove(partyRows.remove(partyRows.size() - 1));
        }
        for (int i = 0; i < campaign.players.size(); i++) {
            final HexPlayer p = campaign.players.get(i);
            String tag = null;
            if (otherBuild.contains(p.name)) {
                tag = t("lblHexOtherBuild");
            } else if (p.ai) {
                tag = t("lblHexCompanion");
            } else if (!present.contains(p.name)) {
                tag = t("lblHexAway");
            } else if (p.name.equals(me) && humans > 1) {
                tag = t("lblHexYou");
            }
            final List<String> carried = new ArrayList<>();
            carried.add(t("lblHexCardsCount", String.valueOf(p.deck.size())));
            if (!p.relics.isEmpty()) {
                carried.add(t("lblHexRelicCount", String.valueOf(p.relics.size())));
            }
            if (!p.perks.isEmpty()) {
                carried.add(t("lblHexPerkCount", String.valueOf(p.perks.size())));
            }
            final List<String> doing = new ArrayList<>();
            java.awt.Color statusColor = palette.light();
            if (votes.containsKey(p.name)) {
                doing.add(t("lblHexVotesFor", describeVote(campaign, votes.get(p.name)))
                        + (p.name.equals(campaign.leader) ? " (" + t("lblHexLeaderDecides") + ")" : ""));
            } else if (!votes.isEmpty() && !p.ai && present.contains(p.name)) {
                doing.add(t("lblHexNotVoted"));
                statusColor = orange;
            }
            if (shopping.contains(p.name)) {
                doing.add(t("lblHexInShops"));
            }
            if (campaign.pendingEncounter == null && campaign.pendingReward(p.name) != null) {
                doing.add(t("lblHexChoosing"));
            }
            if (!p.perkOffer.isEmpty() && !session.isAiHead(p.name)) {
                doing.add(t("lblHexChoosingPerk"));
            }
            // ready check before a fight: who is ready, who has looked at their deck
            if (campaign.pendingEncounter != null && !session.isFightRunning()) {
                if (session.isAiHead(p.name)) {
                    doing.add(t("lblHexReadyAi"));
                } else if (ready.contains(p.name)) {
                    doing.add(t("lblHexReady"));
                    statusColor = new java.awt.Color(0x9BD18B);
                } else {
                    doing.add(t("lblHexNotReady"));
                    statusColor = orange;
                }
                if (!session.isAiHead(p.name) && campaign.deckChecked.contains(p.name)) {
                    doing.add(t("lblHexDeckChecked"));
                }
            }
            partyRows.get(i).set(p.name, tag, p.gold, String.join(" · ", carried), String.join(" · ", doing),
                    statusColor, headColor(i), p.name.equals(me));
        }

        // the archmages, the record, tasks, what is different in this cycle and in the next fight
        final String[] bosses = new String[HexColor.COLORS.length];
        final java.awt.Color[] colors = new java.awt.Color[bosses.length];
        final boolean[] beaten = new boolean[bosses.length];
        for (int i = 0; i < bosses.length; i++) {
            final HexColor c = HexColor.COLORS[i];
            bosses[i] = HexContent.get().getBossName(c);
            colors[i] = new java.awt.Color(c.getRgb());
            beaten[i] = campaign.defeatedBosses.contains(c);
        }
        bossTrack.set(bosses, colors, beaten, t("lblHexBeaten"));
        String record = t("lblHexRecord", String.valueOf(campaign.battlesWon), String.valueOf(campaign.battlesLost));
        if (campaign.goldMultiplier > 1) {
            record += "  ·  " + t("lblHexGoldMultiplierInfo", String.valueOf(campaign.goldMultiplier));
        }
        if (campaign.difficulty() != forge.gamemodes.hexcrawl.HexDifficulty.NORMAL) {
            record += "  ·  " + t(campaign.difficulty().key());
        }
        if (campaign.teamPermanents) {
            record += "  ·  " + t("lblHexTeamPermanentsInfo");
        }
        lblRecord.setText(record);
        for (final JComponent c : new JComponent[] { hdBosses, bossTrack, lblRecord }) {
            c.setVisible(true);
        }
        final List<String> tasks = new ArrayList<>();
        for (final HexQuest quest : campaign.quests) {
            tasks.add(t(quest.archenemy ? "lblHexQuestLineArchenemy" : "lblHexQuestLine", quest.enemyName, String.valueOf(campaign.gold(quest.gold))));
        }
        section(hdTasks, tasksBox, tasks);
        section(hdCycle, cycleBox, campaign.cycleAdvantages());
        final List<String> boosts = new ArrayList<>(campaign.fightBoosts());
        final forge.gamemodes.hexcrawl.HexDungeon inside = campaign.inLabyrinth() ? campaign.dungeonHere() : null;
        if (inside != null) {
            for (final String ruleCard : inside.ruleCards()) {
                final forge.item.IPaperCard rule = HexContent.get().findStartCard(ruleCard);
                if (rule != null) {
                    boosts.add(t("lblHexLabyRule", rule.getName()));
                }
            }
            boosts.add(t("lblHexLabyLife", String.valueOf(inside.lifeLeft)));
            for (final String buff : inside.buffs) {
                boosts.add(t("lblHexBuff" + buff, String.valueOf(HexCampaign.DUNGEON_SHRINE_LIFE), String.valueOf(HexCampaign.DUNGEON_WEAKEN_LIFE)));
            }
        }
        section(hdBoosts, boostsBox, boosts);

        final List<String> lines = new ArrayList<>(campaign.journal);
        Collections.reverse(lines);
        fillJournal(lines);
        if (shopRefresher != null) {
            shopRefresher.run();
        }
        if (deckRefresher != null) {
            deckRefresher.run();
        }
        revalidate();
        repaint();
    }

    /** A section of short lines (tasks, advantages, boosts), hidden while it has none. */
    private void section(final Heading heading, final JPanel box, final List<String> lines) {
        heading.setVisible(!lines.isEmpty());
        final StringBuilder sb = new StringBuilder();
        for (final String line : lines) {
            sb.append(sb.length() == 0 ? "" : "\n").append("• ").append(line);
        }
        setText(box, sb.toString(), body(java.awt.Font.PLAIN, 12.5f), TEXT, MIDDLE_W);
    }

    /** The journal, newest first; the latest entry a little brighter. */
    private void fillJournal(final List<String> lines) {
        final List<String> shown = lines.subList(0, Math.min(lines.size(), 80));
        final String key = shown.size() + "|" + (shown.isEmpty() ? "" : shown.get(0)) + "|" + lines.size();
        hdJournal.setVisible(!shown.isEmpty());
        if (key.equals(journalShown)) {
            return;
        }
        journalShown = key;
        pnlJournal.removeAll();
        for (int i = 0; i < shown.size(); i++) {
            pnlJournal.add(wrapped(shown.get(i), body(java.awt.Font.PLAIN, 12f), i == 0 ? TEXT : new java.awt.Color(0xbdb6a6), MIDDLE_W));
        }
        pnlJournal.revalidate();
    }

    /** The enemy the party is about to fight, with its figure, life and what the scouts found out. */
    private void fillThreat(final HexCampaign campaign) {
        final HexEncounter enc = campaign.pendingEncounter;
        if (enc == null) {
            threatBox.setVisible(false);
            threatBox.putClientProperty("shown", null);
            return;
        }
        final String more = (enemyExtras(campaign, enc) + scoutReport(campaign, enc)).trim();
        final String key = enc.enemyName + '|' + campaign.enemyLife(enc) + '|' + more;
        threatBox.setVisible(true);
        if (key.equals(threatBox.getClientProperty("shown"))) {
            return;
        }
        threatBox.putClientProperty("shown", key);
        threatBox.removeAll();
        final Threat card = new Threat(new MigLayout("insets 12 12 14 12, gap 10", "[56!][grow, fill]", "[top]"));
        card.add(new Sprite(HexSprites.get(enc.enemyName), 56), "w 56!, h 56!");
        final int textW = SIDE_W - 32 - 24 - 56 - 10;
        final JPanel text = transparent(new MigLayout("insets 0, gap 0 2, wrap 1"));
        text.add(label(caps(t(enc.boss ? "lblHexBossAheadTitle" : enc.archenemy ? "lblHexArchenemyAheadTitle" : "lblHexEnemyAheadTitle")),
                tracked(display(11f), 0.2f), BAD));
        text.add(wrapped(enc.enemyName, display(17f), TEXT_BRIGHT, textW));
        text.add(label(t("lblHexLifeAmount", String.valueOf(campaign.enemyLife(enc))), body(java.awt.Font.BOLD, 12.5f), new java.awt.Color(0xf3c4b2)));
        if (!more.isEmpty()) {
            text.add(wrapped(more, body(java.awt.Font.PLAIN, 12f), new java.awt.Color(0xe0d2c8), textW), "gaptop 4");
        }
        card.add(text, "wmin 0");
        card.setToolTipText("<html>" + describe(enc).replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") + "</html>");
        threatBox.add(card);
        threatBox.revalidate();
    }

    private String describe(final HexEncounter enc) {
        return t(enc.boss ? "lblHexBossAhead" : "lblHexEncounterAhead", enc.enemyName, String.valueOf(getCampaign().enemyLife(enc)))
                + enemyExtras(getCampaign(), enc) + scoutReport(getCampaign(), enc);
    }

    private static final String[] COLOR_NAME_KEYS = { "lblWhite", "lblBlue", "lblBlack", "lblRed", "lblGreen" };

    /** What the match screen's banner shows of the coming (or running) fight, or null if there is none. */
    public static forge.gamemodes.hexcrawl.HexFightInfo fightInfo(final HexCampaign campaign) {
        final HexEncounter enc = campaign == null ? null : campaign.pendingEncounter;
        if (enc == null) {
            return null;
        }
        final Localizer loc = Localizer.getInstance();
        final forge.gamemodes.hexcrawl.HexFightInfo info = new forge.gamemodes.hexcrawl.HexFightInfo();
        info.enemyName = enc.enemyName;
        if (enc.boss) {
            info.kind = loc.getMessage(enc.color == HexColor.COLORLESS ? "lblHexFightKindBoss" : "lblHexFightKindArchmage");
        } else if (enc.isGuardian()) {
            info.kind = loc.getMessage("lblHexFightKindDungeonBoss");
        } else if (enc.dungeonStage > 0) {
            info.kind = loc.getMessage("lblHexFightKindDungeonGuard");
        } else if (enc.siege) {
            info.kind = loc.getMessage("lblHexFightKindSiege");
        } else if (enc.archenemy) {
            info.kind = loc.getMessage("lblHexFightKindArchenemy");
        } else if (enc.questId != 0) {
            info.kind = loc.getMessage("lblHexFightKindTask");
        } else {
            info.kind = loc.getMessage(enc.roaming ? "lblHexFightKindHunter" : "lblHexFightKindEnemy");
        }
        info.startLife = campaign.enemyLife(enc);
        info.phaseCard = HexContent.get().phaseTwoCard(enc);
        if (enc.dungeonStage > 0) {
            for (final String ruleCard : campaign.dungeonRuleCards(enc)) {
                final forge.item.IPaperCard rule = HexContent.get().findStartCard(ruleCard);
                if (rule != null) {
                    info.pills.add(loc.getMessage("lblHexLabyRule", rule.getName()));
                }
            }
            info.pills.add(loc.getMessage("lblHexFightPillDungeonLife", String.valueOf(campaign.partyStartLife())));
            for (final String buff : forge.gamemodes.hexcrawl.HexLabyrinth.BUFFS) {
                if (campaign.dungeonBuff(buff) > 0) {
                    info.pills.add(loc.getMessage("lblHexBuff" + buff, String.valueOf(HexCampaign.DUNGEON_SHRINE_LIFE),
                            String.valueOf(HexCampaign.DUNGEON_WEAKEN_LIFE)));
                }
            }
        }
        final forge.gamemodes.hexcrawl.HexBiomeRule biome = forge.gamemodes.hexcrawl.HexBiomeRule.forFight(campaign, enc);
        if (biome != null) {
            info.pills.add(loc.getMessage(biome.getLabelKey()));
        }
        info.pills.addAll(campaign.cycleAdvantages());
        final String scout = (scoutReport(campaign, enc) + "\n" + enemyExtras(campaign, enc).trim()).trim();
        info.scout = scout.isEmpty() ? null : scout;
        return info;
    }

    /** Before a fight: the enemy deck's colours, its most valuable cards and the reward. */
    static String scoutReport(final HexCampaign campaign, final HexEncounter enc) {
        final Localizer loc = Localizer.getInstance();
        final StringBuilder sb = new StringBuilder();
        final HexContent.ScoutReport report = HexContent.get().scout(enc);
        if (report != null) {
            final List<String> colors = new ArrayList<>();
            for (final HexColor c : report.colors) {
                colors.add(loc.getMessage(COLOR_NAME_KEYS[java.util.Arrays.asList(HexColor.COLORS).indexOf(c)]));
            }
            if (!colors.isEmpty()) {
                sb.append('\n').append(loc.getMessage("lblHexScoutColors", String.join(", ", colors)));
            }
            if (!report.keyCards.isEmpty()) {
                final List<String> cards = new ArrayList<>();
                for (final String name : report.keyCards) {
                    cards.add(forge.util.CardTranslation.getTranslatedName(name));
                }
                sb.append('\n').append(loc.getMessage("lblHexScoutCards", String.join(", ", cards)));
            }
        }
        if (enc.gold > 0) {
            sb.append('\n').append(loc.getMessage("lblHexScoutReward", String.valueOf(campaign.gold(enc.gold))));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ travelling

    /** What a vote is for, for the party panel: a field next to the party, a far field, or fast travel. */
    private String describeVote(final HexCampaign campaign, final String key) {
        if (HexSession.EXIT_KEY.equals(key)) {
            return t("lblHexVoteExit");
        }
        if (key != null && key.startsWith(HexSession.ROOM_KEY)) {
            return t("lblHexVoteRoom");
        }
        final HexTile t = key == null ? null : campaign.map.tiles.get(key);
        if (t == null) {
            return "?";
        }
        final String where = t.name != null ? (t.poi == HexPoi.TOWN ? t("lblHexTown") + " " + t.name : t.name) : t(t.color.getLabelKey());
        if (campaign.isAdjacent(t)) {
            return where;
        }
        if (campaign.canFastTravel(t)) {
            return t("lblHexVoteFastTravel", where, String.valueOf(HexCampaign.FAST_TRAVEL_GOLD));
        }
        final List<HexTile> route = campaign.route(t);
        return t("lblHexVoteMarch", where, String.valueOf(route == null ? 0 : route.size()));
    }

    /** The host's leader choice; only with more than one person at the table. */
    private void fillLeader(final HexCampaign campaign, final int humans, final boolean guest) {
        cboLeader.setVisible(!guest && humans > 1);
        if (!cboLeader.isVisible()) {
            return;
        }
        final List<String> names = new ArrayList<>();
        names.add(null);
        for (final HexPlayer p : campaign.players) {
            if (!p.ai) {
                names.add(p.name);
            }
        }
        fillingLeader = true;
        try {
            if (!names.equals(leaderChoices)) {
                leaderChoices.clear();
                leaderChoices.addAll(names);
                cboLeader.removeAllItems();
                for (final String name : names) {
                    cboLeader.addItem(name == null ? t("lblHexLeaderNobody") : t("lblHexLeaderIs", name));
                }
            }
            cboLeader.setSelectedIndex(Math.max(0, leaderChoices.indexOf(campaign.leader)));
        } finally {
            fillingLeader = false;
        }
    }

    /**
     * The keys that step to a neighbouring field, as {dq, dr} (see {@link HexMap#DIRECTIONS}): on the screen
     * Q goes up left, E up right, A left, D right, Z down left and C down right; Y too goes down left, as it sits
     * where Z does on a German keyboard.
     */
    public static final Map<Character, int[]> STEP_KEYS;
    static {
        final Map<Character, int[]> keys = new java.util.LinkedHashMap<>();
        keys.put('Q', new int[] { 0, -1 });
        keys.put('E', new int[] { 1, -1 });
        keys.put('A', new int[] { -1, 0 });
        keys.put('D', new int[] { 1, 0 });
        keys.put('Z', new int[] { -1, 1 });
        keys.put('Y', new int[] { -1, 1 });
        keys.put('C', new int[] { 0, 1 });
        STEP_KEYS = Collections.unmodifiableMap(keys);
    }

    /** Steering with the keyboard: a step does what a click on the field does; F fights, T opens the town, Space finds the party, H the legend. */
    private void bindKeys() {
        final javax.swing.InputMap keys = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final javax.swing.ActionMap actions = getActionMap();
        for (final Map.Entry<Character, int[]> step : STEP_KEYS.entrySet()) {
            final int dq = step.getValue()[0];
            final int dr = step.getValue()[1];
            final char key = step.getKey();
            bindKey(keys, actions, key, "hexStep" + key, () -> {
                final HexCampaign campaign = getCampaign();
                if (campaign != null && campaign.inLabyrinth()) {
                    stepRoomBy(ROOM_KEYS.get(key)); // A and D: left and right in the labyrinth as well
                    return;
                }
                final HexTile target = campaign == null ? null : campaign.map.get(campaign.partyQ + dq, campaign.partyR + dr);
                if (target != null) {
                    onTileClicked(target);
                }
            });
        }
        // in a labyrinth: W A S D or the arrow keys, X out at the entrance
        bindKey(keys, actions, 'W', "hexRoomUp", () -> stepRoomBy(ROOM_KEYS.get('W')));
        bindKey(keys, actions, 'S', "hexRoomDown", () -> stepRoomBy(ROOM_KEYS.get('S')));
        bindCode(keys, actions, java.awt.event.KeyEvent.VK_UP, "hexArrowUp", () -> stepRoomBy(ROOM_KEYS.get('W')));
        bindCode(keys, actions, java.awt.event.KeyEvent.VK_DOWN, "hexArrowDown", () -> stepRoomBy(ROOM_KEYS.get('S')));
        bindCode(keys, actions, java.awt.event.KeyEvent.VK_LEFT, "hexArrowLeft", () -> stepRoomBy(ROOM_KEYS.get('A')));
        bindCode(keys, actions, java.awt.event.KeyEvent.VK_RIGHT, "hexArrowRight", () -> stepRoomBy(ROOM_KEYS.get('D')));
        bindKey(keys, actions, 'X', "hexLeave", this::onExitClicked);
        bindKey(keys, actions, 'F', "hexFight", () -> {
            if (btnFight.isVisible() && btnFight.isEnabled()) {
                btnFight.doClick();
            }
        });
        bindKey(keys, actions, 'T', "hexTown", () -> {
            if (btnShop.isVisible() && btnShop.isEnabled()) {
                btnShop.doClick();
            }
        });
        bindKey(keys, actions, ' ', "hexParty", mapPanel::centerOnParty);
        bindKey(keys, actions, 'H', "hexLegend", () -> {
            final HexCampaign campaign = getCampaign();
            if (campaign != null && campaign.inLabyrinth()) {
                labyrinthPanel.toggleLegend();
            } else {
                mapPanel.toggleLegend();
            }
        });
    }

    /** The keys that step to a room of a labyrinth, as {dx, dy}: W up, A left, S down, D right (and the arrows). */
    public static final Map<Character, int[]> ROOM_KEYS;
    static {
        final Map<Character, int[]> keys = new java.util.LinkedHashMap<>();
        keys.put('W', new int[] { 0, -1 });
        keys.put('A', new int[] { -1, 0 });
        keys.put('S', new int[] { 0, 1 });
        keys.put('D', new int[] { 1, 0 });
        ROOM_KEYS = Collections.unmodifiableMap(keys);
    }

    /** Inside a labyrinth: a step to the room next to the party's that way (if there is a door). */
    private void stepRoomBy(final int[] dir) {
        final HexCampaign campaign = getCampaign();
        final forge.gamemodes.hexcrawl.HexDungeon d = campaign == null || !campaign.inLabyrinth() ? null : campaign.dungeonHere();
        if (d != null && dir != null) {
            onRoomClicked(d.x + dir[0], d.y + dir[1]);
        }
    }

    private void bindKey(final javax.swing.InputMap keys, final javax.swing.ActionMap actions, final char key, final String name,
            final Runnable action) {
        bindCode(keys, actions, key == ' ' ? java.awt.event.KeyEvent.VK_SPACE : key, name, action); // VK_A to VK_Z are the capital letters
    }

    private void bindCode(final javax.swing.InputMap keys, final javax.swing.ActionMap actions, final int code, final String name,
            final Runnable action) {
        keys.put(javax.swing.KeyStroke.getKeyStroke(code, 0), name);
        actions.put(name, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent e) {
                if (isShowing() && session != null && !settingUp && !menu.isOpen()
                        && !(java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent)) {
                    action.run();
                }
            }
        });
    }

    /** A room of the labyrinth clicked: a vote for it, if it is next to the party's. */
    private void onRoomClicked(final int x, final int y) {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || session == null || session.isFightRunning() || !campaign.inLabyrinth()) {
            return;
        }
        if (campaign.canStepRoom(x, y)) {
            session.voteRoom(x, y);
            refresh();
        } else if (campaign.isBusy() && campaign.dungeonHere().nextToParty(campaign.dungeonHere().room(x, y))) {
            FOptionPane.showMessageDialog(t("lblHexBusy"), campaign.name);
        }
    }

    /** The way out of the labyrinth clicked: a vote to leave (at the entrance only). */
    private void onExitClicked() {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || session == null || session.isFightRunning() || !campaign.inLabyrinth()) {
            return;
        }
        if (campaign.canLeaveLabyrinth()) {
            session.voteExit();
            refresh();
        } else {
            FOptionPane.showMessageDialog(t("lblHexLabyExitFar"), campaign.name);
        }
    }

    /** Before the party goes into a dungeon: its decks lock, and its rule cards are in play in every fight. */
    private boolean confirmDungeon(final HexTile target) {
        if (target.poi != HexPoi.DUNGEON || target.dungeon == null || target.dungeon.cleared) {
            return true;
        }
        // the rule cards themselves, big: they are in play in every fight inside
        final List<String> names = new ArrayList<>();
        JPanel panel = null;
        for (final String ruleCard : target.dungeon.ruleCards()) {
            final forge.item.IPaperCard rule = HexContent.get().findStartCard(ruleCard);
            if (rule == null) {
                continue;
            }
            names.add(rule.getName());
            if (panel == null) {
                panel = new JPanel(new MigLayout("insets 0, gap 12, alignx center"));
                panel.setOpaque(false);
            }
            panel.add(HexRewardCards.card(rule, null, false), "w " + HexRewardCards.W + "!, h " + HexRewardCards.H + "!");
        }
        final JComponent picture = panel;
        final String rules = names.isEmpty() ? "-" : String.join(" + ", names);
        final String question = target.dungeon.multicolor ? t("lblHexRainbowConfirm", rules) : t("lblHexDungeonConfirm", rules);
        final int choice = FOptionPane.showOptionDialog(question, t(target.dungeon.multicolor ? "lblHexRainbowDungeon" : "lblHexDungeon"),
                picture == null ? FOptionPane.QUESTION_ICON : null, picture,
                Arrays.asList(Localizer.getInstance().getMessage("lblYes"), Localizer.getInstance().getMessage("lblNo")), 0);
        return choice == 0;
    }

    private void onTileClicked(final HexTile target) {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || session.isFightRunning() || campaign.inLabyrinth()) {
            return;
        }
        if (target == campaign.tile()) {
            if (campaign.canEnterLabyrinth() && confirmDungeon(target)) {
                session.vote(target.q, target.r); // into the dungeon the party stands before
                refresh();
            }
            return;
        }
        if (!campaign.isAdjacent(target) && target.poi == HexPoi.TOWN && target.explored && !campaign.canRoute(target)) {
            if (campaign.canFastTravel(target)) {
                final boolean go = FOptionPane.showConfirmDialog(
                        t("lblHexFastTravelConfirm", target.name, String.valueOf(HexCampaign.FAST_TRAVEL_GOLD)), t("lblHexFastTravel"));
                if (go) {
                    session.vote(target.q, target.r);
                    refresh();
                }
            } else if (campaign.inTown()) {
                FOptionPane.showMessageDialog(t("lblHexFastTravelNoGold", String.valueOf(HexCampaign.FAST_TRAVEL_GOLD)), t("lblHexFastTravel"));
            } else {
                FOptionPane.showMessageDialog(t("lblHexFastTravelFromTown"), t("lblHexFastTravel"));
            }
            return;
        }
        if (!campaign.isAdjacent(target) && campaign.canRoute(target)) {
            if (!confirmDungeon(target)) {
                return;
            }
            session.vote(target.q, target.r); // a far field: the party marches there step by step
            refresh();
            return;
        }
        if (!campaign.canEnter(target)) {
            if (campaign.isAdjacent(target)) {
                final String why = campaign.won ? "lblHexJournalVictory" : campaign.isBusy() ? "lblHexBusy" : "lblHexCenterLocked";
                FOptionPane.showMessageDialog(t(why), campaign.name);
            }
            return; // only neighbouring fields can be entered; they are outlined on the map
        }
        if (!confirmDungeon(target)) {
            return;
        }
        final boolean alone = session.getPresent().size() == 1 && !session.isGuest();
        session.vote(target.q, target.r);
        // playing alone the step happens at once, and so does a fight; only a hunter that caught
        // the party leaves time to look at the deck first
        if (alone && campaign.pendingEncounter != null && campaign.pendingEncounter.ambush && !session.isFightRunning()) {
            refresh();
            final int choice = FOptionPane.showOptionDialog(t("lblHexAmbush", campaign.pendingEncounter.enemyName) + "\n\n"
                    + describe(campaign.pendingEncounter), campaign.name,
                    FOptionPane.INFORMATION_ICON, Arrays.asList(t("lblHexFight"), t("lblHexDeckFirst")), 0);
            if (choice == 0) {
                session.readyToFight();
            } else {
                showDeck();
            }
        }
    }

    // ------------------------------------------------------------------ rewards

    private void promptRewardIfWaiting() {
        showSummaryIfNew(); // first what happened in the fight, then the thanks of a freed town, then the reward
        showThanksIfNew();
        final HexCampaign campaign = getCampaign();
        if (campaign == null || rewardPromptOpen || !isShowing() || session.isFightRunning()
                || campaign.pendingEncounter != null || campaign.pendingReward(session.getMyHead()) == null) {
            promptNewGamePlusIfWon();
            return;
        }
        promptReward();
        promptNewGamePlusIfWon();
    }

    /**
     * Once the last enemy (Emrakul) of the current cycle is beaten and everybody has taken their
     * reward, the host is asked whether to start New Game Plus right away; a guest is told that the
     * host can. Asked once per cycle; the button stays for later.
     */
    private void promptNewGamePlusIfWon() {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || session == null || !campaign.won || campaign.isBusy() || session.isFightRunning()
                || !isShowing() || rewardPromptOpen || newGamePlusPromptedCycle == campaign.cycle) {
            return;
        }
        newGamePlusPromptedCycle = campaign.cycle;
        showStats(true); // the chronicle of the cycle first
        if (isGuest() || HexNet.isGuest()) {
            FOptionPane.showMessageDialog(ending(campaign) + t("lblHexVictoryGuest", campaign.finalBossName()), campaign.name);
            return;
        }
        final int choice = FOptionPane.showOptionDialog(
                ending(campaign) + t("lblHexVictoryNewGamePlus", campaign.finalBossName(), String.valueOf(campaign.cycle),
                        String.valueOf(HexCampaign.LIFE_PER_CYCLE), String.valueOf(HexCampaign.ADVANTAGES_PER_CYCLE)),
                campaign.name, FOptionPane.INFORMATION_ICON,
                Arrays.asList(t("lblHexNewGamePlus", String.valueOf(campaign.cycle)), t("lblHexLater")), 0);
        if (choice == 0) {
            session.newGamePlus();
        }
    }

    /** The true ending's line before the victory's text (after Arzakon), else nothing. */
    private String ending(final HexCampaign campaign) {
        return campaign.arzakonBeaten ? t("lblHexArzakonEnding") + "\n\n" : "";
    }

    /**
     * A chest's relic whose slot is taken: this person clicks the relic their head keeps, the other is sold. Returns
     * false if the window was closed without a choice.
     */
    private boolean promptChestRelic(final HexCampaign campaign, final HexReward reward) {
        final HexContent content = HexContent.get();
        final HexRelic worn = content.getRelic(reward.chestRelicWorn);
        final HexRelic found = content.getRelic(reward.chestRelic);
        if (worn == null || found == null) {
            return true;
        }
        final JPanel pictures = new JPanel(new MigLayout("insets 0, gap 24"));
        pictures.setOpaque(false);
        final List<JComponent> clickable = new ArrayList<>();
        for (final HexRelic relic : new HexRelic[] { worn, found }) {
            final JComponent card = HexRewardCards.card(null, relic.name, true);
            card.setToolTipText(relicEffects(relic, campaign.cycle).trim());
            clickable.add(card);
            final String caption = t(relic == worn ? "lblHexChestRelicWorn" : "lblHexChestRelicFound",
                    String.valueOf(content.relicSellPrice(relic.name)));
            pictures.add(HexRewardCards.captioned(caption, card, HexRewardCards.W, HexRewardCards.H));
        }
        final String message = t("lblHexChestRelicChoice", reward.playerName, found.name, worn.name)
                + "\n\n" + worn.name + ":\n" + relicEffects(worn, campaign.cycle).trim()
                + "\n\n" + found.name + ":\n" + relicEffects(found, campaign.cycle).trim();
        final int choice = HexRewardCards.choose(message, campaign.name, pictures, clickable, Collections.<String>emptyList());
        if (choice < 0) {
            return false;
        }
        final boolean takeNew = choice == 1;
        session.chooseChestRelic(takeNew);
        // what the reward window says next, before the host's state comes back
        reward.chestRelicWorn = null;
        reward.chestRelicSold = takeNew ? worn.name : found.name;
        reward.chestRelicGold = content.relicSellPrice(reward.chestRelicSold);
        return true;
    }

    /** Lets this person choose the reward of their own head. */
    public void promptReward() {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || rewardPromptOpen || campaign.pendingEncounter != null) {
            return;
        }
        final HexReward reward = campaign.pendingReward(session.getMyHead());
        if (reward == null) {
            return;
        }
        rewardPromptOpen = true;
        try {
            if (reward.chestRelicWorn != null && !promptChestRelic(campaign, reward)) {
                return; // closed: asked again later
            }
            final List<String> cards = reward.options();
            // a treasure found without a fight: nothing to choose, only to take
            final boolean onlyTake = reward.gold == 0 && cards.isEmpty();
            // the Gold token and the cards on offer side by side: a click takes one (-1 is the gold for chooseReward)
            final JPanel pictures = new JPanel(new MigLayout("insets 0, gap 12"));
            pictures.setOpaque(false);
            final List<JComponent> clickable = new ArrayList<>();
            final List<Integer> picks = new ArrayList<>();
            if (!onlyTake) {
                final JComponent gold = HexRewardCards.card(HexRewardCards.goldToken(), t("lblHexTakeGold", String.valueOf(reward.gold)), true);
                gold.setToolTipText(t("lblHexTakeGold", String.valueOf(reward.gold)));
                clickable.add(gold);
                picks.add(-1);
                pictures.add(gold, "w " + HexRewardCards.W + "!, h " + HexRewardCards.H + "!");
            }
            for (int i = 0; i < cards.size(); i++) {
                final JComponent card = HexRewardCards.card(HexContent.get().getCard(cards.get(i)), null, true);
                clickable.add(card);
                picks.add(i);
                pictures.add(card, "w " + HexRewardCards.W + "!, h " + HexRewardCards.H + "!");
            }
            String message = t(clickable.size() > 1 ? "lblHexRewardChooseOne" : "lblHexRewardFor", reward.playerName);
            final HexRelic trophy = reward.trophy == null ? null : HexContent.get().getRelic(reward.trophy);
            if (trophy != null) {
                // a beaten boss's trophy comes on top of the choice
                final HexPlayer mine = campaign.player(reward.playerName);
                final boolean worn = mine != null && mine.relics.contains(trophy.name);
                message += "\n\n" + t(worn ? "lblHexRewardTrophyWorn" : "lblHexRewardTrophy", trophy.name) + "\n"
                        + relicEffects(trophy, campaign.cycle).trim();
            }
            if (reward.chestCard != null || reward.chestRelic != null || reward.chestGold > 0) {
                // a dungeon's chest, opened already: its card stands first, apart from the choice
                message += "\n";
                if (reward.chestCard != null) {
                    message += "\n" + t("lblHexRewardChestCard", reward.chestCard, String.valueOf(HexContent.get().price(reward.chestCard)));
                }
                if (reward.chestGold > 0) {
                    message += "\n" + t("lblHexRewardChestGold", String.valueOf(reward.chestGold));
                }
                if (reward.chestRelic != null) {
                    final boolean swapped = reward.chestRelicSold != null && !reward.chestRelicSold.equals(reward.chestRelic);
                    message += "\n" + (swapped
                            ? t("lblHexRewardChestRelicSwapped", reward.chestRelic, reward.chestRelicSold, String.valueOf(reward.chestRelicGold))
                            : reward.chestRelicGold > 0
                            ? t("lblHexRewardChestRelicSold", reward.chestRelic, String.valueOf(reward.chestRelicGold))
                            : t("lblHexRewardChestRelic", reward.chestRelic));
                }
                final PaperCard treasure = reward.chestCard == null ? null : HexContent.get().getCard(reward.chestCard);
                if (treasure != null) {
                    // the chest's card is already the head's: only to look at
                    pictures.add(HexRewardCards.captioned(t("lblHexChest"), HexRewardCards.card(treasure, null, false),
                            HexRewardCards.W, HexRewardCards.H), 0);
                    if (!clickable.isEmpty()) {
                        pictures.add(new JLabel(), 1); // a gap between the chest and the choice
                    }
                }
            }
            final boolean shown = pictures.getComponentCount() > 0;
            final List<String> buttons = onlyTake ? Arrays.asList(t("lblHexRewardTakeTreasure")) : Collections.<String>emptyList();
            final int choice = HexRewardCards.choose(message, campaign.name, shown ? pictures : null, clickable, buttons);
            if (onlyTake ? choice == 0 : choice >= 0 && choice < picks.size()) {
                session.chooseReward(onlyTake ? -1 : picks.get(choice));
            }
        } finally {
            rewardPromptOpen = false;
        }
        promptPerkIfWaiting();
    }

    // ------------------------------------------------------------------ perks

    private void promptEventIfWaiting() {
        final HexCampaign campaign = getCampaign();
        final HexPlayer mine = campaign == null ? null : campaign.player(session.getMyHead());
        if (mine == null || mine.eventOffer == null || eventPromptOpen || perkPromptOpen || rewardPromptOpen || !isShowing()
                || session.isFightRunning() || campaign.pendingEncounter != null) {
            return;
        }
        promptEvent();
    }

    /** A ruined shrine or a caravan: this person picks a card of their own to give, or leaves it. */
    private void promptEvent() {
        final HexCampaign campaign = getCampaign();
        final HexPlayer mine = campaign == null ? null : campaign.player(session.getMyHead());
        final forge.gamemodes.hexcrawl.HexEvent event = mine == null ? null : forge.gamemodes.hexcrawl.HexEvent.of(mine.eventOffer);
        if (event == null || eventPromptOpen) {
            return;
        }
        eventPromptOpen = true;
        try {
            // the cards that may be given: no basic lands; deck cards only while the deck is above its minimum
            final List<String> names = new ArrayList<>();
            final List<Boolean> fromSideboard = new ArrayList<>();
            final List<String> lines = new ArrayList<>();
            final boolean deckOk = mine.deck.size() > HexCampaign.MIN_DECK_SIZE;
            for (final boolean side : new boolean[] { false, true }) {
                if (!side && !deckOk) {
                    continue;
                }
                for (final String card : new java.util.TreeSet<>(side ? mine.sideboard : mine.deck)) {
                    if (!HexContent.get().isBasicLand(card)) {
                        names.add(card);
                        fromSideboard.add(side);
                        lines.add((side ? t("lblHexSideboardMark") + " " : "") + card);
                    }
                }
            }
            final javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
            lines.forEach(model::addElement);
            final forge.toolbox.FList<String> list = new forge.toolbox.FList<>(model);
            list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
            final CardPicturePanel pic = new CardPicturePanel();
            pic.setOpaque(false);
            list.addListSelectionListener(e -> {
                final int i = list.getSelectedIndex();
                final PaperCard card = i >= 0 && i < names.size() ? HexContent.get().getCard(names.get(i)) : null;
                if (card != null) {
                    pic.setItem(card);
                }
            });
            if (!names.isEmpty()) {
                list.setSelectedIndex(0);
            }
            final JPanel panel = new JPanel(new net.miginfocom.swing.MigLayout("insets 0, gap 12", "[300!, fill][200!, fill]", "[top]"));
            panel.setOpaque(false);
            panel.add(new forge.toolbox.FScrollPane(list, true), "h 280!");
            panel.add(pic, "h 280!");
            final List<String> options = new ArrayList<>();
            options.add(t("lblHexEventGive_" + event.name()));
            options.add(t("lblHexEventLeave"));
            options.add(t("lblHexLater"));
            final int choice = FOptionPane.showOptionDialog(event.getLabel() + ": " + event.getDescription(), campaign.name,
                    FOptionPane.QUESTION_ICON, panel, options, 0);
            final int i = list.getSelectedIndex();
            if (choice == 0 && i >= 0 && i < names.size()) {
                session.chooseEvent(mine.name, names.get(i), fromSideboard.get(i));
            } else if (choice == 1) {
                session.skipEvent(mine.name);
            }
        } finally {
            eventPromptOpen = false;
        }
    }

    private void promptPerkIfWaiting() {
        final HexCampaign campaign = getCampaign();
        final HexPlayer mine = campaign == null ? null : campaign.player(session.getMyHead());
        if (mine == null || perkPromptOpen || rewardPromptOpen || !isShowing() || session.isFightRunning()
                || mine.perkOffer.isEmpty() || campaign.pendingReward(mine.name) != null) {
            return;
        }
        promptPerk();
    }

    /** Lets this person pick a perk for their head. */
    private void promptPerk() {
        final HexCampaign campaign = getCampaign();
        final HexPlayer mine = campaign == null ? null : campaign.player(session.getMyHead());
        if (mine == null || perkPromptOpen || mine.perkOffer.isEmpty()) {
            return;
        }
        perkPromptOpen = true;
        try {
            final List<HexPerk> offer = new ArrayList<>();
            final StringBuilder message = new StringBuilder(t("lblHexPerkPrompt", String.valueOf(HexCampaign.PERK_EVERY_WINS)));
            for (final String name : mine.perkOffer) {
                final HexPerk perk = HexPerk.of(name);
                if (perk != null) {
                    offer.add(perk);
                    message.append("\n\n").append(perk.getLabel()).append(": ").append(perk.getDescription());
                    if (perk.count(mine) > 0) {
                        message.append(' ').append(t("lblHexPerkHave", String.valueOf(perk.count(mine))));
                    }
                }
            }
            final List<String> options = new ArrayList<>();
            for (final HexPerk perk : offer) {
                options.add(perk.getLabel());
            }
            options.add(t("lblHexLater"));
            final int choice = FOptionPane.showOptionDialog(message.toString(), campaign.name, FOptionPane.QUESTION_ICON, options, 0);
            if (choice >= 0 && choice < offer.size()) {
                session.choosePerk(offer.get(choice).name());
            }
        } finally {
            perkPromptOpen = false;
        }
    }

    // ------------------------------------------------------------------ towns and decks

    private void showDeck() {
        if (session == null || getCampaign() == null) {
            return;
        }
        if (getCampaign().pendingEncounter != null) {
            session.deckSeen(); // shown in the ready check
        }
        new HexDeckDialog(session).show(refresher -> deckRefresher = refresher);
        refresh();
    }

    private static boolean alwaysOkPartner() {
        return FModel.getPreferences().getPrefBoolean(FPref.YIELD_AUTO_OK_PARTNER);
    }

    private void showAutoOk() {
        btnAutoOk.setText(t(alwaysOkPartner() ? "lblHexAutoOkOn" : "lblHexAutoOkOff"));
    }

    /** The chronicle of the campaign: what every head did, and the enemies beaten. */
    private void showStats(final boolean victory) {
        if (getCampaign() == null) {
            return;
        }
        new HexStatsDialog(getCampaign(), victory).show();
    }

    /** After the victory: the host starts the next cycle, with stronger enemies on a new map. */
    private void newGamePlus() {
        final HexCampaign campaign = getCampaign();
        if (session == null || campaign == null || !campaign.won) {
            return;
        }
        if (campaign.isBusy()) {
            FOptionPane.showMessageDialog(t("lblHexNewGamePlusBusy"), campaign.name);
            return;
        }
        if (FOptionPane.showConfirmDialog(t("lblHexNewGamePlusConfirm", String.valueOf(campaign.cycle),
                String.valueOf(HexCampaign.LIFE_PER_CYCLE), String.valueOf(HexCampaign.ADVANTAGES_PER_CYCLE)), campaign.name)) {
            session.newGamePlus();
        }
    }

    public void openShop() {
        if (session == null || getCampaign() == null || !getCampaign().inTown()) {
            return;
        }
        // the others see who is still in the shops, and the window shows who wants to move on
        session.setShopping(true);
        try {
            new HexTownDialog(session).describingVotes(key -> describeVote(getCampaign(), key))
                    .show(refresher -> shopRefresher = refresher);
        } finally {
            session.setShopping(false);
        }
        refresh();
    }

    /** What makes an enemy harder than its life total says: its followers and what it starts with. */
    static String enemyExtras(final HexCampaign campaign, final HexEncounter enc) {
        return enemyExtras(campaign, enc, true);
    }

    /** The same, without the followers where every enemy has the same (the tasks on a notice board). */
    static String enemyExtras(final HexCampaign campaign, final HexEncounter enc, final boolean withFollowers) {
        final Localizer loc = Localizer.getInstance();
        final StringBuilder sb = new StringBuilder();
        if (enc.archenemy) { // alone, with schemes: the one thing to know about it
            sb.append(' ').append(loc.getMessage("lblHexEnemyArchenemy", String.valueOf(HexContent.SCHEMES_PER_ARCHENEMY)));
        }
        final int followers = campaign.enemyHeads(enc) - 1;
        if (withFollowers && followers > 0) {
            sb.append(' ').append(loc.getMessage(enc.headsAlike() ? "lblHexEnemyCopies" : "lblHexEnemyFollowers", String.valueOf(followers)));
        }
        if (!enc.startCards.isEmpty()) {
            sb.append(' ').append(loc.getMessage("lblHexEnemyStarts", HexContent.get().startCardNames(enc)));
        }
        final List<String> cycleAdvantages = campaign.cycleAdvantages();
        if (!cycleAdvantages.isEmpty()) {
            sb.append(' ').append(campaign.cycle > 1
                    ? loc.getMessage("lblHexEnemyCycle", String.valueOf(campaign.cycle - 1), String.join(", ", cycleAdvantages))
                    : loc.getMessage("lblHexEnemyLevel", loc.getMessage(campaign.difficulty().key()), String.join(", ", cycleAdvantages)));
        }
        final forge.gamemodes.hexcrawl.HexBiomeRule biome = forge.gamemodes.hexcrawl.HexBiomeRule.forFight(campaign, enc);
        if (biome != null) {
            sb.append(' ').append(loc.getMessage(biome.getLabelKey()));
        }
        return sb.toString();
    }

    /** What a relic does in this cycle of the campaign, one effect per line. */
    static String relicEffects(final HexRelic relic, final int cycle) {
        final Localizer loc = Localizer.getInstance();
        final HexContent content = HexContent.get();
        final StringBuilder sb = new StringBuilder();
        if (relic.life != 0) {
            sb.append(loc.getMessage("lblHexRelicLife", signed(relic.life))).append('\n');
        }
        if (relic.hand != 0) {
            sb.append(loc.getMessage("lblHexRelicHand", signed(relic.hand))).append('\n');
        }
        if (!relic.battlefield.isEmpty()) {
            sb.append(loc.getMessage("lblHexRelicStart", cardNames(content, relic.battlefield))).append('\n');
        }
        if (!relic.randomCards.isEmpty()) {
            sb.append(loc.getMessage("lblHexRelicRandomStart", String.valueOf(relic.randomPerCycle * cycle),
                    cardNames(content, relic.randomCards), String.valueOf(relic.randomPerCycle))).append('\n');
        }
        if (relic.enemyLife != 0) {
            sb.append(loc.getMessage("lblHexRelicEnemyLife", signed(relic.enemyLife))).append('\n');
        }
        if (!relic.enemyBattlefield.isEmpty()) {
            sb.append(loc.getMessage("lblHexRelicEnemyStart", cardNames(content, relic.enemyBattlefield))).append('\n');
        }
        return sb.toString();
    }

    private static String signed(final int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    private static String cardNames(final HexContent content, final List<String> ids) {
        final List<String> names = new ArrayList<>();
        for (final String id : ids) {
            final forge.item.IPaperCard card = content.findStartCard(id);
            names.add(card == null ? id : card.getName());
        }
        return String.join(", ", names);
    }

    // ------------------------------------------------------------------ campaigns and guests

    private void invite() {
        if (session == null || session.isGuest()) {
            return;
        }
        if (HostingServer.isHosting()) {
            new HexInviteDialog(session).show();
            return;
        }
        startHosting(true);
    }

    /** Starts the server for friends; the invitation window opens once it runs, if asked for. */
    private void startHosting(final boolean showInvitation) {
        HexInviteDialog.lookUpExternalAddress();
        // the router is asked to let friends in without a question to the host
        FServerManager.setUpnpWithoutAsking(true);
        forge.screens.home.online.CSubmenuOnlineLobby.SINGLETON_INSTANCE.hostGame(false);
        hostTimer.start();
        // the invitation as soon as the server runs
        final int[] waited = {0};
        final Timer waitForServer = new Timer(500, null);
        waitForServer.addActionListener(e -> {
            if (HostingServer.isHosting() || ++waited[0] > 40) {
                waitForServer.stop();
                refresh();
                if (showInvitation && HostingServer.isHosting() && session != null) {
                    new HexInviteDialog(session).show();
                }
            }
        });
        waitForServer.start();
    }

    /** One line about the connection: for the host who is there, for a guest whether the host is reachable. */
    private void showConnection(final boolean guest) {
        String text = null;
        java.awt.Color color = new java.awt.Color(0x9BD18B);
        if (guest) {
            final String state = HexNet.connectionState();
            final HexCampaign c = getCampaign();
            if ("RECONNECTING".equals(state)) {
                text = t("lblHexConnReconnecting");
                color = new java.awt.Color(0xF0C05A);
            } else if (state == null || "FAILED".equals(state) || "SEAT_LOST".equals(state)) {
                text = t("lblHexConnLost");
                color = new java.awt.Color(0xF07A6A);
            } else if (c == null || session == null) {
                text = t("lblHexConnWaiting");
            } else if (!session.sameBuildAsHost()) {
                text = t("lblHexConnOtherBuild", session.getHostHead());
                color = new java.awt.Color(0xF0C05A);
                warnOtherBuild(session.getHostHead());
            } else {
                text = t("lblHexConnGuest", session.getHostHead(), session.getMyHead());
            }
        } else if (HostingServer.isHosting() && session != null) {
            text = t("lblHexConnHost", String.valueOf(Math.max(0, session.getPresent().size() - 1)));
            for (final String other : session.guestsWithOtherBuild()) {
                color = new java.awt.Color(0xF0C05A);
                warnOtherBuild(other);
            }
        }
        lblConnection.setVisible(text != null);
        if (text != null) {
            lblConnection.setText(text);
            lblConnection.setForeground(color);
        }
    }

    /** Whom the other-version warning was shown for already (once per program run and person). */
    private final java.util.Set<String> warnedOtherBuild = new java.util.HashSet<>();

    /** A different build breaks every fight: say so once, in a way nobody can miss. */
    private void warnOtherBuild(final String who) {
        if (who == null || !warnedOtherBuild.add(who)) {
            return;
        }
        SwingUtilities.invokeLater(() -> FOptionPane.showMessageDialog(t("lblHexOtherBuildWarning", who), t("lblHexOtherBuild"), FOptionPane.WARNING_ICON));
    }

    /** Guest: leaves the host's campaign. */
    private void leaveHost() {
        if (!FOptionPane.showConfirmDialog(t("lblHexLeaveConfirm"), t("lblHexLeave"))) {
            return;
        }
        forge.screens.home.online.VSubmenuOnlineLobby.SINGLETON_INSTANCE.closeConn(null);
        HexNet.leave();
        session = null;
        refresh();
    }

    /** The campaign menu, painted like the side panel and opened over the map; what a guest cannot do is left out. */
    /**
     * Host: the map says a fight runs, but it hangs or has ended without telling the campaign. After asking,
     * the fight is broken off for everybody without a result: the enemy stays and can be fought again.
     */
    private void resetFight() {
        final HexSession s = session;
        if (s == null || s.isGuest() || !s.isFightRunning()) {
            return;
        }
        if (!FOptionPane.showConfirmDialog(t("lblHexResetFightAsk"), t("lblHexResetFight"), t("lblHexResetFightYes"), t("lblHexBack"))) {
            return;
        }
        HexFightHostMenu.tellEverybody(t("lblHexFightAbortedChat"));
        forge.util.ThreadUtil.invokeInGameThread(() -> {
            s.resetFight();
            SwingUtilities.invokeLater(() -> {
                fightOnScreen = HexFight.current() != null;
                refresh();
            });
        });
    }

    private void openMenu(final JComponent anchor) {
        if (menu.isOpen()) {
            menu.close();
            return;
        }
        final boolean guest = isGuest() || HexNet.isGuest();
        final HexCampaign campaign = getCampaign();
        final boolean has = campaign != null;
        menu = new HexMenuOverlay(this::palette);
        menu.heading(t("lblHexCampaignMenu"));
        if (guest) { // a guest plays the host's campaign: only leaving it, the rest is the host's
            menu.item(t("lblHexLeave") + "...", null, true, this::leaveHost);
        } else {
            menu.item(t("lblHexNewCampaign"), null, true, this::newCampaign);
            menu.item(t("lblHexLoadCampaign") + "...", null, true, this::loadCampaign);
            // not while hosting: the guests would be left without a campaign
            if (has && !HostingServer.isHosting()) {
                menu.item(t("lblHexCloseCampaign"), null, true, this::closeCampaign);
            }
            menu.item(t("lblHexRename") + "...", null, has, this::renameCampaign);
            if (has && session != null && session.isFightRunning()) {
                menu.item(t("lblHexResetFight") + "...", null, true, this::resetFight);
            }
            // the last campaigns played, one click each, with the day they were played
            final List<String> recent = HexCampaignIO.recent(has ? campaign.name : null, 5);
            if (!recent.isEmpty()) {
                menu.heading(t("lblHexRecent"));
                final java.time.format.DateTimeFormatter day = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT);
                for (final String name : recent) {
                    final String when = java.time.Instant.ofEpochMilli(HexCampaignIO.saveFile(name).lastModified())
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(day);
                    menu.item(name, when, true, () -> {
                        final HexCampaign loaded = HexCampaignIO.load(name);
                        if (loaded != null) {
                            openCampaign(loaded);
                        }
                    });
                }
            }
        }
        menu.heading(t("lblHexMenuSaves"));
        menu.item(t("lblHexExport") + "...", null, has, this::exportCampaign);
        if (!guest) {
            menu.item(t("lblHexImport") + "...", null, true, this::importCampaign);
            menu.item(t("lblHexDelete") + "...", null, true, this::deleteCampaign);
        }
        menu.item(t("lblHexOpenFolder"), null,
                java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN),
                this::openSaveFolder);
        final boolean canJoin = !guest && !HostingServer.isHosting();
        if (has || canJoin) {
            menu.heading(t("lblHexSetupFriends"));
            if (has) { // everybody: who is in the party and who is connected
                menu.item(t("lblHexPartyWindow") + "...", null, true,
                        () -> new HexPartyDialog(session, () -> lblConnection.isVisible() ? lblConnection.getText() : null).show());
            }
            if (canJoin) {
                menu.item(t("lblHexJoinButton") + "...", null, true, () -> new HexJoinDialog().show());
            }
        }
        menu.heading(t("lblHexMenuSettings"));
        // the map's legend with its keys, as H or the "?" on the map open it
        menu.item(t("lblHexMenuLegend"), "H", has, () -> {
            if (getCampaign() != null && getCampaign().inLabyrinth()) {
                labyrinthPanel.toggleLegend();
            } else {
                mapPanel.toggleLegend();
            }
        });
        // Forge's own music switch, the same as in the settings
        menu.toggle(t("lblHexMusic"), t("lblHexOn"), t("lblHexOff"), () -> FModel.getPreferences().getPrefBoolean(FPref.UI_ENABLE_MUSIC), () -> {
            FModel.getPreferences().setPref(FPref.UI_ENABLE_MUSIC, String.valueOf(!FModel.getPreferences().getPrefBoolean(FPref.UI_ENABLE_MUSIC)));
            FModel.getPreferences().save();
            forge.sound.SoundSystem.instance.changeBackgroundTrack();
        });
        menu.show(anchor, sidePanel);
    }

    /** Back to the empty screen; the campaign is saved as it is and can be loaded again. */
    private void closeCampaign() {
        if (session == null || isGuest() || HostingServer.isHosting()) {
            return;
        }
        if (settingUp) {
            setupPage.close();
            settingUp = false;
        }
        setSession(null);
    }

    /** Deletes a saved campaign other than the open one; it goes to the recycle bin where there is one. */
    private void deleteCampaign() {
        final HexCampaign open = getCampaign();
        final List<String> choices = HexCampaignIO.recent(open == null ? null : open.name, Integer.MAX_VALUE);
        if (choices.isEmpty()) {
            FOptionPane.showMessageDialog(t(open == null ? "lblHexNoSaves" : "lblHexNoOtherSaves"), t("lblHexDelete"));
            return;
        }
        final String choice = FOptionPane.showInputDialog(t("lblHexDeleteChoose"), t("lblHexDelete"), null, choices.get(0), choices);
        if (choice == null) {
            return;
        }
        final java.io.File file = HexCampaignIO.saveFile(choice);
        final boolean trash = java.awt.Desktop.isDesktopSupported()
                && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH);
        if (!FOptionPane.showConfirmDialog(t(trash ? "lblHexDeleteConfirm" : "lblHexDeleteConfirmForGood", choice), t("lblHexDelete"))) {
            return;
        }
        final boolean done = trash ? java.awt.Desktop.getDesktop().moveToTrash(file) : file.delete();
        if (!done) {
            FOptionPane.showErrorDialog(t("lblHexDeleteFailed", choice), t("lblHexDelete"));
        }
    }

    /** Shows the folder of the saves in the file manager (for backups, or a save to send by hand). */
    private void openSaveFolder() {
        try {
            java.awt.Desktop.getDesktop().open(HexCampaignIO.dir());
        } catch (final java.io.IOException | RuntimeException e) {
            FOptionPane.showErrorDialog(HexCampaignIO.dir().getPath(), t("lblHexOpenFolder"));
        }
    }

    /** Writes the campaign to a file the person picks (a guest exports what the host shared). */
    private void exportCampaign() {
        final HexCampaign campaign = getCampaign();
        if (campaign == null) {
            return;
        }
        final javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle(t("lblHexExport"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(t("lblHexSaveFiles"), "json"));
        chooser.setSelectedFile(new java.io.File(campaign.name.replaceAll("[^A-Za-z0-9 _.-]", "_") + ".json"));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            target = new java.io.File(target.getParentFile(), target.getName() + ".json");
        }
        if (target.exists() && !FOptionPane.showConfirmDialog(t("lblHexExportOverwrite", target.getName()), t("lblHexExport"))) {
            return;
        }
        try {
            HexCampaignIO.exportTo(campaign, target);
            FOptionPane.showMessageDialog(t("lblHexExported", campaign.name, target.getPath()), t("lblHexExport"));
        } catch (final RuntimeException e) {
            FOptionPane.showErrorDialog(t("lblHexExportFailed", target.getPath()), t("lblHexExport"));
        }
    }

    /** Reads a campaign from a file the person picks, saves it under a free name and opens it. */
    private void importCampaign() {
        final javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle(t("lblHexImport"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(t("lblHexSaveFiles"), "json"));
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        final java.io.File source = chooser.getSelectedFile();
        final HexCampaign campaign = HexCampaignIO.importFrom(source);
        if (campaign == null) {
            FOptionPane.showErrorDialog(t("lblHexImportFailed", source.getName()), t("lblHexImport"));
            return;
        }
        final String wanted = campaign.name;
        final String name = HexCampaignIO.saveImported(campaign);
        if (!name.equals(wanted)) {
            FOptionPane.showMessageDialog(t("lblHexImportedAs", wanted, name), t("lblHexImport"));
        }
        openCampaign(campaign);
    }

    /** The host gives the campaign a new name (its save is renamed too). */
    private void renameCampaign() {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || isGuest()) {
            return;
        }
        final String name = FOptionPane.showInputDialog(t("lblHexCampaignName"), t("lblHexRename"), null, campaign.name);
        if (name == null || name.trim().isEmpty() || name.trim().equals(campaign.name)) {
            return;
        }
        if (!HexCampaignIO.canRename(campaign, name)) {
            FOptionPane.showErrorDialog(t("lblHexRenameTaken", name.trim()), t("lblHexRename"));
            return;
        }
        session.rename(name.trim());
        refresh();
    }

    /** Shows the page of a new campaign in place of the map. */
    // ------------------------------------------------------------------ the home menu's Hexkampagne entries

    /** Not while playing in a friend's campaign: that has to be left first. Says so, and returns true then. */
    private boolean refusedAsGuest() {
        if (isGuest() || HexNet.isGuest()) {
            FOptionPane.showMessageDialog(t("lblHexSidebarGuest"), t("lblHexCampaign"));
            return true;
        }
        return false;
    }

    /** "Neue Kampagne": the page of a new campaign, as in the campaign menu. */
    public void startNewCampaign() {
        if (!refusedAsGuest()) {
            newCampaign();
        }
    }

    /** "Lade Kampagne": the choice of a saved campaign, as in the campaign menu. */
    public void chooseCampaignToLoad() {
        if (!refusedAsGuest()) {
            loadCampaign();
        }
    }

    /** "Verbinden": the window to join a friend's campaign, as in the campaign menu. */
    public void joinFriend() {
        if (refusedAsGuest()) {
            return;
        }
        if (HostingServer.isHosting()) {
            FOptionPane.showMessageDialog(t("lblHexSidebarHosting"), t("lblHexCampaign"));
            return;
        }
        new HexJoinDialog().show();
    }

    private void newCampaign() {
        if (isGuest() || HexNet.isGuest()) {
            return;
        }
        setupPage.open(HexCampaignIO.freeName(t("lblHexCampaign") + " " + (HexCampaignIO.list().size() + 1)));
        settingUp = true;
        refresh();
    }

    /** Back to the map from the page of a new campaign. */
    private void endSetup() {
        setupPage.close();
        settingUp = false;
        refresh();
    }

    /** The page of a new campaign is filled in: the campaign starts, and the server for friends if they were asked. */
    private void createCampaign(final HexNewCampaignPage.Settings settings) {
        settingUp = false;
        String me = FModel.getPreferences().getPref(FPref.PLAYER_NAME);
        if (me == null || me.trim().isEmpty()) {
            me = t("lblHexHero");
        }
        final List<HexPlayer> players = new ArrayList<>();
        players.add(new HexPlayer(me, false));
        for (int i = 1; i <= settings.companions(); i++) {
            players.add(new HexPlayer(t("lblHexCompanion") + " " + i, true));
        }
        final HexCampaign campaign = HexCampaign.create(settings.name(), new Random().nextLong(), HexMap.radiusFor(players.size()), players,
                settings.goldMultiplier(), settings.difficulty());
        campaign.teamPermanents = settings.teamPermanents();
        HexCampaignIO.save(campaign);
        setSession(new HexSession(campaign));
        if (HostingServer.isHosting()) {
            hostTimer.start();
        } else if (settings.host()) {
            startHosting(false); // the code was on the page already
        }
        broadcast();
        refresh();
    }

    private void loadCampaign() {
        final List<String> choices = HexCampaignIO.list();
        if (choices.isEmpty()) {
            FOptionPane.showMessageDialog(t("lblHexNoSaves"), t("lblHexLoadCampaign"));
            return;
        }
        final String choice = FOptionPane.showInputDialog(t("lblHexLoadCampaign"), t("lblHexLoadCampaign"), null, choices.get(0), choices);
        if (choice != null) {
            final HexCampaign campaign = HexCampaignIO.load(choice);
            if (campaign != null) {
                openCampaign(campaign);
            }
        }
    }

    /** Plays this campaign from now on (as the host of any table). */
    private void openCampaign(final HexCampaign campaign) {
        if (settingUp) {
            setupPage.close();
            settingUp = false;
        }
        setSession(new HexSession(campaign));
        if (HostingServer.isHosting()) {
            hostTimer.start();
        }
        broadcast();
    }
}
