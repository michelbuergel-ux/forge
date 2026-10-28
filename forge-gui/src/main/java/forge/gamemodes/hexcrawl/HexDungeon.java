package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.List;

/**
 * A dungeon of a colour: three fights in a row, each harder than the one before, the last against its
 * guardian. The party's life carries over from fight to fight, and its decks stay as they are until it
 * leaves; a chest with a very valuable card waits at the end. Leaving, losing or fleeing starts it anew.
 */
public class HexDungeon {
    /** The fights of a dungeon. */
    public static final int FIGHTS = 3;

    public HexColor color;
    /** The enemies of the three fights, the guardian last. */
    public List<HexEncounter> stages = new ArrayList<>();
    /** The fights won in the run going on (0: nobody is inside). */
    public int stage;
    /** The guardian is beaten and the chest opened: the dungeon stays empty for the rest of the cycle. */
    public boolean cleared;
    /** The party's life for the next fight of the run (0: not in a run). */
    public int lifeLeft;

    /** The enemy the party meets next in here, or null once the dungeon is cleared. */
    public HexEncounter next() {
        return cleared || stages.isEmpty() ? null : stages.get(Math.min(stage, stages.size() - 1));
    }

    /** The party is inside, between two fights. */
    public boolean inRun() {
        return !cleared && stage > 0;
    }
}
