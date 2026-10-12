package forge.screens.home.hexcrawl;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import forge.gamemodes.hexcrawl.HexContent;

/**
 * The little pictures of the enemies on the hex map, cut out of the sprite sheets of the
 * Adventure mode: the first frame of the "Idle" animation, or the "Avatar" picture.
 */
final class HexSprites {
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final BufferedImage NONE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private HexSprites() { }

    /** The picture of the named enemy, or null if there is none. */
    static synchronized BufferedImage get(final String enemyName) {
        BufferedImage img = CACHE.get(enemyName);
        if (img == null) {
            img = load(enemyName);
            CACHE.put(enemyName, img == null ? NONE : img);
        }
        return img == NONE ? null : img;
    }

    private static BufferedImage load(final String enemyName) {
        try {
            final File atlas = HexContent.get().getSpriteAtlas(enemyName);
            if (atlas == null) {
                return null;
            }
            final List<String> lines = Files.readAllLines(atlas.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return null;
            }
            int[] idle = null;
            int[] avatar = null;
            String sheet = null;
            for (int i = 0; i < lines.size(); i++) {
                final String line = lines.get(i).trim();
                if (sheet == null && !line.isEmpty()) {
                    sheet = line; // the first line names the picture file
                    continue;
                }
                final boolean isIdle = line.equals("Idle");
                if ((isIdle && idle == null) || (line.equals("Avatar") && avatar == null)) {
                    final int[] region = region(lines, i + 1);
                    if (isIdle) {
                        idle = region;
                    } else {
                        avatar = region;
                    }
                }
            }
            final int[] r = idle != null ? idle : avatar;
            final File png = sheet == null ? null : new File(atlas.getParentFile(), sheet);
            if (r == null || png == null || !png.isFile()) {
                return null;
            }
            final BufferedImage all = ImageIO.read(png);
            if (all == null || r[0] < 0 || r[1] < 0 || r[2] <= 0 || r[3] <= 0
                    || r[0] + r[2] > all.getWidth() || r[1] + r[3] > all.getHeight()) {
                return null;
            }
            return all.getSubimage(r[0], r[1], r[2], r[3]);
        } catch (final Exception e) {
            return null; // the map works without pictures
        }
    }

    /** Reads "xy: x, y" and "size: w, h" from the lines after a region name. */
    private static int[] region(final List<String> lines, final int from) {
        final int[] r = { -1, -1, -1, -1 };
        for (int i = from; i < Math.min(lines.size(), from + 6); i++) {
            final String line = lines.get(i).trim();
            if (line.startsWith("xy:")) {
                final String[] p = line.substring(3).split(",");
                r[0] = Integer.parseInt(p[0].trim());
                r[1] = Integer.parseInt(p[1].trim());
            } else if (line.startsWith("size:")) {
                final String[] p = line.substring(5).split(",");
                r[2] = Integer.parseInt(p[0].trim());
                r[3] = Integer.parseInt(p[1].trim());
            } else if (!line.contains(":")) {
                break; // the next region begins
            }
        }
        return r[0] < 0 || r[2] < 0 ? null : r;
    }
}
