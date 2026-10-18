package forge.screens.home.hexcrawl;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.DefaultListModel;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import forge.deckchooser.FDeckViewer;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gui.CardPicturePanel;
import forge.item.PaperCard;
import forge.toolbox.FButton;
import forge.toolbox.FComboBox;
import forge.toolbox.FLabel;
import forge.toolbox.FList;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * The deck and the sideboard of the heads: cards can be moved between them at any time
 * outside a fight. Everybody can look at every head; changes are made only for the own head
 * (and by the host for heads nobody plays), the same as in the towns.
 */
final class HexDeckDialog {
    private final Localizer loc = Localizer.getInstance();
    private final HexContent content = HexContent.get();
    private final HexSession session;
    private final List<String> heads = new ArrayList<>();

    private final FComboBox<String> cboPlayer;
    private final FLabel lblCounts = new FLabel.Builder().fontSize(13).fontAlign(SwingConstants.LEFT).build();
    private final FLabel lblMessage = new FLabel.Builder().fontSize(12).fontAlign(SwingConstants.LEFT).build();
    private final DefaultListModel<String> deckModel = new DefaultListModel<>();
    private final DefaultListModel<String> sideModel = new DefaultListModel<>();
    private final FList<String> lstDeck = new FList<>(deckModel);
    private final FList<String> lstSide = new FList<>(sideModel);
    private final List<String> deckNames = new ArrayList<>();
    private final List<String> sideNames = new ArrayList<>();
    private final CardPicturePanel picCard = new CardPicturePanel();
    private final FButton btnToSide = new FButton();
    private final FButton btnToDeck = new FButton();
    private final FButton btnView = new FButton();

    HexDeckDialog(final HexSession session0) {
        session = session0;
        for (final HexPlayer p : campaign().players) {
            heads.add(p.name);
        }
        cboPlayer = new FComboBox<>(heads);
        final int mine = heads.indexOf(session.getMyHead());
        if (mine >= 0) {
            cboPlayer.setSelectedIndex(mine);
        }
    }

    private String t(final String key, final Object... args) {
        return loc.getMessage(key, args);
    }

    private HexCampaign campaign() {
        return session.getCampaign();
    }

    private HexPlayer head() {
        final HexCampaign c = campaign();
        return c == null || heads.isEmpty() ? null : c.player(heads.get(Math.max(0, cboPlayer.getSelectedIndex())));
    }

    private boolean mayEdit(final HexPlayer p) {
        return p != null && !session.isFightRunning() && session.mayActFor(session.getMyHead(), p.name);
    }

    /** Shows the dialog until it is closed. The given hook is told how to refresh the open dialog. */
    void show(final java.util.function.Consumer<Runnable> refreshHook) {
        if (heads.isEmpty() || campaign() == null) {
            return;
        }
        final JPanel panel = new JPanel(new MigLayout("insets 0, gap 8, wrap 4", "[340!, fill][150!, fill][340!, fill][grow, fill]"));
        panel.setOpaque(false);

        panel.add(cboPlayer, "h 26!");
        panel.add(lblCounts, "h 26!, span 3");

        panel.add(new FLabel.Builder().text(t("lblHexDeckList")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 22!");
        panel.add(new FLabel.Builder().text("").build(), "h 22!");
        panel.add(new FLabel.Builder().text(t("lblHexSideboardList")).fontStyle(Font.BOLD).fontAlign(SwingConstants.LEFT).build(), "h 22!");
        picCard.setOpaque(false);
        panel.add(picCard, "spany 2, h 360!, w 258!, gapleft 10");

        lstDeck.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstSide.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new FScrollPane(lstDeck, true), "h 330!");
        final JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8, wrap 1", "[fill, grow]", "push[][]push"));
        buttons.setOpaque(false);
        btnToSide.setText(t("lblHexToSideboard"));
        btnToDeck.setText(t("lblHexToDeck"));
        buttons.add(btnToSide, "h 30!");
        buttons.add(btnToDeck, "h 30!");
        panel.add(buttons, "h 330!");
        panel.add(new FScrollPane(lstSide, true), "h 330!");

        btnView.setText(t("lblHexViewDeck"));
        panel.add(btnView, "h 28!");
        panel.add(lblMessage, "h 28!, span 3");

        lstDeck.addListSelectionListener(e -> {
            if (lstDeck.getSelectedIndex() >= 0) {
                lstSide.clearSelection();
                showCard(deckNames, lstDeck.getSelectedIndex());
            }
        });
        lstSide.addListSelectionListener(e -> {
            if (lstSide.getSelectedIndex() >= 0) {
                lstDeck.clearSelection();
                showCard(sideNames, lstSide.getSelectedIndex());
            }
        });
        btnToSide.addActionListener(e -> move(true));
        btnToDeck.addActionListener(e -> move(false));
        btnView.addActionListener(e -> {
            final HexPlayer p = head();
            if (p != null) {
                FDeckViewer.show(content.toDeck(p.name, p.deck));
            }
        });
        cboPlayer.addActionListener(e -> {
            lblMessage.setText("");
            lstDeck.clearSelection();
            lstSide.clearSelection();
            fill();
        });
        fill();

        final FOptionPane pane = new FOptionPane(null, t("lblHexDeckAndSideboard"), null, panel,
                Collections.singletonList(t("lblHexClose")), 0);
        refreshHook.accept(this::fill); // a guest's change arrives a moment later, with the next state from the host
        try {
            pane.setVisible(true);
        } finally {
            refreshHook.accept(null);
            pane.dispose();
        }
    }

    private void showCard(final List<String> names, final int i) {
        final PaperCard card = i >= 0 && i < names.size() ? content.getCard(names.get(i)) : null;
        if (card != null) {
            picCard.setItem(card);
        }
    }

    private void move(final boolean toSideboard) {
        final HexPlayer p = head();
        final int i = toSideboard ? lstDeck.getSelectedIndex() : lstSide.getSelectedIndex();
        final List<String> names = toSideboard ? deckNames : sideNames;
        if (p == null || i < 0 || i >= names.size()) {
            return;
        }
        if (session.isFightRunning()) {
            lblMessage.setText(t("lblHexNoChangesInFight"));
        } else if (toSideboard && p.deck.size() <= HexCampaign.MIN_DECK_SIZE) {
            lblMessage.setText(t("lblHexDeckTooSmall", String.valueOf(HexCampaign.MIN_DECK_SIZE)));
        } else if (mayEdit(p)) {
            lblMessage.setText("");
            session.moveCard(p.name, names.get(i), toSideboard);
        }
        fill();
    }

    private void fill() {
        final HexPlayer p = head();
        if (p == null) {
            return;
        }
        lblCounts.setText(t("lblHexDeckCounts", String.valueOf(p.deck.size()), String.valueOf(p.sideboard.size())));
        setList(lstDeck, deckModel, deckNames, p.deck);
        setList(lstSide, sideModel, sideNames, p.sideboard);
        final boolean editable = mayEdit(p);
        btnToSide.setEnabled(editable);
        btnToDeck.setEnabled(editable);
    }

    /** One row per card name with the number of copies; the selected row stays selected, so cards can be moved one after another. */
    private void setList(final FList<String> list, final DefaultListModel<String> model, final List<String> keys, final List<String> cards) {
        final TreeMap<String, Integer> counts = new TreeMap<>();
        for (final String card : cards) {
            counts.merge(card, 1, Integer::sum);
        }
        final List<String> lines = new ArrayList<>();
        keys.clear();
        for (final Map.Entry<String, Integer> e : counts.entrySet()) {
            final PaperCard card = content.getCard(e.getKey());
            keys.add(e.getKey());
            lines.add(e.getValue() + "x " + e.getKey() + (card == null ? "" : "   (" + card.getRules().getType() + ")"));
        }
        boolean same = lines.size() == model.getSize();
        for (int i = 0; same && i < lines.size(); i++) {
            same = lines.get(i).equals(model.get(i));
        }
        if (!same) {
            final int selected = list.getSelectedIndex();
            model.clear();
            for (final String line : lines) {
                model.addElement(line);
            }
            if (selected >= 0 && !lines.isEmpty()) {
                list.setSelectedIndex(Math.min(selected, lines.size() - 1));
            }
        }
    }
}
