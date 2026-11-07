package forge.ai;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexCampaignIO;
import forge.gamemodes.hexcrawl.HexPlayer;

/**
 * The campaign saves: a save takes the old one's place in one step, backups are kept now and then, and a save that
 * can't be read is restored from the newest backup that can.
 */
public class HexSaveSafetyTest extends AITest {
    private static final long MINUTE = 60_000L;

    private static HexCampaign campaign(final String name) {
        final HexCampaign c = HexCampaign.create(name, 31L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        c.name = name;
        return c;
    }

    private static String unique(final String base) {
        return base + " " + Long.toHexString(System.nanoTime());
    }

    private static void forget(final String name) {
        final File save = HexCampaignIO.saveFile(name);
        save.delete();
        new File(save.getPath() + ".tmp").delete();
        HexCampaignIO.deleteBackups(name);
    }

    /** Makes every backup of the campaign look this many minutes old. */
    private static void ageBackups(final String name, final long minutes) {
        for (final File b : HexCampaignIO.backups(name)) {
            AssertJUnit.assertTrue(b.setLastModified(System.currentTimeMillis() - minutes * MINUTE));
        }
    }

    @Test
    public void aSaveLeavesNoHalfWrittenFile() {
        final String name = unique("SaveTest");
        try {
            final HexCampaign c = campaign(name);
            HexCampaignIO.save(c);
            c.steps = 5;
            HexCampaignIO.save(c);
            final File save = HexCampaignIO.saveFile(name);
            AssertJUnit.assertTrue(save.isFile());
            AssertJUnit.assertFalse("the new save took the old one's place", new File(save.getPath() + ".tmp").exists());
            AssertJUnit.assertEquals(5, HexCampaignIO.load(name).steps);
        } finally {
            forget(name);
        }
    }

    @Test
    public void backupsAreKeptNowAndThen() {
        final String name = unique("BackupTest");
        try {
            final HexCampaign c = campaign(name);
            HexCampaignIO.save(c);
            AssertJUnit.assertTrue("a new campaign has nothing to back up yet", HexCampaignIO.backups(name).isEmpty());
            c.steps = 1;
            HexCampaignIO.save(c);
            AssertJUnit.assertEquals("the save as it was", 1, HexCampaignIO.backups(name).size());
            c.steps = 2;
            HexCampaignIO.save(c);
            AssertJUnit.assertEquals("not again within a quarter of an hour", 1, HexCampaignIO.backups(name).size());
            ageBackups(name, 20);
            c.steps = 3;
            HexCampaignIO.save(c);
            AssertJUnit.assertEquals("a quarter of an hour later, again", 2, HexCampaignIO.backups(name).size());
        } finally {
            forget(name);
        }
    }

    @Test
    public void openingACampaignBacksItUpAndOnlyTheNewestAreKept() throws Exception {
        final String name = unique("KeepTest");
        try {
            final HexCampaign c = campaign(name);
            HexCampaignIO.save(c);
            // many old backups (copies of the save)
            final File dir = HexCampaignIO.backupDir(name);
            dir.mkdirs();
            for (int i = 0; i < 15; i++) {
                final File b = new File(dir, String.format("2020010%d-1200%02d.json", 1 + i / 10, i));
                Files.copy(HexCampaignIO.saveFile(name).toPath(), b.toPath());
                b.setLastModified(System.currentTimeMillis() - 600 * MINUTE);
            }
            AssertJUnit.assertNotNull(HexCampaignIO.load(name));
            final List<File> backups = HexCampaignIO.backups(name);
            AssertJUnit.assertEquals(12, backups.size());
            AssertJUnit.assertFalse("the newest is the one just made", backups.get(0).getName().startsWith("2020"));
            AssertJUnit.assertNotNull(HexCampaignIO.load(name));
            AssertJUnit.assertEquals("opened again unchanged: no second copy", 12, HexCampaignIO.backups(name).size());
        } finally {
            forget(name);
        }
    }

    @Test
    public void aBrokenSaveIsRestoredFromTheNewestBackup() throws Exception {
        final String name = unique("BrokenTest");
        try {
            final HexCampaign c = campaign(name);
            HexCampaignIO.save(c);
            c.steps = 7;
            HexCampaignIO.save(c); // the backup holds steps 0
            final File save = HexCampaignIO.saveFile(name);
            // cut short, as by a crash
            final String json = Files.readString(save.toPath(), StandardCharsets.UTF_8);
            Files.writeString(save.toPath(), json.substring(0, json.length() / 2), StandardCharsets.UTF_8);

            final HexCampaign back = HexCampaignIO.load(name);
            AssertJUnit.assertNotNull(back);
            AssertJUnit.assertEquals(0, back.steps);
            AssertJUnit.assertNotNull("the player is told", back.restoredFrom);
            AssertJUnit.assertEquals(name, back.name);
            AssertJUnit.assertNotNull("the save can be read again", HexCampaignIO.importFrom(save));
            final File[] broken = HexCampaignIO.backupDir(name).listFiles((d, n) -> n.startsWith("broken-"));
            AssertJUnit.assertEquals("the broken save is kept for a look", 1, broken.length);
            AssertJUnit.assertFalse("but never restored from", HexCampaignIO.backups(name).contains(broken[0]));
            AssertJUnit.assertNull("loaded again: from its save", HexCampaignIO.load(name).restoredFrom);
        } finally {
            forget(name);
        }
    }

    @Test
    public void aSaveThatWasStillBeingPutInPlaceIsFound() throws Exception {
        final String name = unique("TmpTest");
        try {
            final HexCampaign c = campaign(name);
            c.steps = 4;
            HexCampaignIO.save(c);
            final File save = HexCampaignIO.saveFile(name);
            final File tmp = new File(save.getPath() + ".tmp");
            Files.move(save.toPath(), tmp.toPath()); // a crash between the old save gone and the new one in place
            AssertJUnit.assertTrue(HexCampaignIO.list().contains(name));
            AssertJUnit.assertTrue(HexCampaignIO.exists(name));
            final HexCampaign back = HexCampaignIO.load(name);
            AssertJUnit.assertEquals(4, back.steps);
            AssertJUnit.assertNull("nothing was lost", back.restoredFrom);
            AssertJUnit.assertTrue(save.isFile());
            AssertJUnit.assertFalse(tmp.exists());
        } finally {
            forget(name);
        }
    }

    @Test
    public void aDeletedCampaignStaysDeletedAndNothingReadableFails() throws Exception {
        final String name = unique("GoneTest");
        try {
            final HexCampaign c = campaign(name);
            HexCampaignIO.save(c);
            c.steps = 1;
            HexCampaignIO.save(c);
            final File save = HexCampaignIO.saveFile(name);
            AssertJUnit.assertTrue(save.delete());
            AssertJUnit.assertNull("deleted: the backups do not bring it back", HexCampaignIO.load(name));

            Files.writeString(save.toPath(), "{ not a campaign", StandardCharsets.UTF_8);
            HexCampaignIO.deleteBackups(name);
            try {
                HexCampaignIO.load(name);
                AssertJUnit.fail("nothing to restore from");
            } catch (final IllegalStateException expected) {
                // the screen shows that it could not be loaded
            }
        } finally {
            forget(name);
        }
    }

    @Test
    public void aCampaignWithAColonInItsNameKeepsItsName() throws Exception {
        final String name = unique("HEX 16: Colon");
        final String fileName = name.replace(':', '_');
        try {
            final HexCampaign c = campaign(name);
            HexCampaignIO.save(c);
            c.steps = 2;
            HexCampaignIO.save(c);
            Files.writeString(HexCampaignIO.saveFile(fileName).toPath(), "{", StandardCharsets.UTF_8);
            final HexCampaign back = HexCampaignIO.load(fileName);
            AssertJUnit.assertEquals(name, back.name);
            AssertJUnit.assertNotNull(back.restoredFrom);
        } finally {
            forget(fileName);
        }
    }

    @Test
    public void renamingTakesTheBackupsAlong() {
        final String name = unique("RenameBackupTest");
        final String newName = unique("RenamedBackupTest");
        try {
            final HexCampaign c = campaign(name);
            HexCampaignIO.save(c);
            c.steps = 1;
            HexCampaignIO.save(c);
            AssertJUnit.assertEquals(1, HexCampaignIO.backups(name).size());
            AssertJUnit.assertTrue(HexCampaignIO.rename(c, newName));
            AssertJUnit.assertTrue(HexCampaignIO.backups(name).isEmpty());
            AssertJUnit.assertEquals(1, HexCampaignIO.backups(newName).size());
        } finally {
            forget(name);
            forget(newName);
        }
    }

    @Test
    public void severalThreadsSaveOneAtATime() throws Exception {
        final String name = unique("ThreadTest");
        try {
            final HexCampaign c = campaign(name);
            final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
            final List<Thread> threads = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                threads.add(new Thread(() -> {
                    for (int i = 0; i < 10; i++) {
                        try {
                            HexCampaignIO.save(c);
                        } catch (final Throwable e) {
                            errors.add(e);
                        }
                    }
                }));
            }
            threads.forEach(Thread::start);
            for (final Thread t : threads) {
                t.join(60_000);
            }
            AssertJUnit.assertTrue(errors.toString(), errors.isEmpty());
            AssertJUnit.assertNotNull(HexCampaignIO.importFrom(HexCampaignIO.saveFile(name)));
        } finally {
            forget(name);
        }
    }
}
