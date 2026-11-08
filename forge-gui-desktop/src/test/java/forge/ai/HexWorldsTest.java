package forge.ai;

import java.util.Map;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexTheme;
import forge.gamemodes.hexcrawl.HexWorlds;
import forge.item.PaperCard;
import forge.model.FModel;

/** The worlds beyond Magic's planes (Marvel, Fallout, ...) and the themes haste, deathtouch and direct damage. */
public class HexWorldsTest extends AITest {

    private static PaperCard card(final String name) {
        final PaperCard card = FModel.getMagicDb().getCommonCards().getCard(name);
        AssertJUnit.assertNotNull(name, card);
        return card;
    }

    private static boolean fits(final String theme, final String name) {
        return HexTheme.get(theme).fits(card(name).getRules());
    }

    @Test
    public void testEveryWorldIsAThemeWithCards() {
        for (final String world : HexWorlds.IDS) {
            final HexTheme theme = HexTheme.get(world);
            AssertJUnit.assertNotNull(world, theme);
            AssertJUnit.assertEquals(world, HexTheme.Group.WORLDS, theme.group());
            AssertJUnit.assertTrue(world, theme.isStrict());
            final Map<String, PaperCard> cards = HexWorlds.cards(world);
            final StringBuilder towns = new StringBuilder();
            for (final HexColor color : HexColor.values()) {
                towns.append(' ').append(color).append('=').append(HexContent.get().themeCardCount(color, theme));
            }
            System.out.println("world " + world + ": " + cards.size() + " cards," + towns);
            AssertJUnit.assertTrue(world + " has cards", cards.size() >= 20);
        }
    }

    @Test
    public void testNewCardsAndNewArtBelongToAWorld() {
        // new in the world's sets
        AssertJUnit.assertTrue(fits("fallout", "Dogmeat, Ever Loyal"));
        AssertJUnit.assertTrue(fits("marvel", "Aunt May"));
        AssertJUnit.assertTrue(fits("lotr", "Bill the Pony"));
        AssertJUnit.assertTrue(fits("doctorwho", "The Fourth Doctor"));
        AssertJUnit.assertTrue(fits("warhammer40k", "Abaddon the Despoiler"));
        // a reprint with the world's own picture, shown in that printing
        AssertJUnit.assertTrue("Fallout's Sol Ring", fits("fallout", "Sol Ring"));
        final PaperCard solRing = HexContent.get().getCard("Sol Ring", "fallout");
        AssertJUnit.assertEquals("PIP", solRing.getEdition());
        // Secret Lair drops, by collector number
        AssertJUnit.assertEquals("SLD", HexWorlds.printing("universesbeyond", "Nezahal, Primal Tide").getEdition()); // Monster Hunter
        AssertJUnit.assertEquals("SLD", HexWorlds.printing("marvel", "Abundant Growth").getEdition()); // Abundant Groot
        AssertJUnit.assertTrue("Sonic", fits("universesbeyond", "Sonic the Hedgehog"));
        AssertJUnit.assertEquals("Masters of the Universe", "SLD", HexWorlds.printing("universesbeyond", "Full Throttle").getEdition());
        AssertJUnit.assertEquals("Godzilla", "IKO", HexWorlds.printing("universesbeyond", "Crystalline Giant").getEdition());
        AssertJUnit.assertEquals("Age of Sigmar", "SLD", HexWorlds.printing("warhammer40k", "Loxodon Warhammer").getEdition());
        AssertJUnit.assertEquals("Blood Bowl", "SLD", HexWorlds.printing("warhammer40k", "Fellwar Stone").getEdition());
        // not of the world
        AssertJUnit.assertFalse(fits("fallout", "Grizzly Bears"));
        AssertJUnit.assertFalse(fits("marvel", "Dogmeat, Ever Loyal"));
        // outside a world's section the shop shows the card as always
        AssertJUnit.assertEquals(HexContent.get().getCard("Sol Ring").getEdition(), HexContent.get().getCard("Sol Ring", "tokens").getEdition());
    }

    @Test
    public void testHasteDeathtouchAndDirectDamage() {
        AssertJUnit.assertTrue(fits("haste", "Raging Goblin"));
        AssertJUnit.assertTrue("gives haste", fits("haste", "Lightning Greaves"));
        AssertJUnit.assertFalse(fits("haste", "Grizzly Bears"));
        AssertJUnit.assertTrue(fits("deathtouch", "Typhoid Rats"));
        AssertJUnit.assertTrue("gives deathtouch", fits("deathtouch", "Basilisk Collar"));
        AssertJUnit.assertFalse(fits("deathtouch", "Grizzly Bears"));
        AssertJUnit.assertTrue(fits("burn", "Lightning Bolt"));
        AssertJUnit.assertTrue(fits("burn", "Lava Spike"));
        AssertJUnit.assertTrue(fits("burn", "Prodigal Pyromancer"));
        AssertJUnit.assertTrue("damage equal to a power", fits("burn", "Fling"));
        AssertJUnit.assertTrue(fits("burn", "Pyroclasm"));
        AssertJUnit.assertFalse("combat damage is not direct damage", fits("burn", "Ophidian"));
        AssertJUnit.assertFalse("damage to you", fits("burn", "Sulfurous Springs"));
        AssertJUnit.assertFalse(fits("burn", "Grizzly Bears"));
        for (final String id : new String[] { "haste", "deathtouch", "burn" }) {
            AssertJUnit.assertEquals(HexTheme.Group.PLAY, HexTheme.get(id).group());
        }
    }

    @Test
    public void testTheNewThemesComeLast() {
        // themes go over the network by index: the new ones after all the others
        AssertJUnit.assertEquals(48, HexTheme.indexOf("marvel"));
        AssertJUnit.assertEquals(59, HexTheme.indexOf("universesbeyond"));
        AssertJUnit.assertEquals(62, HexTheme.indexOf("burn"));
        AssertJUnit.assertEquals(65, HexTheme.indexOf("alchemy"));
        AssertJUnit.assertEquals(HexTheme.all().size() - 1, HexTheme.indexOf("alchemy"));
    }

    @Test
    public void testCardDrawMillAndAlchemy() {
        AssertJUnit.assertTrue(fits("carddraw", "Divination"));
        AssertJUnit.assertTrue(fits("carddraw", "Harmonize"));
        AssertJUnit.assertTrue("draws a card", fits("carddraw", "Elvish Visionary"));
        AssertJUnit.assertTrue("cards equal to", fits("carddraw", "Collective Unconscious"));
        AssertJUnit.assertFalse(fits("carddraw", "Grizzly Bears"));
        AssertJUnit.assertTrue(fits("mill", "Thought Scour"));
        AssertJUnit.assertTrue(fits("mill", "Hedron Crab"));
        AssertJUnit.assertFalse(fits("mill", "Grizzly Bears"));
        AssertJUnit.assertTrue("an Alchemy card", fits("alchemy", "Angel of Eternal Dawn"));
        AssertJUnit.assertTrue("an Alchemy card", fits("alchemy", "Captain Eberhart"));
        AssertJUnit.assertFalse(fits("alchemy", "Lightning Bolt"));
        AssertJUnit.assertFalse(fits("alchemy", "Grizzly Bears"));
        AssertJUnit.assertTrue(HexTheme.get("alchemy").isStrict());
        for (final String id : new String[] { "carddraw", "mill", "alchemy" }) {
            AssertJUnit.assertEquals(HexTheme.Group.PLAY, HexTheme.get(id).group());
            final StringBuilder towns = new StringBuilder();
            for (final HexColor color : HexColor.values()) {
                towns.append(' ').append(color).append('=').append(HexContent.get().themeCardCount(color, HexTheme.get(id)));
            }
            System.out.println("theme " + id + ":" + towns);
        }
    }
}
