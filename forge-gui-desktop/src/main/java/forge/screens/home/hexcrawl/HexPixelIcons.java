package forge.screens.home.hexcrawl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

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
                img.setRGB(x, y, 0xFF000000 | rgb);
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
