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

    public static HexCampaign load(final String name) {
        final File f = file(name);
        if (!f.isFile()) {
            return null;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, HexCampaign.class);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not load campaign " + name, e);
        }
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
