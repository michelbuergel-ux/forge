package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.GamePlayerUtil;

/**
 * Starts the fight of a campaign: all heads of the party as one Multi-Headed Giant team
 * (shared turns and life) against the enemy, on this computer. Heads marked as companions,
 * and every head after the first human one, are played by the AI.
 */
public final class HexBattle {
    public static final int PARTY_TEAM = 0;
    public static final int ENEMY_TEAM = 1;

    /** How a fight ended for the party. */
    public enum Result { WON, LOST, FLED }

    /** Told (on the game thread) how a fight ended and what every head did in it. */
    public interface FightEnd {
        void finished(Result result, HexFightSummary summary);
    }

    private static Result resultOf(final HexFight fight, final forge.game.Game game) {
        if (fight.hasFled()) {
            return Result.FLED;
        }
        return partyWon(game) ? Result.WON : Result.LOST;
    }

    /** A head of the party votes to flee from the fight running on this computer (or to fight on). */
    public static void voteFlee(final forge.game.player.Player voter, final boolean flee) {
        final HexFight fight = HexFight.current();
        if (fight != null) {
            fight.vote(voter, flee);
        }
    }

    private HexBattle() { }

    /** The players of the fight, in seat order: the heads first, the enemy last. */
    public static List<RegisteredPlayer> createPlayers(final HexCampaign campaign, final HexEncounter enc, final boolean allAi) {
        final HexContent content = HexContent.get();
        final List<RegisteredPlayer> players = new ArrayList<>();
        boolean humanSeated = allAi;
        int avatar = 0;
        for (final HexPlayer p : campaign.players) {
            final Deck deck = content.toDeck(p.name, p.deck);
            final RegisteredPlayer rp = new RegisteredPlayer(deck);
            final LobbyPlayer lobbyPlayer;
            if (!p.ai && !humanSeated) {
                humanSeated = true;
                lobbyPlayer = GamePlayerUtil.getGuiPlayer(p.name, avatar, 0, false);
            } else {
                lobbyPlayer = GamePlayerUtil.createAiPlayer(p.name, avatar);
            }
            avatar++;
            rp.setPlayer(lobbyPlayer);
            rp.setTeamNumber(PARTY_TEAM);
            rp.setStartingLife(campaign.partyStartLife());
            applyRelics(campaign, p, rp);
            applyPerksAndEvents(campaign, p, rp);
            players.add(rp);
        }
        addEnemies(campaign, enc, players, avatar);
        return players;
    }

    /**
     * The enemy side: the enemy itself with its equipment in play and, against a party of
     * several heads, more heads: copies of a boss or task enemy, the followers of any other enemy.
     * They are one team with shared turns and a shared life total.
     */
    private static void addEnemies(final HexCampaign campaign, final HexEncounter enc, final List<RegisteredPlayer> players, final int firstAvatar) {
        final HexContent content = HexContent.get();
        final int life = campaign.enemyLife(enc);
        int avatar = firstAvatar;
        final RegisteredPlayer enemy = new RegisteredPlayer(content.loadEnemyDeck(enc));
        enemy.setPlayer(GamePlayerUtil.createAiPlayer(enc.enemyName, avatar++));
        enemy.setTeamNumber(ENEMY_TEAM);
        enemy.setStartingLife(life);
        addEquipment(enemy, enc);
        // an archmage brings its presence and the card that awakens it at half life (the lead head only:
        // the life total is shared, and one awakening is enough)
        final List<forge.item.IPaperCard> bossCards = content.bossCommandCards(enc, campaign.cycle);
        if (!bossCards.isEmpty()) {
            enemy.addExtraCardsInCommandZone(bossCards);
        }
        applyEnemyRelics(campaign, enemy);
        applyCycle(campaign, enc, enemy, 1);
        // a dungeon's enchantment or artifact: on the enemy's side, it affects everybody
        final String ruleCard = campaign.dungeonRuleCard(enc);
        final forge.item.IPaperCard rule = ruleCard == null ? null : content.findStartCard(ruleCard);
        if (rule != null) {
            enemy.addExtraCardsOnBattlefield(java.util.Collections.singletonList(rule));
        }
        players.add(enemy);
        if (enc.archenemy) {
            // an archenemy fights alone, with a deck of schemes (the same ones each time this fight is set up)
            enemy.setSchemes(content.schemeDeck(new java.util.Random(campaign.seed * 31 + campaign.fights() * 7919L + enc.questId)));
            return;
        }
        if (enc.headsAlike()) {
            for (int n = 2; n <= campaign.enemyHeads(); n++) {
                final RegisteredPlayer rp = new RegisteredPlayer(content.loadEnemyDeck(enc));
                rp.setPlayer(GamePlayerUtil.createAiPlayer(enc.enemyName + " " + n, avatar++)); // every player needs a name of its own
                rp.setTeamNumber(ENEMY_TEAM);
                rp.setStartingLife(life);
                addEquipment(rp, enc);
                applyCycle(campaign, enc, rp, n);
                players.add(rp);
            }
            return;
        }
        int head = 2;
        for (final HexEncounter follower : content.followers(enc, campaign.enemyHeads() - 1)) {
            final RegisteredPlayer rp = new RegisteredPlayer(content.loadEnemyDeck(follower));
            rp.setPlayer(GamePlayerUtil.createAiPlayer(follower.enemyName, avatar++));
            rp.setTeamNumber(ENEMY_TEAM);
            rp.setStartingLife(life);
            applyCycle(campaign, enc, rp, head++);
            players.add(rp);
        }
    }

    /**
     * New Game Plus: what every enemy head gets in this cycle (the extra life is in the shared
     * life total already). The cards in play are the same each time the fight is set up.
     */
    private static void applyCycle(final HexCampaign campaign, final HexEncounter enc, final RegisteredPlayer rp, final int head) {
        final HexContent content = HexContent.get();
        final int hand = campaign.advantage(HexAdvantage.HAND);
        if (hand > 0) {
            rp.setStartingHand(rp.getStartingHand() + hand);
        }
        final List<forge.item.IPaperCard> inPlay = new ArrayList<>();
        // New Game Plus: the region's rule (swamp: a Zombie, plains: a Soldier, mountain: a Goblin)
        final HexBiomeRule biome = HexBiomeRule.forFight(campaign, enc);
        if (biome != null && biome.enemyToken != null) {
            final forge.item.IPaperCard token = content.findStartCard(biome.enemyToken);
            if (token != null) {
                inPlay.add(token);
            }
        }
        final forge.item.PaperCard land = content.getCard(enc.color.getBasicLand());
        for (int i = 0; land != null && i < campaign.advantage(HexAdvantage.LANDS); i++) {
            inPlay.add(land);
        }
        final java.util.Random rnd = new java.util.Random(enc.enemyName.hashCode() * 31L + enc.q * 131L + enc.r * 17L
                + campaign.cycle * 7919L + head * 104729L);
        for (int i = 0; i < campaign.advantage(HexAdvantage.BATTLEFIELD); i++) {
            final forge.item.IPaperCard card = content.advantageCard(enc.color, rnd);
            if (card != null) {
                inPlay.add(card);
            }
        }
        if (!inPlay.isEmpty()) {
            rp.addExtraCardsOnBattlefield(inPlay);
        }
        rp.setExtraDrawsPerTurn(campaign.advantage(HexAdvantage.DRAW));
    }

    /** The cards and tokens the enemy starts the fight with (its equipment). */
    private static void addEquipment(final RegisteredPlayer rp, final HexEncounter enc) {
        final List<forge.item.IPaperCard> equipment = new ArrayList<>();
        addStartCards(equipment, enc.startCards);
        if (!equipment.isEmpty()) {
            rp.addExtraCardsOnBattlefield(equipment);
        }
    }

    /** What the relics of a head do at the start of a fight: cards in play and a different hand size. */
    private static void applyRelics(final HexCampaign campaign, final HexPlayer p, final RegisteredPlayer rp) {
        final HexContent content = HexContent.get();
        final List<forge.item.IPaperCard> inPlay = new ArrayList<>();
        int hand = 0;
        for (final String name : p.relics) {
            final HexRelic relic = content.getRelic(name);
            if (relic == null) {
                continue;
            }
            hand += relic.hand;
            addStartCards(inPlay, relic.battlefield);
            if (!relic.randomCards.isEmpty()) {
                // New Game Plus: some of these cards, new ones every fight, more with every cycle
                final List<String> drawn = new ArrayList<>();
                final java.util.Random rnd = java.util.concurrent.ThreadLocalRandom.current();
                for (int i = 0; i < relic.randomPerCycle * campaign.cycle; i++) {
                    drawn.add(relic.randomCards.get(rnd.nextInt(relic.randomCards.size())));
                }
                addStartCards(inPlay, drawn);
            }
            if (relic.avatar != null) {
                // a Vanguard avatar: in the command zone, where its abilities work; its hand size is counted
                // above, its life in the party's shared life (HexCampaign.fightLife)
                final forge.item.PaperCard avatar = content.findAvatar(relic.avatar);
                if (avatar != null) {
                    rp.addExtraCardsInCommandZone(java.util.Collections.singletonList(avatar));
                }
            }
        }
        if (!inPlay.isEmpty()) {
            rp.addExtraCardsOnBattlefield(inPlay);
        }
        if (hand != 0) {
            rp.setStartingHand(Math.max(1, rp.getStartingHand() + hand));
        }
    }

    /** Perks of the head and events of the campaign: more cards in the opening hand, a land in play. */
    private static void applyPerksAndEvents(final HexCampaign campaign, final HexPlayer p, final RegisteredPlayer rp) {
        // New Game Plus: the region's rule (forest: a land, island: a card)
        final HexBiomeRule biome = HexBiomeRule.forFight(campaign, campaign.pendingEncounter);
        final int hand = HexPerk.EXTRA_CARD.count(p) + campaign.boostHand + (biome == null ? 0 : biome.partyHand)
                + campaign.dungeonBuff("HAND");
        if (hand != 0) {
            rp.setStartingHand(Math.max(1, rp.getStartingHand() + hand));
        }
        int lands = HexPerk.START_LAND.count(p) + (campaign.boostLand ? 1 : 0) + (biome == null ? 0 : biome.partyLands)
                + campaign.dungeonBuff("LAND");
        if (lands > 0) {
            // the basic land the deck has most of
            final Map<String, Integer> basics = new HashMap<>();
            for (final String card : p.deck) {
                if (HexContent.get().isBasicLand(card)) {
                    basics.merge(card, 1, Integer::sum);
                }
            }
            String land = HexColor.COLORLESS.getBasicLand();
            int most = 0;
            for (final Map.Entry<String, Integer> e : basics.entrySet()) {
                if (e.getValue() > most) {
                    most = e.getValue();
                    land = e.getKey();
                }
            }
            final List<forge.item.IPaperCard> inPlay = new ArrayList<>();
            final forge.item.PaperCard card = HexContent.get().getCard(land);
            for (; card != null && lands > 0; lands--) {
                inPlay.add(card);
            }
            if (!inPlay.isEmpty()) {
                rp.addExtraCardsOnBattlefield(inPlay);
            }
        }
    }

    /** Some relics also put something onto the enemy's side. */
    private static void applyEnemyRelics(final HexCampaign campaign, final RegisteredPlayer enemy) {
        final List<forge.item.IPaperCard> inPlay = new ArrayList<>();
        for (final HexPlayer p : campaign.players) {
            for (final String name : p.relics) {
                final HexRelic relic = HexContent.get().getRelic(name);
                if (relic != null) {
                    addStartCards(inPlay, relic.enemyBattlefield);
                }
            }
        }
        if (!inPlay.isEmpty()) {
            enemy.addExtraCardsOnBattlefield(inPlay);
        }
    }

    private static void addStartCards(final List<forge.item.IPaperCard> target, final List<String> names) {
        for (final String cardName : names) {
            final forge.item.IPaperCard card = HexContent.get().findStartCard(cardName);
            if (card != null) {
                target.add(card);
            }
        }
    }

    /**
     * Did the party win the game? Asked of the players themselves: the "winning team" of an
     * outcome is only filled in when a team has several members, not for a single head.
     */
    public static boolean partyWon(final forge.game.Game game) {
        if (game == null || game.getOutcome() == null || game.getOutcome().isDraw()) {
            return false;
        }
        for (final forge.game.player.Player p : game.getRegisteredPlayers()) {
            if (p.getTeam() == PARTY_TEAM && p.getOutcome() != null && p.getOutcome().hasWon()) {
                return true;
            }
        }
        return false;
    }

    public static GameRules createRules() {
        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setAppliedVariants(EnumSet.of(GameType.Constructed, GameType.MultiHeadedGiant));
        rules.setGamesPerMatch(1);
        rules.setPlayForAnte(false); // the campaign handles ante itself
        rules.setStreamlinedPriority(true); // fewer OK clicks: auto-pass, no delays, lean phase stops
        rules.setManaBurn(FModel.getPreferences().getPrefBoolean(FPref.LEGACY_MANABURN));
        rules.setOrderCombatants(FModel.getPreferences().getPrefBoolean(FPref.LEGACY_ORDER_COMBATANTS));
        rules.setUseGrayText(FModel.getPreferences().getPrefBoolean(FPref.UI_GRAY_INACTIVE_TEXT));
        return rules;
    }

    /**
     * Starts the pending fight over the network: the host and every connected guest play their
     * own head on their own computer, all other heads are played by the AI.
     * @param onFinished called (on the game thread) with true if the party won, once the game is over
     */
    public static HostedMatch startHosted(final HexSession session, final forge.gamemodes.match.GameLobby lobby, final FightEnd onFinished) {
        final HexCampaign campaign = session.getCampaign();
        final HexEncounter enc = campaign.pendingEncounter;
        if (enc == null) {
            throw new IllegalStateException("No fight pending");
        }
        final HexContent content = HexContent.get();
        final List<RegisteredPlayer> players = new ArrayList<>();
        final Map<RegisteredPlayer, Integer> humanSeats = new java.util.LinkedHashMap<>();
        int avatar = 0;
        for (final HexPlayer p : campaign.players) {
            final RegisteredPlayer rp = new RegisteredPlayer(content.toDeck(p.name, p.deck));
            int seat = -1;
            if (!session.isAiHead(p.name)) {
                for (int i = 0; i < lobby.getNumberOfSlots(); i++) {
                    final forge.gamemodes.match.LobbySlot slot = lobby.getSlot(i);
                    final boolean hostSeat = slot.getType() == forge.gamemodes.match.LobbySlotType.LOCAL && p.name.equals(session.getHostHead());
                    final boolean guestSeat = slot.getType() == forge.gamemodes.match.LobbySlotType.REMOTE && p.name.equals(slot.getName());
                    if (hostSeat || guestSeat) {
                        seat = i;
                        break;
                    }
                }
            }
            if (seat >= 0) {
                final forge.gamemodes.match.LobbySlot slot = lobby.getSlot(seat);
                rp.setPlayer(GamePlayerUtil.getGuiPlayer(p.name, slot.getAvatarIndex(), slot.getSleeveIndex(), false));
                humanSeats.put(rp, seat);
            } else {
                rp.setPlayer(GamePlayerUtil.createAiPlayer(p.name, avatar));
            }
            avatar++;
            rp.setTeamNumber(PARTY_TEAM);
            rp.setStartingLife(campaign.partyStartLife());
            applyRelics(campaign, p, rp);
            applyPerksAndEvents(campaign, p, rp);
            players.add(rp);
        }
        addEnemies(campaign, enc, players, avatar);

        final HostedMatch[] holder = new HostedMatch[1];
        final HexFight fight = new HexFight(campaign, enc);
        // a guest who drops out is played by the AI until they are back
        forge.gamemodes.net.server.FServerManager.getInstance().setAiStandInWhileDisconnected(true);
        final Runnable hook = () -> {
            forge.gamemodes.net.server.FServerManager.getInstance().setAiStandInWhileDisconnected(false);
            final HostedMatch match = holder[0];
            final Result result = resultOf(fight, match == null ? null : match.getGame());
            onFinished.finished(result, fight.end(result));
        };
        holder[0] = lobby.startPreparedMatch(createRules(), players, humanSeats, hook, HexMusic.fightPlaylist(campaign, enc));
        fight.begin(holder[0].getGame());
        return holder[0];
    }

    /**
     * Starts the pending fight of the campaign with the local player at the table.
     * @param onFinished called (on the game thread) with true if the party won, once the game is over
     */
    public static HostedMatch start(final HexCampaign campaign, final FightEnd onFinished) {
        final HexEncounter enc = campaign.pendingEncounter;
        if (enc == null) {
            throw new IllegalStateException("No fight pending");
        }
        final List<RegisteredPlayer> players = createPlayers(campaign, enc, false);
        final Map<RegisteredPlayer, IGuiGame> guis = new HashMap<>();
        for (final RegisteredPlayer rp : players) {
            if (rp.getPlayer() instanceof forge.player.LobbyPlayerHuman) {
                guis.put(rp, GuiBase.getInterface().getNewGuiGame());
            }
        }
        final HostedMatch match = GuiBase.getInterface().hostMatch();
        final HexFight fight = new HexFight(campaign, enc);
        match.setEndGameHook(() -> {
            final Result result = resultOf(fight, match.getGame());
            onFinished.finished(result, fight.end(result));
        });
        match.startMatch(createRules(), null, players, guis, HexMusic.fightPlaylist(campaign, enc));
        fight.begin(match.getGame());
        return match;
    }
}
