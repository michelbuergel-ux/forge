package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.List;

/**
 * A dungeon of a colour: a labyrinth of rooms in the style of Shandalar. Its ways show room by room as the party
 * walks them; dead ends hold treasures and shrines, most behind an enemy, and the boss waits at its far end,
 * reached in more than one way. One enchantment or artifact (its rule card) is in play in all its fights. The
 * party's life carries over from fight to fight and its decks stay locked while it is inside; losing or
 * leaving starts the dungeon anew (but what was taken stays taken).
 */
public class HexDungeon {
    /** The labyrinth's rooms per side. */
    public static final int SIZE = 6;
    /** The doors of a room, as a bitmask. */
    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 4;
    public static final int WEST = 8;
    public static final int[] DOORS = { NORTH, EAST, SOUTH, WEST };
    /** A fight in a dungeon ({@link HexEncounter#dungeonStage}): with a room's guard, or with the boss. */
    public static final int GUARD = 1;
    public static final int BOSS = 2;

    public HexColor color;
    public int size;
    /** The rooms, row by row. */
    public List<HexRoom> rooms = new ArrayList<>();
    public int entranceX;
    public int entranceY;
    public int bossX;
    public int bossY;
    /** The enchantment or artifact in play in every fight of the dungeon (it affects everybody). */
    public String ruleCard;

    /** The party is inside, in this room (and came from that one). */
    public boolean inside;
    public int x;
    public int y;
    public int prevX;
    public int prevY;
    /** The party's life for its next fight in here (0: not inside). */
    public int lifeLeft;
    /** What the shrines gave the party on this visit (LIFE, HAND, LAND, WEAKEN). */
    public List<String> buffs = new ArrayList<>();
    /** The boss is beaten and its chest open: the dungeon stays empty for the rest of the cycle. */
    public boolean cleared;

    public static int dx(final int door) {
        return door == EAST ? 1 : door == WEST ? -1 : 0;
    }

    public static int dy(final int door) {
        return door == SOUTH ? 1 : door == NORTH ? -1 : 0;
    }

    public static int opposite(final int door) {
        return door == NORTH ? SOUTH : door == SOUTH ? NORTH : door == EAST ? WEST : EAST;
    }

    /** The room at a place, or null outside the labyrinth. */
    public HexRoom room(final int rx, final int ry) {
        if (rx < 0 || ry < 0 || rx >= size || ry >= size || rooms.size() != size * size) {
            return null;
        }
        return rooms.get(ry * size + rx);
    }

    /** The party's room, or null. */
    public HexRoom here() {
        return room(x, y);
    }

    public HexRoom entrance() {
        return room(entranceX, entranceY);
    }

    public HexRoom boss() {
        return room(bossX, bossY);
    }

    /** The rooms one step away through an open door. */
    public List<HexRoom> neighbours(final HexRoom r) {
        final List<HexRoom> result = new ArrayList<>();
        for (final int door : DOORS) {
            if ((r.doors & door) != 0) {
                final HexRoom n = room(r.x + dx(door), r.y + dy(door));
                if (n != null) {
                    result.add(n);
                }
            }
        }
        return result;
    }

    /** Is there an open door between the party's room and this one? */
    public boolean nextToParty(final HexRoom r) {
        final HexRoom here = here();
        return here != null && r != null && neighbours(here).contains(r);
    }

    /** The party is in a room: it knows the room now, and sees into those behind its open doors. */
    public void see(final HexRoom r) {
        r.visited = true;
        r.seen = true;
        for (final HexRoom n : neighbours(r)) {
            n.seen = true;
        }
    }

    /** How often a shrine's buff was given on this visit. */
    public int buffCount(final String buff) {
        int n = 0;
        for (final String b : buffs) {
            if (b.equals(buff)) {
                n++;
            }
        }
        return n;
    }

    /**
     * The dungeon starts anew: the beaten enemies come back (only those whose treasure or shrine is not taken,
     * and never the boss of a cleared dungeon), the rooms are dark again, the buffs and the life are gone.
     */
    public void reset() {
        for (final HexRoom r : rooms) {
            final boolean taken = (r.kind == HexRoom.Kind.TREASURE || r.kind == HexRoom.Kind.SHRINE) && r.looted;
            if (!taken) {
                r.beaten = false; // a guard comes back, unless what it guarded is gone
            }
            r.visited = false;
            r.seen = false;
        }
        inside = false;
        x = prevX = entranceX;
        y = prevY = entranceY;
        lifeLeft = 0;
        buffs.clear();
    }
}
