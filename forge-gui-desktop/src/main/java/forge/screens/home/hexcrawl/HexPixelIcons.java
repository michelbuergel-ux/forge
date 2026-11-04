package forge.screens.home.hexcrawl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import forge.gamemodes.hexcrawl.HexColor;

/**
 * The map's own pictures (towns, dungeons, the bosses' stars, the party, events, an enemy without a picture) as
 * little pixel images, about the size of the enemies' sprites (16 pixels), so that they look alike. Each is drawn
 * once and kept; they are scaled up without smoothing, like the sprites.
 */
final class HexPixelIcons {
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private HexPixelIcons() { }

    // ------------------------------------------------------------------ the pictures

    /** A house with a red roof, a lit window and a door; a besieged one scorched, with flames on its roof. */
    private static final String[] HOUSE = {
        "................",
        ".......kk.......",
        "......kRrk......",
        ".....kRRrrk.....",
        "....kRRRrrrk....",
        "...kRRRRrrrrk...",
        "..kRRRRRrrrrrk..",
        ".kRRRRRRrrrrrrk.",
        "kkkkkkkkkkkkkkkk",
        ".kWWWWWWWWWWWwk.",
        ".kWkkkkWWkkkkwk.",
        ".kWkyykWWkDDkwk.",
        ".kWkyykWWkDDkwk.",
        ".kWkkkkWWkDDkwk.",
        ".kWWWWWWWkDDkwk.",
        ".kkkkkkkkkkkkkk.",
    };

    /** The flames on a besieged house's roof; their bottom row lies on the house's row 5. */
    private static final String[] FLAMES = {
        ".......F........",
        ".......FF.......",
        "......FYFF......",
        "...F..FYYF..F...",
        "..FF..fYYF..FF..",
        "..FYF.ffFf.FYF..",
        "..FYF......FYF..",
        "..fYf......fYf..",
        "..fff......fff..",
    };
    private static final int FLAMES_ABOVE = 3;

    /** A dungeon: a hill of stone with a dark cave mouth. */
    private static final String[] DUNGEON = {
        "................",
        "................",
        ".....kkkkkk.....",
        "...kkLSSSSskk...",
        "..kLLSSSSSSssk..",
        ".kLLSSkkkkSSssk.",
        ".kLSSkMMMMkSssk.",
        ".kLSkMMmmMMkssk.",
        "kLSSkMmmmmMkSssk",
        "kLSSkMmmmmMkSssk",
        "kLSSkMmmmmMkSssk",
        "kSSSkMmmmmMkSssk",
        "kSSkMMmmmmMMkssk",
        "kSSkMMMMMMMMkssk",
        "ksSkMMMMMMMMkssk",
        "kkkkkkkkkkkkkkkk",
    };

    /** A collapsed dungeon: grey, its mouth full of boulders. */
    private static final String[] COLLAPSED = {
        "................",
        "................",
        ".....kkkkkk.....",
        "...kkLSSSSskk...",
        "..kLLSSSSSSssk..",
        ".kLLSSkkkkSSssk.",
        ".kLSSkMHBMkSssk.",
        ".kLSkMMBbMMkssk.",
        "kLSSkHBMHBbkSssk",
        "kLSSkBbMBBbkSssk",
        "kLSSkbbMBbbkSssk",
        "kSSSkMMMMMMkSssk",
        "kSSkHBbMHBBbkssk",
        "kSSkBbbMBBbbkssk",
        "ksSkbbbMbbbbkssk",
        "kkkkkkkkkkkkkkkk",
    };

    /** The party: a hooded hero with a raised sword on a golden medallion, under a red pennant. */
    private static final String[] PARTY = {
        ".kPkkkkkkk......",
        ".kPRRRRRRk......",
        ".kPRrRRk........",
        ".kPRRRRRRk......",
        ".kPkkkkkkk......",
        ".kPk............",
        ".kPk.kkkkkk.....",
        ".kPkkhhhGGGkk...",
        "..khhhGGGGGWGk..",
        ".khhGGGBBGGWGgk.",
        ".khGGGBBBBGWGgk.",
        ".kGGGGBFFBGWGgk.",
        ".kGGGGBBBBBPGgk.",
        ".kGGGBBBBBBBGgk.",
        ".kGGGBBbBBBBGgk.",
        ".kGGBBBbBBBBBgk.",
        ".kGGBBBbBBBBBgk.",
        "..kGBBBbBBBBBk..",
        "...kBBBBBBBBk...",
        ".....kkkkkk.....",
    };
    /** Where the medallion's centre lies in the party's picture. */
    private static final double PARTY_X = 8;
    private static final double PARTY_Y = 13;

    /** An event: a golden coin with a question mark. */
    private static final String[] COIN = {
        "................",
        ".....kkkkkk.....",
        "...kkhhhhGGkk...",
        "..khhGqqqqGGgk..",
        ".khGGqqGGqqGggk.",
        ".khGGGGGGqqGggk.",
        ".khGGGGGqqGGggk.",
        ".kGGGGGqqGGGggk.",
        ".kGGGGGqqGGGggk.",
        ".kGGGGGGGGGGggk.",
        ".kGGGGGqqGGGggk.",
        ".kGGGGGqqGGgggk.",
        "..kgGGGGGGGggk..",
        "...kkggggggkk...",
        ".....kkkkkk.....",
        "................",
    };

    /** An enemy without a picture: two crossed swords (outlined when drawn). */
    private static final String[] BLADES = {
        "L..............L",
        ".L............L.",
        "..L..........L..",
        "...L........L...",
        "....L......L....",
        ".....L....L.....",
        "......L..L......",
        ".......LL.......",
        ".......LL.......",
        "......L..L......",
        "...Y.L....L.Y...",
        "....YY....YY....",
        "...D.YY..YY.D...",
        "..D..........D..",
        ".DD..........DD.",
        "................",
    };

    private static Map<Character, Integer> palette(final Object... pairs) {
        final Map<Character, Integer> p = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            p.put((Character) pairs[i], (Integer) pairs[i + 1]);
        }
        return p;
    }

    private static BufferedImage paint(final String[] rows, final Map<Character, Integer> colors) {
        final int w = rows[0].length();
        final BufferedImage img = new BufferedImage(w, rows.length, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows.length; y++) {
            if (rows[y].length() != w) {
                throw new IllegalStateException("row " + y + " is " + rows[y].length() + " wide, not " + w);
            }
            for (int x = 0; x < w; x++) {
                final char ch = rows[y].charAt(x);
                if (ch == '.') {
                    continue;
                }
                final Integer rgb = colors.get(ch);
                if (rgb == null) {
                    throw new IllegalStateException("no colour for '" + ch + "'");
                }
                img.setRGB(x, y, (rgb >>> 24) == 0 ? 0xFF000000 | rgb : rgb); // a colour may bring its own alpha
            }
        }
        return img;
    }

    /** The picture with a border of one pixel around all it shows (in a picture one pixel larger on each side). */
    private static BufferedImage outlined(final BufferedImage img, final int argb) {
        final BufferedImage out = new BufferedImage(img.getWidth() + 2, img.getHeight() + 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                if (opaque(img, x - 1, y - 1)) {
                    out.setRGB(x, y, img.getRGB(x - 1, y - 1));
                } else if (opaque(img, x - 2, y - 1) || opaque(img, x, y - 1) || opaque(img, x - 1, y - 2) || opaque(img, x - 1, y)) {
                    out.setRGB(x, y, argb);
                }
            }
        }
        return out;
    }

    private static boolean opaque(final BufferedImage img, final int x, final int y) {
        return x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight() && (img.getRGB(x, y) >>> 24) != 0;
    }

    private static int shade(final Color c, final double f) {
        return new Color((int) Math.min(255, c.getRed() * f), (int) Math.min(255, c.getGreen() * f), (int) Math.min(255, c.getBlue() * f)).getRGB() & 0xFFFFFF;
    }

    private static int lighter(final Color c, final double f) {
        return new Color((int) (c.getRed() + (255 - c.getRed()) * f), (int) (c.getGreen() + (255 - c.getGreen()) * f),
                (int) (c.getBlue() + (255 - c.getBlue()) * f)).getRGB() & 0xFFFFFF;
    }

    static synchronized BufferedImage town(final boolean besieged) {
        return CACHE.computeIfAbsent("town" + besieged, k -> {
            final BufferedImage house = paint(HOUSE, palette('k', 0x2A1E14, 'R', 0xB5452E, 'r', 0x86301F,
                    'W', besieged ? 0xD2BFA4 : 0xF2E8D0, 'w', besieged ? 0xA8957A : 0xCDBB96,
                    'y', besieged ? 0xFF8A3A : 0xFFD45A, 'D', 0x6B4423));
            if (!besieged) {
                return house;
            }
            // flames on the roof, and a red glow about it all
            final BufferedImage fire = paint(FLAMES, palette('F', 0xF2741B, 'Y', 0xFFD23F, 'f', 0xC0391B));
            final BufferedImage both = new BufferedImage(16, 16 + FLAMES_ABOVE, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = both.createGraphics();
            g.drawImage(house, 0, FLAMES_ABOVE, null);
            g.drawImage(fire, 0, 0, null);
            g.dispose();
            return outlined(both, 0x96C81E2D);
        });
    }

    /**
     * The five-colour dungeon: the stones framing its mouth in the five colours, white, blue, black, red and green
     * from either side up to the top, five gems of them on its hill, and a golden glow about it (18 pixels; its
     * mouth's middle at 9, 10).
     */
    static synchronized BufferedImage rainbowDungeon() {
        return CACHE.computeIfAbsent("rainbowDungeon", k -> {
            final String[] rows = DUNGEON.clone();
            final int[][] frame = { { 4, 8 }, { 4, 7 }, { 5, 6 }, { 6, 5 }, { 7, 5 }, { 8, 5 }, { 9, 5 }, { 10, 6 }, { 11, 7 }, { 11, 8 } };
            final char[] colors = { '1', '2', '3', '4', '5', '5', '4', '3', '2', '1' };
            for (int i = 0; i < frame.length; i++) {
                final char[] row = rows[frame[i][1]].toCharArray();
                row[frame[i][0]] = colors[i];
                rows[frame[i][1]] = new String(row);
            }
            rows[3] = "...kk12345skk...";
            return outlined(paint(rows, palette('k', 0x2A2218, 'L', 0xC4B79E, 'S', 0x9C8F7A, 's', 0x6F6454, 'M', 0x14100C, 'm', 0x2E241A,
                    '1', 0xF4EDC4, '2', 0x4F86C6, '3', 0x6A4A86, '4', 0xD8402E, '5', 0x4FB35A)), 0x90FFD23F);
        });
    }

    static synchronized BufferedImage dungeon(final boolean collapsed) {
        return CACHE.computeIfAbsent("dungeon" + collapsed, k -> collapsed
                ? paint(COLLAPSED, palette('k', 0x2A2A2A, 'L', 0xB4B4B4, 'S', 0x8C8C8C, 's', 0x626262, 'M', 0x1E1E1E,
                        'H', 0xD2D2D2, 'B', 0xA4A4A4, 'b', 0x6A6A6A))
                : paint(DUNGEON, palette('k', 0x2A2218, 'L', 0xC4B79E, 'S', 0x9C8F7A, 's', 0x6F6454, 'M', 0x14100C, 'm', 0x2E241A)));
    }

    static synchronized BufferedImage party() {
        return CACHE.computeIfAbsent("party", k -> paint(PARTY, palette('k', 0x3A2400, 'P', 0x6B4A22, 'R', 0xD8302A, 'r', 0x8E1A14,
                'h', 0xFFF1B0, 'G', 0xF2C440, 'g', 0xB07A12, 'B', 0x1E2A4A, 'b', 0x34497A, 'F', 0xF2C9A0, 'W', 0xEDEFF5)));
    }

    static synchronized BufferedImage coin() {
        return CACHE.computeIfAbsent("coin", k -> paint(COIN, palette('k', 0x5A3E00, 'h', 0xFFF1B0, 'G', 0xE8C04A, 'g', 0xB08A20, 'q', 0x5A3E00)));
    }

    static synchronized BufferedImage blades() {
        return CACHE.computeIfAbsent("blades", k -> outlined(paint(BLADES, palette('L', 0xDDE2EA, 'Y', 0xE0A100, 'D', 0x6B4423)), 0xFF1A0A0A));
    }

    /**
     * A boss's star in its colour: a five-pointed star of 16 pixels, lit from the upper left and shaded to the
     * lower right, with a dark border around it (18 pixels in all; its middle at 9, 9.3).
     */
    static synchronized BufferedImage star(final Color color) {
        return CACHE.computeIfAbsent("star" + Integer.toHexString(color.getRGB()), k -> {
            final Polygon star = new Polygon();
            for (int i = 0; i < 10; i++) {
                final double rad = i % 2 == 0 ? 8.1 : 3.3;
                final double a = Math.PI / 5 * i - Math.PI / 2;
                star.addPoint((int) Math.round((8 + rad * Math.cos(a)) * 16), (int) Math.round((8.3 + rad * Math.sin(a)) * 16));
            }
            final boolean[][] in = new boolean[18][18]; // a margin of one pixel: no row or column of the star at the edge
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    in[y + 1][x + 1] = star.contains(x * 16 + 8, y * 16 + 8);
                }
            }
            final int base = color.getRGB() & 0xFFFFFF;
            final int light = lighter(color, 0.45);
            final int shadow = shade(color, 0.6);
            final BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 1; y <= 16; y++) {
                for (int x = 1; x <= 16; x++) {
                    if (!in[y][x]) {
                        continue;
                    }
                    final int rgb;
                    if (!in[y - 1][x] || !in[y][x - 1]) {
                        rgb = light; // at the upper or left edge
                    } else if (!in[y + 1][x] || !in[y][x + 1]) {
                        rgb = shadow; // at the lower or right edge
                    } else {
                        rgb = base;
                    }
                    img.setRGB(x - 1, y - 1, 0xFF000000 | rgb);
                }
            }
            return outlined(img, 0xFF1A0A0A);
        });
    }

    /** Arzakon's star: a boss's star of a violet near black, with a ring of the five colours and a golden glow about it. */
    static synchronized BufferedImage arzakonStar() {
        final BufferedImage star = star(new Color(0x2A1238)); // before: the cache may not be filled from inside its own filling
        return CACHE.computeIfAbsent("arzakon", k -> {
            final BufferedImage ring = new BufferedImage(star.getWidth(), star.getHeight(), BufferedImage.TYPE_INT_ARGB);
            final int[] colors = { 0xFFF4EDC4, 0xFF4F86C6, 0xFF6A4A86, 0xFFD8402E, 0xFF4FB35A };
            int n = 0;
            for (int y = 0; y < star.getHeight(); y++) {
                for (int x = 0; x < star.getWidth(); x++) {
                    final int argb = star.getRGB(x, y);
                    if ((argb >>> 24) == 0) {
                        continue;
                    }
                    final boolean edge = (star.getRGB(Math.max(0, x - 1), y) >>> 24) == 0 || (star.getRGB(Math.min(star.getWidth() - 1, x + 1), y) >>> 24) == 0
                            || (star.getRGB(x, Math.max(0, y - 1)) >>> 24) == 0 || (star.getRGB(x, Math.min(star.getHeight() - 1, y + 1)) >>> 24) == 0;
                    // its border in the five colours, by turns
                    ring.setRGB(x, y, edge ? colors[n++ % colors.length] : argb);
                }
            }
            return outlined(outlined(ring, 0x90FFD23F), 0x40FFD23F);
        });
    }

    // ------------------------------------------------------------------ the labyrinth

    /** The squares of a room of the labyrinth, across: its floor and rock are pictures this size, and its walls lie on them. */
    static final int ROOM = 32;

    /** A closed chest, with a golden lock and corners; the treasure it holds floats above it (rows 0 to 5). */
    private static final String[] CHEST = {
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..kkkkkkkkkkkk..",
        ".kYWWWWWWWWWWYk.",
        ".kWwWWWWWWWWwWk.",
        ".kBBBBBYYBBBBBk.",
        ".kWWWWWYyWWWWWk.",
        ".kWwWWWyyWWWwWk.",
        ".kWWWWWWWWWWWWk.",
        ".kYwwwwwwwwwwYk.",
        ".kkkkkkkkkkkkkk.",
    };

    /** An open, empty chest (grey): its lid stands up behind it. */
    private static final String[] CHEST_EMPTY = {
        "................",
        "................",
        "................",
        "................",
        "..kkkkkkkkkkkk..",
        ".kWWWWWWWWWWWWk.",
        ".kWwWWWWWWWWwWk.",
        ".kBBBBBBBBBBBBk.",
        ".kkkkkkkkkkkkkk.",
        ".kDDDDDDDDDDDDk.",
        ".kBBBBBYYBBBBBk.",
        ".kWWWWWYyWWWWWk.",
        ".kWwWWWyyWWWwWk.",
        ".kWWWWWWWWWWWWk.",
        ".kwwwwwwwwwwwwk.",
        ".kkkkkkkkkkkkkk.",
    };

    /** What a chest holds, above it: a card, gold coins, a relic's gem. */
    private static final String[] ITEM_CARD = {
        "......kkkkk.....",
        "......kCCCk.....",
        "......kccck.....",
        "......kccck.....",
        "......kCCCk.....",
        "......kkkkk.....",
    };
    private static final String[] ITEM_GOLD = {
        ".....kkkkkk.....",
        "....kGhGGGgk....",
        "....kgGGGggk....",
        "....kGhGGGgk....",
        "....kggggggk....",
        ".....kkkkkk.....",
    };
    private static final String[] ITEM_RELIC = {
        ".......kk.......",
        "......kPPk......",
        ".....kPhPpk.....",
        ".....kPPppk.....",
        "......kPpk......",
        ".......kk.......",
    };

    /** A fountain (a shrine): a stone basin whose water, and the jet above it, has the colour of its blessing. */
    private static final String[] FOUNTAIN = {
        "......h..h......",
        ".....h.hh.h.....",
        "....C..CC..C....",
        ".......CC.......",
        "......kCCk......",
        "......kSSk......",
        ".....kkSSkk.....",
        "......kSsk......",
        "......kSsk......",
        ".kkkkkkkkkkkkkk.",
        "kLSCCCCCCCCCCSsk",
        "kLShCCCCCCcCCSsk",
        "kLSSSSSSSSSSSSsk",
        ".kLSSSSSSSSSSsk.",
        "..kkSSSSSSSSkk..",
        "...kkkkkkkkkk...",
    };

    /** The entrance: stairs up, out of the labyrinth. */
    private static final String[] STAIRS = {
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "....kmmmmmmk....",
        "...kkkkkkkkkk...",
        "...kLLLLLLLLk...",
        "...kssssssssk...",
        "..kkkkkkkkkkkk..",
        "..kLLLLLLLLLLk..",
        "..kssssssssssk..",
        ".kkkkkkkkkkkkkk.",
        ".kLLLLLLLLLLLLk.",
        ".kssssssssssssk.",
        ".kkkkkkkkkkkkkk.",
        "................",
    };

    /** A chest: closed with what it holds above it (CARD, GOLD or RELIC), or open and empty (null). */
    static synchronized BufferedImage chest(final String kind) {
        return CACHE.computeIfAbsent("chest" + kind, k -> {
            if (kind == null) {
                return paint(CHEST_EMPTY, palette('k', 0x22201C, 'W', 0x6E655A, 'w', 0x524A40, 'B', 0x3A342C,
                        'Y', 0x8A8A8A, 'y', 0x6A6A6A, 'D', 0x14120F));
            }
            final BufferedImage img = paint(CHEST, palette('k', 0x2A1A0C, 'W', 0x9A5F2C, 'w', 0x6E4220, 'B', 0x4A2E14,
                    'Y', 0xE8C04A, 'y', 0xA8801E));
            final String[] item = "CARD".equals(kind) ? ITEM_CARD : "RELIC".equals(kind) ? ITEM_RELIC : ITEM_GOLD;
            final BufferedImage above = paint(item, palette('k', 0x2A1A08, 'C', 0xF4F0E4, 'c', 0x3F6FC4,
                    'G', 0xE8C04A, 'h', "RELIC".equals(kind) ? 0xE4C8FF : 0xFFF1B0, 'g', 0xB08A20, 'P', 0x9B4FD0, 'p', 0x5E2A8A));
            final Graphics2D g = img.createGraphics();
            g.drawImage(above, 0, 0, null);
            g.dispose();
            return img;
        });
    }

    /**
     * A fountain in the colour of its blessing, with a glow of two pixels about it (20 pixels in all); dry and grey
     * once used (null), without the glow (16 pixels).
     */
    static synchronized BufferedImage fountain(final Color water) {
        return CACHE.computeIfAbsent("fountain" + (water == null ? "" : Integer.toHexString(water.getRGB())), k -> {
            final Map<Character, Integer> stone = palette('k', 0x2A2218, 'S', 0x9C9384, 's', 0x6E675C, 'L', 0xC2BAA8);
            if (water == null) {
                final String[] dry = FOUNTAIN.clone();
                for (int i = 0; i < 4; i++) {
                    dry[i] = "................";
                }
                dry[4] = "......kSSk......";
                stone.put('C', 0x2E2A26);
                stone.put('c', 0x24211E);
                stone.put('h', 0x3A3530);
                return paint(dry, stone);
            }
            stone.put('C', water.getRGB() & 0xFFFFFF);
            stone.put('c', shade(water, 0.6));
            stone.put('h', lighter(water, 0.55));
            final int glow = water.getRGB() & 0xFFFFFF;
            return outlined(outlined(paint(FOUNTAIN, stone), 0x70000000 | glow), 0x30000000 | glow);
        });
    }

    static synchronized BufferedImage stairs() {
        return CACHE.computeIfAbsent("stairs", k -> paint(STAIRS, palette('k', 0x2A2218, 'L', 0xC9BCA2, 's', 0x9A8C72, 'm', 0x0E0C0A)));
    }

    private static int mix(final Color c, final double f) {
        return f < 0 ? shade(c, 1 + f) : lighter(c, f);
    }

    /**
     * A room's floor of flagstones ({@link #ROOM} pixels square): three rows of stones of different widths, each a
     * little lighter or darker, lit at the top and left, shaded at the bottom and right, with a few specks; placed
     * by the seed, so they stay where they are.
     */
    static synchronized BufferedImage floor(final Color base, final int seed) {
        return CACHE.computeIfAbsent("floor" + Integer.toHexString(base.getRGB()) + "/" + seed, k -> {
            final BufferedImage img = new BufferedImage(ROOM, ROOM, BufferedImage.TYPE_INT_ARGB);
            final int joint = 0xFF000000 | shade(base, 0.6);
            for (int y = 0; y < ROOM; y++) {
                for (int x = 0; x < ROOM; x++) {
                    img.setRGB(x, y, joint);
                }
            }
            final java.util.Random rnd = new java.util.Random(seed);
            final int rows = 3;
            for (int row = 0; row < rows; row++) {
                final int y0 = ROOM * row / rows;
                final int y1 = ROOM * (row + 1) / rows;
                int sx = -rnd.nextInt(10);
                while (sx < ROOM) {
                    final int sw = 11 + rnd.nextInt(8);
                    final int x0 = Math.max(sx, 0);
                    final int x1 = Math.min(sx + sw, ROOM);
                    if (x1 - x0 > 3) {
                        final double tone = rnd.nextDouble() * 0.2 - 0.1;
                        final Color stone = new Color(mix(base, tone));
                        final int face = 0xFF000000 | (stone.getRGB() & 0xFFFFFF);
                        final int lit = 0xFF000000 | lighter(stone, 0.16);
                        final int dark = 0xFF000000 | shade(stone, 0.8);
                        // a joint of one pixel below and right of each stone
                        for (int y = y0; y < y1 - 1; y++) {
                            for (int x = x0; x < x1 - 1; x++) {
                                final boolean corner = (x == x0 || x == x1 - 2) && (y == y0 || y == y1 - 2);
                                if (corner) {
                                    continue; // the corners cut off: the stones look rounded
                                }
                                img.setRGB(x, y, y == y0 || x == x0 ? lit : y == y1 - 2 || x == x1 - 2 ? dark : face);
                            }
                        }
                        if (rnd.nextInt(3) == 0) {
                            img.setRGB(x0 + 2 + rnd.nextInt(Math.max(1, x1 - x0 - 4)), y0 + 2 + rnd.nextInt(Math.max(1, y1 - y0 - 4)), dark);
                        }
                    }
                    sx += sw;
                }
            }
            return img;
        });
    }

    /** Solid rock ({@link #ROOM} pixels square): dark, with a few stones in it, placed by the seed. */
    static synchronized BufferedImage rock(final Color dark, final Color stone, final int seed) {
        return CACHE.computeIfAbsent("rock" + seed, k -> {
            final BufferedImage img = new BufferedImage(ROOM, ROOM, BufferedImage.TYPE_INT_ARGB);
            final int ground = 0xFF000000 | (dark.getRGB() & 0xFFFFFF);
            for (int y = 0; y < ROOM; y++) {
                for (int x = 0; x < ROOM; x++) {
                    img.setRGB(x, y, ground);
                }
            }
            final int face = 0xFF000000 | (stone.getRGB() & 0xFFFFFF);
            final int lit = 0xFF000000 | lighter(stone, 0.07);
            final int edge = 0xFF000000 | shade(dark, 0.6);
            final java.util.Random rnd = new java.util.Random(seed);
            for (int i = 0; i < 5; i++) {
                final double rw = 3.5 + rnd.nextInt(3);
                final double rh = rw * 0.66;
                final double cx = 3 + rw + rnd.nextInt(Math.max(1, (int) (ROOM - 6 - 2 * rw)));
                final double cy = 3 + rh + rnd.nextInt(Math.max(1, (int) (ROOM - 6 - 2 * rh)));
                for (int y = (int) (cy - rh - 1); y <= cy + rh + 1; y++) {
                    for (int x = (int) (cx - rw - 1); x <= cx + rw + 1; x++) {
                        if (x < 0 || y < 0 || x >= ROOM || y >= ROOM) {
                            continue;
                        }
                        final double dx = (x + 0.5 - cx) / rw;
                        final double dy = (y + 0.5 - cy) / rh;
                        final double in = dx * dx + dy * dy;
                        if (in <= 1) {
                            img.setRGB(x, y, dy < -0.6 ? lit : face);
                        } else if (in <= 1.6 && dy > 0) {
                            img.setRGB(x, y, edge); // its shadow below
                        }
                    }
                }
            }
            return img;
        });
    }

    // ------------------------------------------------------------------ what grows and lies in a labyrinth of each colour

    private static final BufferedImage NONE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private static final String[] TREE = {
        "...kkkkkk...",
        "..kLGGGGGk..",
        ".kLLGGGGGgk.",
        "kLGGGGGGGGgk",
        "kGGGGGGGGggk",
        "kGGGGGGGgggk",
        ".kGGGGGggggk",
        "..kkggggkkk.",
        ".....kTk....",
        ".....kTk....",
        "....kTTtk...",
        "...kkkkkk...",
    };
    private static final String[] GRASS = {
        "v.V.v",
        ".vVv.",
        "vVVVv",
    };
    /** A pool: of water in blue labyrinths, of lava in red ones. */
    private static final String[] POOL = {
        "...kkkkkkkk...",
        ".kkWWwwWWWWkk.",
        "kWWWWWWwwWWWWk",
        "kWWwwWWWWWWWDk",
        "kWWWWWWWWWWDDk",
        ".kDWWWWWWWDDk.",
        "..kkDDDDDDkk..",
        "....kkkkkk....",
    };
    private static final String[] SKULL = {
        ".BBB.",
        "BBBBB",
        "BkBkB",
        "BBBBB",
        ".BkB.",
    };
    private static final String[] BONES = {
        "B.....B",
        ".BBBBB.",
        "B.....B",
    };
    /** A cobweb in a top left corner (mirrored for the right one). */
    private static final String[] WEB = {
        "WWWWWW",
        "WWW.W.",
        "WW.W..",
        "W.W.W.",
        "WW..W.",
        "W.....",
    };
    private static final String[] CANDLE = {
        ".y.",
        ".Y.",
        "kWk",
        "kWk",
        "kWk",
        "kkk",
    };
    private static final String[] PILLAR = {
        "kkkkkk",
        "kLLLSk",
        "kkkkkk",
        ".kLSk.",
        ".kLSk.",
        ".kLSk.",
        ".kLSk.",
        ".kLSk.",
        ".kLSk.",
        ".kLSk.",
        "kkkkkk",
        "kLLLSk",
        "kkkkkk",
    };
    private static final String[] CRYSTAL = {
        ".h.",
        "hCc",
        "hCc",
        "kCk",
        ".k.",
    };

    private static final int[] DX = { 1, 0, -1, 0 };
    private static final int[] DY = { 0, 1, 0, -1 };

    /** Draws a little picture onto the room's, its top left at (x, y), mirrored if asked. */
    private static void stamp(final BufferedImage img, final BufferedImage part, final int x, final int y, final boolean mirror) {
        final Graphics2D g = img.createGraphics();
        if (mirror) {
            g.drawImage(part, x + part.getWidth(), y, -part.getWidth(), part.getHeight(), null);
        } else {
            g.drawImage(part, x, y, null);
        }
        g.dispose();
    }

    private static void set(final BufferedImage img, final int x, final int y, final int argb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, argb);
        }
    }

    /** A pixel along a wall: side 0 the top, 1 the bottom, 2 the left, 3 the right; off pixels from it. */
    private static void setAlong(final BufferedImage img, final int side, final int along, final int off, final int argb) {
        switch (side) {
            case 0: set(img, along, off, argb); break;
            case 1: set(img, along, ROOM - 1 - off, argb); break;
            case 2: set(img, off, along, argb); break;
            default: set(img, ROOM - 1 - off, along, argb); break;
        }
    }

    /** A vine along a wall, with leaves, and a tendril hanging into the room. */
    private static void vine(final BufferedImage img, final Random rnd) {
        final int side = rnd.nextInt(4);
        final int len = 10 + rnd.nextInt(12);
        final int start = 2 + rnd.nextInt(ROOM - 4 - len);
        final int stem = 0xFF2F6A22;
        final int leaf = 0xFF7CC04A;
        int off = 1 + rnd.nextInt(2);
        for (int i = 0; i < len; i++) {
            if (rnd.nextInt(3) == 0) {
                off = Math.max(1, Math.min(4, off + (rnd.nextBoolean() ? 1 : -1)));
            }
            setAlong(img, side, start + i, off, stem);
            if (i % 3 == 1) {
                setAlong(img, side, start + i, off + (i % 2 == 0 ? -1 : 1), leaf);
            }
        }
        final int at = start + rnd.nextInt(len);
        final int down = 2 + rnd.nextInt(4);
        for (int j = 1; j <= down; j++) {
            setAlong(img, side, at, off + j, stem);
        }
        setAlong(img, side, at + 1, off + down, leaf);
    }

    /** A glowing crack of magma: a crooked line, its hot spots brighter, a dim red glow along it. */
    private static void crack(final BufferedImage img, final Random rnd, final int len) {
        int x = 4 + rnd.nextInt(ROOM - 8);
        int y = 4 + rnd.nextInt(ROOM - 8);
        int dir = rnd.nextInt(4);
        final int[][] pts = new int[len][];
        for (int i = 0; i < len; i++) {
            pts[i] = new int[] { x, y };
            if (rnd.nextInt(3) == 0) {
                dir = (dir + (rnd.nextBoolean() ? 1 : 3)) % 4;
            }
            x = Math.max(1, Math.min(ROOM - 2, x + DX[dir]));
            y = Math.max(1, Math.min(ROOM - 2, y + DY[dir]));
        }
        for (final int[] pt : pts) {
            for (int k = 0; k < 4; k++) {
                final int gx = pt[0] + DX[k];
                final int gy = pt[1] + DY[k];
                if (gx >= 0 && gy >= 0 && gx < ROOM && gy < ROOM && (img.getRGB(gx, gy) >>> 24) == 0) {
                    img.setRGB(gx, gy, 0x90C0391B);
                }
            }
        }
        for (int i = 0; i < pts.length; i++) {
            img.setRGB(pts[i][0], pts[i][1], i % 4 == 0 ? 0xFFFFE27A : 0xFFF2741B);
        }
    }

    /** A little puddle near a wall: an oval of water, lit at the top. */
    private static void puddle(final BufferedImage img, final Random rnd) {
        final double rw = 2 + rnd.nextInt(3);
        final double rh = 1 + rnd.nextInt(2);
        final boolean top = rnd.nextBoolean();
        final double cx = 4 + rw + rnd.nextInt((int) (ROOM - 8 - 2 * rw));
        final double cy = top ? 3 + rh : ROOM - 4 - rh;
        for (int y = (int) (cy - rh - 1); y <= cy + rh + 1; y++) {
            for (int x = (int) (cx - rw - 1); x <= cx + rw + 1; x++) {
                final double dx = (x + 0.5 - cx) / rw;
                final double dy = (y + 0.5 - cy) / rh;
                final double in = dx * dx + dy * dy;
                if (in <= 1) {
                    set(img, x, y, dy < -0.3 && dx < 0.2 ? 0xFF9AD0F5 : 0xFF2F6FC0);
                } else if (in <= 1.8 && dy > 0) {
                    set(img, x, y, 0xFF1E4A8A);
                }
            }
        }
    }

    /** A spot near a wall for a little thing w by h: x, then y. */
    private static int[] nearWall(final Random rnd, final int w, final int h) {
        switch (rnd.nextInt(4)) {
            case 0: return new int[] { 2 + rnd.nextInt(ROOM - 4 - w), 2 };
            case 1: return new int[] { 2 + rnd.nextInt(ROOM - 4 - w), ROOM - 2 - h };
            case 2: return new int[] { 2, 2 + rnd.nextInt(ROOM - 4 - h) };
            default: return new int[] { ROOM - 2 - w, 2 + rnd.nextInt(ROOM - 4 - h) };
        }
    }

    private static boolean empty(final BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * What lies on a room's floor in a labyrinth of this colour ({@link #ROOM} pixels square, to draw over the floor), or
     * null for nothing: green ones have vines, grass and, in a roomy room (empty and unguarded), a tree; red ones
     * glowing cracks of magma and pools of lava; blue ones puddles and pools; black ones bones, skulls and cobwebs;
     * white ones candles and marble pillars. Placed by the seed, so it stays.
     */
    static synchronized BufferedImage floorDecor(final HexColor color, final int seed, final boolean roomy) {
        if (color == null) {
            return null;
        }
        final BufferedImage found = CACHE.computeIfAbsent("decor" + color + "/" + seed + "/" + roomy, k -> {
            final Random rnd = new Random(seed * 7919L + color.ordinal() * 31L);
            final BufferedImage img = new BufferedImage(ROOM, ROOM, BufferedImage.TYPE_INT_ARGB);
            final boolean big = roomy && rnd.nextInt(100) < 30;
            final boolean small = rnd.nextInt(100) < 60;
            switch (color) {
                case GREEN: {
                    final BufferedImage grass = paint(GRASS, palette('v', 0x3E7A2A, 'V', 0x7CC04A));
                    if (big) {
                        final int tx = 16 + rnd.nextInt(4);
                        final int ty = 5 + rnd.nextInt(4);
                        stamp(img, grass, tx - 4, ty + 10, false);
                        stamp(img, grass, tx + 9, ty + 9, true);
                        stamp(img, paint(TREE, palette('k', 0x10240E, 'L', 0x8FD05A, 'G', 0x4E9A34, 'g', 0x2F6A22,
                                'T', 0x6B4423, 't', 0x4A2E14)), tx, ty, rnd.nextBoolean());
                    }
                    if (big || small) {
                        vine(img, rnd);
                        if (rnd.nextBoolean()) {
                            vine(img, rnd);
                        }
                    }
                    if (rnd.nextInt(100) < 40) {
                        final int[] at = nearWall(rnd, 5, 3);
                        stamp(img, grass, at[0], at[1], false);
                    }
                    break;
                }
                case RED:
                    if (big) {
                        final BufferedImage lava = outlined(paint(POOL, palette('k', 0x3A0E08, 'W', 0xF2741B, 'w', 0xFFE27A, 'D', 0xB8301A)), 0x90E0501E);
                        crack(img, rnd, 10 + rnd.nextInt(6));
                        stamp(img, lava, 8 + rnd.nextInt(7), 10 + rnd.nextInt(7), rnd.nextBoolean());
                    } else if (small) {
                        crack(img, rnd, 8 + rnd.nextInt(9));
                        if (rnd.nextInt(3) == 0) {
                            crack(img, rnd, 6 + rnd.nextInt(6));
                        }
                    }
                    break;
                case BLUE:
                    if (big) {
                        stamp(img, paint(POOL, palette('k', 0x2A2E36, 'W', 0x2F6FC0, 'w', 0x9AD0F5, 'D', 0x1E4A8A)),
                                8 + rnd.nextInt(7), 10 + rnd.nextInt(7), rnd.nextBoolean());
                    }
                    if (small) {
                        puddle(img, rnd);
                        if (rnd.nextBoolean()) {
                            puddle(img, rnd);
                        }
                    }
                    break;
                case BLACK: {
                    final BufferedImage skull = paint(SKULL, palette('B', 0xE6E0D0, 'k', 0x1A1614));
                    final BufferedImage bones = paint(BONES, palette('B', 0xD8D0BC));
                    final BufferedImage web = paint(WEB, palette('W', 0x90D8D8D8));
                    if (big) {
                        final int bx = 12 + rnd.nextInt(8);
                        final int by = 12 + rnd.nextInt(8);
                        stamp(img, bones, bx - 2, by + 5, false);
                        stamp(img, skull, bx, by, false);
                        stamp(img, skull, bx + 6, by + 3, true);
                        stamp(img, web, rnd.nextBoolean() ? 1 : ROOM - 7, 1, false);
                    } else if (small) {
                        switch (rnd.nextInt(3)) {
                            case 0: {
                                final int[] at = nearWall(rnd, 7, 3);
                                stamp(img, bones, at[0], at[1], false);
                                break;
                            }
                            case 1: {
                                final boolean right = rnd.nextBoolean();
                                stamp(img, web, right ? ROOM - 7 : 1, 1, right);
                                break;
                            }
                            default: {
                                final int[] at = nearWall(rnd, 5, 5);
                                stamp(img, skull, at[0], at[1], rnd.nextBoolean());
                                break;
                            }
                        }
                    }
                    break;
                }
                case WHITE: {
                    final BufferedImage candle = paint(CANDLE, palette('y', 0xFFF3B0, 'Y', 0xF2A32A, 'W', 0xF4EEDC, 'k', 0x3A3226));
                    if (big) {
                        final BufferedImage pillar = paint(PILLAR, palette('L', 0xF2EEE6, 'S', 0xB8B2A6, 'k', 0x3A3630));
                        final int px = 18 + rnd.nextInt(5);
                        stamp(img, pillar, px, 6 + rnd.nextInt(5), false);
                        stamp(img, candle, px - 5, 18 + rnd.nextInt(5), false);
                        stamp(img, candle, px + 8, 17 + rnd.nextInt(5), false);
                    } else if (small) {
                        final int[] at = nearWall(rnd, 3, 6);
                        stamp(img, candle, at[0], at[1], false);
                        if (rnd.nextBoolean()) {
                            final int[] other = nearWall(rnd, 3, 6);
                            stamp(img, candle, other[0], other[1], false);
                        }
                    }
                    break;
                }
                default:
                    break;
            }
            return empty(img) ? NONE : img;
        });
        return found == NONE ? null : found;
    }

    /**
     * What shows in the rock of a labyrinth of this colour (to draw over the rock), or null: moss and a vine in green
     * ones, glowing veins of lava in red ones, blue crystals in blue ones, a skull in black ones, quartz in white ones.
     */
    static synchronized BufferedImage rockDecor(final HexColor color, final int seed) {
        if (color == null) {
            return null;
        }
        final BufferedImage found = CACHE.computeIfAbsent("rockdecor" + color + "/" + seed, k -> {
            final Random rnd = new Random(seed * 104729L + color.ordinal() * 17L);
            final BufferedImage img = new BufferedImage(ROOM, ROOM, BufferedImage.TYPE_INT_ARGB);
            if (rnd.nextInt(100) >= 50) {
                return NONE;
            }
            switch (color) {
                case GREEN:
                    for (int i = 0; i < 3; i++) {
                        final int mx = 3 + rnd.nextInt(ROOM - 6);
                        final int my = 3 + rnd.nextInt(ROOM - 6);
                        for (int j = 0; j < 4; j++) {
                            set(img, mx + rnd.nextInt(3), my + rnd.nextInt(2), j % 2 == 0 ? 0xFF3E7A2A : 0xFF5E9A3A);
                        }
                    }
                    if (rnd.nextBoolean()) {
                        vine(img, rnd);
                    }
                    break;
                case RED:
                    crack(img, rnd, 14 + rnd.nextInt(10));
                    if (rnd.nextBoolean()) {
                        crack(img, rnd, 8 + rnd.nextInt(8));
                    }
                    break;
                case BLUE:
                case WHITE: {
                    final BufferedImage crystal = color == HexColor.BLUE
                            ? paint(CRYSTAL, palette('h', 0xBFE6FF, 'C', 0x4FA8E8, 'c', 0x2A6AB0, 'k', 0x10243A))
                            : paint(CRYSTAL, palette('h', 0xFFFFFF, 'C', 0xE8E2D0, 'c', 0xB8AE96, 'k', 0x4A4438));
                    final int n = 1 + rnd.nextInt(3);
                    for (int i = 0; i < n; i++) {
                        stamp(img, crystal, 3 + rnd.nextInt(ROOM - 8), 3 + rnd.nextInt(ROOM - 10), rnd.nextBoolean());
                    }
                    break;
                }
                case BLACK:
                    stamp(img, paint(SKULL, palette('B', 0x8A8478, 'k', 0x0E0C0A)), 4 + rnd.nextInt(ROOM - 10), 4 + rnd.nextInt(ROOM - 10), rnd.nextBoolean());
                    break;
                default:
                    break;
            }
            return empty(img) ? NONE : img;
        });
        return found == NONE ? null : found;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the picture with each of its pixels {@code pixel} wide (not smoothed, like the enemies' sprites), its
     * point (ax, ay), in its own pixels, at (x, y).
     */
    static void draw(final Graphics2D g, final BufferedImage img, final double x, final double y, final double pixel,
            final double ax, final double ay) {
        final int w = (int) Math.round(img.getWidth() * pixel);
        final int h = (int) Math.round(img.getHeight() * pixel);
        final int left = (int) Math.round(x - ax * pixel);
        final int top = (int) Math.round(y - ay * pixel);
        final Object before = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, left, top, w, h, null);
        if (before != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, before);
        }
    }

    /** Draws the picture centred on (cx, cy). */
    static void drawCentered(final Graphics2D g, final BufferedImage img, final double cx, final double cy, final double pixel) {
        draw(g, img, cx, cy, pixel, img.getWidth() / 2.0, img.getHeight() / 2.0);
    }

    /** Where the party's medallion centre lies in its picture: x, then y. */
    static double[] partyCentre() {
        return new double[] { PARTY_X, PARTY_Y };
    }
}
