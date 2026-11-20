package forge.gamemodes.hexcrawl;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.card.CardAiHints;
import forge.card.CardRules;
import forge.card.DeckHints;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * A developer's look at the shop themes, for audits: per theme how many cards it holds and which of them fit only
 * through the AI's deck hints (not by type or rules text), with those hints. Hidden: written only on request (see
 * HexThemeReportTest, run with -Dhexcrawl.themeReport=FILE).
 */
public final class HexThemeReport {
    private HexThemeReport() {
    }

    public static void write(final Path file) throws IOException {
        final List<CardRules> cards = new ArrayList<>();
        for (final PaperCard card : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            cards.add(card.getRules());
        }
        cards.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("Hydra Adventure: shop themes, " + cards.size() + " cards\n");
            out.write("\"hint only\": cards that fit only through the AI's deck hints, not by type or rules text.\n\n");
            for (final HexTheme theme : HexTheme.all()) {
                int fitting = 0;
                final List<CardRules> weak = new ArrayList<>();
                for (final CardRules rules : cards) {
                    if (theme.fits(rules)) {
                        fitting++;
                        if (theme.onlyByHints(rules)) {
                            weak.add(rules);
                        }
                    }
                }
                out.write(String.format(Locale.ROOT, "== %s (%s): %d cards, %d hint only%n", theme.getLabel(), theme.id, fitting, weak.size()));
                for (final CardRules rules : weak) {
                    out.write("   " + rules.getName() + "   [" + hintsOf(rules.getAiHints()) + "]\n");
                }
            }
        }
    }

    private static String hintsOf(final CardAiHints hints) {
        final StringBuilder text = new StringBuilder();
        append(text, "Has", hints.getDeckHas());
        append(text, "Hints", hints.getDeckHints());
        append(text, "Needs", hints.getDeckNeeds());
        return text.toString();
    }

    private static void append(final StringBuilder text, final String label, final DeckHints h) {
        if (h != null && h.isValid()) {
            text.append(text.length() == 0 ? "" : "; ").append(label).append(": ").append(filtersOf(h));
        }
    }

    /** The parsed hints ("ABILITY=Discard, TYPE=Elf"); DeckHints keeps them to itself, and this is a developer's report. */
    private static String filtersOf(final DeckHints h) {
        try {
            final java.lang.reflect.Field field = DeckHints.class.getDeclaredField("filters");
            field.setAccessible(true);
            final Object filters = field.get(h);
            return filters == null ? "-" : filters.toString().replace("(", "").replace(")", "").replace(",", "=").replace("]=", "],");
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return "?";
        }
    }
}
