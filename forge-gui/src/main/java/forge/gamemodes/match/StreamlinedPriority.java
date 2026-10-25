package forge.gamemodes.match;

import forge.game.GameView;
import forge.game.phase.PhaseType;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

import java.util.EnumSet;
import java.util.Set;

/**
 * Settings for games with streamlined priority (Hex Campaign fights), applied on every player's
 * computer while such a game is open. They are temporary: the saved preferences stay as they are,
 * and whatever a player changes during the game is kept.
 */
public final class StreamlinedPriority {
    /** Phase stops in the own (team's) turn: the main phases, and after blockers for combat tricks. */
    private static final Set<PhaseType> OWN_STOPS = EnumSet.of(PhaseType.MAIN1, PhaseType.COMBAT_DECLARE_BLOCKERS, PhaseType.MAIN2);
    /** Phase stops in the opponents' turn: after blockers only (being attacked and spells interrupt anyway). */
    private static final Set<PhaseType> OPPONENT_STOPS = EnumSet.of(PhaseType.COMBAT_DECLARE_BLOCKERS);

    private StreamlinedPriority() { }

    /** Applies the settings if the game uses streamlined priority, and drops those of an earlier game. */
    public static void applyFor(final GameView game) {
        final ForgePreferences prefs = FModel.getPreferences();
        prefs.clearTemporaryPrefs();
        if (game == null || !game.isStreamlinedPriority()) {
            return;
        }
        // no prompt when there is nothing to play, and no pauses between steps
        prefs.setTemporaryPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS, "true");
        prefs.setTemporaryPref(FPref.YIELD_SKIP_PHASE_DELAY, "true");
        prefs.setTemporaryPref(FPref.YIELD_SKIP_RESOLVE_DELAY, "true");
        // "Always OK my partner" is a saved choice: it stays as the player left it in the last fight

        final PhaseType[] phases = PhaseType.values();
        for (int p = 1; p < phases.length; p++) {
            prefs.setTemporaryPref(FPref.PHASES_HUMAN[p - 1], String.valueOf(OWN_STOPS.contains(phases[p])));
            prefs.setTemporaryPref(FPref.PHASES_AI[p - 1], String.valueOf(OPPONENT_STOPS.contains(phases[p])));
        }
    }

    /** Puts the saved preferences back once the game is over. */
    public static void clear() {
        FModel.getPreferences().clearTemporaryPrefs();
    }
}
