package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A hexagon shaped map of hex fields in axial coordinates. The centre is colourless and holds
 * the final boss; around it the five colours form large connected regions, each with its
 * archmage far out and a few towns.
 */
public class HexMap {
    public static final int[][] DIRECTIONS = { {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1} };

    public int radius;
    public Map<String, HexTile> tiles = new LinkedHashMap<>();
    public int startQ;
    public int startR;

    public HexTile get(final int q, final int r) {
        return tiles.get(q + "," + r);
    }

    public static int distance(final int q1, final int r1, final int q2, final int r2) {
        final int dq = q1 - q2;
        final int dr = r1 - r2;
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
    }

    public List<HexTile> neighbors(final HexTile t) {
        final List<HexTile> result = new ArrayList<>(6);
        for (final int[] d : DIRECTIONS) {
            final HexTile n = get(t.q + d[0], t.r + d[1]);
            if (n != null) {
                result.add(n);
            }
        }
        return result;
    }

    public List<HexTile> tilesWith(final HexPoi poi) {
        final List<HexTile> result = new ArrayList<>();
        for (final HexTile t : tiles.values()) {
            if (t.poi == poi) {
                result.add(t);
            }
        }
        return result;
    }

    public List<HexTile> region(final HexColor color) {
        final List<HexTile> result = new ArrayList<>();
        for (final HexTile t : tiles.values()) {
            if (t.color == color) {
                result.add(t);
            }
        }
        return result;
    }

    public HexTile boss(final HexColor color) {
        for (final HexTile t : tiles.values()) {
            if (t.poi == HexPoi.BOSS && t.color == color) {
                return t;
            }
        }
        return null;
    }

    /** Creates a new map. The same seed always gives the same map. */
    public static HexMap generate(final long seed, final int radius0) {
        final Random rnd = new Random(seed);
        final HexMap map = new HexMap();
        map.radius = Math.max(4, radius0);
        final int radius = map.radius;

        for (int q = -radius; q <= radius; q++) {
            for (int r = Math.max(-radius, -q - radius); r <= Math.min(radius, -q + radius); r++) {
                map.tiles.put(q + "," + r, new HexTile(q, r));
            }
        }

        // colours: five sectors around a colourless centre, with a wobbling border
        final double rotation = rnd.nextDouble() * Math.PI * 2;
        final double sector = Math.PI * 2 / 5;
        final HexColor[] order = HexColor.COLORS.clone();
        final int shift = rnd.nextInt(5); // which colour starts where
        for (final HexTile t : map.tiles.values()) {
            final int dist = distance(t.q, t.r, 0, 0);
            if (dist <= 1) {
                t.color = HexColor.COLORLESS;
                continue;
            }
            final double x = Math.sqrt(3) * (t.q + t.r / 2.0);
            final double y = 1.5 * t.r;
            double angle = Math.atan2(y, x) + rotation + (rnd.nextDouble() - 0.5) * 0.5;
            angle = ((angle % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
            t.color = order[((int) (angle / sector) + shift) % 5];
        }
        // smooth: a field surrounded by another colour joins it, so regions stay in one piece
        for (int pass = 0; pass < 3; pass++) {
            for (final HexTile t : map.tiles.values()) {
                if (t.color == HexColor.COLORLESS) {
                    continue;
                }
                final int[] count = new int[HexColor.values().length];
                for (final HexTile n : map.neighbors(t)) {
                    if (n.color != HexColor.COLORLESS) {
                        count[n.color.ordinal()]++;
                    }
                }
                int best = t.color.ordinal();
                for (int i = 0; i < count.length; i++) {
                    if (count[i] > count[best]) {
                        best = i;
                    }
                }
                if (count[t.color.ordinal()] <= 1 && count[best] >= 3) {
                    t.color = HexColor.values()[best];
                }
            }
        }

        // the final boss waits in the centre
        final HexTile center = map.get(0, 0);
        center.poi = HexPoi.BOSS;

        // each colour: its archmage on the outer rings, towns in between
        final List<HexTile> pois = new ArrayList<>();
        pois.add(center);
        // (all archmages first, so that towns cannot take the last free spot of a small region)
        for (final HexColor color : HexColor.COLORS) {
            final List<HexTile> region = map.region(color);
            HexTile boss = null;
            for (int spacing = 3; spacing >= 1 && boss == null; spacing--) {
                for (int minDist = radius; minDist >= 2 && boss == null; minDist--) {
                    boss = pick(region, pois, rnd, minDist, radius, spacing);
                }
            }
            if (boss != null) {
                boss.poi = HexPoi.BOSS;
                pois.add(boss);
            }
        }
        for (final HexColor color : HexColor.COLORS) {
            final List<HexTile> region = map.region(color);
            for (int i = 0; i < 2; i++) {
                HexTile town = null;
                for (int spacing = 3; spacing >= 1 && town == null; spacing--) {
                    town = pick(region, pois, rnd, 2, radius - 1, spacing);
                }
                if (town != null) {
                    town.poi = HexPoi.TOWN;
                    pois.add(town);
                }
            }
        }

        // the journey starts in a town close to the centre
        HexTile start = null;
        for (final HexTile t : map.tilesWith(HexPoi.TOWN)) {
            if (start == null || distance(t.q, t.r, 0, 0) < distance(start.q, start.r, 0, 0)) {
                start = t;
            }
        }
        if (start == null) { // cannot really happen, but a map must have a start
            start = map.get(2, 0);
            start.poi = HexPoi.TOWN;
        }
        map.startQ = start.q;
        map.startR = start.r;
        start.explored = true;
        return map;
    }

    private static HexTile pick(final List<HexTile> region, final List<HexTile> taken, final Random rnd,
            final int minDistFromCenter, final int maxDistFromCenter, final int spacing) {
        final List<HexTile> candidates = new ArrayList<>();
        for (final HexTile t : region) {
            final int dist = distance(t.q, t.r, 0, 0);
            if (t.poi != HexPoi.NONE || dist < minDistFromCenter || dist > maxDistFromCenter) {
                continue;
            }
            boolean free = true;
            for (final HexTile other : taken) {
                if (distance(t.q, t.r, other.q, other.r) < spacing) {
                    free = false;
                    break;
                }
            }
            if (free) {
                candidates.add(t);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(rnd.nextInt(candidates.size()));
    }
}
