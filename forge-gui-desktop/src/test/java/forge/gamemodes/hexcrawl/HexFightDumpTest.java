package forge.gamemodes.hexcrawl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;

/** The record of a stuck fight: the game's state and every thread's whole stack, in a file of its own. */
public class HexFightDumpTest extends AITest {

    @Test
    public void aDumpHoldsTheGameAndEveryThreadsWholeStack() throws Exception {
        final File dir = Files.createTempDirectory("hexdump").toFile();
        final Game game = initAndCreateGame();
        final File file = HexFightDump.write(dir, "force", game, "Lorthos");
        AssertJUnit.assertNotNull(file);
        final String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        AssertJUnit.assertTrue(text.contains("stuck fight (force)"));
        AssertJUnit.assertTrue(text.contains("Enemy: Lorthos"));
        AssertJUnit.assertTrue(text.contains("=== Game"));
        AssertJUnit.assertTrue(text.contains("Turn "));
        AssertJUnit.assertTrue("every player", text.contains("Player " + game.getRegisteredPlayers().get(0).getName()));
        AssertJUnit.assertTrue(text.contains("=== Deadlocked threads: none"));
        // the thread that wrote it, with its whole stack (a JVM's own thread dump cuts it after 8 frames)
        final String me = "\"" + Thread.currentThread().getName() + "\"";
        AssertJUnit.assertTrue(text.contains(me));
        final String mine = text.substring(text.indexOf(me));
        final String block = mine.substring(0, mine.indexOf("\n\n"));
        AssertJUnit.assertTrue("whole stack: " + block, block.split("\n    at ").length > 12);
        AssertJUnit.assertTrue(block.contains("HexFightDumpTest"));
    }

    @Test
    public void onlyTheNewestDumpsAreKept() throws Exception {
        final File dir = Files.createTempDirectory("hexdump").toFile();
        for (int i = 0; i < HexFightDump.KEEP + 5; i++) {
            Files.write(new File(dir, String.format("stuck-fight-20200101-0000%02d-abort.txt", i)).toPath(), new byte[] { 1 });
        }
        final File file = HexFightDump.write(dir, "abort", null, "Lorthos");
        AssertJUnit.assertTrue(file.isFile());
        AssertJUnit.assertEquals(HexFightDump.KEEP, dir.listFiles().length);
        AssertJUnit.assertFalse("the oldest went", new File(dir, "stuck-fight-20200101-000000-abort.txt").exists());
        AssertJUnit.assertTrue(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains("(no game)"));
    }
}
