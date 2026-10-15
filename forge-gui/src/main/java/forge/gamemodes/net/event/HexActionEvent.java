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
    /** A guest's first message: it only tells the host the guest's build. */
    public static final String HELLO = "HELLO";

    private final String type;
    private final int q;
    private final int r;
    private final String text;
    private final boolean flag;
    private final String head;
    /** The sender's build (see {@link forge.gamemodes.hexcrawl.HexNet#buildId()}). */
    private final String build = forge.gamemodes.hexcrawl.HexNet.buildId();

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
    /** A card or relic name (BUY, SELL, SELL_RELIC) or a colour (LAND). */
    public String getText() { return text; }
    public boolean getFlag() { return flag; }
    /** The head the action is for; null means the sender's own head. */
    public String getHead() { return head; }
    /** The build of the program that sent the action. */
    public String getBuild() { return build; }
}
