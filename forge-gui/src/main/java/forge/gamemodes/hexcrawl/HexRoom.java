package forge.gamemodes.hexcrawl;

/** A room of a dungeon's labyrinth: its doors, what is in it, and what the party knows of it. */
public class HexRoom {
    /** What a room holds; ROCK: no room at all, solid rock where the labyrinth has a gap. */
    public enum Kind { EMPTY, ENTRANCE, TREASURE, SHRINE, BOSS, ROCK }

    public int x;
    public int y;
    /** The open doors: {@link HexDungeon#NORTH}, EAST, SOUTH and WEST together. */
    public int doors;
    public Kind kind = Kind.EMPTY;
    /** A treasure room: CARD, GOLD or RELIC; a shrine: its buff (LIFE, HAND, LAND or WEAKEN). */
    public String content;
    /** The enemy in the room, or null; it stays beaten until the dungeon starts anew. */
    public HexEncounter guard;
    public boolean beaten;
    /** The treasure is taken or the shrine used: the room stays empty for the rest of the cycle. */
    public boolean looted;
    /** The party has been in the room; it has seen it through an open door. */
    public boolean visited;
    public boolean seen;

    public HexRoom() { }

    public HexRoom(final int x0, final int y0) {
        x = x0;
        y = y0;
    }

    /** An enemy waits in the room. */
    public boolean guarded() {
        return guard != null && !beaten;
    }

    /** The room still holds its treasure or its shrine's buff. */
    public boolean full() {
        return (kind == Kind.TREASURE || kind == Kind.SHRINE) && !looted;
    }

    public int doorCount() {
        return Integer.bitCount(doors);
    }
}
