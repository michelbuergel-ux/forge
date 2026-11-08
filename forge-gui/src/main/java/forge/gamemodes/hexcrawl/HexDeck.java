package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.List;

/**
 * One of a head's decks: its name and its cards. The cards are a list of what the deck should hold; they are taken
 * from the cards the head owns when the head picks the deck, so one card the head owns can be in several decks. While
 * a deck is the one the head plays, its cards are the head's deck itself and this list is kept empty.
 */
public class HexDeck {
    /** The name the head gave it; null for the first deck of an older save (shown as the main deck). */
    public String name;
    /** Card names, one entry per copy (empty while it is the deck played). */
    public List<String> cards = new ArrayList<>();

    public HexDeck() { }

    public HexDeck(final String name0) { name = name0; }
}
