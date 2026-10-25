package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_EDGE;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT_BRIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.caps;
import static forge.screens.home.hexcrawl.HexTownStyle.decorative;
import static forge.screens.home.hexcrawl.HexTownStyle.display;
import static forge.screens.home.hexcrawl.HexTownStyle.label;
import static forge.screens.home.hexcrawl.HexTownStyle.tracked;
import static forge.screens.home.hexcrawl.HexTownStyle.wrapped;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexTile;
import forge.localinstance.properties.ForgeConstants;
import forge.screens.home.hexcrawl.HexTownStyle.Banner;
import forge.screens.home.hexcrawl.HexTownStyle.GoldButton;
import forge.screens.home.hexcrawl.HexTownStyle.Glyph;
import forge.screens.home.hexcrawl.HexTownStyle.Palette;
import forge.screens.home.hexcrawl.HexTownStyle.ShadowLabel;
import forge.screens.home.hexcrawl.HexTownStyle.WindowPanel;
import forge.util.Localizer;
import forge.view.FDialog;
import net.miginfocom.swing.MigLayout;

/**
 * The people of a freed town thank the party: their picture, and what the party gets from now on
 * (the town's shops sell cheaper for the rest of the cycle). Shown once on every screen.
 */
final class HexThanksDialog {
    static final String PICTURE = ForgeConstants.RES_DIR + "hexcrawl" + File.separator + "images" + File.separator + "hexmap-townsfolk.png";
    private static final int TEXT_H = 150;

    private static BufferedImage picture;
    private static boolean pictureRead;

    private final Localizer loc = Localizer.getInstance();
    private final String town;
    private final String besieger;
    private final Palette palette;
    private FDialog dialog;

    HexThanksDialog(final HexCampaign campaign, final String town0, final String besieger0) {
        town = town0;
        besieger = besieger0;
        HexColor color = HexColor.COLORLESS;
        for (final HexTile t : campaign.map.tiles.values()) {
            if (town0 != null && town0.equals(t.name)) {
                color = t.color;
            }
        }
        palette = HexTownStyle.palette(color);
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    /** The townsfolk's picture, read once; null if it is missing (the window then shows only the words). */
    static synchronized BufferedImage picture() {
        if (!pictureRead) {
            pictureRead = true;
            try {
                final File file = new File(PICTURE);
                picture = file.isFile() ? ImageIO.read(file) : null;
            } catch (final Exception e) {
                picture = null;
            }
        }
        return picture;
    }

    /** Shows the window until it is closed. */
    void show() {
        final Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        final BufferedImage pic = picture();
        int picW = pic == null ? 0 : Math.min(pic.getWidth(), screen.width - 40 - 48);
        int picH = pic == null ? 0 : picW * pic.getHeight() / pic.getWidth();
        final int room = screen.height - 40 - (Banner.CLOTH + 10) - TEXT_H;
        if (pic != null && picH > room) {
            picH = Math.max(120, room);
            picW = picH * pic.getWidth() / pic.getHeight();
        }
        final int width = Math.max(620, picW + 48);
        final int height = (Banner.CLOTH + 10) + (pic == null ? 0 : picH + 12) + TEXT_H;
        dialog = new FDialog(true, false, "0");
        dialog.getTitleBar().setVisible(false);
        dialog.add(build(width, height, picW, picH), "w 100%!, h 100%!");
        dialog.setSize(width, height);
        try {
            dialog.setVisible(true);
        } finally {
            dialog.dispose();
        }
    }

    /** The whole window, laid out for the given size. */
    JPanel build(final int width, final int height, final int picW, final int picH) {
        final WindowPanel root = new WindowPanel(palette, new MigLayout("insets 0, gap 0, wrap 1, fill", "[grow, fill]", "[][][grow, fill]"));
        root.add(buildBanner(), "h " + (Banner.CLOTH + 10) + "!");
        final BufferedImage pic = picture();
        if (pic != null && picW > 0) {
            root.add(new Picture(pic), "w " + picW + "!, h " + picH + "!, alignx center, gaptop 4, gapbottom 8");
        }
        final JPanel words = new JPanel(new MigLayout("insets 0 26 16 26, gap 0, wrap 1, fill", "[grow, fill]", "[grow][]"));
        words.setOpaque(false);
        words.add(wrapped(t("lblHexThanksText", besieger, town, String.valueOf(HexCampaign.SIEGE_DISCOUNT_PERCENT)),
                body(Font.PLAIN, 15f), TEXT, width - 52), "aligny top");
        final GoldButton go = new GoldButton(t("lblHexThanksButton"), GoldButton.Kind.GOLD, 15f);
        go.addActionListener(e -> dialog.setVisible(false));
        words.add(go, "alignx center, w 240!, h 44!");
        root.add(words, "wmin 0, hmin 0");
        root.setPreferredSize(new Dimension(width, height));
        return root;
    }

    private JPanel buildBanner() {
        final Banner banner = new Banner(palette, new MigLayout("insets 0 26 10 22, gap 18, aligny center", "[]push[]", "[" + Banner.CLOTH + "!]"));
        final JPanel names = new JPanel(new MigLayout("insets 0, gap 2, wrap 1"));
        names.setOpaque(false);
        names.add(new ShadowLabel(t("lblHexThanksTitle", town), decorative(27f), TEXT_BRIGHT));
        names.add(label(caps(t("lblHexThanksSubtitle")), tracked(display(13f), 0.15f), palette.light()));
        banner.add(names);
        final GoldButton close = new GoldButton("", GoldButton.Kind.OUTLINE, 13f).withGlyph(Glyph.CLOSE);
        close.setToolTipText(t("lblHexClose"));
        close.addActionListener(e -> {
            if (dialog != null) {
                dialog.setVisible(false);
            }
        });
        banner.add(close, "w 44!, h 44!");
        // the banner carries the window around
        final MouseAdapter drag = new MouseAdapter() {
            private Point start;
            private Point origin;

            @Override
            public void mousePressed(final MouseEvent e) {
                if (dialog != null) {
                    start = e.getLocationOnScreen();
                    origin = dialog.getLocation();
                }
            }

            @Override
            public void mouseDragged(final MouseEvent e) {
                if (start != null) {
                    final Point now = e.getLocationOnScreen();
                    dialog.setLocation(origin.x + now.x - start.x, origin.y + now.y - start.y);
                }
            }
        };
        banner.addMouseListener(drag);
        banner.addMouseMotionListener(drag);
        return banner;
    }

    /** The picture, scaled to the room it gets, in a thin gilded frame. */
    private static final class Picture extends JComponent {
        private final BufferedImage image;

        Picture(final BufferedImage image0) {
            image = image0;
        }

        @Override
        protected void paintComponent(final Graphics g0) {
            final Graphics2D g = (Graphics2D) g0.create();
            final boolean exact = getWidth() == image.getWidth() && getHeight() == image.getHeight();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    exact ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            g.setColor(GOLD_EDGE);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(1, 1, getWidth() - 2, getHeight() - 2);
            g.dispose();
        }
    }
}
