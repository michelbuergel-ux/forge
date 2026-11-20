package forge.ai;

import forge.gamemodes.hexcrawl.HexTheme;
import forge.model.FModel;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/** The energy theme of the shops: cards that give energy counters or pay them. */
public class HexEnergyThemeTest extends AITest {

    private static boolean fits(final String name) {
        return HexTheme.get("energy").fits(FModel.getMagicDb().getCommonCards().getCard(name).getRules());
    }

    @Test
    public void energyCardsFitTheTheme() {
        AssertJUnit.assertNotNull(HexTheme.get("energy"));
        AssertJUnit.assertEquals(HexTheme.Group.PLAY, HexTheme.get("energy").group());
        for (final String name : new String[] { "Aethersphere Harvester", "Aethergeode Miner", "Harnessed Lightning",
                "Longtusk Cub", "Rogue Refiner", "Aether Hub", "Glimmer of Genius", "Bristling Hydra" }) {
            AssertJUnit.assertTrue(name, fits(name));
        }
        for (final String name : new String[] { "Grizzly Bears", "Lightning Bolt", "Serra Angel", "Glistener Elf" }) {
            AssertJUnit.assertFalse(name, fits(name));
        }
    }
}
