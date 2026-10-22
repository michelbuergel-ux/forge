package forge.gamemodes.hexcrawl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import forge.ImageKeys;
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

    private static boolean loaded;

    private HexCustomCards() { }

    public static synchronized void loadOnce() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (ImageKeys.ADVENTURE_CARD_PICS_DIR == null) {
            // card views of these cards carry an "a:" image key, which needs the folder (also on guests)
            ImageKeys.ADVENTURE_CARD_PICS_DIR = ForgeConstants.ADVENTURE_COMMON_DIR + "custom_card_pics";
        }
        load(new File(ForgeConstants.ADVENTURE_COMMON_DIR + "custom_cards"));
        load(new File(HEX_CARDS_DIR));
    }

    private static void load(final File dir) {
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
                final CardDb db = rules.isVariant() ? FModel.getMagicDb().getVariantCards() : FModel.getMagicDb().getCommonCards();
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
