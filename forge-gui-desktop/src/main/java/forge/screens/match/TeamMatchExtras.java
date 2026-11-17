package forge.screens.match;

import forge.gamemodes.match.TeamSignal;
import forge.gui.FThreads;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.util.Localizer;

/** What the match screen adds in team games: the chime and pulse of pings, team signals and the team controls. */
final class TeamMatchExtras {
    private final CMatchUI ui;

    TeamMatchExtras(final CMatchUI ui0) {
        ui = ui0;
    }

    /** A ping arrived (after Forge's own handling): a chime and a line for a mate's, and the lit card pulses. */
    void showPing(final PlayerView from, final CardView card) {
        if (card != null && from != null && !ui.isLocalPlayer(from)) {
            forge.sound.SoundSystem.instance.play(forge.sound.SoundEffectType.TakeShard, false);
            final String text = Localizer.getInstance().getMessage("lblPingFrom", from.getName(), card.getName());
            FThreads.invokeInEdtNowOrLater(() -> ui.getCPrompt().showSignal(text, 4000));
        }
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

    /** The team signal buttons, in team games with several heads on this screen's side. */
    void updateTeamControls() {
        final boolean team = ui.getTeamPlayers() != null && ui.getTeamPlayers().size() > 1 && ui.hasLocalPlayers();
        ui.getCPrompt().showTeamControls(team);
    }

    void showTeamSignal(final PlayerView from, final TeamSignal signal, final String detail) {
        final String what = detail != null ? detail : Localizer.getInstance().getMessage(signal.getLabelKey());
        final String text = from == null ? what : from.getName() + ": " + what;
        FThreads.invokeInEdtNowOrLater(() -> ui.getCPrompt().showSignal(text, 4000));
    }
}
