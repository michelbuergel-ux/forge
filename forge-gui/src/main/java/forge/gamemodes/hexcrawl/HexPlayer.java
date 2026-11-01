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
    /** An event that waits for this head's choice (HexEvent name), or null; and the colour of its field. */
    public String eventOffer;
    public HexColor eventColor;
    /**
     * The head's last trade at an event, shown on its screen once (a counter for it): the card it gave, and the
     * cards (a caravan), the relic or the gold (a ruined shrine) it got for it.
     */
    public List<String> gifts = new ArrayList<>();
    public int giftSerial;
    public String giftGiven;
    public String giftRelic;
    public int giftGold;
    /**
     * The cards the head bought during this visit of a town (the town's field and the party's step count), with what
     * it paid: sold back, each brings that again.
     */
    public List<HexShopOffer> bought = new ArrayList<>();
    public List<Integer> boughtPaid = new ArrayList<>();
    public String boughtTown;
    public int boughtStep;
    /** Names of the relics the head carries, at most one per slot. */
    public List<String> relics = new ArrayList<>();
    /** The trophies of the bosses the head has beaten: the boss's colour (HexColor name) to the trophy's tier (1 to 3). */
    public java.util.Map<String, Integer> trophies = new java.util.LinkedHashMap<>();
    /** The two shop themes the head picked (HexTheme ids, null if none yet). */
    public String[] themes = new String[HexCampaign.PICKED_THEMES];
    /** For each picked theme: the number of fights fought when it was picked (it can change after the next fight). */
    public int[] themesPickedAt = { -1, -1 };
    /** The perks the head has taken (HexPerk names, one entry per stack). */
    public List<String> perks = new ArrayList<>();
    /** The perks the head may choose from right now (empty if no choice is due). */
    public List<String> perkOffer = new ArrayList<>();
    /** What the head did in this cycle of the campaign (New Game Plus starts it anew), and in the whole campaign. */
    public HexHeadStats stats = new HexHeadStats();
    public HexHeadStats totalStats = new HexHeadStats();

    public HexPlayer() { }

    public HexPlayer(final String name0, final boolean ai0) { name = name0; ai = ai0; }
}
