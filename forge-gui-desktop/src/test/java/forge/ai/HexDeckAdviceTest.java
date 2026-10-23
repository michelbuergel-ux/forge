package forge.ai;

import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexDeckStats;
import forge.gamemodes.hexcrawl.HexDeckStats.Advice;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The numbers and the advice of the Hex Campaign's deck window. */
public class HexDeckAdviceTest extends AITest {

    private static void add(final List<String> list, final String card, final int copies) {
        list.addAll(Collections.nCopies(copies, card));
    }

    /** A green deck of 40 with a few red spells and only Forests. */
    private static List<String> greenWithRedSplash() {
        final List<String> deck = new ArrayList<>();
        add(deck, "Llanowar Elves", 4);       // G
        add(deck, "Grizzly Bears", 4);        // 1G
        add(deck, "Centaur Courser", 4);      // 2G
        add(deck, "Giant Growth", 4);         // G
        add(deck, "Colossal Dreadmaw", 4);    // 4GG
        add(deck, "Lightning Bolt", 2);       // R
        add(deck, "Shock", 2);                // R
        add(deck, "Forest", 16);
        return deck;
    }

    @Test
    public void testSymbolsSourcesAndLands() {
        final HexDeckStats stats = HexDeckStats.of(greenWithRedSplash(), HexContent.get());
        AssertJUnit.assertEquals(40, stats.cards);
        AssertJUnit.assertEquals(16, stats.lands);
        AssertJUnit.assertEquals(4 + 4 + 4 + 4 + 8, stats.symbols[4]); // green symbols
        AssertJUnit.assertEquals(4, stats.symbols[3]);                  // red symbols
        AssertJUnit.assertEquals(16, stats.sources[4]);                 // Forests make green
        AssertJUnit.assertEquals(0, stats.sources[3]);
        AssertJUnit.assertEquals(Arrays.asList(4, 3), stats.deckColors());
        AssertJUnit.assertEquals("red is a main colour at 4 of 28 symbols", Arrays.asList(4, 3), stats.mainColors());
        AssertJUnit.assertEquals(3, stats.wantedSources(3)); // a splash still wants 3 sources
        AssertJUnit.assertEquals(12 + 4, stats.cheapSpells); // Elves, Bears, Giant Growth, Bolt, Shock
    }

    @Test
    public void testNoRedSourceIsFixedWithMountainsFromTheSideboard() {
        final List<String> deck = greenWithRedSplash();
        final List<String> side = new ArrayList<>();
        add(side, "Mountain", 5);
        final HexDeckStats stats = HexDeckStats.of(deck, HexContent.get());
        final List<Advice> advice = stats.advice(deck, side, HexContent.get());
        final Advice first = advice.get(0);
        AssertJUnit.assertEquals(Advice.Kind.NO_SOURCE, first.kind);
        AssertJUnit.assertEquals(3, first.color);
        AssertJUnit.assertEquals("Mountain", first.landIn);
        AssertJUnit.assertEquals("Forest", first.landOut);
        AssertJUnit.assertEquals(3, first.count);

        // after the swap the colours are fine
        for (int i = 0; i < 3; i++) {
            deck.remove("Forest");
            deck.add("Mountain");
        }
        final HexDeckStats fixed = HexDeckStats.of(deck, HexContent.get());
        for (final Advice a : fixed.advice(deck, side, HexContent.get())) {
            AssertJUnit.assertTrue(a.kind + " left", a.kind != Advice.Kind.NO_SOURCE && a.kind != Advice.Kind.FEW_SOURCES);
        }
    }

    @Test
    public void testWithoutAFixTheAdviceHasNone() {
        final List<String> deck = greenWithRedSplash();
        final Advice first = HexDeckStats.of(deck, HexContent.get()).advice(deck, new ArrayList<>(), HexContent.get()).get(0);
        AssertJUnit.assertEquals(Advice.Kind.NO_SOURCE, first.kind);
        AssertJUnit.assertFalse(first.hasFix());
    }

    @Test
    public void testASoundMonoColouredDeckIsSolid() {
        final List<String> deck = new ArrayList<>();
        add(deck, "Llanowar Elves", 4);
        add(deck, "Grizzly Bears", 5);
        add(deck, "Centaur Courser", 5);
        add(deck, "Giant Growth", 3);
        add(deck, "Colossal Dreadmaw", 2);
        add(deck, "Craw Wurm", 4);
        add(deck, "Forest", 17);
        final HexDeckStats stats = HexDeckStats.of(deck, HexContent.get());
        final List<Advice> advice = stats.advice(deck, new ArrayList<>(), HexContent.get());
        AssertJUnit.assertEquals(Advice.Kind.SOLID, advice.get(0).kind);
        AssertJUnit.assertEquals(Collections.singletonList(4), stats.mainColors());
    }

    @Test
    public void testFewLandsAndFewCreatures() {
        final List<String> deck = new ArrayList<>();
        add(deck, "Colossal Dreadmaw", 4);
        add(deck, "Craw Wurm", 4);
        add(deck, "Giant Growth", 18);
        add(deck, "Forest", 14);
        final List<String> side = new ArrayList<>();
        add(side, "Forest", 3);
        add(side, "Grizzly Bears", 2);
        add(side, "Shock", 1);
        final HexDeckStats stats = HexDeckStats.of(deck, HexContent.get());
        final List<Advice> advice = stats.advice(deck, side, HexContent.get());
        Advice lands = null;
        Advice creatures = null;
        for (final Advice a : advice) {
            lands = a.kind == Advice.Kind.FEW_LANDS ? a : lands;
            creatures = a.kind == Advice.Kind.FEW_CREATURES ? a : creatures;
        }
        AssertJUnit.assertNotNull(lands);
        AssertJUnit.assertEquals("Forest", lands.landIn);
        AssertJUnit.assertNotNull(creatures);
        AssertJUnit.assertEquals(8, creatures.numbers[0]);
        AssertJUnit.assertEquals("the Bears fit, the Shock does not count", 2, creatures.numbers[1]);
    }

    @Test
    public void testAColourlessDeck() {
        final List<String> deck = new ArrayList<>();
        add(deck, "Ornithopter", 4);
        add(deck, "Juggernaut", 4);
        add(deck, "Solemn Simulacrum", 4);
        add(deck, "Bonesplitter", 4);
        add(deck, "Mind Stone", 4);
        add(deck, "Steel Overseer", 3);
        add(deck, "Wastes", 17);
        final HexDeckStats stats = HexDeckStats.of(deck, HexContent.get());
        AssertJUnit.assertTrue(stats.deckColors().isEmpty());
        AssertJUnit.assertTrue(stats.mainColors().isEmpty());
        AssertJUnit.assertEquals(0, stats.totalSymbols());
    }
}
