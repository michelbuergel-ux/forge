package forge.gamemodes.hexcrawl;

/**
 * How hard the campaign is, chosen when it starts: the enemies' life and decks, advantages they have from the start,
 * the party's life, and what a defeat and a dungeon cost. Gold is a setting of its own (the gold multiplier), and
 * New Game Plus makes every level harder on top of this. NORMAL is the game as it was before there were levels.
 */
public enum HexDifficulty {
    EASY(75, -0.2, 0, 25, 0, 0, 10),
    NORMAL(100, 0, 0, 20, 1, HexCampaign.ANTE_MAX_PRICE, HexCampaign.DUNGEON_HEAL),
    HARD(125, 0.2, 1, 20, 1, 1000, 3),
    BRUTAL(150, 0.35, 2, 18, 2, 1000, 0);

    /** The base life of every enemy, in percent (before the bonuses of heads, cycles, relics and the rest). */
    public final int enemyLifePercent;
    /** Added to a field's danger when its enemy is chosen: stronger decks earlier on the map. */
    public final double dangerShift;
    /** Advantages the enemies have from the first fight on, rolled like those of New Game Plus. */
    public final int startAdvantages;
    /** The party's shared life before heads, perks and relics. */
    public final int teamLife;
    /** Cards every head loses in a defeat. */
    public final int cardsLost;
    /** Only cards worth less than this can be lost. */
    public final int lossMaxPrice;
    /** Life the party gets back between two fights in a dungeon. */
    public final int dungeonHeal;

    HexDifficulty(final int enemyLifePercent0, final double dangerShift0, final int startAdvantages0, final int teamLife0,
            final int cardsLost0, final int lossMaxPrice0, final int dungeonHeal0) {
        enemyLifePercent = enemyLifePercent0;
        dangerShift = dangerShift0;
        startAdvantages = startAdvantages0;
        teamLife = teamLife0;
        cardsLost = cardsLost0;
        lossMaxPrice = lossMaxPrice0;
        dungeonHeal = dungeonHeal0;
    }

    /** The level's name, as a text key. */
    public String key() {
        return "lblHexDifficulty" + name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /** A campaign saved before there were levels (null) plays on Normal. */
    public static HexDifficulty of(final HexDifficulty d) {
        return d == null ? NORMAL : d;
    }
}
