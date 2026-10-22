package forge.gamemodes.hexcrawl;

import forge.gui.GuiBase;
import forge.localinstance.properties.ForgeConstants;
import forge.sound.MusicPlaylist;
import forge.sound.SoundSystem;

/**
 * The Adventure mode's music for the campaign: every colour region has its own tracks, towns and
 * bosses too. The tracks live in the Adventure folder, which the desktop's sound system only
 * searches while an adventure directory is set, so it is set while the campaign is open.
 */
public final class HexMusic {
    private static volatile boolean active;

    private HexMusic() { }

    /** The campaign screen is open (or one of its fights): use the Adventure music folders. */
    public static synchronized void enter() {
        if (active) {
            return;
        }
        active = true;
        GuiBase.setAdventureDirectory(ForgeConstants.ADVENTURE_COMMON_DIR);
        MusicPlaylist.invalidateMusicPlaylist();
    }

    /** Back to the rest of Forge: its own menu and match music again. */
    public static synchronized void leave() {
        if (!active) {
            return;
        }
        active = false;
        GuiBase.setAdventureDirectory(null);
        MusicPlaylist.invalidateMusicPlaylist();
    }

    public static boolean isActive() {
        return active;
    }

    public static MusicPlaylist of(final HexColor color) {
        if (color == null) {
            return MusicPlaylist.COLORLESS;
        }
        switch (color) {
            case WHITE: return MusicPlaylist.WHITE;
            case BLUE: return MusicPlaylist.BLUE;
            case BLACK: return MusicPlaylist.BLACK;
            case RED: return MusicPlaylist.RED;
            case GREEN: return MusicPlaylist.GREEN;
            default: return MusicPlaylist.COLORLESS;
        }
    }

    /** The colour of the field a fight is about (the field, not where its enemy came from). */
    public static HexColor fieldColor(final HexCampaign campaign, final HexEncounter enc) {
        final HexTile t = campaign == null || enc == null || campaign.map == null ? null : campaign.map.get(enc.q, enc.r);
        return t != null ? t.color : enc == null ? null : enc.color;
    }

    /** The music of a fight: the boss music against bosses, else the music of the field's region. Null outside the campaign. */
    public static MusicPlaylist fightPlaylist(final HexCampaign campaign, final HexEncounter enc) {
        if (!active || enc == null) {
            return null;
        }
        MusicPlaylist.invalidateMusicPlaylist(); // a playlist read before the folder was set would stay empty
        return enc.boss ? MusicPlaylist.BOSS : of(fieldColor(campaign, enc));
    }

    /** The music on the campaign screen: the town's or the region's where the party stands. */
    public static MusicPlaylist mapPlaylist(final HexCampaign campaign) {
        if (campaign == null || campaign.map == null) {
            return MusicPlaylist.MENUS;
        }
        final HexTile t = campaign.tile();
        if (t.poi == HexPoi.TOWN) {
            return MusicPlaylist.TOWN;
        }
        if (t.poi == HexPoi.BOSS) {
            return MusicPlaylist.CASTLE;
        }
        return of(t.color);
    }

    private static MusicPlaylist playing;

    /** Plays the campaign screen's music if it changed (a new region, a town). */
    public static synchronized void playMap(final HexCampaign campaign) {
        if (!active) {
            return;
        }
        final MusicPlaylist p = mapPlaylist(campaign);
        if (p != playing) {
            playing = p;
            MusicPlaylist.invalidateMusicPlaylist();
            SoundSystem.instance.setBackgroundMusic(p);
        }
    }

    /** Something else took over the music (a fight, the menus): the next playMap starts again. */
    public static synchronized void forget() {
        playing = null;
    }
}
