package forge.gamemodes.hexcrawl;

/** An enemy the party has beaten, for the statistics. */
public class HexDefeat {
    /** What it was: an ordinary enemy, the enemy of a task, an archenemy, an archmage, the last enemy or a town's besieger. */
    public enum Kind { ENEMY, TASK, ARCHENEMY, ARCHMAGE, BOSS, SIEGE, DUNGEON }

    public String name;
    public Kind kind;
    public HexColor color;
    public int cycle;
    /** Turns the fight took (0 if unknown). */
    public int turns;

    public HexDefeat() { }

    public HexDefeat(final String name0, final Kind kind0, final HexColor color0, final int cycle0, final int turns0) {
        name = name0;
        kind = kind0;
        color = color0;
        cycle = cycle0;
        turns = turns0;
    }

    public static Kind kindOf(final HexEncounter enc) {
        if (enc.boss) {
            return enc.color == HexColor.COLORLESS ? Kind.BOSS : Kind.ARCHMAGE;
        }
        if (enc.siege) {
            return Kind.SIEGE;
        }
        if (enc.isGuardian()) {
            return Kind.DUNGEON;
        }
        if (enc.archenemy) {
            return Kind.ARCHENEMY;
        }
        return enc.questId != 0 ? Kind.TASK : Kind.ENEMY;
    }
}
