package forge.gamemodes.hexcrawl;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.localinstance.properties.ForgeConstants;
import forge.util.Localizer;

/** The Hydra Adventure's texts live in a language folder of their own (res/languages/hexcrawl), apart from the main files. */
public class HexLanguageFilesTest {

    /** A Localizer of its own: the shared one stays as the other tests left it. */
    private static Localizer fresh(final String locale, final String dir) throws Exception {
        final Constructor<Localizer> c = Localizer.class.getDeclaredConstructor();
        c.setAccessible(true);
        final Localizer l = c.newInstance();
        l.initialize(locale, dir);
        return l;
    }

    private static void write(final Path file, final String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    public void modeFolderIsReadAfterTheMainFileWithEnglishAsFallback() throws Exception {
        final Path dir = Files.createTempDirectory("lang");
        write(dir.resolve("en-US.properties"), "lblMain=Main\n");
        write(dir.resolve("de-DE.properties"), "lblMain=Haupt\n");
        write(dir.resolve("mode/en-US.properties"), "lblModeOnly=Mode\nlblModeEnglishOnly=English only\n");
        write(dir.resolve("mode/de-DE.properties"), "lblModeOnly=Modus\n");
        final Localizer de = fresh("de-DE", dir.toString() + File.separator);
        AssertJUnit.assertEquals("Haupt", de.getMessage("lblMain"));
        AssertJUnit.assertEquals("Modus", de.getMessage("lblModeOnly"));
        AssertJUnit.assertEquals("English only", de.getMessage("lblModeEnglishOnly"));
        AssertJUnit.assertEquals("Mode", fresh("en-US", dir.toString() + File.separator).getMessage("lblModeOnly"));
    }

    @Test
    public void hydraTextsAreFoundInTheirOwnFiles() throws Exception {
        AssertJUnit.assertTrue(new File(ForgeConstants.LANG_DIR, "hexcrawl/de-DE.properties").isFile());
        AssertJUnit.assertEquals("Hydra-Abenteuer", fresh("de-DE", ForgeConstants.LANG_DIR).getMessage("lblHexCampaign"));
        AssertJUnit.assertEquals("Hydra Adventure", fresh("en-US", ForgeConstants.LANG_DIR).getMessage("lblHexCampaign"));
        // the main files keep the rest
        AssertJUnit.assertEquals("Ja", fresh("de-DE", ForgeConstants.LANG_DIR).getMessage("lblYes"));
    }

    private static java.util.Set<String> keys(final String file) throws Exception {
        final java.util.Properties props = new java.util.Properties();
        try (java.io.Reader in = Files.newBufferedReader(Path.of(ForgeConstants.LANG_DIR, "hexcrawl", file), StandardCharsets.UTF_8)) {
            props.load(in);
        }
        return props.stringPropertyNames();
    }

    @Test
    public void germanAndEnglishHaveTheSameKeys() throws Exception {
        final java.util.Set<String> en = keys("en-US.properties");
        final java.util.Set<String> de = keys("de-DE.properties");
        final java.util.Set<String> onlyEn = new java.util.TreeSet<>(en);
        onlyEn.removeAll(de);
        final java.util.Set<String> onlyDe = new java.util.TreeSet<>(de);
        onlyDe.removeAll(en);
        AssertJUnit.assertTrue("only English: " + onlyEn, onlyEn.isEmpty());
        AssertJUnit.assertTrue("only German: " + onlyDe, onlyDe.isEmpty());
    }
}
