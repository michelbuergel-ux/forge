package forge.gamemodes.hexcrawl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds the labyrinth of a dungeon: a maze of rooms carved from the entrance, with a few walls more broken
 * through so that ways cross and the boss can be reached in more than one way; treasures and shrines in the
 * dead ends (the treasures always guarded), a few enemies in the corridors, and the dungeon's rule card.
 */
public final class HexLabyrinth {
    /** Walls broken through after the maze is carved: crossings and loops. */
    static final int LOOPS = 6;
    static final int TREASURES = 3;
    static final int SHRINES = 2;
    /** Enemies in the corridors, besides those guarding treasures and shrines. */
    static final int WANDERERS = 2;
    /** Corridor enemies stand at least this many steps from the entrance. */
    static final int WANDERER_DEPTH = 3;

    public static final List<String> TREASURE_KINDS = Arrays.asList("CARD", "GOLD", "RELIC");
    public static final List<String> BUFFS = Arrays.asList("LIFE", "HAND", "LAND", "WEAKEN");

    /** The enchantments and artifacts a dungeon of a colour may have in play: they affect every player alike. */
    private static final Map<HexColor, List<String>> RULE_CARDS = new EnumMap<>(HexColor.class);
    static {
        RULE_CARDS.put(HexColor.WHITE, Arrays.asList("Crusade", "Moat", "Light of Day"));
        RULE_CARDS.put(HexColor.BLUE, Arrays.asList("Arcane Laboratory", "Teferi's Puzzle Box"));
        RULE_CARDS.put(HexColor.BLACK, Arrays.asList("Bad Moon", "Night of Souls' Betrayal"));
        RULE_CARDS.put(HexColor.RED, Arrays.asList("Furnace of Rath", "Manabarbs", "Gravity Sphere", "Mana Flare", "Aether Flash"));
        RULE_CARDS.put(HexColor.GREEN, Arrays.asList("Concordant Crossroads", "Heartbeat of Spring", "Fecundity", "Vernal Bloom"));
        RULE_CARDS.put(HexColor.COLORLESS, Arrays.asList("Howling Mine", "Font of Mythos", "Orb of Dreams", "Ankh of Mishra",
                "Torpor Orb", "Dingus Egg", "Sphere of Resistance", "Thorn of Amethyst"));
    }

    private HexLabyrinth() { }

    /** The rule cards a dungeon of this colour may have: its colour's and the colourless ones. */
    public static List<String> ruleCards(final HexColor color) {
        final List<String> result = new ArrayList<>(RULE_CARDS.getOrDefault(color, Collections.emptyList()));
        if (color != HexColor.COLORLESS) {
            result.addAll(RULE_CARDS.get(HexColor.COLORLESS));
        }
        return result;
    }

    /** Builds the labyrinth of a dungeon on the field q, r; the party starts outside, at its entrance. */
    public static void build(final HexDungeon d, final int q, final int r, final Random rnd, final int cycle) {
        final int n = HexDungeon.SIZE;
        d.size = n;
        d.rooms.clear();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                d.rooms.add(new HexRoom(x, y));
            }
        }
        d.entranceX = n / 2;
        d.entranceY = n - 1;
        final HexRoom entrance = d.entrance();
        entrance.kind = HexRoom.Kind.ENTRANCE;
        carve(d, entrance, rnd);
        for (int i = 0, tries = 0; i < LOOPS && tries < 200; tries++) {
            if (breakWall(d, d.rooms.get(rnd.nextInt(d.rooms.size())), rnd, true)) {
                i++;
            }
        }

        // the boss: in the room farthest from the entrance, with two ways in
        int[] dist = distances(d, null);
        HexRoom boss = entrance;
        for (final HexRoom room : d.rooms) {
            if (dist[index(d, room)] > dist[index(d, boss)]) {
                boss = room;
            }
        }
        d.bossX = boss.x;
        d.bossY = boss.y;
        // a second way to it, from deep inside (should there be none, from less deep): never from the entrance
        final int deep = Math.max(1, dist[index(d, boss)] / 2);
        for (int tries = 0; !twoWays(d) && tries < 600; tries++) {
            final HexRoom from = tries % 3 == 0 ? boss : d.rooms.get(rnd.nextInt(d.rooms.size()));
            openDeep(d, from, rnd, dist, tries < 150 ? deep : tries < 400 ? 2 : 1);
        }
        boss.kind = HexRoom.Kind.BOSS;
        dist = distances(d, null);
        int far = 1;
        for (final int v : dist) {
            far = Math.max(far, v);
        }

        // treasures and shrines in the dead ends, the farthest first (then in corridors, should there be too few)
        final List<HexRoom> places = new ArrayList<>();
        for (final int doors : new int[] { 1, 2, 3, 4 }) {
            final List<HexRoom> these = new ArrayList<>();
            for (final HexRoom room : d.rooms) {
                if (room.kind == HexRoom.Kind.EMPTY && room.doorCount() == doors) {
                    these.add(room);
                }
            }
            final int[] fd = dist;
            these.sort((a, b) -> fd[index(d, b)] != fd[index(d, a)] ? fd[index(d, b)] - fd[index(d, a)] : a.y != b.y ? a.y - b.y : a.x - b.x);
            places.addAll(these);
        }
        final List<HexRoom.Kind> kinds = new ArrayList<>();
        for (int i = 0; i < TREASURES; i++) {
            kinds.add(HexRoom.Kind.TREASURE);
        }
        for (int i = 0; i < SHRINES; i++) {
            kinds.add(HexRoom.Kind.SHRINE);
        }
        Collections.shuffle(kinds, rnd);
        final List<String> treasures = new ArrayList<>(TREASURE_KINDS);
        Collections.shuffle(treasures, rnd);
        final List<String> buffs = new ArrayList<>(BUFFS);
        Collections.shuffle(buffs, rnd);
        final HexContent content = HexContent.get();
        for (int i = 0; i < kinds.size() && i < places.size(); i++) {
            final HexRoom room = places.get(i);
            room.kind = kinds.get(i);
            if (room.kind == HexRoom.Kind.TREASURE) {
                room.content = treasures.remove(0);
                room.guard = guard(content, d, room, dist[index(d, room)], far, rnd); // a treasure is always guarded
            } else {
                room.content = buffs.remove(0);
                if (rnd.nextBoolean()) {
                    room.guard = guard(content, d, room, dist[index(d, room)], far, rnd);
                }
            }
        }

        // a few enemies in the corridors: some ways are blocked by a fight, others lead around it
        final List<HexRoom> corridors = new ArrayList<>();
        for (final HexRoom room : d.rooms) {
            if (room.kind == HexRoom.Kind.EMPTY && room.doorCount() >= 2 && dist[index(d, room)] >= WANDERER_DEPTH) {
                corridors.add(room);
            }
        }
        Collections.shuffle(corridors, rnd);
        for (int i = 0; i < WANDERERS && i < corridors.size(); i++) {
            corridors.get(i).guard = guard(content, d, corridors.get(i), dist[index(d, corridors.get(i))], far, rnd);
        }

        final HexEncounter keeper = content.dungeonGuardian(d.color, rnd, cycle);
        keeper.dungeonStage = HexDungeon.BOSS;
        boss.guard = keeper;

        // the rule card, and where every enemy stands
        final List<String> rules = new ArrayList<>();
        for (final String card : ruleCards(d.color)) {
            if (content.findStartCard(card) != null) {
                rules.add(card);
            }
        }
        d.ruleCard = rules.isEmpty() ? null : rules.get(rnd.nextInt(rules.size()));
        for (final HexRoom room : d.rooms) {
            if (room.guard != null) {
                room.guard.color = d.color;
                room.guard.q = q;
                room.guard.r = r;
                room.guard.roomX = room.x;
                room.guard.roomY = room.y;
            }
        }
        d.reset();
    }

    private static HexEncounter guard(final HexContent content, final HexDungeon d, final HexRoom room, final int dist, final int far,
            final Random rnd) {
        final HexEncounter enc = content.randomEncounter(d.color, 0.55 + 0.45 * dist / far, rnd);
        enc.dungeonStage = HexDungeon.GUARD;
        return enc;
    }

    private static int index(final HexDungeon d, final HexRoom room) {
        return room.y * d.size + room.x;
    }

    /** Opens the door between two rooms (both sides). */
    private static void open(final HexDungeon d, final HexRoom room, final int door) {
        final HexRoom other = d.room(room.x + HexDungeon.dx(door), room.y + HexDungeon.dy(door));
        if (other != null) {
            room.doors |= door;
            other.doors |= HexDungeon.opposite(door);
        }
    }

    /** A maze from the entrance: every room reached, each by one way (a random depth-first walk). */
    private static void carve(final HexDungeon d, final HexRoom start, final Random rnd) {
        final boolean[] done = new boolean[d.rooms.size()];
        final ArrayDeque<HexRoom> stack = new ArrayDeque<>();
        done[index(d, start)] = true;
        stack.push(start);
        while (!stack.isEmpty()) {
            final HexRoom room = stack.peek();
            final List<Integer> doors = new ArrayList<>();
            for (final int door : HexDungeon.DOORS) {
                final HexRoom next = d.room(room.x + HexDungeon.dx(door), room.y + HexDungeon.dy(door));
                if (next != null && !done[index(d, next)]) {
                    doors.add(door);
                }
            }
            if (doors.isEmpty()) {
                stack.pop();
                continue;
            }
            final int door = doors.get(rnd.nextInt(doors.size()));
            open(d, room, door);
            final HexRoom next = d.room(room.x + HexDungeon.dx(door), room.y + HexDungeon.dy(door));
            done[index(d, next)] = true;
            stack.push(next);
        }
    }

    /** Opens a wall between two rooms both at least this deep in the labyrinth (by the steps of the first maze). */
    private static void openDeep(final HexDungeon d, final HexRoom room, final Random rnd, final int[] dist, final int deep) {
        if (dist[index(d, room)] < deep) {
            return;
        }
        final List<Integer> walls = new ArrayList<>();
        for (final int door : HexDungeon.DOORS) {
            final HexRoom other = d.room(room.x + HexDungeon.dx(door), room.y + HexDungeon.dy(door));
            if ((room.doors & door) == 0 && other != null && dist[index(d, other)] >= deep && other != d.entrance()) {
                walls.add(door);
            }
        }
        if (!walls.isEmpty()) {
            open(d, room, walls.get(rnd.nextInt(walls.size())));
        }
    }

    /**
     * Opens a closed wall of the room to a neighbour inside the labyrinth; false if it has none. Loops keep the
     * dead ends (neither room may be one), where the treasures lie.
     */
    private static boolean breakWall(final HexDungeon d, final HexRoom room, final Random rnd, final boolean keepDeadEnds) {
        if (keepDeadEnds && room.doorCount() <= 1) {
            return false;
        }
        final List<Integer> walls = new ArrayList<>();
        for (final int door : HexDungeon.DOORS) {
            final HexRoom other = d.room(room.x + HexDungeon.dx(door), room.y + HexDungeon.dy(door));
            if ((room.doors & door) == 0 && other != null && !(keepDeadEnds && other.doorCount() <= 1)) {
                walls.add(door);
            }
        }
        if (walls.isEmpty()) {
            return false;
        }
        open(d, room, walls.get(rnd.nextInt(walls.size())));
        return true;
    }

    /** Steps from the entrance to every room (-1: cannot be reached), not passing through the blocked room. */
    static int[] distances(final HexDungeon d, final HexRoom blocked) {
        final int[] dist = new int[d.rooms.size()];
        Arrays.fill(dist, -1);
        final HexRoom start = d.entrance();
        if (start == blocked) {
            return dist;
        }
        final ArrayDeque<HexRoom> todo = new ArrayDeque<>();
        dist[index(d, start)] = 0;
        todo.add(start);
        while (!todo.isEmpty()) {
            final HexRoom room = todo.poll();
            for (final HexRoom n : d.neighbours(room)) {
                if (n != blocked && dist[index(d, n)] < 0) {
                    dist[index(d, n)] = dist[index(d, room)] + 1;
                    todo.add(n);
                }
            }
        }
        return dist;
    }

    /** The boss can be reached in two ways: with any one room next to it blocked, there is still a way. */
    public static boolean twoWays(final HexDungeon d) {
        final HexRoom boss = d.boss();
        final List<HexRoom> doors = d.neighbours(boss);
        if (doors.size() < 2) {
            return false;
        }
        for (final HexRoom n : doors) {
            if (n == d.entrance()) {
                continue;
            }
            if (distances(d, n)[index(d, boss)] < 0) {
                return false;
            }
        }
        return true;
    }
}
