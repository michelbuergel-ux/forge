package forge.gamemodes.hexcrawl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import forge.ImageKeys;
import forge.StaticData;
import forge.card.CardDb;
import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.util.FileUtil;

/**
 * The Adventure mode's own cards (the bosses' presences, items that work from the command zone)
 * and the campaign's boss phase cards, added to the card database once. The mobile Adventure
 * mode does the same in its Config.loadResources; the desktop has no such step.
 */
public final class HexCustomCards {
    /** The campaign's own cards: what the archmages do at half life. */
    public static final String HEX_CARDS_DIR = ForgeConstants.RES_DIR + "hexcrawl" + File.separator + "cards";

    /** The card databases the cards were added to (the program has one; a test may make another, see loadOnce). */
    private static final Set<StaticData> LOADED_INTO = Collections.newSetFromMap(new IdentityHashMap<>());

    private HexCustomCards() { }

    /**
     * Adds the cards to the card database, once. The game's own and the one effects look cards up in
     * (StaticData.instance(), for a token copy by name) are the same in the program; should a test have made another
     * since, the cards go into that one too.
     */
    public static synchronized void loadOnce() {
        if (ImageKeys.ADVENTURE_CARD_PICS_DIR == null) {
            // card views of these cards carry an "a:" image key, which needs the folder (also on guests)
            ImageKeys.ADVENTURE_CARD_PICS_DIR = ForgeConstants.ADVENTURE_COMMON_DIR + "custom_card_pics";
        }
        for (final StaticData data : new StaticData[] { FModel.getMagicDb(), StaticData.instance() }) {
            if (data != null && LOADED_INTO.add(data)) {
                load(new File(ForgeConstants.ADVENTURE_COMMON_DIR + "custom_cards"), data);
                load(new File(HEX_CARDS_DIR), data);
            }
        }
    }

    private static void load(final File dir, final StaticData data) {
        final File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
        if (files == null) {
            return;
        }
        final CardRules.Reader reader = new CardRules.Reader();
        for (final File file : files) {
            final String baseName = file.getName().substring(0, file.getName().length() - 4);
            try (InputStreamReader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                reader.reset();
                final List<String> lines = FileUtil.readAllLines(in, true);
                final CardRules rules = reader.readCard(lines, baseName);
                final CardDb db = rules.isVariant() ? data.getVariantCards() : data.getCommonCards();
                if (db.getCard(rules.getName()) != null) {
                    continue; // loaded already (the Adventure mode runs in this program too)
                }
                rules.setCustom();
                db.addCard(new PaperCard(rules, CardEdition.UNKNOWN_CODE, CardRarity.Special) {
                    @Override
                    public String getImageKey(final boolean altState) {
                        return ImageKeys.ADVENTURECARD_PREFIX + getName();
                    }
                });
            } catch (final IOException | RuntimeException e) {
                System.err.println("Hex Campaign: could not read the card " + file + ": " + e);
            }
        }
    }
}
