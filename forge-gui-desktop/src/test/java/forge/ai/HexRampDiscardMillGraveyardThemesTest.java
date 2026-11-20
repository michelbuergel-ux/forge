package forge.ai;

import forge.gamemodes.hexcrawl.HexTheme;
import forge.model.FModel;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/** Ramp, discard, mill and graveyard read what the cards do; the "aristocrats" go with sacrifice. */
public class HexRampDiscardMillGraveyardThemesTest extends AITest {

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
    public void rampMakesManaOrFetchesLands() {
        check("ramp", new String[] { "Llanowar Elves", "Rampant Growth", "Cultivate", "Exploration", "Mana Reflection", "Sol Ring",
            "Azusa, Lost but Seeking", "Goblin Electromancer" },
            new String[] { "Forest", "Grizzly Bears", "Lightning Bolt" });
    }

    @Test
    public void discardDiscards() {
        check("discard", new String[] { "Hymn to Tourach", "Faithless Looting", "Fiery Temper", "Raffine's Silencer", "Archfiend of Ifnir" },
            // graveyard payoffs that only hint at discarding; cycling discards only itself
            new String[] { "Wickerfolk Thresher", "Oni-Cult Anvil", "Raugrin Triome", "Grizzly Bears" });
    }

    @Test
    public void millMillsAndTheGraveyardBringsBack() {
        check("mill", new String[] { "Glimpse the Unthinkable", "Stitcher's Supplier" },
            new String[] { "Bone Dragon", "Kaervek, the Punisher", "Grizzly Bears" });
        check("graveyard", new String[] { "Reanimate", "Animate Dead", "Gravedigger", "Raise Dead", "Eternal Witness",
            "Unburial Rites", "Bone Dragon" },
            new String[] { "Grizzly Bears", "Lightning Bolt" });
    }

    @Test
    public void deathTriggersGoWithSacrifice() {
        check("sacrifice", new String[] { "Blood Artist", "Zulaport Cutthroat", "Teysa Karlov", "Grim Haruspex" },
            new String[] { "Grizzly Bears" });
    }
}
