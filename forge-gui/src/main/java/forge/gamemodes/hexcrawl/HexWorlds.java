package forge.gamemodes.hexcrawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import forge.StaticData;
import forge.card.CardDb;
import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;

/**
 * The worlds of the shop themes that are not Magic's own planes (Marvel, Fallout, The Lord of the Rings, ...): the
 * cards of their sets and Secret Lair drops (res/hexcrawl/worlds.txt).
 *
 * <p>A card belongs to a world if it is new in one of the world's printings, or shows new art there: Fallout's
 * Sol Ring with Vault Boy belongs to Fallout, a Commander set's reprint with its old picture does not. The shops show
 * a world's cards in the world's printing.
 */
public final class HexWorlds {
    private HexWorlds() { }

    /** The worlds, in the order of the shop themes (their ids are the themes' ids). */
    public static final List<String> IDS = List.of("marvel", "startrek", "warhammer40k", "dnd", "lotr", "doctorwho", "fallout",
            "assassinscreed", "finalfantasy", "tmnt", "avatar", "universesbeyond");

    public static final String FILE = ForgeConstants.RES_DIR + "hexcrawl" + File.separator + "worlds.txt";

    /** A card printed a little before its set (a prerelease promo) is still new in it. */
    private static final long NEW_MARGIN = TimeUnit.DAYS.toMillis(90);

    /** One source of a world: a set, or only some of its cards (by collector number; null for all). */
    private record Source(String code, Set<String> numbers) { }

    /** World id -> card name -> the card's printing in the world. */
    private static Map<String, Map<String, PaperCard>> worlds;

    /** Is this a world of {@link #IDS}? */
    public static boolean isWorld(final String id) {
        return id != null && IDS.contains(id);
    }

    /** Does the card belong to the world? */
    public static boolean belongsTo(final String world, final String cardName) {
        return cardName != null && cards(world).containsKey(cardName);
    }

    /** The card's printing in the world (the picture the shop shows), or null if it does not belong to it. */
    public static PaperCard printing(final String world, final String cardName) {
        return cardName == null ? null : cards(world).get(cardName);
    }

    /** The cards of a world, by name (empty for no such world). */
    public static Map<String, PaperCard> cards(final String world) {
        final Map<String, Map<String, PaperCard>> all = load();
        final Map<String, PaperCard> cards = all.get(world);
        return cards == null ? Collections.emptyMap() : cards;
    }

    private static synchronized Map<String, Map<String, PaperCard>> load() {
        if (worlds != null) {
            return worlds;
        }
        final StaticData data = StaticData.instance();
        if (data == null) {
            return Collections.emptyMap(); // too early: try again later
        }
        final Map<String, Map<String, PaperCard>> result = new HashMap<>();
        for (final Map.Entry<String, List<Source>> world : read(FILE).entrySet()) {
            final Map<String, PaperCard> cards = new LinkedHashMap<>();
            for (final Source source : world.getValue()) {
                collect(data, source, cards);
            }
            result.put(world.getKey(), Collections.unmodifiableMap(cards));
        }
        worlds = result;
        return worlds;
    }

    /** Reads the file: "[world]", then per line a set code, alone or with collector numbers; "#" comments. */
    static Map<String, List<Source>> read(final String path) {
        final Map<String, List<Source>> result = new LinkedHashMap<>();
        final File file = new File(path);
        if (!file.isFile()) {
            System.err.println("Hex Campaign: no worlds file " + path);
            return result;
        }
        List<Source> current = null;
        try (BufferedReader in = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    current = result.computeIfAbsent(line.substring(1, line.length() - 1).trim(), k -> new ArrayList<>());
                    continue;
                }
                if (current == null) {
                    continue;
                }
                final String[] parts = line.split("\\s+");
                final Set<String> numbers = parts.length > 1 ? new HashSet<>(Arrays.asList(parts).subList(1, parts.length)) : null;
                current.add(new Source(parts[0], numbers));
            }
        } catch (final IOException e) {
            System.err.println("Hex Campaign: cannot read " + path + ": " + e);
        }
        return result;
    }

    /** Adds the cards of one source that belong to the world (new there, or with new art); the first printing found stays. */
    private static void collect(final StaticData data, final Source source, final Map<String, PaperCard> cards) {
        final CardEdition edition = data.getEditions().get(source.code());
        if (edition == null) {
            return; // a set this Forge does not know
        }
        final CardDb db = data.getCommonCards();
        for (final CardEdition.EditionEntry e : edition.getAllCardsInSet()) {
            if (source.numbers() != null && !source.numbers().contains(e.collectorNumber())) {
                continue;
            }
            final PaperCard card = db.getCard(e.name(), source.code(), e.collectorNumber());
            if (card == null || card.getRules() == null || card.getRules().getType().isBasicLand() || cards.containsKey(card.getName())) {
                continue;
            }
            if (isNewOrNewArt(data, db, card, edition)) {
                cards.put(card.getName(), card);
            }
        }
    }

    private static boolean isNewOrNewArt(final StaticData data, final CardDb db, final PaperCard card, final CardEdition edition) {
        PaperCard first = null;
        Date firstDate = null;
        for (final PaperCard other : db.getAllCards(card.getName())) {
            final CardEdition ed = data.getEditions().get(other.getEdition());
            if (ed == null || ed.getDate() == null) {
                continue;
            }
            if (first == null || ed.getDate().before(firstDate)) {
                first = other;
                firstDate = ed.getDate();
            }
        }
        if (first == null || edition.getDate() == null || firstDate.getTime() >= edition.getDate().getTime() - NEW_MARGIN) {
            return true; // first printed here
        }
        final String artist = card.getArtist();
        return artist != null && !artist.isEmpty() && !artist.equalsIgnoreCase(first.getArtist());
    }
}
