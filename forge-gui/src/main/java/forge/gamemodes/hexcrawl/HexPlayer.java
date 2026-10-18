package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.List;

/** One head of the party: a name, a purse, a deck, a sideboard and the relics it carries. */
public class HexPlayer {
    public String name;
    /** Played by the computer (a companion) instead of a person. */
    public boolean ai;
    public int gold;
    /** Card names of the deck, one entry per copy. */
    public List<String> deck = new ArrayList<>();
    /** Cards the head owns but does not play, one entry per copy. Not touched by losses. */
    public List<String> sideboard = new ArrayList<>();
    /** Names of the relics the head carries, at most one per slot. */
    public List<String> relics = new ArrayList<>();

    public HexPlayer() { }

    public HexPlayer(final String name0, final boolean ai0) { name = name0; ai = ai0; }
}
