package forge.screens.home.hexcrawl;

import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
import forge.toolbox.FButton;
import forge.toolbox.FLabel;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin;
import forge.toolbox.FTextArea;
import forge.util.Localizer;
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
    private final FLabel lblTitle = new FLabel.Builder().fontSize(18).fontStyle(java.awt.Font.BOLD).fontAlign(SwingConstants.LEFT).build();
    private final FLabel lblPlace = new FLabel.Builder().fontSize(13).fontAlign(SwingConstants.LEFT).build();
    private final FTextArea txtParty = new FTextArea();
    private final FTextArea txtJournal = new FTextArea();
    private final FButton btnFight = new FButton();
    private final FButton btnNewGamePlus = new FButton();
    private final FButton btnRewards = new FButton();
    private final FButton btnPerk = new FButton();
    private final FButton btnShop = new FButton();
    private final FButton btnDecks = new FButton();
    private final FButton btnInvite = new FButton();
    private final FButton btnJoin = new FButton();
    private final FButton btnLeave = new FButton();
    /** Who is connected to whom: shown to the host and to guests. */
    private final FLabel lblConnection = new FLabel.Builder().fontSize(12).fontStyle(java.awt.Font.BOLD).fontAlign(SwingConstants.LEFT).build();
    private final FButton btnNew = new FButton();
    private final FButton btnLoad = new FButton();
    private final JPanel pnlFiles = new JPanel(new MigLayout("insets 0, gap 6, fillx", "[grow, fill][grow, fill]"));

    private HexSession session;
    private boolean rewardPromptOpen;
    /** The cycle whose victory this screen has asked about New Game Plus already. */
    private int newGamePlusPromptedCycle;
    private boolean perkPromptOpen;
    /** The last event (see HexCampaign.eventSerial) already shown on this screen. */
    private int shownEventSerial = -1;
    /** Host or single player: a fight of the campaign is on the match screen (until its result is closed). */
    private volatile boolean fightOnScreen;
    private Runnable shopRefresher;
    private Runnable deckRefresher;
    private int ticks;
    private final Timer hostTimer = new Timer(1500, e -> hostTick());
    /** Keeps the connection line up to date (a lost connection sends no message). */
    private final Timer connectionTimer = new Timer(2000, e -> {
        if (isShowing() && (HexNet.isGuest() || HostingServer.isHosting())) {
            showConnection(isGuest() || HexNet.isGuest());
        }
    });

    public HexCrawlPanel() {
        super(new MigLayout("insets 10, gap 10, fill", "[grow, fill][330!, fill]", "[grow, fill]"));
        setOpaque(false);
        add(mapPanel, "grow");

        final JPanel side = new JPanel(new MigLayout("insets 0, gap 6, wrap 1, fillx, hidemode 3", "[fill]"));
        side.setOpaque(false);
        btnRewards.setText(t("lblHexRewards"));
        btnPerk.setText(t("lblHexChoosePerk"));
        btnShop.setText(t("lblHexShop"));
        btnDecks.setText(t("lblHexDecks"));
        btnInvite.setText(t("lblHexInvite"));
        btnNew.setText(t("lblHexNewCampaign"));
        btnLoad.setText(t("lblHexLoadCampaign"));
        txtParty.setFont(FSkin.getFont(13).getBaseFont());
        txtJournal.setFont(FSkin.getFont(12).getBaseFont());
        side.add(lblTitle, "h 26!");
        side.add(lblPlace, "h 22!");
        side.add(lblConnection, "h 20!");
        side.add(txtParty);
        side.add(btnFight, "h 34!");
        side.add(btnNewGamePlus, "h 34!, hidemode 3");
        side.add(btnRewards, "h 34!");
        side.add(btnPerk, "h 34!");
        side.add(btnShop, "h 30!");
        side.add(btnDecks, "h 30!");
        side.add(btnInvite, "h 28!");
        side.add(btnJoin, "h 28!");
        side.add(btnLeave, "h 26!");
        pnlFiles.setOpaque(false);
        pnlFiles.add(btnNew, "h 28!");
        pnlFiles.add(btnLoad, "h 28!");
        side.add(pnlFiles);
        final FScrollPane scroll = new FScrollPane(txtJournal, true);
        side.add(scroll, "grow, push, hmin 80");
        add(side, "grow");

        mapPanel.setOnTileClicked(this::onTileClicked);
        btnFight.addActionListener(e -> { if (session != null) { session.readyToFight(); } });
        btnNewGamePlus.addActionListener(e -> newGamePlus());
        btnRewards.addActionListener(e -> promptReward());
        btnPerk.addActionListener(e -> promptPerk());
        btnShop.addActionListener(e -> openShop());
        btnDecks.addActionListener(e -> showDeck());
        btnInvite.addActionListener(e -> invite());
        btnJoin.setText(t("lblHexJoinButton"));
        btnLeave.setText(t("lblHexLeave"));
        btnJoin.addActionListener(e -> new HexJoinDialog().show());
        btnLeave.addActionListener(e -> leaveHost());
        btnNew.addActionListener(e -> newCampaign());
        btnLoad.addActionListener(e -> loadCampaign());

        // a reward waiting for us is offered as soon as this screen is visible again after a fight
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
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
        });
    }

    /** The party came across an event: everybody sees what happened, once. */
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

    private void onStateFromHost(final HexStateEvent state) {
        if (session == null || !session.isGuest()) {
            setSession(HexSession.forGuest());
        }
        session.applyState(state.getStateJson(), state.getYourHead());
        // bring the campaign to the front while we are on the home screen (not in the middle of a match)
        if (Singletons.getControl().getCurrentScreen() == FScreen.HOME_SCREEN && !isShowing()) {
            CHomeUI.SINGLETON_INSTANCE.itemClick(EDocID.HOME_HEXCRAWL);
        }
    }

    /** Host: keeps the list of connected guests up to date and makes room for more of them. */
    private void hostTick() {
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

    public void refresh() {
        final HexCampaign campaign = getCampaign();
        mapPanel.setCampaign(campaign);
        mapPanel.setVotes(session == null ? null : session.getVotes());
        final boolean has = campaign != null;
        final boolean guest = isGuest() || HexNet.isGuest();
        pnlFiles.setVisible(!guest);
        btnInvite.setVisible(has && !guest);
        btnInvite.setText(t(HostingServer.isHosting() ? "lblHexInviteShow" : "lblHexInvite"));
        btnJoin.setVisible(!guest && !HostingServer.isHosting());
        btnLeave.setVisible(HexNet.isGuest());
        showConnection(guest);
        btnDecks.setVisible(has);
        btnNewGamePlus.setVisible(has && !guest && getCampaign().won);
        if (has) {
            btnNewGamePlus.setText(t("lblHexNewGamePlus", String.valueOf(getCampaign().cycle)));
        }
        if (!has) {
            btnFight.setVisible(false);
            btnRewards.setVisible(false);
            btnShop.setVisible(false);
            lblTitle.setText(t("lblHexCampaign"));
            lblPlace.setText(t(guest ? "lblHexWaitingForHost" : "lblHexNoCampaign"));
            txtParty.setText("");
            txtJournal.setText(t("lblHexIntro"));
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
        btnShop.setVisible(campaign.inTown());

        lblTitle.setText(campaign.cycle > 1 ? campaign.name + "  –  " + t("lblHexCycle", String.valueOf(campaign.cycle - 1)) : campaign.name);
        final HexTile here = campaign.tile();
        String place = t(here.color.getLabelKey());
        if (here.poi == HexPoi.TOWN) {
            place += " – " + t("lblHexTown") + " " + here.name;
        } else if (here.poi == HexPoi.BOSS) {
            place += " – " + here.name;
        }
        lblPlace.setText(place);

        final StringBuilder party = new StringBuilder();
        for (final HexPlayer p : campaign.players) {
            party.append(p.name);
            if (p.ai) {
                party.append(" (").append(t("lblHexCompanion")).append(')');
            } else if (!present.contains(p.name)) {
                party.append(" (").append(t("lblHexAway")).append(')');
            } else if (p.name.equals(me) && humans > 1) {
                party.append(" (").append(t("lblHexYou")).append(')');
            }
            party.append(": ").append(t("lblHexGoldAndCards", String.valueOf(p.gold), String.valueOf(p.deck.size())));
            if (!p.relics.isEmpty()) {
                party.append(", ").append(t("lblHexRelicCount", String.valueOf(p.relics.size())));
            }
            if (votes.containsKey(p.name)) {
                party.append("  ✓ ").append(t("lblHexVoted"));
            }
            if (campaign.pendingEncounter == null && campaign.pendingReward(p.name) != null) {
                party.append("  … ").append(t("lblHexChoosing"));
            }
            if (!p.perks.isEmpty()) {
                party.append(", ").append(t("lblHexPerkCount", String.valueOf(p.perks.size())));
            }
            if (!p.perkOffer.isEmpty() && !session.isAiHead(p.name)) {
                party.append("  … ").append(t("lblHexChoosingPerk"));
            }
            // ready check before a fight: who is ready, who has looked at their deck
            if (campaign.pendingEncounter != null && !session.isFightRunning()) {
                if (session.isAiHead(p.name)) {
                    party.append("  ✓ ").append(t("lblHexReadyAi"));
                } else {
                    party.append(ready.contains(p.name) ? "  ✓ " + t("lblHexReady") : "  … " + t("lblHexNotReady"));
                    if (campaign.deckChecked.contains(p.name)) {
                        party.append("  ✓ ").append(t("lblHexDeckChecked"));
                    }
                }
            }
            party.append('\n');
        }
        party.append('\n').append(t("lblHexArchmages")).append(' ');
        for (final HexColor c : HexColor.COLORS) {
            party.append(campaign.defeatedBosses.contains(c) ? "[x] " : "[ ] ").append(HexContent.get().getBossName(c)).append("  ");
        }
        party.append('\n').append(t("lblHexRecord", String.valueOf(campaign.battlesWon), String.valueOf(campaign.battlesLost)));
        if (campaign.goldMultiplier > 1) {
            party.append("   ").append(t("lblHexGoldMultiplierInfo", String.valueOf(campaign.goldMultiplier)));
        }
        for (final HexQuest quest : campaign.quests) {
            party.append('\n').append(t("lblHexQuestLine", quest.enemyName, String.valueOf(campaign.gold(quest.gold))));
        }
        final List<String> cycleAdvantages = campaign.cycleAdvantages();
        if (!cycleAdvantages.isEmpty()) {
            party.append("\n\n").append(t("lblHexCycleTitle", String.valueOf(campaign.cycle - 1)));
            for (final String a : cycleAdvantages) {
                party.append("\n  • ").append(a);
            }
        }
        final List<String> boosts = campaign.fightBoosts();
        if (!boosts.isEmpty()) {
            party.append("\n\n").append(t("lblHexBoostsTitle"));
            for (final String boost : boosts) {
                party.append("\n  • ").append(boost);
            }
        }
        if (campaign.won) {
            party.append("\n\n").append(t("lblHexJournalVictory"));
            party.append("\n").append(t(guest ? "lblHexNewGamePlusWait" : "lblHexNewGamePlusHint"));
        } else if (campaign.pendingEncounter != null) {
            party.append("\n\n").append(describe(campaign.pendingEncounter));
        } else if (humans > 1 && !campaign.isBusy()) {
            party.append("\n\n").append(t("lblHexVoteHint", String.valueOf(votes.size()), String.valueOf(humans)));
        }
        txtParty.setText(party.toString());

        final List<String> lines = new ArrayList<>(campaign.journal);
        Collections.reverse(lines);
        txtJournal.setText(String.join("\n", lines));
        txtJournal.setCaretPosition(0);
        if (shopRefresher != null) {
            shopRefresher.run();
        }
        if (deckRefresher != null) {
            deckRefresher.run();
        }
        revalidate();
        repaint();
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

    private void onTileClicked(final HexTile target) {
        final HexCampaign campaign = getCampaign();
        if (campaign == null || session.isFightRunning() || target == campaign.tile()) {
            return;
        }
        if (!campaign.isAdjacent(target) && target.poi == HexPoi.TOWN && target.explored) {
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
        if (!campaign.canEnter(target)) {
            if (campaign.isAdjacent(target)) {
                final String why = campaign.won ? "lblHexJournalVictory" : campaign.isBusy() ? "lblHexBusy" : "lblHexCenterLocked";
                FOptionPane.showMessageDialog(t(why), campaign.name);
            }
            return; // only neighbouring fields can be entered; they are outlined on the map
        }
        final boolean alone = session.getPresent().size() == 1 && !session.isGuest();
        session.vote(target.q, target.r);
        // playing alone the step happens at once: offer the fight right away
        if (alone && campaign.pendingEncounter != null && !session.isFightRunning()) {
            refresh();
            final int choice = FOptionPane.showOptionDialog(describe(campaign.pendingEncounter), campaign.name,
                    FOptionPane.INFORMATION_ICON, Arrays.asList(t("lblHexFight"), t("lblHexLater")), 0);
            if (choice == 0) {
                session.readyToFight();
            }
        }
    }

    // ------------------------------------------------------------------ rewards

    private void promptRewardIfWaiting() {
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
        final Localizer loc = Localizer.getInstance();
        final StringBuilder sb = new StringBuilder();
        final int followers = campaign.enemyHeads() - 1;
        if (followers > 0) {
            sb.append(' ').append(loc.getMessage(enc.headsAlike() ? "lblHexEnemyCopies" : "lblHexEnemyFollowers", String.valueOf(followers)));
        }
        if (!enc.startCards.isEmpty()) {
            sb.append(' ').append(loc.getMessage("lblHexEnemyStarts", HexContent.get().startCardNames(enc)));
        }
        final List<String> cycleAdvantages = campaign.cycleAdvantages();
        if (!cycleAdvantages.isEmpty()) {
            sb.append(' ').append(loc.getMessage("lblHexEnemyCycle", String.valueOf(campaign.cycle - 1), String.join(", ", cycleAdvantages)));
        }
        return sb.toString();
    }

    /** What a relic does, one effect per line. */
    static String relicEffects(final HexRelic relic) {
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
        final HexCampaign campaign = HexCampaign.create(name.trim(), new Random().nextLong(), 6, players,
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
