package forge.gamemodes.hexcrawl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import forge.localinstance.properties.ForgeConstants;

/** Saves campaigns as JSON files in the user folder (hexcrawl/&lt;name&gt;.json). */
public final class HexCampaignIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private HexCampaignIO() { }

    public static File dir() {
        final File dir = new File(ForgeConstants.USER_DIR + "hexcrawl");
        dir.mkdirs();
        return dir;
    }

    private static File file(final String name) {
        return new File(dir(), name.replaceAll("[^A-Za-z0-9 _.-]", "_") + ".json");
    }

    public static void save(final HexCampaign campaign) {
        final File target = file(campaign.name);
        final File tmp = new File(target.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            GSON.toJson(campaign, w);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not save campaign " + campaign.name, e);
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Could not replace " + target);
        }
        if (!tmp.renameTo(target)) {
            throw new IllegalStateException("Could not write " + target);
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
        campaign.name = newName.trim();
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
        try (Reader r = new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)) {
            final HexCampaign c = GSON.fromJson(r, HexCampaign.class);
            final boolean whole = c != null && c.map != null && c.map.tiles != null && !c.map.tiles.isEmpty()
                    && c.players != null && !c.players.isEmpty();
            return whole ? c : null;
        } catch (final Exception e) {
            return null; // not JSON, or not a campaign
        }
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
        return name != null && !name.trim().isEmpty() && file(name.trim()).exists();
    }

    /** A name no saved campaign has: the given one, else with " 2", " 3", ... */
    public static String freeName(final String name) {
        final String base = name == null || name.trim().isEmpty() ? "Campaign" : name.trim();
        String free = base;
        for (int n = 2; file(free).exists(); n++) {
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

    public static HexCampaign load(final String name) {
        final File f = file(name);
        if (!f.isFile()) {
            return null;
        }
        final HexCampaign c;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            c = GSON.fromJson(r, HexCampaign.class);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not load campaign " + name, e);
        }
        if (c != null) {
            c.settlePerks(); // a save from when perks came more often keeps what it earned
        }
        return c;
    }

    public static List<String> list() {
        final List<String> names = new ArrayList<>();
        final File[] files = dir().listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (final File f : files) {
                names.add(f.getName().substring(0, f.getName().length() - 5));
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
