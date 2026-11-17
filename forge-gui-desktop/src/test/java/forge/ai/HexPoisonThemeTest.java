package forge.ai;

import forge.gamemodes.hexcrawl.HexTheme;
import forge.model.FModel;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/** The poison theme of the shops: cards that give poison counters, add more of them, or care about them. */
public class HexPoisonThemeTest extends AITest {

    private static boolean fits(final String name) {
        return HexTheme.get("poison").fits(FModel.getMagicDb().getCommonCards().getCard(name).getRules());
    }

    @Test
    public void poisonCardsFitTheTheme() {
        AssertJUnit.assertNotNull(HexTheme.get("poison"));
        AssertJUnit.assertEquals(HexTheme.Group.PLAY, HexTheme.get("poison").group());
        for (final String name : new String[] { "Glistener Elf", "Blighted Agent", "Plague Myr", "Atraxa, Praetors' Voice",
                "Contagion Clasp", "Vraska's Fall", "Pestilent Syphoner", "Virulent Sliver" }) {
            AssertJUnit.assertTrue(name, fits(name));
        }
        for (final String name : new String[] { "Grizzly Bears", "Lightning Bolt", "Serra Angel" }) {
            AssertJUnit.assertFalse(name, fits(name));
        }
    }
}
