package forge.gamemodes.net.event;

/** Client -> server: something a guest wants to do in the hex campaign (vote, shop, reward, ...). */
public final class HexActionEvent implements NetEvent {
    private static final long serialVersionUID = 1L;

    public static final String VOTE = "VOTE";
    public static final String FIGHT = "FIGHT";
    public static final String REWARD = "REWARD";
    public static final String BUY = "BUY";
    public static final String SELL = "SELL";
    public static final String LAND = "LAND";
    public static final String SELL_RELIC = "SELL_RELIC";
    public static final String QUEST = "QUEST";
    /** Moves a card between deck and sideboard; the flag says "to the sideboard". */
    public static final String MOVE = "MOVE";
    /** A head picks the theme of one of its theme tabs: q = slot, text = theme id. */
    public static final String THEME = "THEME";
    /** A head takes a perk: text = perk name. */
    public static final String PERK = "PERK";
    /** A head gives something to a team mate (index r): gold (q) or a card (text, flag = from the sideboard). */
    public static final String GIVE = "GIVE";
    /** A head looked at its deck before a fight (ready check). */
    public static final String DECK_SEEN = "DECK_SEEN";
    /** A head pays for a whole new stock of the town. */
    public static final String RESTOCK = "RESTOCK";
    /** After the victory: the host starts the next New Game Plus cycle. */
    public static final String NEW_GAME_PLUS = "NEW_GAME_PLUS";
    /** A head's answer to a shrine or caravan: q 1 = leave it; else text = the card given, flag = from the sideboard. */
    public static final String EVENT_CHOICE = "EVENT_CHOICE";
    /** The host names the head whose vote decides where to go (text; null = everybody votes). */
    public static final String LEADER = "LEADER";

    private final String type;
    private final int q;
    private final int r;
    private final String text;
    private final boolean flag;
    private final String head;

    public HexActionEvent(final String type0, final int q0, final int r0, final String text0, final boolean flag0, final String head0) {
        type = type0;
        q = q0;
        r = r0;
        text = text0;
        flag = flag0;
        head = head0;
    }

    public String getType() { return type; }
    public int getQ() { return q; }
    public int getR() { return r; }
    /** A card or relic name (BUY, SELL, SELL_RELIC, MOVE) or a colour (LAND). */
    public String getText() { return text; }
    /** REWARD: take the card; SELL: from the sideboard; MOVE: to the sideboard. */
    public boolean getFlag() { return flag; }
    /** The head the action is for; null means the sender's own head. */
    public String getHead() { return head; }
}
