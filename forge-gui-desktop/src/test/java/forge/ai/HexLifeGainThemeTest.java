package forge.ai;

import forge.gamemodes.hexcrawl.HexTheme;
import forge.model.FModel;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/** The life gain theme of the shops: cards that give life, lifelink, and doubling life gained; not cards that merely care about life. */
public class HexLifeGainThemeTest extends AITest {

    private static boolean fits(final String name) {
        return HexTheme.get("lifegain").fits(FModel.getMagicDb().getCommonCards().getCard(name).getRules());
    }

    @Test
    public void lifeGainCardsFitTheTheme() {
        for (final String name : new String[] { "Soul Warden", "Healing Salve", "Lightning Helix", "Vampire Nighthawk",
                "Rhox Faithmender", "Boon Reflection", "Alhammarret's Archive", "Exquisite Blood" }) {
            AssertJUnit.assertTrue(name, fits(name));
        }
        // paying life, life totals, stopping or punishing life gain, and nothing to do with life at all
        for (final String name : new String[] { "Necropotence", "Everlasting Torment", "Tainted Remedy", "Grizzly Bears",
                "Lightning Bolt" }) {
            AssertJUnit.assertFalse(name, fits(name));
        }
    }
}
