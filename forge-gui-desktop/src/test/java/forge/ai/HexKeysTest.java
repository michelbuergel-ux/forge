package forge.ai;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexMap;
import forge.screens.home.hexcrawl.HexCrawlPanel;

/** Steering with the keyboard: every key steps the way it sits on the keyboard. */
public class HexKeysTest {

    @Test
    public void everyKeyStepsWhereItSits() {
        final Set<String> directions = new HashSet<>();
        for (final int[] d : HexMap.DIRECTIONS) {
            directions.add(d[0] + "," + d[1]);
        }
        final Set<String> used = new HashSet<>();
        for (final Map.Entry<Character, int[]> key : HexCrawlPanel.STEP_KEYS.entrySet()) {
            final int dq = key.getValue()[0];
            final int dr = key.getValue()[1];
            AssertJUnit.assertTrue(key.getKey() + " is a step to a neighbour", directions.contains(dq + "," + dr));
            used.add(dq + "," + dr);
            // where the neighbour is drawn, as the map panel does it
            final double dx = Math.sqrt(3) * (dq + dr / 2.0);
            final double dy = 1.5 * dr;
            switch (key.getKey()) {
                case 'Q': AssertJUnit.assertTrue("Q: up left", dx < 0 && dy < 0); break;
                case 'E': AssertJUnit.assertTrue("E: up right", dx > 0 && dy < 0); break;
                case 'A': AssertJUnit.assertTrue("A: left", dx < 0 && dy == 0); break;
                case 'D': AssertJUnit.assertTrue("D: right", dx > 0 && dy == 0); break;
                case 'Z': case 'Y': AssertJUnit.assertTrue(key.getKey() + ": down left", dx < 0 && dy > 0); break;
                case 'C': AssertJUnit.assertTrue("C: down right", dx > 0 && dy > 0); break;
                default: AssertJUnit.fail("unexpected key " + key.getKey());
            }
        }
        AssertJUnit.assertEquals("all six neighbours can be reached", 6, used.size());
    }
}
