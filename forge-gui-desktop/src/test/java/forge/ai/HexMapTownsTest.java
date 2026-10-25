package forge.ai;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexTile;

/** The size of a new map and where its towns lie. */
public class HexMapTownsTest {

    @Test
    public void biggerPartiesGetBiggerMaps() {
        assertEquals(HexMap.radiusFor(1), 7);
        assertEquals(HexMap.radiusFor(2), 7);
        assertEquals(HexMap.radiusFor(3), 8);
        assertEquals(HexMap.radiusFor(4), 8);
        assertEquals(HexMap.townsPerColor(7), 3);
        assertEquals(HexMap.townsPerColor(8), 4);
    }

    @Test
    public void everyColourHasATownNearTheCentreAndOneNearTheNextBorder() {
        final HexColor[] colors = HexColor.COLORS;
        int borders = 0;
        int twoBetween = 0;
        for (final int radius : new int[] { 6, 7, 8 }) {
            for (long seed = 0; seed < 150; seed++) {
                final HexMap map = HexMap.generate(seed, radius);
                for (int i = 0; i < colors.length; i++) {
                    final HexColor color = colors[i];
                    final HexColor next = colors[(i + 1) % colors.length];
                    int towns = 0;
                    boolean nearCentre = false;
                    boolean nearBorder = false;
                    boolean exact = false;
                    for (final HexTile t : map.region(color)) {
                        if (t.poi != HexPoi.TOWN) {
                            continue;
                        }
                        towns++;
                        final int fromCentre = HexMap.distance(t.q, t.r, 0, 0);
                        nearCentre |= fromCentre <= HexMap.CENTER_TOWN_REACH;
                        final boolean border = fromCentre > HexMap.CENTER_TOWN_REACH && map.distanceTo(t, next) <= HexMap.BORDER_TOWN_REACH;
                        nearBorder |= border;
                        exact |= border && map.distanceTo(t, next) == HexMap.BORDER_TOWN_REACH;
                    }
                    final String where = "radius " + radius + ", seed " + seed + ", " + color;
                    assertTrue(nearCentre, "no town near the centre: " + where);
                    assertTrue(nearBorder, "no town near the border to " + next + ": " + where);
                    assertEquals(towns, HexMap.townsPerColor(radius), "towns: " + where);
                    borders++;
                    if (exact) {
                        twoBetween++;
                    }
                }
                assertEquals(map.tilesWith(HexPoi.BOSS).size(), 6, "archmages and Emrakul: radius " + radius + ", seed " + seed);
            }
        }
        // always with two free fields between the border and the town
        System.out.println("border towns with two fields between: " + twoBetween + " of " + borders);
        assertEquals(twoBetween, borders);
    }
}
