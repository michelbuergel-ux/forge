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
    /** Width of the side panel, and of the text in its scrolling middle part. */
    private static final int SIDE_W = 350;
    private static final int MIDDLE_W = SIDE_W - 32 - 18;
    private final JLabel lblTitle = label(" ", decorative(22f), TEXT_BRIGHT);
    private final JLabel lblPlace = label(" ", tracked(display(11.5f), 0.12f), MUTED);
    private final GoldButton btnFight = new GoldButton("", GoldButton.Kind.GOLD, 15f);
    private final GoldButton btnNewGamePlus = new GoldButton("", GoldButton.Kind.GOLD, 13f);
    private final GoldButton btnRewards = new GoldButton("", GoldButton.Kind.GOLD, 13f);
    private final GoldButton btnPerk = new GoldButton("", GoldButton.Kind.GOLD, 13f);
    private final GoldButton btnEvent = new GoldButton("", GoldButton.Kind.GOLD, 13f);
    private final GoldButton btnShop = new GoldButton("", GoldButton.Kind.GOLD, 14f);
    private final GoldButton btnDecks = new GoldButton("", GoldButton.Kind.OUTLINE, 13f);
    private final GoldButton btnStats = new GoldButton("", GoldButton.Kind.OUTLINE, 13f);
    /** "Always OK my partner" in the fights, switched here as well (a saved setting of this computer). */
    private final GoldButton btnAutoOk = new GoldButton("", GoldButton.Kind.OUTLINE, 11f);
    private final GoldButton btnInvite = new GoldButton("", GoldButton.Kind.OUTLINE, 11f);
    private final GoldButton btnJoin = new GoldButton("", GoldButton.Kind.OUTLINE, 11f);
    private final GoldButton btnLeave = new GoldButton("", GoldButton.Kind.OUTLINE, 11f);
    /** Who is connected to whom: shown to the host and to guests. */
    private final JLabel lblConnection = label(" ", body(java.awt.Font.BOLD, 12f), MUTED);
    private final GoldButton btnNew = new GoldButton("", GoldButton.Kind.OUTLINE, 11f);
    private final GoldButton btnLoad = new GoldButton("", GoldButton.Kind.OUTLINE, 11f);

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
    /** The last fight summary this screen has shown (-1: none yet, take the campaign's as seen). */
    private int shownSummarySerial = -1;
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
        add(mapPanel, "grow");

        final Plate side = new Plate(this::palette, new MigLayout("insets 18 16 14 16, gap 0, wrap 1, fillx, hidemode 3",
                "[grow, fill]"));
        btnRewards.setText(t("lblHexRewards"));
        btnPerk.setText(t("lblHexChoosePerk"));
        btnEvent.setText(t("lblHexEventChooseButton"));
        btnShop.setText(t("lblHexShop"));
        btnDecks.setText(t("lblHexDecks"));
        btnStats.setText(t("lblHexChronicle"));
        btnInvite.setText(t("lblHexInvite"));
        btnNew.setText(t("lblHexNewCampaign"));
        btnLoad.setText(t("lblHexLoadCampaign"));
        side.add(lblTitle);
        side.add(lblPlace, "gaptop 2");
        side.add(lblConnection, "gaptop 6");

        // what there is to do right now, the most pressing first
        final JPanel actions = transparent(new MigLayout("insets 0, gap 0 8, wrap 1, fillx, hidemode 3", "[grow, fill]"));
        actions.add(threatBox);
        actions.add(btnFight, "h 46!");
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
        final JPanel footer = transparent(new MigLayout("insets 0, gap 6 6, wrap 2, hidemode 3", "[grow, fill][grow, fill]"));
        footer.add(new Heading(t("lblHexFilesTitle")), "span 2");
        footer.add(btnInvite, "span 2, h 32!");
        footer.add(btnJoin, "span 2, h 32!");
        footer.add(btnLeave, "span 2, h 32!");
        footer.add(btnNew, "h 32!");
        footer.add(btnLoad, "h 32!");
        side.add(footer, "gaptop 10");
        add(side, "grow");

        mapPanel.setOnTileClicked(this::onTileClicked);
        btnFight.addActionListener(e -> { if (session != null) { session.readyToFight(); } });
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
        btnJoin.setText(t("lblHexJoinButton"));
        btnLeave.setText(t("lblHexLeave"));
        btnJoin.addActionListener(e -> new HexJoinDialog().show());
        btnLeave.addActionListener(e -> leaveHost());
        btnNew.addActionListener(e -> newCampaign());
        btnLoad.addActionListener(e -> loadCampaign());

        // a reward waiting for us is offered as soon as this screen is visible again after a fight
        addHierarchyListener(e -> {
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
        shownSummarySerial = session == null || session.getCampaign() == null || session.getCampaign().lastFight == null
                ? -1 : session.getCampaign().lastFight.serial;
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
            panel.add(col);
        }
        final String title = t("lblHexSummary_" + s.result, s.enemyName, String.valueOf(s.turns));
        FOptionPane.showOptionDialog(null, title, null, panel, Arrays.asList(t("lblHexClose")), 0);
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
            FOptionPane.showMessageDialog(campaign.lastEvent, t("lblHexEventTitle"));
        }
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
                    HexBattle.startHosted(s, FServerManager.getInstance().getLobby(), s::fightFinished);
                } else {
                    final HostedMatch match = HexBattle.start(s.getCampaign(), s::fightFinished);
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
        if (!changed && ++ticks % 4 == 0) {
            broadcast(); // also reaches people who reconnected
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
        final Palette palette = palette();
        lblPlace.setForeground(palette.light());
        final boolean has = campaign != null;
        final boolean guest = isGuest() || HexNet.isGuest();
        btnNew.setVisible(!guest);
        btnLoad.setVisible(!guest);
        btnInvite.setVisible(has && !guest);
        btnInvite.setText(t(HostingServer.isHosting() ? "lblHexInviteShow" : "lblHexInvite"));
        btnJoin.setVisible(!guest && !HostingServer.isHosting());
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
        if (session.isFightRunning()) {
            btnFight.setText(t("lblHexFightRunning"));
            btnFight.setEnabled(false);
        } else if (ready.contains(me)) {
            btnFight.setText(t("lblHexFightWaiting", String.valueOf(ready.size()), String.valueOf(humans)));
            btnFight.setEnabled(false);
        } else {
            btnFight.setText(t("lblHexFight"));
            btnFight.setEnabled(true);
        }
        btnRewards.setVisible(campaign.pendingEncounter == null && campaign.pendingReward(me) != null);
        final HexPlayer mine = campaign.player(me);
        btnPerk.setVisible(mine != null && !mine.perkOffer.isEmpty() && campaign.pendingReward(me) == null);
        btnEvent.setVisible(mine != null && mine.eventOffer != null);
        btnShop.setVisible(campaign.inTown());

        lblTitle.setText(campaign.cycle > 1 ? campaign.name + "  –  " + t("lblHexCycle", String.valueOf(campaign.cycle - 1)) : campaign.name);
        lblTitle.setToolTipText(lblTitle.getText());
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
            if (p.ai) {
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
        section(hdBoosts, boostsBox, campaign.fightBoosts());

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

    private void onTileClicked(final HexTile target) {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || session.isFightRunning() || target == campaign.tile()) {
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
        showSummaryIfNew(); // first what happened in the fight, then the reward
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
            FOptionPane.showMessageDialog(t("lblHexVictoryGuest", HexContent.get().getBossName(HexColor.COLORLESS)), campaign.name);
            return;
        }
        final int choice = FOptionPane.showOptionDialog(
                t("lblHexVictoryNewGamePlus", HexContent.get().getBossName(HexColor.COLORLESS), String.valueOf(campaign.cycle),
                        String.valueOf(HexCampaign.LIFE_PER_CYCLE), String.valueOf(HexCampaign.ADVANTAGES_PER_CYCLE)),
                campaign.name, FOptionPane.INFORMATION_ICON,
                Arrays.asList(t("lblHexNewGamePlus", String.valueOf(campaign.cycle)), t("lblHexLater")), 0);
        if (choice == 0) {
            session.newGamePlus();
        }
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
            final List<String> options = new ArrayList<>();
            options.add(t("lblHexTakeGold", String.valueOf(reward.gold)));
            final List<String> cards = reward.options();
            // the cards on offer side by side: take one of them, or the gold
            final JPanel pictures = new JPanel(new MigLayout("insets 0, gap 12"));
            pictures.setOpaque(false);
            for (final String name : cards) {
                options.add(t("lblHexTakeCard", name));
                final PaperCard card = HexContent.get().getCard(name);
                final CardPicturePanel picture = new CardPicturePanel();
                picture.setOpaque(false);
                if (card != null) {
                    picture.setItem(card);
                }
                pictures.add(picture, "w 230!, h 322!");
            }
            final String message = t(cards.size() > 1 ? "lblHexRewardChooseOne" : "lblHexRewardFor", reward.playerName);
            final int choice = FOptionPane.showOptionDialog(message, campaign.name, cards.isEmpty() ? FOptionPane.QUESTION_ICON : null,
                    cards.isEmpty() ? null : pictures, options, 0);
            if (choice >= 0) {
                session.chooseReward(choice - 1); // the first button is the gold
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
        new HexTownDialog(session).show(refresher -> shopRefresher = refresher);
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
            sb.append(' ').append(loc.getMessage("lblHexEnemyCycle", String.valueOf(campaign.cycle - 1), String.join(", ", cycleAdvantages)));
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
                if (HostingServer.isHosting()) {
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
            } else {
                text = t("lblHexConnGuest", session.getHostHead(), session.getMyHead());
            }
        } else if (HostingServer.isHosting() && session != null) {
            text = t("lblHexConnHost", String.valueOf(Math.max(0, session.getPresent().size() - 1)));
        }
        lblConnection.setVisible(text != null);
        if (text != null) {
            lblConnection.setText(text);
            lblConnection.setForeground(color);
        }
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

    private void newCampaign() {
        final String defaultName = t("lblHexCampaign") + " " + (HexCampaignIO.list().size() + 1);
        final String name = FOptionPane.showInputDialog(t("lblHexCampaignName"), t("lblHexNewCampaign"), null, defaultName);
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        final int companions = FOptionPane.showOptionDialog(t("lblHexHowManyCompanions"), t("lblHexNewCampaign"),
                FOptionPane.QUESTION_ICON, Arrays.asList("0", "1", "2", "3"), 0);
        if (companions < 0) {
            return;
        }
        final List<String> goldOptions = new ArrayList<>();
        for (final int m : HexCampaign.GOLD_MULTIPLIERS) {
            goldOptions.add(m == 1 ? t("lblHexGoldNormal") : m + "x");
        }
        final int goldChoice = FOptionPane.showOptionDialog(t("lblHexGoldMultiplierQuestion"), t("lblHexNewCampaign"),
                FOptionPane.QUESTION_ICON, goldOptions, 0);
        if (goldChoice < 0) {
            return;
        }
        String me = FModel.getPreferences().getPref(FPref.PLAYER_NAME);
        if (me == null || me.trim().isEmpty()) {
            me = t("lblHexHero");
        }
        final List<HexPlayer> players = new ArrayList<>();
        players.add(new HexPlayer(me, false));
        for (int i = 1; i <= companions; i++) {
            players.add(new HexPlayer(t("lblHexCompanion") + " " + i, true));
        }
        final HexCampaign campaign = HexCampaign.create(name.trim(), new Random().nextLong(), HexMap.radiusFor(players.size()), players,
                HexCampaign.GOLD_MULTIPLIERS[goldChoice]);
        HexCampaignIO.save(campaign);
        setSession(new HexSession(campaign));
        if (HostingServer.isHosting()) {
            hostTimer.start();
        }
        broadcast();
    }

    private void loadCampaign() {
        final List<String> saved = HexCampaignIO.list();
        if (saved.isEmpty()) {
            FOptionPane.showMessageDialog(t("lblHexNoSaves"), t("lblHexLoadCampaign"));
            return;
        }
        final String choice = FOptionPane.showInputDialog(t("lblHexLoadCampaign"), t("lblHexLoadCampaign"), null, saved.get(0), saved);
        if (choice != null) {
            final HexCampaign campaign = HexCampaignIO.load(choice);
            if (campaign != null) {
                setSession(new HexSession(campaign));
                if (HostingServer.isHosting()) {
                    hostTimer.start();
                }
                broadcast();
            }
        }
    }
}
