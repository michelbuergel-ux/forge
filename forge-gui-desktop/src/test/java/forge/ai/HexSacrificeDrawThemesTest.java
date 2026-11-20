package forge.ai;

import forge.gamemodes.hexcrawl.HexTheme;
import forge.model.FModel;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/** The sacrifice theme (with paying life) and the card draw theme read what the cards do, not their reminder text. */
public class HexSacrificeDrawThemesTest extends AITest {

    private static boolean fits(final String theme, final String name) {
        return HexTheme.get(theme).fits(FModel.getMagicDb().getCommonCards().getCard(name).getRules());
    }

    private static void check(final String theme, final String[] in, final String[] out) {
        for (final String name : in) {
            AssertJUnit.assertTrue(theme + ": " + name, fits(theme, name));
        }
        for (final String name : out) {
            AssertJUnit.assertFalse(theme + ": " + name, fits(theme, name));
        }
    }

    @Test
    public void sacrificeThemeHasOutletsEdictsAndPayingLife() {
        check("sacrifice", new String[] {
            // outlets (also on a line of their own), edicts, sacrifice as a cost, exploit
            "Carrion Feeder", "Viscera Seer", "Ashnod's Altar", "Bone Shards", "Diabolic Edict", "Village Rites", "Sidisi's Faithful",
            // paying life
            "Necropotence", "Sylvan Library", "Bolas's Citadel", "Toxic Deluge" },
            new String[] {
            // lands that may cost life, Food makers, nothing about sacrifice at all
            "Hallowed Fountain", "Savor", "Grizzly Bears", "Lightning Bolt" });
    }

    @Test
    public void cardDrawThemeIgnoresReminderText() {
        check("carddraw", new String[] { "Divination", "Sign in Blood", "Stroke of Genius", "Phyrexian Arena", "Opt" },
            new String[] {
            // cycling, Clues and Blood draw only in their reminder text; drawing opponents are punished, not helped
            "Raugrin Triome", "Shimmerscale Drake", "Press for Answers", "Underworld Dreams", "Grizzly Bears" });
    }
}
