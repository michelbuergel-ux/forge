package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.player.PlayerControllerHuman;
import forge.util.Localizer;

/**
 * The practice ground of a town: a game against the AI, for fun only, while the others shop. A head plays its deck
 * as it is against a training dummy (lands only, lots of life) or a sparring partner (an ordinary enemy of the
 * region); nothing is won or lost, nothing is saved. When the party moves on, the game simply ends.
 */
public final class HexPractice {
    private HexPractice() { }

    /** Whom a head practises against. */
    public enum Opponent { DUMMY, SPARRING }

    /** The training dummy's life: enough to try anything on it. */
    public static final int DUMMY_LIFE = 99;
    public static final int LIFE = 20;
    /** The dummy's deck: lands, nothing else. */
    private static final int DUMMY_LANDS = 40;

    private static volatile HostedMatch match;

    /** Is a practice game on screen on this computer (until its screen is closed)? */
    public static boolean isRunning() {
        final HostedMatch m = match;
        return m != null && m.getGame() != null;
    }

    /** The players of a practice game: the head first, then its opponent. */
    public static List<RegisteredPlayer> createPlayers(final HexCampaign campaign, final HexPlayer head, final Opponent opponent,
            final Random rnd) {
        final HexContent content = HexContent.get();
        final List<RegisteredPlayer> players = new ArrayList<>();
        final RegisteredPlayer me = new RegisteredPlayer(content.toDeck(head.name, head.deck));
        me.setPlayer(GamePlayerUtil.getGuiPlayer(head.name, 0, 0, false));
        me.setTeamNumber(HexBattle.PARTY_TEAM);
        me.setStartingLife(LIFE);
        players.add(me);
        final HexColor color = campaign.tile() == null ? HexColor.COLORLESS : campaign.tile().color;
        final RegisteredPlayer other;
        if (opponent == Opponent.SPARRING) {
            final HexEncounter enc = sparringPartner(content, color, rnd);
            other = new RegisteredPlayer(content.loadEnemyDeck(enc));
            other.setPlayer(GamePlayerUtil.createAiPlayer(enc.enemyName, 1));
            other.setStartingLife(LIFE);
        } else {
            other = new RegisteredPlayer(dummyDeck(content, color));
            other.setPlayer(GamePlayerUtil.createAiPlayer(Localizer.getInstance().getMessage("lblHexPracticeDummy"), 1));
            other.setStartingLife(DUMMY_LIFE);
        }
        other.setTeamNumber(HexBattle.ENEMY_TEAM);
        players.add(other);
        return players;
    }

    /** An ordinary enemy of the region (of the weaker half); one of any colour if the region has none. */
    static HexEncounter sparringPartner(final HexContent content, final HexColor color, final Random rnd) {
        try {
            return content.randomEncounter(color, 0, rnd);
        } catch (final IllegalStateException e) {
            final List<HexColor> colors = new ArrayList<>(java.util.Arrays.asList(HexColor.COLORS));
            Collections.shuffle(colors, rnd);
            return content.randomEncounter(colors.get(0), 0, rnd);
        }
    }

    /** The training dummy's deck: basic lands of the region (Wastes in a colourless one). */
    static Deck dummyDeck(final HexContent content, final HexColor color) {
        final Deck deck = new Deck(Localizer.getInstance().getMessage("lblHexPracticeDummy"));
        final PaperCard land = content.getCard(color.getBasicLand());
        if (land != null) {
            deck.getOrCreate(DeckSection.Main).add(land, DUMMY_LANDS);
        }
        return deck;
    }

    /** The rules of a practice game: a plain duel, one game. */
    public static GameRules createRules() {
        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setGamesPerMatch(1);
        rules.setPlayForAnte(false);
        rules.setStreamlinedPriority(true);
        rules.setManaBurn(FModel.getPreferences().getPrefBoolean(FPref.LEGACY_MANABURN));
        rules.setOrderCombatants(FModel.getPreferences().getPrefBoolean(FPref.LEGACY_ORDER_COMBATANTS));
        rules.setUseGrayText(FModel.getPreferences().getPrefBoolean(FPref.UI_GRAY_INACTIVE_TEXT));
        return rules;
    }

    /** Starts a practice game for a head of the campaign on this computer. */
    public static HostedMatch start(final HexCampaign campaign, final String headName, final Opponent opponent) {
        final HexPlayer head = campaign.player(headName);
        if (head == null) {
            throw new IllegalArgumentException("No head " + headName);
        }
        final List<RegisteredPlayer> players = createPlayers(campaign, head, opponent, new Random());
        final Map<RegisteredPlayer, IGuiGame> guis = new HashMap<>();
        guis.put(players.get(0), GuiBase.getInterface().getNewGuiGame());
        final HostedMatch m = GuiBase.getInterface().hostMatch();
        match = m;
        m.startMatch(createRules(), null, players, guis, null);
        return m;
    }

    /** The practice game on screen, if any. */
    public static HostedMatch current() {
        return match;
    }

    /** The practice is over and its screen closed. */
    public static void forget() {
        match = null;
    }

    /**
     * Ends the practice game on screen as a draw (the party moves on). Waits a little for the game to stop; call it
     * off the screen thread. The screen itself is closed by the caller.
     */
    public static void endGame() {
        final HostedMatch m = match;
        final Game g = m == null ? null : m.getGame();
        if (g == null) {
            return;
        }
        if (!g.isGameOver()) {
            for (final Player p : g.getPlayers()) {
                p.intentionalDraw();
            }
            g.setGameOver(GameEndReason.Draw);
        }
        for (final PlayerControllerHuman pch : m.getHumanControllers()) {
            pch.getInputQueue().onGameOver(true); // the game loop waits for nobody any more
        }
        final long until = System.currentTimeMillis() + HexFight.ABORT_WAIT_MS;
        while (HexFight.gameLoopRunning() && System.currentTimeMillis() < until) {
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
