package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.GOLD;
import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.caps;
import static forge.screens.home.hexcrawl.HexTownStyle.display;
import static forge.screens.home.hexcrawl.HexTownStyle.label;
import static forge.screens.home.hexcrawl.HexTownStyle.tracked;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.FutureTask;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;

import forge.gui.CardPicturePanel;
import forge.gui.FThreads;
import forge.item.InventoryItem;
import forge.item.PaperToken;
import forge.model.FModel;
import forge.toolbox.FOptionPane;
import net.miginfocom.swing.MigLayout;

/** Card pictures in the reward and event dialogs: the rewards are taken by clicking them, the gold as a Gold token. */
final class HexRewardCards {
    static final int W = 230;
    static final int H = 322;
    private static final String GOLD_TOKEN = "c_a_gold_sac";
    private static final String CHOICE = "hexChoice";
    /** Card clicks come back above the buttons' results. */
    private static final int CLICKED = 1000;

    private HexRewardCards() {
    }

    /** The Gold token's picture, or null if the token is unknown. */
    static PaperToken goldToken() {
        try {
            return FModel.getMagicDb().getAllTokens().getToken(GOLD_TOKEN);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * A card picture (item may be null: a blank card) with an optional line over it (the gold's amount).
     * A clickable one glows under the mouse and is taken by a click (see choose).
     */
    static JComponent card(final InventoryItem item, final String overlay, final boolean clickable) {
        final JPanel stack = new JPanel() {
            @Override
            public boolean isOptimizedDrawingEnabled() {
                return false; // the overlay lies on the picture
            }
        };
        stack.setLayout(new OverlayLayout(stack));
        stack.setOpaque(false);
        final Overlay top = new Overlay(item == null, overlay, clickable);
        stack.add(top);
        final CardPicturePanel picture = new CardPicturePanel();
        picture.setOpaque(false);
        if (item != null) {
            picture.setItem(item);
        }
        stack.add(picture);
        return stack;
    }

    /** A card picture with a caption above it (the head that got it). */
    static JComponent captioned(final String caption, final JComponent card, final int w, final int h) {
        final JPanel column = new JPanel(new MigLayout("insets 0, gap 0, wrap 1"));
        column.setOpaque(false);
        column.add(label(caps(caption), tracked(display(12f), 0.16f), GOLD_LIGHT), "alignx center");
        column.add(card, "w " + w + "!, h " + h + "!");
        return column;
    }

    /**
     * Shows the dialog: a click on the n-th of the clickable cards gives n, the buttons (if any) come after them;
     * closing the dialog gives -1.
     */
    static int choose(final String message, final String title, final JComponent pictures, final List<JComponent> clickable,
            final List<String> buttons) {
        for (int i = 0; i < clickable.size(); i++) {
            clickable.get(i).putClientProperty(CHOICE, i);
        }
        final FutureTask<Integer> future = new FutureTask<>(() -> {
            final FOptionPane pane = new FOptionPane(message, title, null, pictures, buttons, 0);
            pane.setVisible(true);
            final int result = pane.getResult();
            pane.dispose();
            if (result < 0) {
                return -1;
            }
            return result >= CLICKED ? result - CLICKED : clickable.size() + result;
        });
        FThreads.invokeInEdtAndWait(future);
        try {
            return future.get();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Lies on a card picture: the hover glow, the line over it, a blank card's face; takes the clicks. */
    private static final class Overlay extends JComponent {
        private final boolean blank;
        private final String text;
        private final boolean clickable;
        private boolean hover;

        Overlay(final boolean blank, final String text, final boolean clickable) {
            this.blank = blank;
            this.text = text;
            this.clickable = clickable;
            setOpaque(false);
            if (clickable) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                final MouseAdapter mouse = new MouseAdapter() {
                    @Override
                    public void mouseEntered(final MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(final MouseEvent e) {
                        hover = false;
                        repaint();
                    }

                    @Override
                    public void mouseReleased(final MouseEvent e) {
                        // on release, not click: a click is lost when the mouse moves a little while pressed
                        if (SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint())) {
                            take();
                        }
                    }
                };
                addMouseListener(mouse);
            }
        }

        private void take() {
            final Object index = getParent() instanceof JComponent ? ((JComponent) getParent()).getClientProperty(CHOICE) : null;
            final Window window = SwingUtilities.getWindowAncestor(this);
            if (index instanceof Integer && window instanceof FOptionPane) {
                ((FOptionPane) window).setResult(CLICKED + (Integer) index);
            }
        }

        @Override
        protected void paintComponent(final Graphics g0) {
            final Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            final int w = getWidth();
            final int h = getHeight();
            final int arc = Math.max(8, w / 18);
            if (blank) {
                g.setPaint(new GradientPaint(0, 0, new Color(0x6b5320), 0, h, new Color(0x2e2410)));
                g.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);
                g.setColor(GOLD);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(2, 2, w - 5, h - 5, arc, arc);
            }
            if (text != null) {
                // the amount on a dark band across the middle of the card
                float size = Math.max(16f, w / 8.5f);
                g.setFont(display(size).deriveFont(Font.BOLD));
                while (size > 10f && g.getFontMetrics().stringWidth(text) > w - w / 6 - 16) {
                    size -= 1f;
                    g.setFont(display(size).deriveFont(Font.BOLD));
                }
                final FontMetrics fm = g.getFontMetrics();
                final int bandH = fm.getHeight() + 14;
                final int bandY = h * 11 / 20 - bandH / 2;
                g.setColor(new Color(0, 0, 0, 175));
                g.fillRoundRect(w / 12, bandY, w - w / 6, bandH, bandH / 2, bandH / 2);
                g.setColor(GOLD);
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(w / 12, bandY, w - w / 6, bandH, bandH / 2, bandH / 2);
                final int tx = (w - fm.stringWidth(text)) / 2;
                final int ty = bandY + (bandH - fm.getHeight()) / 2 + fm.getAscent();
                g.setColor(new Color(0, 0, 0, 200));
                g.drawString(text, tx + 2, ty + 2);
                g.setColor(GOLD_LIGHT);
                g.drawString(text, tx, ty);
            }
            if (clickable && hover) {
                g.setColor(new Color(255, 230, 150, 40)); // a light wash over the card
                g.fillRoundRect(0, 0, w, h, arc, arc);
                for (int i = 0; i < 7; i++) {
                    g.setColor(new Color(GOLD_LIGHT.getRed(), GOLD_LIGHT.getGreen(), GOLD_LIGHT.getBlue(), 255 - i * 34));
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRoundRect(i, i, w - 1 - 2 * i, h - 1 - 2 * i, arc, arc);
                }
            }
            g.dispose();
        }
    }
}
