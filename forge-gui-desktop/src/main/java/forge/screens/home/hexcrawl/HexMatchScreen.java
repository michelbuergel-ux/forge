package forge.screens.home.hexcrawl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;

import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexFight;
import forge.gamemodes.hexcrawl.HexFightInfo;
import forge.gamemodes.hexcrawl.HexNet;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.match.CMatchUI;
import forge.screens.match.MatchModeExtras;
import forge.toolbox.FButton;
import forge.toolbox.FOptionPane;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinnedCheckBoxMenuItem;
import forge.util.Localizer;
import forge.view.FView;

/**
 * The match screen of a campaign fight: the banner with the enemy's life and the team mates' last actions, the guest's
 * fight music, Flee and Fight on in the first turns, the game menu's items for streamlined priority and the host's way
 * out of a hanging fight, and the result screen that leads back to the map.
 */
public final class HexMatchScreen implements MatchModeExtras {
    /** The match screen the banner and the buttons below belong to: a new match brings its own. */
    private CMatchUI forUi;
    private HexFightBanner banner;
    private FButton btnFlee;
    private FButton btnFightOn;
    private SkinnedCheckBoxMenuItem okOwnItem;
    private SkinnedCheckBoxMenuItem okPartnerItem;
    private List<JMenuItem> hostItems = Collections.emptyList();

    /** Registered with the match screen when the campaign's menu loads. */
    static void register() {
        MatchModeExtras.set(new HexMatchScreen());
    }

    private static String t(final String key, final Object... args) {
        return Localizer.getInstance().getMessage(key, args);
    }

    // ------------------------------------------------------------------ the banner

    /** Forgets the banner and the buttons of an earlier match screen. */
    private void own(final CMatchUI ui) {
        if (ui != forUi) {
            forUi = ui;
            banner = null;
            btnFlee = null;
            btnFightOn = null;
        }
    }

    /** Puts the banner above the match (again: laying out a screen clears it). Only in campaign fights. */
    @Override
    public void placeOn(final CMatchUI ui) {
        own(ui);
        if (banner == null) {
            final HexFightInfo info = HexNet.getFightInfo();
            if (info == null) {
                return;
            }
            banner = new HexFightBanner(info, ui::setCard);
        }
        final JPanel insets = FView.SINGLETON_INSTANCE.getPnlInsets();
        if (banner.getParent() != insets) {
            insets.add(banner, java.awt.BorderLayout.NORTH);
            insets.revalidate();
            forge.gui.framework.SResizingUtil.resizeWindow();
        }
        refresh(ui);
    }

    @Override
    public void removeFrom(final CMatchUI ui) {
        if (ui == forUi && banner != null && banner.getParent() != null) {
            final java.awt.Container parent = banner.getParent();
            parent.remove(banner);
            parent.revalidate();
            forge.gui.framework.SResizingUtil.resizeWindow();
        }
    }

    /** The enemy team's life (the team shares it) and whether its boss has woken, into the banner. */
    @Override
    public void refresh(final CMatchUI ui) {
        final GameView gv = ui.getGameView();
        if (ui != forUi || banner == null || gv == null) {
            return;
        }
        final HexFightInfo info = HexNet.getFightInfo();
        final String phaseCard = info == null ? null : info.phaseCard;
        Integer life = null;
        boolean awakened = false;
        for (final PlayerView p : gv.getPlayers()) {
            if (!isEnemy(ui, p)) {
                continue;
            }
            life = life == null ? p.getLife() : Math.max(life, p.getLife());
            if (phaseCard != null && p.getCommand() != null) {
                for (final CardView c : p.getCommand()) {
                    if (phaseCard.equals(c.getName()) && c.getCounters(forge.game.card.CounterEnumType.AWAKENING) > 0) {
                        awakened = true;
                    }
                }
            }
        }
        if (life != null) {
            banner.setEnemy(life, awakened);
        }
    }

    /** A player of the other side: an opponent of this screen's players. */
    private static boolean isEnemy(final CMatchUI ui, final PlayerView p) {
        for (final PlayerView mine : ui.getLocalPlayers()) {
            if (mine.isOpponentOf(p)) {
                return true;
            }
        }
        return false;
    }

    /** A team mate, not one of this screen's own players. */
    private static boolean isTeamMate(final CMatchUI ui, final PlayerView p) {
        return p != null && !ui.isLocalPlayer(p) && !isEnemy(ui, p);
    }

    @Override
    public void noteTeamCast(final CMatchUI ui, final GameEventSpellAbilityCast event) {
        final StackItemView si = event.si();
        if (ui != forUi || banner == null || si == null || si.isTrigger() || !isTeamMate(ui, si.getActivatingPlayer())) {
            return;
        }
        final CardView card = si.getSourceCard();
        String text = si.getActivatingPlayer().getName() + ": " + (card != null ? card.getName() : si.getText());
        final String targets = event.targetDescription();
        if (targets != null && !targets.isEmpty()) {
            text += " → " + targets;
        }
        banner.addTeamAction(text, card);
    }

    @Override
    public void noteTeamLand(final CMatchUI ui, final CardView land) {
        if (ui == forUi && banner != null && land != null && isTeamMate(ui, land.getController())) {
            banner.addTeamAction(t("lblHexBannerLand", land.getController().getName(), land.getName()), land);
        }
    }

    // ------------------------------------------------------------------ music, flee

    @Override
    public void matchStarting(final CMatchUI ui) {
        // a guest's match has no music of its own (the host's HostedMatch sets it there)
        if (ui.isNetGame() && HexNet.isGuest() && HexNet.isCampaignFight() && HexNet.getFightMusic() != null) {
            forge.sound.SoundSystem.instance.setBackgroundMusic(HexNet.getFightMusic());
        }
    }

    /** Flee (and Fight on, with team mates) in the first turns of a campaign fight. */
    @Override
    public List<? extends JComponent> promptButtons(final CMatchUI ui) {
        final GameView gv = ui.getGameView();
        if (gv == null || !ui.hasLocalPlayers() || !HexNet.isCampaignFight() || gv.getTurn() > HexFight.FLEE_MAX_TURN || gv.isGameOver()) {
            return Collections.emptyList();
        }
        own(ui);
        if (btnFlee == null) {
            btnFlee = new FButton(t("lblFlee"));
            btnFlee.setFont(FSkin.getFont(11));
            btnFlee.addActionListener(e -> {
                if (ui.getGameController() != null && FOptionPane.showConfirmDialog(
                        t("lblFleeConfirm", String.valueOf(HexNet.getFleePrice()), String.valueOf(HexFight.FLEE_MAX_TURN)), t("lblFlee"))) {
                    ui.getGameController().voteFlee(true);
                }
            });
            btnFightOn = new FButton(t("lblFightOn"));
            btnFightOn.setFont(FSkin.getFont(11));
            btnFightOn.addActionListener(e -> {
                if (ui.getGameController() != null) {
                    ui.getGameController().voteFlee(false);
                }
            });
        }
        int side = 0;
        for (final PlayerView p : gv.getPlayers()) {
            side += isEnemy(ui, p) ? 0 : 1;
        }
        final List<FButton> buttons = new ArrayList<>();
        buttons.add(btnFlee);
        if (side > 1) {
            buttons.add(btnFightOn);
        }
        return buttons;
    }

    // ------------------------------------------------------------------ game menu

    @Override
    public void addMenuItems(final JMenu menu, final CMatchUI ui) {
        okOwnItem = prefItem("lblOkOwnSpells", "nlOkOwnSpells", FPref.YIELD_AUTO_OK_OWN, ui);
        okPartnerItem = prefItem("lblAlwaysOkPartner", "lblAlwaysOkPartnerHint", FPref.YIELD_AUTO_OK_PARTNER, ui);
        menu.add(okOwnItem);
        menu.add(okPartnerItem);
        // a campaign fight on this computer (the host's): its way out when the fight hangs
        hostItems = HexFightHostMenu.items();
        hostItems.forEach(menu::add);
    }

    @Override
    public void menuOpened(final CMatchUI ui) {
        HexFightHostMenu.refresh(hostItems);
        final boolean streamlined = ui.getGameView() != null && ui.getGameView().isStreamlinedPriority();
        if (okOwnItem != null) {
            okOwnItem.setVisible(streamlined);
            okPartnerItem.setVisible(streamlined);
        }
    }

    /** A setting of streamlined priority, saved and told to the game at once (in a network game the host asks for this player). */
    private static SkinnedCheckBoxMenuItem prefItem(final String label, final String hint, final FPref pref, final CMatchUI ui) {
        final SkinnedCheckBoxMenuItem item = new SkinnedCheckBoxMenuItem(t(label));
        item.setToolTipText(t(hint));
        item.setState(FModel.getPreferences().getPrefBoolean(pref));
        item.addActionListener(e -> {
            final boolean on = !FModel.getPreferences().getPrefBoolean(pref);
            FModel.getPreferences().setPref(pref, String.valueOf(on));
            FModel.getPreferences().save();
            ui.getGameController().setYieldPref(pref, String.valueOf(on));
            item.setState(on);
        });
        return item;
    }

    // ------------------------------------------------------------------ result

    /** A campaign fight is decided once: no next game, no rematch, back to the map. */
    @Override
    public boolean ownsResult() {
        return HexNet.isCampaignFight();
    }

    @Override
    public String resultTitle(final GameView game, final int winningTeam) {
        // the party is always team 0 (a single head wins as a player, not as a team)
        final boolean partyWon = winningTeam == HexBattle.PARTY_TEAM
                || (winningTeam == -1 && game.isWinner(forge.player.GamePlayerUtil.getGuiPlayer()));
        return t(partyWon ? "lblHexFightWon" : "lblHexFightLost");
    }

    @Override
    public String resultQuitText() {
        return t("lblHexBackToCampaign");
    }

    @Override
    public void resultClosed() {
        HexNet.resultScreenClosed(); // back to the map
    }
}
