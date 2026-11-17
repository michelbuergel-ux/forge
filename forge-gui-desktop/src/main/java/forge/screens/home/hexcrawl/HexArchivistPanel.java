package forge.screens.home.hexcrawl;

import static forge.screens.home.hexcrawl.HexTownStyle.GOLD_LIGHT;
import static forge.screens.home.hexcrawl.HexTownStyle.MUTED;
import static forge.screens.home.hexcrawl.HexTownStyle.TEXT;
import static forge.screens.home.hexcrawl.HexTownStyle.body;
import static forge.screens.home.hexcrawl.HexTownStyle.label;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import forge.card.CardRarity;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.gamemodes.hexcrawl.HexCardSearch;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gui.CardPicturePanel;
import forge.item.PaperCard;
import forge.toolbox.FList;
import forge.toolbox.FScrollPane;
import forge.util.CardTranslation;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * The wandering archivist's search: any card the shops know, found by its name, type or rules text (in English and in
 * the language Forge shows cards in), narrowed by colour, rarity and mana value. Each line says what the archivist takes
 * for the card; what the head cannot pay for is grey. The chosen card shows on the right.
 */
final class HexArchivistPanel extends JPanel {
    /** The most cards the list shows: a longer list helps nobody, a narrower search does. */
    static final int MAX_SHOWN = 300;
    /** The colour pills: white, blue, black, red, green, colourless, multicoloured. */
    private static final byte[] COLORS = { MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK, MagicColor.RED, MagicColor.GREEN };
    private static final String[] COLOR_KEYS = { "lblWhite", "lblBlue", "lblBlack", "lblRed", "lblGreen" };
    private static final CardRarity[] RARITIES = { CardRarity.Common, CardRarity.Uncommon, CardRarity.Rare, CardRarity.MythicRare };
    private static final String[] RARITY_KEYS = { "C", "U", "R", "M" };
    /** The mana value pills: 0–1, 2, 3, 4, 5, 6 and more. */
    private static final String[] MANA_VALUES = { "0–1", "2", "3", "4", "5", "6+" };

    private final int gold;
    private final HexTownStyle.SearchField field;
    private final Set<Integer> colors = new java.util.HashSet<>(); // 0..4 the colours, 5 colourless, 6 multicoloured
    private final Set<CardRarity> rarities = EnumSet.noneOf(CardRarity.class);
    private final Set<Integer> manaValues = new java.util.HashSet<>(); // 0..5 as MANA_VALUES
    private boolean affordableOnly;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final FList<String> list = new FList<>(model);
    private final List<PaperCard> shown = new ArrayList<>();
    private final JLabel count = label("", body(Font.PLAIN, 12.5f), MUTED);
    private final JLabel price = label("", body(Font.BOLD, 15f), GOLD_LIGHT);
    private final CardPicturePanel picture = new CardPicturePanel();
    private final Timer delay;

    HexArchivistPanel(final int gold0) {
        super(new MigLayout("insets 0, gap 8, wrap 2", "[400!, fill][230!, fill]", "[][][][top][]"));
        gold = gold0;
        setOpaque(false);
        final Localizer loc = Localizer.getInstance();
        field = new HexTownStyle.SearchField(loc.getMessage("lblHexArchivistSearch"));
        add(field, "span 2, h 34!");

        final HexTownStyle.PillTray colorTray = new HexTownStyle.PillTray();
        for (int i = 0; i < 7; i++) {
            final String text = i < 5 ? loc.getMessage(COLOR_KEYS[i]) : loc.getMessage(i == 5 ? "lblHexArchivistColorless" : "lblHexArchivistMulti");
            colorTray.add(toggle(text, colors, i), "h 26!");
        }
        add(colorTray, "span 2");

        final JPanel row = new JPanel(new MigLayout("insets 0, gap 8"));
        row.setOpaque(false);
        final HexTownStyle.PillTray rarityTray = new HexTownStyle.PillTray();
        for (int i = 0; i < RARITIES.length; i++) {
            final CardRarity rarity = RARITIES[i];
            final HexTownStyle.Pill pill = new HexTownStyle.Pill(loc.getMessage("lblHexArchivistRarity_" + RARITY_KEYS[i]));
            pill.addActionListener(e -> {
                if (!rarities.remove(rarity)) {
                    rarities.add(rarity);
                }
                pill.setActive(rarities.contains(rarity));
                refresh();
            });
            rarityTray.add(pill, "h 26!");
        }
        row.add(rarityTray);
        final HexTownStyle.PillTray affordTray = new HexTownStyle.PillTray();
        final HexTownStyle.Pill afford = new HexTownStyle.Pill(loc.getMessage("lblHexArchivistAfford"));
        afford.addActionListener(e -> {
            affordableOnly = !affordableOnly;
            afford.setActive(affordableOnly);
            refresh();
        });
        affordTray.add(afford, "h 26!");
        row.add(affordTray);
        add(row, "span 2");

        final JPanel manaRow = new JPanel(new MigLayout("insets 0, gap 8, aligny center", "[][]"));
        manaRow.setOpaque(false);
        final JLabel manaLabel = label(loc.getMessage("lblHexArchivistManaValue"), body(Font.PLAIN, 13f), MUTED);
        manaLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 4, 2)); // room for the descenders
        manaRow.add(manaLabel, "w pref!, gapright 4");
        final HexTownStyle.PillTray manaTray = new HexTownStyle.PillTray();
        manaTray.setToolTipText(loc.getMessage("lblHexArchivistManaValue"));
        for (int i = 0; i < MANA_VALUES.length; i++) {
            final HexTownStyle.Pill pill = toggle(MANA_VALUES[i], manaValues, i);
            pill.setToolTipText(loc.getMessage("lblHexArchivistManaValue"));
            manaTray.add(pill, "h 26!");
        }
        manaRow.add(manaTray);
        add(manaRow, "span 2");
        add(count, "span 2, gaptop 2");

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final ListCellRenderer<? super String> base = list.getCellRenderer();
        list.setCellRenderer((l, value, index, selected, focus) -> {
            final Component c = base.getListCellRendererComponent(l, value, index, selected, focus);
            if (index >= 0 && index < shown.size() && !selected && !affordable(shown.get(index))) {
                c.setForeground(new Color(TEXT.getRed(), TEXT.getGreen(), TEXT.getBlue(), 110)); // too dear: grey
            }
            return c;
        });
        list.addListSelectionListener(e -> showSelected());
        add(new FScrollPane(list, true), "h 330!");
        final JPanel right = new JPanel(new MigLayout("insets 0, gap 6, wrap 1", "[fill]", "[322!][]"));
        right.setOpaque(false);
        picture.setOpaque(false);
        right.add(picture);
        right.add(price, "alignx center");
        add(right);

        delay = new Timer(180, e -> refresh());
        delay.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(final DocumentEvent e) {
                delay.restart();
            }

            @Override
            public void removeUpdate(final DocumentEvent e) {
                delay.restart();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                delay.restart();
            }
        });
        refresh();
    }

    /** A pill that adds its number to the set, or takes it out again, and searches anew. */
    private HexTownStyle.Pill toggle(final String text, final Set<Integer> set, final int value) {
        final HexTownStyle.Pill pill = new HexTownStyle.Pill(text);
        pill.addActionListener(e -> {
            if (!set.remove(value)) {
                set.add(value);
            }
            pill.setActive(set.contains(value));
            refresh();
        });
        return pill;
    }

    /** The card chosen in the list, or null. */
    PaperCard selected() {
        final int i = list.getSelectedIndex();
        return i >= 0 && i < shown.size() ? shown.get(i) : null;
    }

    /** Typed into the field (for a picture of the window). */
    void search(final String text) {
        field.setText(text);
        refresh();
    }

    private boolean affordable(final PaperCard card) {
        return HexContent.get().archivistPrice(card.getName()) <= gold;
    }

    /** Does the card fit the colour, rarity and mana value pills (none chosen in a group: any)? */
    private boolean fitsFilters(final PaperCard card) {
        if (!rarities.isEmpty() && !rarities.contains(card.getRarity())) {
            return false;
        }
        if (!manaValues.isEmpty()) {
            final int mv = card.getRules().getManaCost().getCMC();
            final int pill = mv <= 1 ? 0 : Math.min(5, mv - 1);
            if (!manaValues.contains(pill)) {
                return false;
            }
        }
        if (!colors.isEmpty()) {
            final ColorSet color = card.getRules().getColor();
            boolean any = colors.contains(5) && color.isColorless() || colors.contains(6) && color.isMulticolor();
            for (int i = 0; i < COLORS.length && !any; i++) {
                any = colors.contains(i) && color.hasAnyColor(COLORS[i]);
            }
            if (!any) {
                return false;
            }
        }
        return !affordableOnly || affordable(card);
    }

    /** Searches anew: the cards that match, those whose name matches first, in name order; at most MAX_SHOWN of them. */
    private void refresh() {
        final PaperCard before = selected();
        final String query = field.getText() == null ? "" : field.getText().trim().toLowerCase(Locale.ROOT);
        final List<PaperCard> found = new ArrayList<>();
        for (final PaperCard card : HexContent.get().archivistCards()) {
            if (fitsFilters(card) && HexCardSearch.matches(card, query)) {
                found.add(card);
            }
        }
        final Comparator<PaperCard> byName = Comparator.comparing(c -> CardTranslation.getTranslatedName(c.getName()).toLowerCase(Locale.ROOT));
        found.sort(Comparator.<PaperCard>comparingInt(c -> nameRank(c, query)).thenComparing(byName));
        shown.clear();
        shown.addAll(found.subList(0, Math.min(MAX_SHOWN, found.size())));
        model.clear();
        for (final PaperCard card : shown) {
            model.addElement(CardTranslation.getTranslatedName(card.getName()) + "  ·  "
                    + Localizer.getInstance().getMessage("lblHexArchivistPrice", String.valueOf(HexContent.get().archivistPrice(card.getName()))));
        }
        final Localizer loc = Localizer.getInstance();
        count.setText(found.isEmpty() ? loc.getMessage("lblHexArchivistNone")
                : found.size() > MAX_SHOWN ? loc.getMessage("lblHexArchivistFoundMany", String.valueOf(found.size()), String.valueOf(MAX_SHOWN))
                : loc.getMessage("lblHexArchivistFound", String.valueOf(found.size())));
        final int keep = before == null ? -1 : shown.indexOf(before);
        if (!shown.isEmpty()) {
            list.setSelectedIndex(Math.max(0, keep));
            list.ensureIndexIsVisible(Math.max(0, keep));
        }
        showSelected();
    }

    /** 0 if the card's name begins with what was typed, 1 if it holds it, 2 otherwise (its type or rules text matched). */
    private static int nameRank(final PaperCard card, final String query) {
        if (query.isEmpty()) {
            return 0;
        }
        final String english = card.getName().toLowerCase(Locale.ROOT);
        final String shownName = CardTranslation.getTranslatedName(card.getName()).toLowerCase(Locale.ROOT);
        if (english.startsWith(query) || shownName.startsWith(query)) {
            return 0;
        }
        return english.contains(query) || shownName.contains(query) ? 1 : 2;
    }

    private void showSelected() {
        final PaperCard card = selected();
        if (card == null) {
            price.setText(" ");
            return;
        }
        try {
            picture.setItem(card);
        } catch (final RuntimeException e) {
            // a picture that cannot be drawn leaves the search usable
            System.err.println("Hex Campaign: no picture of " + card.getName() + ": " + e);
        }
        final int p = HexContent.get().archivistPrice(card.getName());
        price.setText(Localizer.getInstance().getMessage("lblHexArchivistPrice", String.valueOf(p)));
        price.setForeground(p <= gold ? GOLD_LIGHT : HexTownStyle.BAD);
    }
}
