package forge.screens.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import forge.game.player.PlayerView;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.match.views.VField;
import forge.util.collect.FCollectionView;
import forge.view.arcane.PlayArea;

/**
 * Team play: one battlefield for all heads of a side, on the field of its first head (see VField.leadTeam), as the
 * Display menu chooses for the own team and the enemy teams.
 */
final class TeamBattlefieldLayout {
    private TeamBattlefieldLayout() { }

    /**
     * Team games: each side of several heads (ours, and every opposing team) gets one battlefield for all its heads,
     * on the field of its first head; the others' avatars and details join that field in a column.
     */
    static void form(final List<VField> fields, final FCollectionView<PlayerView> sortedPlayers, final Set<PlayerView> teamPlayers) {
        final List<List<VField>> sides = new ArrayList<>();
        for (int i = 0; i < sortedPlayers.size() && i < fields.size(); i++) {
            final PlayerView p = sortedPlayers.get(i);
            List<VField> side = null;
            for (final List<VField> s : sides) {
                final PlayerView first = s.get(0).getPlayer();
                final boolean together = teamPlayers.contains(p) ? teamPlayers.contains(first)
                        : !teamPlayers.contains(first) && !first.isOpponentOf(p);
                if (together) {
                    side = s;
                    break;
                }
            }
            if (side == null) {
                side = new ArrayList<>();
                sides.add(side);
            }
            side.add(fields.get(i));
        }
        // each side as chosen in the Display menu: the own team and the enemy teams apart
        final boolean own = FModel.getPreferences().getPrefBoolean(FPref.UI_TEAM_BATTLEFIELD_OWN);
        final boolean enemy = FModel.getPreferences().getPrefBoolean(FPref.UI_TEAM_BATTLEFIELD_ENEMY);
        for (final List<VField> side : sides) {
            final boolean ours = teamPlayers.contains(side.get(0).getPlayer());
            if (side.size() > 1 && (ours ? own : enemy)) {
                side.get(0).leadTeam(side);
            }
        }
    }

    /** The battlefields on screen, each once (team members share their team's). */
    static List<PlayArea> tabletops(final List<VField> fields) {
        final List<PlayArea> result = new ArrayList<>();
        for (final VField f : fields) {
            if (!result.contains(f.getTabletop())) {
                result.add(f.getTabletop());
            }
        }
        return result;
    }
}
