package forge.screens.match;

import javax.swing.JPanel;

import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.gui.FThreads;
import forge.util.Localizer;
import forge.view.FView;

/**
 * What the match screen adds in team play and Hex Campaign fights: the fight banner with the enemy's life and the
 * team mates' last actions, the chime and pulse of pings, team signals and the team controls. CMatchUI calls in here
 * through one-line hooks; outside these games nothing happens.
 */
final class HexMatchExtras {
    private final CMatchUI ui;

    HexMatchExtras(final CMatchUI ui0) {
        ui = ui0;
    }

    /** The strip above a campaign fight: the enemy, its life and the team mates' last actions; null in other matches. */
    private forge.screens.home.hexcrawl.HexFightBanner fightBanner;

    /** Puts the banner above the match (again: laying out a screen clears it). Only in campaign fights. */
    void placeFightBanner() {
        if (fightBanner == null) {
            final forge.gamemodes.hexcrawl.HexFightInfo info = forge.gamemodes.hexcrawl.HexNet.getFightInfo();
            if (info == null) {
                return;
            }
            fightBanner = new forge.screens.home.hexcrawl.HexFightBanner(info, ui::setCard);
        }
        final JPanel insets = FView.SINGLETON_INSTANCE.getPnlInsets();
        if (fightBanner.getParent() != insets) {
            insets.add(fightBanner, java.awt.BorderLayout.NORTH);
            insets.revalidate();
            forge.gui.framework.SResizingUtil.resizeWindow();
        }
        refreshFightBanner();
    }

    /** Takes the banner away when another screen comes up. */
    void removeFightBanner() {
        if (fightBanner != null && fightBanner.getParent() != null) {
            final java.awt.Container parent = fightBanner.getParent();
            parent.remove(fightBanner);
            parent.revalidate();
            forge.gui.framework.SResizingUtil.resizeWindow();
        }
    }

    /** The enemy team's life (the team shares it) and whether its boss has woken, into the banner. */
    void refreshFightBanner() {
        final GameView gv = ui.getGameView();
        if (fightBanner == null || gv == null) {
            return;
        }
        final forge.gamemodes.hexcrawl.HexFightInfo info = forge.gamemodes.hexcrawl.HexNet.getFightInfo();
        final String phaseCard = info == null ? null : info.phaseCard;
        Integer life = null;
        boolean awakened = false;
        for (final PlayerView p : gv.getPlayers()) {
            if (!isEnemyOfTeam(p)) {
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
            fightBanner.setEnemy(life, awakened);
        }
    }

    /** A player of the other side: not on this screen's team. */
    private boolean isEnemyOfTeam(final PlayerView p) {
        if (ui.getTeamPlayers() != null) {
            return !ui.getTeamPlayers().contains(p);
        }
        for (final PlayerView mine : ui.getLocalPlayers()) {
            if (mine.isOpponentOf(p)) {
                return true;
            }
        }
        return false;
    }

    /** A team mate, not one of this screen's own players. */
    private boolean isTeamMate(final PlayerView p) {
        return p != null && ui.getTeamPlayers() != null && ui.getTeamPlayers().contains(p) && !ui.isLocalPlayer(p);
    }

    void noteTeamCast(final GameEventSpellAbilityCast event) {
        final StackItemView si = event.si();
        if (fightBanner == null || si == null || si.isTrigger() || !isTeamMate(si.getActivatingPlayer())) {
            return;
        }
        final CardView card = si.getSourceCard();
        String text = si.getActivatingPlayer().getName() + ": " + (card != null ? card.getName() : si.getText());
        final String targets = event.targetDescription();
        if (targets != null && !targets.isEmpty()) {
            text += " → " + targets;
        }
        fightBanner.addTeamAction(text, card);
    }

    /** A team mate played a land: into the banner's log. */
    void noteTeamLand(final CardView land) {
        if (fightBanner != null && land != null && isTeamMate(land.getController())) {
            fightBanner.addTeamAction(Localizer.getInstance().getMessage("lblHexBannerLand", land.getController().getName(), land.getName()), land);
        }
    }

    /** A ping arrived (after Forge's own handling): a chime and a line for a mate's, and the lit card pulses. */
    void showPing(final PlayerView from, final CardView card) {
        if (card != null && from != null && !ui.isLocalPlayer(from)) {
            // a team mate points at a card: a chime, and who pointed at what
            forge.sound.SoundSystem.instance.play(forge.sound.SoundEffectType.TakeShard, false);
            final String text = Localizer.getInstance().getMessage("lblPingFrom", from.getName(), card.getName());
            FThreads.invokeInEdtNowOrLater(() -> ui.getCPrompt().showSignal(text, 4000));
        }
        // the lit card pulses while it is lit
        FThreads.invokeInEdtNowOrLater(() -> {
            final long until = System.currentTimeMillis() + 4100;
            final javax.swing.Timer pulse = new javax.swing.Timer(70, null);
            pulse.addActionListener(e -> {
                ui.repaintCardOverlays();
                if (System.currentTimeMillis() > until) {
                    pulse.stop();
                }
            });
            pulse.start();
        });
    }

    /** Team signal buttons in team games; Flee in the first turns of a Hex Campaign fight. */
    void updateTeamControls() {
        final GameView gv = ui.getGameView();
        final boolean team = ui.getTeamPlayers() != null && ui.getTeamPlayers().size() > 1 && ui.hasLocalPlayers();
        final boolean mayFlee = gv != null && ui.hasLocalPlayers() && forge.gamemodes.hexcrawl.HexNet.isCampaignFight()
                && gv.getTurn() <= forge.gamemodes.hexcrawl.HexFight.FLEE_MAX_TURN && !gv.isGameOver();
        ui.getCPrompt().showTeamControls(team, mayFlee);
    }

    void showTeamSignal(final PlayerView from, final forge.gamemodes.match.TeamSignal signal, final String detail) {
        final String what = detail != null ? detail : Localizer.getInstance().getMessage(signal.getLabelKey());
        final String text = signal == forge.gamemodes.match.TeamSignal.FLEE_VOTE || from == null ? what : from.getName() + ": " + what;
        FThreads.invokeInEdtNowOrLater(() -> ui.getCPrompt().showSignal(text, signal == forge.gamemodes.match.TeamSignal.FLEE_VOTE ? 10000 : 4000));
    }
}
