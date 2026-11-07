package forge.gamemodes.hexcrawl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import forge.localinstance.properties.ForgeConstants;

/**
 * Saves campaigns as JSON files in the user folder (hexcrawl/&lt;name&gt;.json). A save is written next to the old one
 * first and takes its place in one step, so there is never a moment without a save; the save as it was goes into
 * hexcrawl/backups/&lt;name&gt;/ now and then (see {@link #backup}), and a save that can't be read is restored from
 * there (see {@link #load}).
 */
public final class HexCampaignIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** A campaign's save goes into its backups at most this often while it is played (and whenever it is opened). */
    static final long BACKUP_EVERY_MS = 15 * 60 * 1000L;
    /** The newest backups of a campaign that are kept. */
    static final int BACKUPS_KEPT = 12;
    /** How often a save is tried to put in place when the file is locked (a virus scanner, a cloud sync). */
    private static final int REPLACE_TRIES = 6;
    private static final String BACKUP_PATTERN = "\\d{8}-\\d{6}(-\\d+)?\\.json";

    private HexCampaignIO() { }

    /** The folder of the saves: hexcrawl in the user folder (the tests give one of their own: forge.hex.saveDir). */
    public static File dir() {
        final String own = System.getProperty("forge.hex.saveDir");
        final File dir = own != null ? new File(own) : new File(ForgeConstants.USER_DIR + "hexcrawl");
        dir.mkdirs();
        return dir;
    }

    private static String safeName(final String name) {
        return name.replaceAll("[^A-Za-z0-9 _.-]", "_");
    }

    private static File file(final String name) {
        return new File(dir(), safeName(name) + ".json");
    }

    /** The new save, written before it takes the old one's place. */
    private static File tmpFile(final File save) {
        return new File(save.getPath() + ".tmp");
    }

    /** The folder of a campaign's backups (it need not exist). */
    public static File backupDir(final String name) {
        return new File(new File(dir(), "backups"), safeName(name.trim()));
    }

    /** Deletes a campaign's backups (its save was deleted). */
    public static void deleteBackups(final String name) {
        final File dir = backupDir(name);
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File f : files) {
                if (!f.delete()) {
                    f.deleteOnExit();
                }
            }
        }
        if (!dir.delete()) {
            dir.deleteOnExit();
        }
    }

    /** The backups of a campaign, newest first. */
    public static List<File> backups(final String name) {
        final List<File> result = new ArrayList<>();
        final File[] files = backupDir(name).listFiles((d, n) -> n.matches(BACKUP_PATTERN));
        if (files != null) {
            result.addAll(Arrays.asList(files));
            result.sort((a, b) -> b.getName().compareTo(a.getName())); // the names are the times
        }
        return result;
    }

    /**
     * Saves the campaign. Several threads may save (the screen, the network): one at a time, as they share the
     * file written first.
     */
    public static synchronized void save(final HexCampaign campaign) {
        final File target = file(campaign.name);
        final File tmp = tmpFile(target);
        write(campaign, tmp);
        backup(target, campaign.name, false);
        replace(tmp, target);
    }

    /** Writes the campaign to the file, and onto the disk before it is closed (a crash then leaves the whole file). */
    private static void write(final HexCampaign campaign, final File f) {
        try (FileOutputStream out = new FileOutputStream(f); Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            GSON.toJson(campaign, w);
            w.flush();
            out.getFD().sync();
        } catch (final Exception e) {
            throw new IllegalStateException("Could not save campaign " + campaign.name, e);
        }
    }

    /**
     * The new save takes the old one's place in one step. Should the file be locked for a moment (a virus scanner, a
     * cloud sync), it is tried again; the old save stays until then, and the new one in its .tmp file.
     */
    private static void replace(final File tmp, final File target) {
        IOException last = null;
        for (int attempt = 1; attempt <= REPLACE_TRIES; attempt++) {
            try {
                try {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (final AtomicMoveNotSupportedException e) {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (final IOException e) {
                last = e;
                if (attempt < REPLACE_TRIES) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new IllegalStateException("Could not write " + target + " (the new save is in " + tmp + ")", last);
    }

    /**
     * Puts a copy of the save into the campaign's backups: always (when it is opened), or if the newest backup is
     * {@link #BACKUP_EVERY_MS} old. Not a save that can't be read, nor one that has not changed since the newest
     * backup; only the newest {@link #BACKUPS_KEPT} are kept. A backup that fails does not keep the game from saving.
     */
    static void backup(final File save, final String name, final boolean always) {
        if (!save.isFile()) {
            return;
        }
        final List<File> existing = backups(name);
        if (!existing.isEmpty()) {
            final long newest = existing.get(0).lastModified();
            if (newest >= save.lastModified() || !always && System.currentTimeMillis() - newest < BACKUP_EVERY_MS) {
                return;
            }
        }
        if (read(save) == null) {
            return; // a broken save would push out the good backups
        }
        final File dir = backupDir(name);
        dir.mkdirs();
        final String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File copy = new File(dir, stamp + ".json");
        for (int n = 2; copy.exists(); n++) {
            copy = new File(dir, stamp + "-" + n + ".json");
        }
        try {
            Files.copy(save.toPath(), copy.toPath());
        } catch (final IOException e) {
            e.printStackTrace();
            return;
        }
        final List<File> all = backups(name);
        for (int i = BACKUPS_KEPT; i < all.size(); i++) {
            if (!all.get(i).delete()) {
                all.get(i).deleteOnExit();
            }
        }
    }

    /** A campaign read from a file; null if it can't be read or holds no whole campaign. */
    private static HexCampaign read(final File source) {
        try (Reader r = new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)) {
            final HexCampaign c = GSON.fromJson(r, HexCampaign.class);
            final boolean whole = c != null && c.map != null && c.map.tiles != null && !c.map.tiles.isEmpty()
                    && c.players != null && !c.players.isEmpty();
            return whole ? c : null;
        } catch (final Exception e) {
            return null; // not JSON, cut short, or not a campaign
        }
    }

    /** May a campaign be named so (not empty, and no other campaign's save has that name)? */
    public static boolean canRename(final HexCampaign campaign, final String newName) {
        final String name = newName == null ? "" : newName.trim();
        if (name.isEmpty() || name.equals(campaign.name)) {
            return false;
        }
        final File target = file(name);
        return !target.exists() || target.getAbsoluteFile().equals(file(campaign.name).getAbsoluteFile()); // only a different case
    }

    /** Renames the campaign: saved under the new name, the old save is removed. False if the name can't be taken. */
    public static boolean rename(final HexCampaign campaign, final String newName) {
        if (!canRename(campaign, newName)) {
            return false;
        }
        final File old = file(campaign.name);
        final File oldBackups = backupDir(campaign.name);
        campaign.name = newName.trim();
        final File newBackups = backupDir(campaign.name);
        if (oldBackups.isDirectory() && !newBackups.exists() && !oldBackups.renameTo(newBackups)) {
            System.err.println("The backups of the campaign stay in " + oldBackups);
        }
        save(campaign);
        final File target = file(campaign.name);
        if (old.isFile() && !old.getAbsoluteFile().equals(target.getAbsoluteFile()) && !old.delete()) {
            old.deleteOnExit();
        }
        return true;
    }

    /** Writes the campaign to a file of its own, to keep or to pass on (the same as its save). */
    public static void exportTo(final HexCampaign campaign, final File target) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
            GSON.toJson(campaign, w);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not export campaign " + campaign.name + " to " + target, e);
        }
    }

    /** Reads a campaign from an exported (or saved) file; null if the file holds no campaign. */
    public static HexCampaign importFrom(final File source) {
        return read(source);
    }

    /** The save file of a campaign by name (it need not exist). */
    public static File saveFile(final String name) {
        return file(name.trim());
    }

    /** The saved campaigns played last, newest first, without the one of this name (the open one). */
    public static List<String> recent(final String without, final int max) {
        final List<String> names = new ArrayList<>();
        final File open = without == null ? null : saveFile(without).getAbsoluteFile();
        for (final String name : list()) {
            if (names.size() < max && !saveFile(name).getAbsoluteFile().equals(open)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Whether a saved campaign has this name already (a new one would replace it). */
    public static boolean exists(final String name) {
        return name != null && !name.trim().isEmpty() && (file(name.trim()).exists() || tmpFile(file(name.trim())).exists());
    }

    /** A name no saved campaign has: the given one, else with " 2", " 3", ... */
    public static String freeName(final String name) {
        final String base = name == null || name.trim().isEmpty() ? "Campaign" : name.trim();
        String free = base;
        for (int n = 2; exists(free); n++) {
            free = base + " " + n;
        }
        return free;
    }

    /** Saves an imported campaign under a free name (which it takes on) and returns that name. */
    public static String saveImported(final HexCampaign campaign) {
        campaign.name = freeName(campaign.name);
        save(campaign);
        return campaign.name;
    }

    /**
     * Loads a campaign: its save, or if that can't be read (cut short by a crash, say) the new save that was still
     * being put in place, or else its newest backup that can be read. A campaign restored so says where from
     * ({@link HexCampaign#restoredFrom}) and is saved at once; the broken save is kept among the backups as
     * broken-&lt;time&gt;.json. Null if there is no save; an exception if none of them can be read.
     */
    public static synchronized HexCampaign load(final String name) {
        final File f = file(name);
        final File tmp = tmpFile(f);
        final List<File> candidates = new ArrayList<>();
        candidates.add(f);
        candidates.add(tmp);
        if (!f.isFile() && !tmp.isFile()) {
            return null; // deleted: its backups do not bring it back
        }
        candidates.addAll(backups(name));
        for (final File candidate : candidates) {
            if (!candidate.isFile()) {
                continue;
            }
            final HexCampaign c = read(candidate);
            if (c == null) {
                continue;
            }
            c.settlePerks(); // a save from when perks came more often keeps what it earned
            if (candidate.equals(f)) {
                backup(f, name, true); // the campaign as it was when it was opened
                return c;
            }
            keepBroken(f, name);
            c.restoredFrom = candidate.equals(tmp) ? null : candidate.getName().substring(0, 15);
            if (!safeName(c.name == null ? "" : c.name).equals(safeName(name))) {
                c.name = name; // a backup from before the campaign was renamed
            }
            save(c);
            if (tmp.isFile() && !tmp.delete()) {
                tmp.deleteOnExit();
            }
            return c;
        }
        throw new IllegalStateException("Could not load campaign " + name + ": neither its save nor a backup can be read");
    }

    /** A save that can't be read, kept among the backups for a look at it (it is not one to restore). */
    private static void keepBroken(final File save, final String name) {
        if (!save.isFile()) {
            return;
        }
        final File dir = backupDir(name);
        dir.mkdirs();
        try {
            Files.copy(save.toPath(), new File(dir, "broken-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".json").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /** The saved campaigns, newest first (one whose new save was still being put in place, too). */
    public static List<String> list() {
        final List<String> names = new ArrayList<>();
        final File[] files = dir().listFiles((d, n) -> n.endsWith(".json") || n.endsWith(".json.tmp") && !new File(d, n.substring(0, n.length() - 4)).exists());
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (final File f : files) {
                final String n = f.getName();
                names.add(n.substring(0, n.length() - (n.endsWith(".tmp") ? 9 : 5)));
            }
        }
        return names;
    }

    public static String toJson(final HexCampaign campaign) {
        return GSON.toJson(campaign);
    }

    public static HexCampaign fromJson(final String json) {
        return GSON.fromJson(json, HexCampaign.class);
    }
}
