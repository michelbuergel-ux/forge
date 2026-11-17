package forge.screens.match.views;

import java.util.List;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

/** Team play: the column beside a shared team battlefield, with every head's avatar and details (see VField.leadTeam). */
final class TeamFieldColumn {
    private TeamFieldColumn() { }

    static JPanel build(final List<VField> members) {
        final int n = members.size();
        final JPanel heads = new JPanel(new MigLayout("insets 0, gap 0, wrap 1", "[grow, fill]"));
        heads.setOpaque(false);
        for (final VField m : members) {
            heads.add(m.getAvatarArea(), "w 100%!, h " + (35f / n) + "%!");
            heads.add(m.getDetailsPanel(), "w 100%!, h " + (64f / n) + "%!, gapleft 1px");
        }
        return heads;
    }
}
