package forge.gamemodes.hexcrawl;

import forge.util.Localizer;

/**
 * A small lasting bonus of one head, chosen every few won fights (see
 * {@link HexCampaign#PERK_EVERY_WINS}). Most of them can be taken more than once, up to a limit.
 */
public enum HexPerk {
    /** +2 life for the whole party in every fight. */
    TEAM_LIFE(5),
    /** One more card in the opening hand. */
    EXTRA_CARD(2),
    /** 10% off in the town shops. */
    DISCOUNT(3),
    /** +20% gold from fights and bounties. */
    GOLD_BONUS(3),
    /** Starts every fight with a basic land in play. */
    START_LAND(1),
    /** The shops pay 50% more for cards sold. */
    SELL_BONUS(2);

    public static final int TEAM_LIFE_PER_STACK = 2;
    public static final int DISCOUNT_PERCENT = 10;
    public static final int GOLD_BONUS_PERCENT = 20;
    public static final int SELL_BONUS_PERCENT = 50;

    /** How often a head can take it. */
    public final int maxStacks;

    HexPerk(final int maxStacks0) {
        maxStacks = maxStacks0;
    }

    public String getLabel() {
        return Localizer.getInstance().getMessage("lblHexPerk_" + name());
    }

    public String getDescription() {
        return Localizer.getInstance().getMessage("lblHexPerkDesc_" + name());
    }

    /** How often the head has taken this perk. */
    public int count(final HexPlayer p) {
        int n = 0;
        if (p != null) {
            for (final String perk : p.perks) {
                if (name().equals(perk)) {
                    n++;
                }
            }
        }
        return n;
    }

    public static HexPerk of(final String name) {
        try {
            return name == null ? null : valueOf(name);
        } catch (final IllegalArgumentException e) {
            return null; // from a newer version
        }
    }
}
