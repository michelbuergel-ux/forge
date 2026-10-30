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
 * Builds the labyrinth of a dungeon: rooms on part of its squares (rock on the others), a maze carved through
 * them from the entrance, with a few walls more broken through so that ways cross and the boss can be reached
 * in more than one way; treasures and shrines in the dead ends, enemies scattered about (before a treasure, or
 * just in the way), and the dungeon's rule card.
 */
public final class HexLabyrinth {
    /** Rooms in a labyrinth; the other squares are rock. */
    public static final int ROOMS = 32;
    /** Labyrinths with gaps tried before one without. */
    static final int LAYOUTS = 25;
    /** The farthest rooms tried for the boss. */
    static final int BOSS_PLACES = 4;
    /** Walls broken through after the maze is carved: crossings and loops. */
    static final int LOOPS = 6;
    static final int TREASURES = 3;
    static final int SHRINES = 2;
    /** Enemies scattered through the labyrinth, besides its boss. */
    public static final int ENEMIES = 6;
    /** Enemies stand at least this many steps from the entrance. */
    static final int ENEMY_DEPTH = 2;

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
        for (int attempt = 0; attempt <= LAYOUTS; attempt++) {
            if (layout(d, rnd, attempt < LAYOUTS ? ROOMS : n * n)) {
                break;
            }
        }
        final HexRoom boss = d.boss();
        boss.kind = HexRoom.Kind.BOSS;
        final int[] dist = distances(d, null);
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
            these.sort((a, b) -> dist[index(d, b)] != dist[index(d, a)] ? dist[index(d, b)] - dist[index(d, a)] : a.y != b.y ? a.y - b.y : a.x - b.x);
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
        for (int i = 0; i < kinds.size() && i < places.size(); i++) {
            final HexRoom room = places.get(i);
            room.kind = kinds.get(i);
            room.content = room.kind == HexRoom.Kind.TREASURE ? treasures.remove(0) : buffs.remove(0);
        }

        // the enemies stand anywhere but near the entrance: before a treasure, or just in the way
        final HexContent content = HexContent.get();
        final List<HexRoom> stands = new ArrayList<>();
        for (final HexRoom room : d.rooms) {
            if (room.kind != HexRoom.Kind.ROCK && room.kind != HexRoom.Kind.ENTRANCE && room.kind != HexRoom.Kind.BOSS
                    && dist[index(d, room)] >= ENEMY_DEPTH) {
                stands.add(room);
            }
        }
        Collections.shuffle(stands, rnd);
        for (int i = 0; i < ENEMIES && i < stands.size(); i++) {
            final HexRoom room = stands.get(i);
            final HexEncounter enc = content.randomEncounter(d.color, 0.55 + 0.45 * dist[index(d, room)] / far, rnd);
            enc.dungeonStage = HexDungeon.GUARD;
            room.guard = enc;
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

    /**
     * Lays out the squares and the ways: this many rooms (rock on the rest, all rooms joined), the entrance at
     * the bottom in the middle, a maze through them with a few loops, and the boss in the room farthest from the
     * entrance; false if it cannot be reached in two ways, or there are too few dead ends for the treasures.
     */
    private static boolean layout(final HexDungeon d, final Random rnd, final int count) {
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
        // rock, square by square, as long as every room can still be reached
        final List<HexRoom> squares = new ArrayList<>(d.rooms);
        Collections.shuffle(squares, rnd);
        int rooms = n * n;
        for (final HexRoom square : squares) {
            if (rooms <= count) {
                break;
            }
            if (square == entrance) {
                continue;
            }
            square.kind = HexRoom.Kind.ROCK;
            if (joined(d, rooms - 1)) {
                rooms--;
            } else {
                square.kind = HexRoom.Kind.EMPTY;
            }
        }

        carve(d, entrance, rnd);
        for (int i = 0, tries = 0; i < LOOPS && tries < 200; tries++) {
            final HexRoom room = d.rooms.get(rnd.nextInt(d.rooms.size()));
            if (room.kind != HexRoom.Kind.ROCK && breakWall(d, room, rnd)) {
                i++;
            }
        }

        // the boss: in one of the rooms farthest from the entrance that can be reached in two ways
        final int[] dist = distances(d, null);
        final List<HexRoom> far = new ArrayList<>();
        for (final HexRoom room : d.rooms) {
            if (dist[index(d, room)] > 0) {
                far.add(room);
            }
        }
        Collections.shuffle(far, rnd);
        far.sort((a, b) -> dist[index(d, b)] - dist[index(d, a)]);
        HexRoom boss = null;
        for (int i = 0; i < far.size() && i < BOSS_PLACES && boss == null; i++) {
            if (secondWay(d, far.get(i), dist)) {
                boss = far.get(i);
            }
        }
        if (boss == null) {
            return false;
        }
        int deadEnds = 0;
        for (final HexRoom room : d.rooms) {
            if (room.kind == HexRoom.Kind.EMPTY && room.doorCount() == 1 && room != boss) {
                deadEnds++;
            }
        }
        return twoWays(d) && deadEnds >= TREASURES;
    }

    /** All this many rooms are joined square to square (doors or not) with the entrance. */
    private static boolean joined(final HexDungeon d, final int rooms) {
        final boolean[] done = new boolean[d.rooms.size()];
        final ArrayDeque<HexRoom> todo = new ArrayDeque<>();
        done[index(d, d.entrance())] = true;
        todo.add(d.entrance());
        int found = 1;
        while (!todo.isEmpty()) {
            final HexRoom room = todo.poll();
            for (final int door : HexDungeon.DOORS) {
                final HexRoom next = d.room(room.x + HexDungeon.dx(door), room.y + HexDungeon.dy(door));
                if (next != null && !done[index(d, next)]) {
                    done[index(d, next)] = true;
                    found++;
                    todo.add(next);
                }
            }
        }
        return found == rooms;
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

    /** Closes the door between two rooms (both sides). */
    private static void close(final HexDungeon d, final HexRoom room, final int door) {
        final HexRoom other = d.room(room.x + HexDungeon.dx(door), room.y + HexDungeon.dy(door));
        room.doors &= ~door;
        if (other != null) {
            other.doors &= ~HexDungeon.opposite(door);
        }
    }

    /**
     * Makes this room the boss's, with two ways to it: as it is, or with one more door, to the deepest room next
     * to it that gives a second way (never to the entrance); false if none does.
     */
    private static boolean secondWay(final HexDungeon d, final HexRoom boss, final int[] dist) {
        d.bossX = boss.x;
        d.bossY = boss.y;
        if (twoWays(d)) {
            return true;
        }
        final List<Integer> walls = new ArrayList<>();
        for (final int door : HexDungeon.DOORS) {
            final HexRoom other = d.room(boss.x + HexDungeon.dx(door), boss.y + HexDungeon.dy(door));
            if ((boss.doors & door) == 0 && other != null && other != d.entrance()) {
                walls.add(door);
            }
        }
        walls.sort((a, b) -> dist[index(d, d.room(boss.x + HexDungeon.dx(b), boss.y + HexDungeon.dy(b)))]
                - dist[index(d, d.room(boss.x + HexDungeon.dx(a), boss.y + HexDungeon.dy(a)))]);
        for (final int door : walls) {
            open(d, boss, door);
            if (twoWays(d)) {
                return true;
            }
            close(d, boss, door);
        }
        return false;
    }

    /**
     * Opens a closed wall of the room to a neighbour inside the labyrinth; false if it has none. Loops keep the
     * dead ends (neither room may be one), where the treasures lie.
     */
    private static boolean breakWall(final HexDungeon d, final HexRoom room, final Random rnd) {
        if (room.doorCount() <= 1) {
            return false;
        }
        final List<Integer> walls = new ArrayList<>();
        for (final int door : HexDungeon.DOORS) {
            final HexRoom other = d.room(room.x + HexDungeon.dx(door), room.y + HexDungeon.dy(door));
            if ((room.doors & door) == 0 && other != null && other.doorCount() > 1) {
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
