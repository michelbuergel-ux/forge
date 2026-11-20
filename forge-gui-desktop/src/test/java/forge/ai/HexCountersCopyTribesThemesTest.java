package forge.ai;

import forge.gamemodes.hexcrawl.HexTheme;
import forge.model.FModel;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/** The counters theme (every kind, doubled and kept ones too), copying keywords, and tribes without the many-type hints. */
public class HexCountersCopyTribesThemesTest extends AITest {

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
    public void countersHoldDoublersAndKeepers() {
        check("counters", new String[] { "Hardened Scales", "The Ozolith", "Doubling Season", "Corpsejack Menace", "Winding Constrictor",
            "Ajani's Pridemate", "Atraxa, Praetors' Voice" },
            // countering spells is not about counters
            new String[] { "Counterspell", "Mana Leak", "Grizzly Bears" });
    }

    @Test
    public void copyCountsTheCopyingKeywords() {
        check("copy", new String[] { "Clone", "Fork", "Grapeshot", "Gleeful Sabotage" }, new String[] { "Grizzly Bears", "Shock" });
    }

    @Test
    public void tribesLeaveOutCardsHintedAtManyTypes() {
        check("tribe.Elf", new String[] { "Llanowar Elves", "Elvish Archdruid" }, new String[] { "Grizzly Bears" });
        check("tribe.Rogue", new String[] { "Thieves' Guild Enforcer" }, new String[] { "Grotag Bug-Catcher", "Moat" });
        check("tribe.Angel", new String[] { "Serra Angel" }, new String[] { "Moat", "Protean War Engine" });
    }
}
