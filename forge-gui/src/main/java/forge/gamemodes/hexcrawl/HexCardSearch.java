package forge.gamemodes.hexcrawl;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import forge.card.CardRules;
import forge.card.ICardFace;
import forge.item.PaperCard;
import forge.util.CardTranslation;

/**
 * The town's card search: does a card match what was typed? Its name, type line and rules text count (every face,
 * in English and in the language Forge shows cards in), not its reminder text; letter case does not matter.
 */
public final class HexCardSearch {
    private HexCardSearch() { }

    private static final Pattern REMINDER = Pattern.compile("\\([^)]*\\)");
    /** All a card's searchable text, lower case, by card name. */
    private static final Map<String, String> TEXT = new ConcurrentHashMap<>();

    /** Does the card match the query? An empty query matches every card; no card matches nothing. */
    public static boolean matches(final PaperCard card, final String query) {
        if (card == null) {
            return false;
        }
        final String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return q.isEmpty() || textOf(card.getRules()).contains(q);
    }

    private static String textOf(final CardRules rules) {
        return TEXT.computeIfAbsent(rules.getName(), name -> {
            final StringBuilder sb = new StringBuilder();
            for (final ICardFace face : rules.getAllFaces()) {
                if (face == null) {
                    continue;
                }
                final String faceName = face.getName();
                final String type = face.getType() == null ? "" : face.getType().toString();
                sb.append(faceName).append('\n').append(type).append('\n');
                if (face.getOracleText() != null) {
                    sb.append(face.getOracleText().replace("\\n", "\n")).append('\n');
                }
                // the same in the language Forge shows cards in (the English text if there is no translation)
                sb.append(CardTranslation.getTranslatedName(faceName)).append('\n')
                        .append(CardTranslation.getTranslatedType(faceName, type)).append('\n')
                        .append(CardTranslation.getTranslatedOracle(faceName).replace("\\n", "\n")).append('\n');
            }
            return REMINDER.matcher(sb).replaceAll("").toLowerCase(Locale.ROOT);
        });
    }
}
