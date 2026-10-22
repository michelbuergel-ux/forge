/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.game.phase;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import forge.game.*;
import forge.game.ability.AbilityKey;
import forge.game.ability.effects.AddTurnEffect;
import forge.game.ability.effects.SkipPhaseEffect;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.cost.CostEnlist;
import forge.game.cost.CostExert;
import forge.game.event.*;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.replacement.ReplacementResult;
import forge.game.replacement.ReplacementType;

import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityNoCleanupDamage;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.util.IHasForgeLog;
import forge.util.TextUtil;

import org.apache.commons.lang3.time.StopWatch;

import java.util.*;


/**
 * <p>
 * Phase class.
 * </p>
 *
 * @author Forge
 * @version $Id: PhaseHandler.java 13001 2012-01-08 12:25:25Z Sloth $
 */
public class PhaseHandler implements java.io.Serializable, IHasForgeLog {
    private static final long serialVersionUID = 5207222278370963197L;

    // used for debugging phase timing
    private final StopWatch sw = new StopWatch();

    // Start turn at 0, since we start even before first untap
    private PhaseType phase = null;
    private int turn = 0;

    private final transient Stack<ExtraTurn> extraTurns = new Stack<>();
    private final transient Map<PhaseType, Stack<ExtraPhase>> extraPhases = Maps.newEnumMap(PhaseType.class);

    private int nUpkeepsThisTurn = 0;
    private int nUpkeepsThisGame = 0;
    private int nCombatsThisTurn = 0;
    private int nMainsThisTurn = 0;
    private int nEndOfTurnsThisTurn = 0;
    private int planarDiceSpecialActionThisTurn = 0;

    private transient Player playerTurn = null;
    private transient Player playerPreviousTurn = null;

    // priority player

    private transient Player pPlayerPriority = null;
    private transient Player pFirstPriority = null;
    /** Shared team turns: the heads holding priority together right now (empty otherwise). */
    private transient volatile List<Player> priorityTeam = Collections.emptyList();
    private final transient Set<Player> skippingPhasePlayers = new HashSet<>();
    private transient Combat combat = null;
    private boolean skipDamageSteps = false;
    private boolean bRepeatCleanup = false;

    /** The need to next phase. */
    private boolean givePriorityToPlayer = false;

    private final transient Game game;


    public PhaseHandler(final Game game0) {
        game = game0;
    }

    public final PhaseType getPhase() {
        return phase;
    }
    private void setPhase(final PhaseType phase0) {
        if (phase == phase0) { return; }
        phase = phase0;
        game.updatePhaseForView();
    }

    public final int getTurn() {
        return turn;
    }

    public final boolean isPlayerTurn(final Player player) {
        if (player == null) {
            return false;
        }
        if (player.equals(playerTurn)) {
            return true;
        }
        // shared team turns: it is the turn of every member of the active team
        return playerTurn != null && isSharedTeamTurns() && player.sameTeam(playerTurn);
    }

    public final boolean isPlayerTurnOfAny(final Iterable<Player> players) {
        for (final Player p : players) {
            if (isPlayerTurn(p)) {
                return true;
            }
        }
        return false;
    }

    public final boolean isSharedTeamTurns() {
        return game.getRules().hasSharedTeamTurns();
    }

    /**
     * The players whose turn it is. Exactly the active player in a normal game; with shared team
     * turns every member of the active team that is still in the game, the turn player first.
     */
    public final List<Player> getActivePlayers() {
        return getTurnTeam(playerTurn);
    }

    /** Like {@link #getActivePlayers()}, minus the players skipping the current phase or step. */
    public final List<Player> getPhaseActors() {
        final List<Player> result = getActivePlayers();
        result.removeAll(skippingPhasePlayers);
        return result;
    }

    private List<Player> getTurnTeam(final Player p) {
        final List<Player> result = Lists.newArrayList();
        if (p == null) {
            return result;
        }
        result.add(p);
        if (isSharedTeamTurns()) {
            for (final Player other : game.getPlayers()) {
                if (other != p && other.sameTeam(p)) {
                    result.add(other);
                }
            }
        }
        return result;
    }

    /** The player representing the team of p in the turn order (p itself in a normal game). */
    private Player getTurnLeader(final Player p) {
        if (p == null || !isSharedTeamTurns()) {
            return p;
        }
        for (final Player other : game.getPlayers()) {
            if (other.sameTeam(p)) {
                return other;
            }
        }
        return p;
    }

    /** Whose turn comes after the turn of p: the next player, or the next team with shared team turns. */
    private Player getNextTurnPlayerAfter(final Player p) {
        Player next = game.getNextPlayerAfter(p);
        if (!isSharedTeamTurns() || next == null) {
            return next;
        }
        int guard = game.getRegisteredPlayers().size();
        while (guard-- > 0 && next != p && next.sameTeam(p)) {
            next = game.getNextPlayerAfter(next);
        }
        return getTurnLeader(next);
    }

    public final Player getPlayerTurn() {
        return playerTurn;
    }
    public final void setPlayerTurn(final Player playerTurn0) {
        if (playerTurn == playerTurn0) { return; }
        playerTurn = playerTurn0;
        game.updatePlayerTurnForView();
        resetPriority();
    }

    public final Player getPreviousPlayerTurn() {
        return playerPreviousTurn;
    }

    public final Player getPriorityPlayer() {
        return pPlayerPriority;
    }
    public final void setPriority(final Player p) {
        pFirstPriority = p;
        pPlayerPriority = p;
    }
    public final void resetPriority() {
        setPriority(playerTurn);
    }

    /**
     * Whether p may act right now: the priority player, or with shared team turns any head of the
     * team that holds priority (a team receives priority as a unit, CR 810.6 for Two-Headed Giant).
     */
    public final boolean hasPriority(final Player p) {
        return p != null && (p.equals(pPlayerPriority) || priorityTeam.contains(p));
    }

    /** The heads sharing priority with p (p first, the others in turn order); just p in a normal game. */
    private List<Player> getPriorityTeam(final Player p) {
        final List<Player> result = Lists.newArrayList(p);
        if (!isSharedTeamTurns()) {
            return result;
        }
        Player next = game.getNextPlayerAfter(p);
        int guard = game.getRegisteredPlayers().size();
        while (guard-- > 0 && next != null && next != p) {
            if (next.sameTeam(p) && !result.contains(next)) {
                result.add(next);
            }
            next = game.getNextPlayerAfter(next);
        }
        return result;
    }

    /** Who receives priority after p: the next player, or with shared team turns the next team. */
    private Player getNextPriorityPlayerAfter(final Player p) {
        Player next = game.getNextPlayerAfter(p);
        if (!isSharedTeamTurns()) {
            return next;
        }
        int guard = game.getRegisteredPlayers().size();
        while (guard-- > 0 && next != null && next != p && next.sameTeam(p)) {
            next = game.getNextPlayerAfter(next);
        }
        return next;
    }

    private boolean isSamePriorityUnit(final Player a, final Player b) {
        if (a == b) {
            return true;
        }
        return isSharedTeamTurns() && a != null && b != null && a.sameTeam(b);
    }

    public final boolean inCombat() { return combat != null; }
    public final Combat getCombat() { return combat; }

    private void advanceToNextPhase() {
        PhaseType oldPhase = phase;
        boolean isTopsy = playerTurn.isPhasesReversed();
        boolean turnEnded = false;

        game.getStack().clearUndoStack(); //can't undo action from previous phase

        if (bRepeatCleanup) { // for when Cleanup needs to repeat itself
            bRepeatCleanup = false;
        } else {
            // If the phase that's ending has a stack of additional phases
            // Take the LIFO one and move to that instead of the normal one
            ExtraPhase extraPhase = null;
            if (extraPhases.containsKey(phase)) {
                extraPhase = extraPhases.get(phase).pop();
                PhaseType nextPhase = extraPhase.getPhase();
                // If no more additional phases are available, remove it from the map
                // and let the next add, reput the key
                if (extraPhases.get(phase).isEmpty()) {
                    extraPhases.remove(phase);
                }
                setPhase(nextPhase);
            } else {
                turnEnded = PhaseType.isLast(phase, isTopsy);
                setPhase(PhaseType.getNext(phase, isTopsy));
            }

            if (turnEnded) {
                turn++;
                extraPhases.clear();
                game.updateTurnForView();
                game.fireEvent(new GameEventTurnBegan(PlayerView.get(playerTurn), turn));

                for (final Player active : getActivePlayers()) {
                    // Tokens starting game in play should suffer from Sum. Sickness
                    for (final Card c : active.getCardsIn(ZoneType.Battlefield, false)) {
                        if (active.getTurn() > 0 || !c.isStartsGameInPlay()) {
                            c.setSickness(false);
                        }
                    }
                    active.incrementTurn();

                    final int lands = CardLists.count(active.getLandsInPlay(), CardPredicates.UNTAPPED);
                    active.setNumPowerSurgeLands(lands);
                }
            }

            // with shared team turns each head can skip a phase on its own;
            // the phase itself is only skipped if every head skips it
            skippingPhasePlayers.clear();
            for (final Player active : getActivePlayers()) {
                final Map<AbilityKey, Object> repRunParams = AbilityKey.mapFromAffected(active);
                repRunParams.put(AbilityKey.Phase, phase);
                ReplacementResult repres = game.getReplacementHandler().run(ReplacementType.BeginPhase, repRunParams);
                if (repres != ReplacementResult.NotReplaced) {
                    skippingPhasePlayers.add(active);
                }
            }
            if (skippingPhasePlayers.containsAll(getActivePlayers())) {
                skippingPhasePlayers.clear();
                // Currently there is no effect to skip entire beginning phase
                // If in the future that kind of effect is added, need to handle it too.
                // Handle skipping of entire combat phase
                if (phase == PhaseType.COMBAT_BEGIN) {
                    setPhase(PhaseType.COMBAT_END);
                }
                advanceToNextPhase();
                return;
            }

            if (extraPhase != null) {
                for (Trigger deltrig : extraPhase.getDelayedTriggers()) {
                    game.getTriggerHandler().registerThisTurnDelayedTrigger(deltrig);
                }
            }
        }

        String phaseType = oldPhase == phase ? "Repeat" : phase == PhaseType.getNext(oldPhase, isTopsy) ? "" : "Additional";
        game.fireEvent(new GameEventTurnPhase(playerTurn, phase, phaseType));
    }

    private boolean isSkippingPhase(final PhaseType phase) {
        switch (phase) {
            case DRAW:
                // the player (or team) going first skips the first draw step
                return turn == 1 && (game.getPlayers().size() == 2 || isSharedTeamTurns());

            case COMBAT_BEGIN:
            case COMBAT_DECLARE_ATTACKERS:
                for (final Player active : getActivePlayers()) {
                    if (!active.isSkippingCombat()) {
                        return false;
                    }
                }
                return true;

            case COMBAT_DECLARE_BLOCKERS:
                skipDamageSteps = !inCombat() || combat.getAttackers().isEmpty();
                //$FALL-THROUGH$
            case COMBAT_FIRST_STRIKE_DAMAGE:
            case COMBAT_DAMAGE:
                return skipDamageSteps;

            default:
                return false;
        }
    }

    private void onPhaseBegin() {
        boolean skipped = false;

        game.getTriggerHandler().resetActiveTriggers();
        game.getUndoManager().barrier("Phase Begin.");
        if (isSkippingPhase(phase)) {
            skipped = true;
            givePriorityToPlayer = false;
        } else  {
            // Perform turn-based actions
            switch (phase) {
                case UNTAP:
                    givePriorityToPlayer = false;
                    for (final Player actor : getPhaseActors()) {
                        game.getUntap().executeUntil(actor);
                    }
                    game.getUntap().executeAt();
                    break;

                case UPKEEP:
                    nUpkeepsThisTurn++;
                    nUpkeepsThisGame++;
                    for (final Player actor : getPhaseActors()) {
                        game.getUpkeep().executeUntil(actor);
                    }
                    game.getUpkeep().executeAt();

                    for (final Player actor : getPhaseActors()) {
                        if (actor.getCardsIn(ZoneType.Battlefield).anyMatch(Card::isContraption)) {
                            actor.advanceCrankCounter();
                        }
                    }

                    break;

                case DRAW:
                    for (Player p : game.getPlayers()) {
                        p.resetNumDrawnThisDrawStep();
                    }
                    for (final Player actor : getPhaseActors()) {
                        actor.drawCard();
                        // a player set up to draw more each turn (the enemies of a Hex Campaign New Game Plus)
                        final int extra = actor.getRegisteredPlayer() == null ? 0 : actor.getRegisteredPlayer().getExtraDrawsPerTurn();
                        for (int i = 0; i < extra; i++) {
                            actor.drawCard();
                        }
                    }
                    break;

                case MAIN1:
                    nMainsThisTurn++;

                    for (final Player actor : getPhaseActors()) {
                        if (actor.isArchenemy()) {
                            actor.setSchemeInMotion(null);
                        }
                    }

                    GameEntityCounterTable table = new GameEntityCounterTable();
                    // CR 703.4f
                    for (final Player actor : getPhaseActors()) {
                        for (Card c : actor.getCardsIn(ZoneType.Battlefield)) {
                            if (c.isSaga() && c.hasChapter()) {
                                c.addCounter(CounterEnumType.LORE, 1, actor, table);
                            }
                        }
                    }
                    table.replaceCounterEffect(game, null);

                    // CR 703.4g
                    for (final Player actor : getPhaseActors()) {
                        if (actor.getCardsIn(ZoneType.Battlefield).anyMatch(Card::isAttraction)) {
                            actor.rollToVisitAttractions();
                        }
                    }

                    break;

                case COMBAT_BEGIN:
                    nCombatsThisTurn++;
                    combat = new Combat(playerTurn);
                    for (final Player active : getActivePlayers()) {
                        game.getBeginOfCombat().executeUntil(active);
                    }
                    //PhaseUtil.verifyCombat();
                    break;

                case COMBAT_DECLARE_ATTACKERS:
                    combat.initConstraints();
                    game.getStack().freezeStack(null);
                    declareAttackersTurnBasedAction();
                    game.getStack().unfreezeStack();

                    givePriorityToPlayer = inCombat();
                    break;

                case COMBAT_DECLARE_BLOCKERS:
                    combat.removeAbsentCombatants();
                    game.getStack().freezeStack(null);
                    declareBlockersTurnBasedAction();
                    game.getStack().unfreezeStack();
                    break;

                case COMBAT_FIRST_STRIKE_DAMAGE:
                    if (combat.removeAbsentCombatants()) {
                        game.updateCombatForView();
                    }

                    // no first strikers, skip this step
                    if (!combat.assignCombatDamage(true)) {
                        givePriorityToPlayer = false;
                    } else {
                        combat.dealAssignedDamage();
                    }
                    break;

                case COMBAT_DAMAGE:
                    if (combat.removeAbsentCombatants()) {
                        game.updateCombatForView();
                    }

                    if (!combat.assignCombatDamage(false)) {
                        givePriorityToPlayer = false;
                    } else {
                        combat.dealAssignedDamage();
                    }
                    break;

                case COMBAT_END:
                    // End Combat always happens
                    for (final Card c : game.getCardsIn(ZoneType.Battlefield)) {
                        c.onEndOfCombat(playerTurn);
                    }
                    game.getEndOfCombat().executeAt();

                    //SDisplayUtil.showTab(EDocID.REPORT_STACK.getDoc());
                    break;

                case MAIN2:
                    nMainsThisTurn++;
                    //SDisplayUtil.showTab(EDocID.REPORT_STACK.getDoc());
                    break;

                case END_OF_TURN:
                    nEndOfTurnsThisTurn++;
                    for (final Player active : getActivePlayers()) {
                        game.getEndOfTurn().executeUntil(active);
                        active.getController().resetAtEndOfTurn();
                    }

                    game.getEndOfTurn().executeAt();
                    break;

                case CLEANUP:
                    // CR 514.1
                    for (final Player active : getActivePlayers()) {
                        discardToMaximumHandSize(active);
                    }

                    // CR 514.2
                    for (final Card c : game.getCardsIncludePhasingIn(ZoneType.Battlefield)) {
                        if (!StaticAbilityNoCleanupDamage.damageNotRemoved(c)) {
                            c.setDamage(0);
                        }
                        c.setHasBeenDealtDeathtouchDamage(false);
                    }
                    game.getEndOfTurn().executeUntil();
                    for (final Player active : getActivePlayers()) {
                        game.getEndOfTurn().executeUntilEndOfPhase(active);
                        game.getEndOfTurn().registerUntilEndCommand(active);
                        game.getEndOfCombat().registerUntilEndCommand(active);
                    }

                    for (Player player : game.getPlayers()) {
                        player.getController().autoPassCancel(); // autopass won't wrap to next turn
                    }

                    nUpkeepsThisTurn = 0;
                    nCombatsThisTurn = 0;
                    nMainsThisTurn = 0;
                    nEndOfTurnsThisTurn = 0;
                    game.getStack().resetMaxDistinctSources();

                    // CR 514.3
                    givePriorityToPlayer = false;

                    // CR 514.3a - part for state-based actions
                    if (game.getAction().checkStateEffects(true)) {
                        bRepeatCleanup = true;
                        givePriorityToPlayer = true;
                    }
                    break;

                default:
                    break;
            }
        }

        if (!skipped) {
            // Run triggers if phase isn't being skipped
            // (shared team turns: once for each head, so "your upkeep" works for all of them)
            boolean first = true;
            for (final Player actor : getPhaseActors()) {
                final Map<AbilityKey, Object> runParams = AbilityKey.mapFromPlayer(actor);
                //runParams.put(AbilityKey.Phase, phase.nameForScripts);
                if (!first) {
                    runParams.put(AbilityKey.SharedTurnRepeat, true);
                }
                first = false;
                game.getTriggerHandler().runTrigger(TriggerType.Phase, runParams, false);
            }
        }

        // This line fixes Combat Damage triggers not going off when they should
        game.getStack().unfreezeStack();

        // CR 514.3a
        if (phase == PhaseType.CLEANUP && (!game.getStack().isEmpty() || game.getStack().hasSimultaneousStackEntries())) {
            bRepeatCleanup = true;
            givePriorityToPlayer = true;
        }
    }

    private void discardToMaximumHandSize(final Player player) {
        final int handSize = player.getZone(ZoneType.Hand).size();
        final int max = player.getMaxHandSize();
        int numDiscard = player.isUnlimitedHandSize() || handSize <= max || handSize == 0 ? 0 : handSize - max;

        if (numDiscard > 0) {
            final CardZoneTable zoneMovements = new CardZoneTable(game.getLastStateBattlefield(), game.getLastStateGraveyard());
            Map<AbilityKey, Object> moveParams = AbilityKey.newMap();
            AbilityKey.addCardZoneTableParams(moveParams, zoneMovements);

            final CardCollection discarded = new CardCollection();
            List<Card> discardedBefore = Lists.newArrayList(player.getDiscardedThisTurn());
            for (Card c : player.getController().chooseCardsToDiscardToMaximumHandSize(numDiscard)) {
                Card moved = player.discard(c, null, false, moveParams);
                if (moved != null) {
                    discarded.add(moved);
                }
            }
            zoneMovements.triggerChangesZoneAll(game, null);

            if (!discarded.isEmpty()) {
                final Map<AbilityKey, Object> runParams = AbilityKey.mapFromPlayer(player);
                runParams.put(AbilityKey.Cards, discarded);
                runParams.put(AbilityKey.Cause, null);
                runParams.put(AbilityKey.DiscardedBefore, discardedBefore);
                game.getTriggerHandler().runTrigger(TriggerType.DiscardedAll, runParams, false);
            }
        }
    }

    private void onPhaseEnd() {
        // If the Stack isn't empty why is nextPhase being called?
        if (!game.getStack().isEmpty()) {
            throw new IllegalStateException("Phase.nextPhase() is called, but Stack isn't empty.");
        }

        final Map<Player, Integer> lossMap = Maps.newHashMap();
        for (Player p : game.getPlayers()) {
            int burn = p.getManaPool().clearPool(true).size();

            if (p.getManaPool().hasBurn()) {
                final int lost = p.loseLife(burn, false, true, null);
                if (lost > 0) {
                    lossMap.put(p, lost);
                }
            }
        }
        if (!lossMap.isEmpty()) { // Run triggers if any player actually lost life
            final Map<AbilityKey, Object> runLifeLostParams = AbilityKey.mapFromPIMap(lossMap);
            game.getTriggerHandler().runTrigger(TriggerType.LifeLostAll, runLifeLostParams, false);
        }

        switch (phase) {
            case UPKEEP:
                for (final Player active : getActivePlayers()) {
                    for (Card c : game.getCardsIncludePhasingIn(ZoneType.Battlefield)) {
                        c.getDamageHistory().setNotAttackedSinceLastUpkeepOf(active);
                        c.getDamageHistory().setNotBlockedSinceLastUpkeepOf(active);
                        c.getDamageHistory().setNotBeenBlockedSinceLastUpkeepOf(active);
                        if (active.equals(c.getController()) && c.getTurnInZone() < game.getPhaseHandler().getTurn()) {
                            c.setCameUnderControlSinceLastUpkeep(false);
                        }
                    }
                    game.getUpkeep().executeUntilEndOfPhase(active);
                    game.getUpkeep().registerUntilEndCommand(active);
                }
                break;

            case UNTAP:
                for (final Player active : getActivePlayers()) {
                    game.getUntap().executeUntilEndOfPhase(active);
                }
                break;

            case COMBAT_END:
                GameEventCombatEnded eventEndCombat = null;
                if (inCombat()) {
                    List<Card> attackers = combat.getAttackers();
                    List<Card> blockers = combat.getAllBlockers();
                    eventEndCombat = GameEventCombatEnded.fromCards(attackers, blockers);
                }
                endCombat();

                if (eventEndCombat != null) {
                    game.fireEvent(eventEndCombat);
                }
                break;

            case CLEANUP:
                if (!bRepeatCleanup) {
                    // only call onCleanupPhase when Cleanup is not repeated
                    game.onCleanupPhase();
                    // set previous player
                    playerPreviousTurn = this.getPlayerTurn();
                    setPlayerTurn(handleNextTurn());

                    // start effects for next turn (do this first for ControlPlayer)
                    game.getCleanup().executeUntil();
                    // done this after check state effects, so it only has effect next check
                    for (final Player active : getActivePlayers()) {
                        game.getCleanup().executeUntil(active);
                    }

                    handleMultiplayerEffects();

                    // "Trigger" for begin turn to get around a phase skipping
                    boolean firstHead = true;
                    for (final Player active : getActivePlayers()) {
                        final Map<AbilityKey, Object> runParams = AbilityKey.mapFromPlayer(active);
                        if (!firstHead) {
                            runParams.put(AbilityKey.SharedTurnRepeat, true);
                        }
                        firstHead = false;
                        game.getTriggerHandler().runTrigger(TriggerType.TurnBegin, runParams, false);
                    }
                }
                planarDiceSpecialActionThisTurn = 0;
                // Play the End Turn sound
                game.fireEvent(new GameEventTurnEnded());
                game.getUndoManager().barrier("Turn Ended.");
                break;
            default: // no action
        }
    }

    private void declareAttackersTurnBasedAction() {
        final Player whoDeclares = Objects.requireNonNullElse(playerTurn.getDeclaresAttackers(), playerTurn);

        // with shared team turns every head declares the attackers it controls, into the same combat
        final List<Player> attackingPlayers = Lists.newArrayList();
        for (final Player active : getActivePlayers()) {
            if (CombatUtil.canAttack(active)) {
                attackingPlayers.add(active);
            }
        }

        if (!attackingPlayers.isEmpty()) {
            boolean success = false;
            do {
                if (game.isGameOver()) { // they just like to close window at any moment
                    return;
                }

                for (final Player attackingPlayer : attackingPlayers) {
                    final Player declarer = Objects.requireNonNullElse(attackingPlayer.getDeclaresAttackers(), attackingPlayer);
                    if (!game.getUndoManager().isOwner(declarer)) {
                        game.getUndoManager().barrier("Opponent declared attackers");
                    }
                }
                // shared team turns: the heads at their own screens declare at the same time
                declareTogether(attackingPlayers,
                        p -> Objects.requireNonNullElse(p.getDeclaresAttackers(), p),
                        p -> Objects.requireNonNullElse(p.getDeclaresAttackers(), p).getController().declareAttackers(p, combat));
                if (game.isGameOver()) {
                    return;
                }
                combat.removeAbsentCombatants();

                success = CombatUtil.validateAttackers(combat);
                if (!success) {
                    for (final Player attackingPlayer : attackingPlayers) {
                        Objects.requireNonNullElse(attackingPlayer.getDeclaresAttackers(), attackingPlayer)
                                .getController().notifyOfValue(null, null, "Attack declaration invalid");
                    }
                    if (attackingPlayers.size() > 1) {
                        // start over, otherwise the heads would stack up on a combat that is already illegal
                        combat.clearAttackers();
                    }
                    continue;
                }

                final CardCollection untapFromCancel = new CardCollection();
                // do a full loop first so attackers can't be used to pay for Propaganda
                for (final Card attacker : combat.getAttackers()) {
                    if (!attacker.attackVigilance()) {
                        // set tapped to true without firing triggers because it may affect propaganda costs
                        attacker.setTapped(true);
                        untapFromCancel.add(attacker);
                    }
                }

                // CR 508.1g
                List<Card> possibleExerters = CombatUtil.getOptionalAttackCostCreatures(combat.getAttackers(), CostExert.class);
                if (!possibleExerters.isEmpty()) {
                    if (attackingPlayers.size() > 1) {
                        final List<Card> chosen = Lists.newArrayList();
                        for (final Player attackingPlayer : attackingPlayers) {
                            final List<Card> own = CardLists.filterControlledBy(possibleExerters, attackingPlayer);
                            if (!own.isEmpty()) {
                                chosen.addAll(attackingPlayer.getController().exertAttackers(own));
                            }
                        }
                        possibleExerters = chosen;
                    } else {
                        possibleExerters = whoDeclares.getController().exertAttackers(possibleExerters);
                    }
                }

                List<Card> possibleEnlisters = CombatUtil.getOptionalAttackCostCreatures(combat.getAttackers(), CostEnlist.class);
                if (!possibleEnlisters.isEmpty()) {
                    // TODO might want to skip if can't be paid
                    if (attackingPlayers.size() > 1) {
                        final List<Card> chosen = Lists.newArrayList();
                        for (final Player attackingPlayer : attackingPlayers) {
                            final List<Card> own = CardLists.filterControlledBy(possibleEnlisters, attackingPlayer);
                            if (!own.isEmpty()) {
                                chosen.addAll(attackingPlayer.getController().enlistAttackers(own));
                            }
                        }
                        possibleEnlisters = chosen;
                    } else {
                        possibleEnlisters = whoDeclares.getController().enlistAttackers(possibleEnlisters);
                    }
                    possibleExerters.addAll(possibleEnlisters);
                }

                for (final Card attacker : combat.getAttackers()) {
                    // TODO currently doesn't refund previous attackers (can really only happen if you cancel paying for a creature with an attack requirement that could be satisfied without a tax)
                    final boolean canAttack = CombatUtil.checkPropagandaEffects(game, attacker, combat, possibleExerters);

                    if (!canAttack) {
                        combat.removeFromCombat(attacker);
                        if (untapFromCancel.contains(attacker)) {
                            attacker.setTapped(false);
                        }
                        success = CombatUtil.validateAttackers(combat);
                        if (!success) {
                            for (Card c : untapFromCancel) {
                                c.setTapped(false);
                            }
                            // might have been sacrificed while paying
                            combat.removeAbsentCombatants();
                            combat.initConstraints();
                            break;
                        }
                    }
                }
            } while (!success);

            CardCollection tapped = new CardCollection();
            for (final Card attacker : combat.getAttackers()) {
                if (!attacker.attackVigilance()) {
                    attacker.setTapped(false);
                    if (attacker.tap(true, true, null, null)) tapped.add(attacker);
                }
            }
            if (!tapped.isEmpty()) {
                final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
                runParams.put(AbilityKey.Cards, tapped);
                whoDeclares.getGame().getTriggerHandler().runTrigger(TriggerType.TapAll, runParams, false);
            }
        }

        if (game.isGameOver()) { // they just like to close window at any moment
            return;
        }

        // Reset all active Triggers
        game.getTriggerHandler().resetActiveTriggers();

        // Prepare and fire event 'attackers declared'
        Multimap<GameEntity, Card> attackersMap = ArrayListMultimap.create();
        for (GameEntity ge : combat.getDefenders()) {
            attackersMap.putAll(ge, combat.getAttackersOf(ge));
        }
        game.fireEvent(new GameEventAttackersDeclared(playerTurn, attackersMap));

        // fire AttackersDeclared trigger
        if (!combat.getAttackers().isEmpty() && isSharedTeamTurns()) {
            // every head that attacks "attacks" on its own, with the creatures it controls
            for (final Player attackingPlayer : combat.getAttackingPlayers()) {
                final CardCollection ownAttackers = CardLists.filterControlledBy(combat.getAttackers(), attackingPlayer);
                if (ownAttackers.isEmpty()) {
                    continue;
                }
                List<GameEntity> attackedTarget = new ArrayList<>();
                for (GameEntity ge : combat.getDefenders()) {
                    final CardCollection ownAttackersOf = CardLists.filterControlledBy(combat.getAttackersOf(ge), attackingPlayer);
                    if (!ownAttackersOf.isEmpty()) {
                        final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
                        runParams.put(AbilityKey.Attackers, ownAttackersOf);
                        runParams.put(AbilityKey.AttackingPlayer, attackingPlayer);
                        runParams.put(AbilityKey.AttackedTarget, Collections.singletonList(ge));
                        attackedTarget.add(ge);
                        game.getTriggerHandler().runTrigger(TriggerType.AttackersDeclaredOneTarget, runParams, false);
                    }
                }
                final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
                runParams.put(AbilityKey.Attackers, ownAttackers);
                runParams.put(AbilityKey.AttackingPlayer, attackingPlayer);
                runParams.put(AbilityKey.AttackedTarget, attackedTarget);
                game.getTriggerHandler().runTrigger(TriggerType.AttackersDeclared, runParams, false);
            }
        } else if (!combat.getAttackers().isEmpty()) {
            List<GameEntity> attackedTarget = new ArrayList<>();
            for (GameEntity ge : combat.getDefenders()) {
                if (!combat.getAttackersOf(ge).isEmpty()) {
                    final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
                    runParams.put(AbilityKey.Attackers, combat.getAttackersOf(ge));
                    runParams.put(AbilityKey.AttackingPlayer, combat.getAttackingPlayer());
                    runParams.put(AbilityKey.AttackedTarget, Collections.singletonList(ge));
                    attackedTarget.add(ge);
                    game.getTriggerHandler().runTrigger(TriggerType.AttackersDeclaredOneTarget, runParams, false);
                }
            }
            final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
            runParams.put(AbilityKey.Attackers, combat.getAttackers());
            runParams.put(AbilityKey.AttackingPlayer, combat.getAttackingPlayer());
            runParams.put(AbilityKey.AttackedTarget, attackedTarget);
            game.getTriggerHandler().runTrigger(TriggerType.AttackersDeclared, runParams, false);
        }

        for (final Card c : combat.getAttackers()) {
            CombatUtil.checkDeclaredAttacker(game, c, combat, true);
        }

        game.getTriggerHandler().resetActiveTriggers();
        game.updateCombatForView();
        game.fireEvent(new GameEventCombatChanged());
    }

    /**
     * Lets each of the players declare (attackers or blockers). With shared team turns the heads
     * that sit at screens of their own (the host's and network guests') are asked at the same
     * time; the others (AI heads, heads sharing one screen) first, one after another. Returns
     * once all of them are done.
     */
    private void declareTogether(final List<Player> players, final java.util.function.Function<Player, Player> declarerOf,
            final java.util.function.Consumer<Player> declare) {
        final List<Player> together = Lists.newArrayList();
        if (isSharedTeamTurns() && players.size() > 1) {
            final Set<Object> keys = new HashSet<>();
            for (final Player p : players) {
                final Player declarer = declarerOf.apply(p);
                if (declarer.getController().canChooseSpellAbilityConcurrently() && keys.add(declarer.getController().getConcurrentChoiceKey())) {
                    together.add(p);
                }
            }
            if (together.size() < 2) {
                together.clear(); // nothing to gain: one by one as usual
            }
        }
        for (final Player p : players) {
            if (!together.contains(p) && !game.isGameOver()) {
                declare.accept(p);
            }
        }
        if (together.isEmpty() || game.isGameOver()) {
            return;
        }
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(together.size());
        final Throwable[] error = new Throwable[1];
        for (final Player p : together) {
            forge.util.ThreadUtil.invokeInGameThread(() -> {
                try {
                    declare.accept(p);
                } catch (final Throwable t) {
                    synchronized (error) {
                        if (error[0] == null) {
                            error[0] = t;
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        try {
            done.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        if (error[0] instanceof RuntimeException re) {
            throw re;
        } else if (error[0] instanceof Error e) {
            throw e;
        } else if (error[0] != null) {
            throw new RuntimeException(error[0]);
        }
    }

    private void declareBlockersTurnBasedAction() {
        // who is attacked, in turn order
        final List<Player> defending = Lists.newArrayList();
        Player next = playerTurn;
        do {
            next = game.getNextPlayerAfter(next);
            if (next != null && combat.isPlayerAttacked(next)) {
                defending.add(next);
            }
        } while (next != playerTurn && next != null);

        if (isSharedTeamTurns()) {
            // team mates at their own screens block at the same time; then each one's blocks are checked
            final List<Player> declaring = Lists.newArrayList();
            for (final Player p : defending) {
                if (mayDeclareBlockers(p)) {
                    declaring.add(p);
                }
                if (game.isGameOver()) {
                    return;
                }
            }
            declareTogether(declaring, d -> Objects.requireNonNullElse(d.getDeclaresBlockers(), d),
                    d -> Objects.requireNonNullElse(d.getDeclaresBlockers(), d).getController().declareBlockers(d, combat));
            for (final Player p : defending) {
                if (game.isGameOver()) { // they just like to close window at any moment
                    return;
                }
                finishBlockers(p);
            }
        } else {
            for (final Player p : defending) {
                if (mayDeclareBlockers(p)) {
                    // If not replaced, run normal declare blockers
                    Objects.requireNonNullElse(p.getDeclaresBlockers(), p).getController().declareBlockers(p, combat);
                }
                if (game.isGameOver()) { // they just like to close window at any moment
                    return;
                }
                finishBlockers(p);
            }
        }

        // CR 509.2
        combat.orderBlockersForDamageAssignment();
        // CR 509.3
        combat.orderAttackersForDamageAssignment();

        combat.removeAbsentCombatants();

        combat.fireTriggersForUnblockedAttackers(game);

        final List<Card> declaredBlockers = combat.getAllBlockers();
        if (!declaredBlockers.isEmpty()) {
            final List<Card> blockedAttackers = Lists.newArrayList();
            for (final Card blocker : declaredBlockers) {
                for (final Card blockedAttacker : combat.getAttackersBlockedBy(blocker)) {
                    if (!blockedAttackers.contains(blockedAttacker)) {
                        blockedAttackers.add(blockedAttacker);
                    }
                }
            }
            final Map<AbilityKey, Object> bdRunParams = AbilityKey.newMap();
            bdRunParams.put(AbilityKey.Blockers, declaredBlockers);
            bdRunParams.put(AbilityKey.Attackers, blockedAttackers);
            game.getTriggerHandler().runTrigger(TriggerType.BlockersDeclared, bdRunParams, false);
        }

        for (final Card c1 : combat.getAllBlockers()) {
            if (c1.getDamageHistory().getCreatureBlockedThisCombat()) {
                continue;
            }

            final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
            runParams.put(AbilityKey.Blocker, c1);
            runParams.put(AbilityKey.Attackers, combat.getAttackersBlockedBy(c1));
            game.getTriggerHandler().runTrigger(TriggerType.Blocks, runParams, false);

            c1.getDamageHistory().setCreatureBlockedThisCombat(true);
            c1.getDamageHistory().clearNotBlockedSinceLastUpkeepOf();
        }

        List<Card> blocked = Lists.newArrayList();
        Map<Integer, Card> lkiCache = Maps.newHashMap();

        for (final Card a : combat.getAttackers()) {
            if (combat.isBlocked(a)) {
                a.getDamageHistory().clearNotBeenBlockedSinceLastUpkeepOf();
            }

            final List<Card> blockers = combat.getBlockers(a);
            if (blockers.isEmpty()) {
                continue;
            }

            blocked.add(a);

            {
                final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
                runParams.put(AbilityKey.Attacker, a);
                runParams.put(AbilityKey.Blockers, blockers);
                runParams.put(AbilityKey.Defender, combat.getDefenderByAttacker(a));
                runParams.put(AbilityKey.DefendingPlayer, combat.getDefenderPlayerByAttacker(a));
                game.getTriggerHandler().runTrigger(TriggerType.AttackerBlocked, runParams, false);
            }

            // Run this trigger once for each blocker
            for (final Card b : blockers) {
                b.addBlockedThisTurn(CardCopyService.getLKICopy(a, lkiCache));
                a.addBlockedByThisTurn(CardCopyService.getLKICopy(b, lkiCache));

            	final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
                runParams.put(AbilityKey.Attacker, a);
                runParams.put(AbilityKey.Blocker, b);
            	game.getTriggerHandler().runTrigger(TriggerType.AttackerBlockedByCreature, runParams, false);
            }

            a.getDamageHistory().setCreatureGotBlockedThisCombat(true);
        }

        if (!blocked.isEmpty()) {
            final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
            runParams.put(AbilityKey.Attackers, blocked);
            game.getTriggerHandler().runTrigger(TriggerType.AttackerBlockedOnce, runParams, false);
        }

        game.updateCombatForView();
        game.fireEvent(new GameEventCombatChanged());
    }

    public void restart() {
        extraPhases.clear();
        extraTurns.clear();
        turn = 0;
    }


    /** May the player declare blockers (it can block, and no replacement effect such as Camouflage does it)? */
    private boolean mayDeclareBlockers(final Player p) {
        // Apply Odric's effect here
        final Player whoDeclaresBlockers = Objects.requireNonNullElse(p.getDeclaresBlockers(), p);
        if (!CombatUtil.canBlock(p, combat)) {
            return false;
        }
        // Replacement effects (for Camouflage)
        final Map<AbilityKey, Object> repRunParams = AbilityKey.mapFromAffected(p);
        repRunParams.put(AbilityKey.Player, whoDeclaresBlockers);
        final ReplacementResult repres = game.getReplacementHandler().run(ReplacementType.DeclareBlocker, repRunParams);
        if (repres != ReplacementResult.NotReplaced) {
            return false;
        }
        if (!game.getUndoManager().isOwner(whoDeclaresBlockers)) {
            game.getUndoManager().barrier("Opponent declared blockers");
        }
        return true;
    }

    /** After a player declared blockers: block costs, blockers that can't block alone, and the event. */
    private void finishBlockers(final Player p) {

        // Handles removing cards like Mogg Flunkies from combat if group block didn't occur
        for (Card blocker : CardLists.filterControlledBy(combat.getAllBlockers(), p)) {
            final List<Card> attackers = combat.getAttackersBlockedBy(blocker);
            for (Card attacker : attackers) {
                boolean hasPaid = CombatUtil.payRequiredBlockCosts(game, blocker, attacker);

                if (!hasPaid) {
                    combat.removeBlockAssignment(attacker, blocker);
                }
            }
        }

        // We may need to do multiple iterations removing blockers, since removing one may invalidate
        // others. The loop below is structured so that if no blockers were removed, no extra passes
        // are needed.
        boolean reachedSteadyState;
        do {
            reachedSteadyState = true;
            List<Card> remainingBlockers = CardLists.filterControlledBy(combat.getAllBlockers(), p);
            for (Card c : remainingBlockers) {
                boolean removeBlocker = false;
                boolean cantBlockAlone = c.hasKeyword("CARDNAME can't attack or block alone.") || c.hasKeyword("CARDNAME can't block alone.");
                if (remainingBlockers.size() < 2 && cantBlockAlone) {
                    removeBlocker = true;
                } else if (remainingBlockers.size() < 3 && c.hasKeyword("CARDNAME can't block unless at least two other creatures block.")) {
                    removeBlocker = true;
                } else if (c.hasKeyword("CARDNAME can't block unless a creature with greater power also blocks.")) {
                    removeBlocker = true;
                    int power = c.getNetPower();
                    // Note: This is O(n^2), but there shouldn't generally be many creatures with the above keyword.
                    for (Card c2 : remainingBlockers) {
                        if (c2.getNetPower() > power) {
                            removeBlocker = false;
                            break;
                        }
                    }
                }
                if (removeBlocker) {
                    combat.undoBlockingAssignment(c);
                    reachedSteadyState = false;
                }
            }
        } while (!reachedSteadyState);

        // Player is done declaring blockers - redraw UI at this point

        // map: defender => (many) attacker => (many) blocker
        Map<GameEntity, Multimap<Card, Card>> blockers = Maps.newHashMap();
        for (GameEntity ge : combat.getDefendersControlledBy(p)) {
            Multimap<Card, Card> protectThisDefender = MultimapBuilder.hashKeys().arrayListValues().build();
            for (Card att : combat.getAttackersOf(ge)) {
                protectThisDefender.putAll(att, combat.getBlockers(att).isEmpty() ? List.of(att) : combat.getBlockers(att));
            }
            blockers.put(ge, protectThisDefender);
        }
        game.fireEvent(new GameEventBlockersDeclared(p, blockers));
    }

    private Player handleNextTurn() {
        game.getStack().onNextTurn();

        game.getTriggerHandler().clearThisTurnDelayedTrigger();

        Player next = getNextActivePlayer();
        while (!next.isInGame()) {
            next = getNextActivePlayer();
        }

        for (final Player member : getTurnTeam(next)) {
            game.getTriggerHandler().handlePlayerDefinedDelTriggers(member);
        }

        for (final Card c : game.getCardsIncludePhasingIn(ZoneType.Battlefield)) {
            c.setStartedTheTurnUntapped(c.isUntapped());
        }

        game.setMonarchBeginTurn(game.getMonarch());

        if (game.getRules().hasAppliedVariant(GameType.Planechase)) {
            for (Card p :game.getActivePlanes()) {
                if (p != null) {
                    p.setController(next, 0);
                    game.getAction().controllerChangeZoneCorrection(p);
                }
            }
        }
        return next;
    }

    private Player getNextActivePlayer() {
        ExtraTurn extraTurn = !extraTurns.isEmpty() ? extraTurns.pop() : null;
        // with shared team turns a turn always belongs to a whole team, represented by its first member
        Player nextPlayer = extraTurn != null ? getTurnLeader(extraTurn.getPlayer()) : getNextTurnPlayerAfter(playerTurn);
        // The bottom of the extra turn stack is the normal turn
        boolean isExtraTurn = !extraTurns.isEmpty();
        final List<Player> nextTeam = getTurnTeam(nextPlayer);

        boolean turnSkipped = false;
        for (final Player member : nextTeam) {
            member.setExtraTurnCount(getExtraTurnForPlayer(member));

            final Map<AbilityKey, Object> repRunParams = AbilityKey.mapFromAffected(member);
            repRunParams.put(AbilityKey.ExtraTurn, isExtraTurn);
            ReplacementResult repres = game.getReplacementHandler().run(ReplacementType.BeginTurn, repRunParams);
            if (repres != ReplacementResult.NotReplaced) {
                // if one head skips its turn, the team skips it
                turnSkipped = true;
                break;
            }
        }
        if (turnSkipped) {
            if (extraTurn == null) {
                setPlayerTurn(nextPlayer);
            }
            return getNextActivePlayer();
        }

        for (final Player member : nextTeam) {
            member.setExtraTurn(isExtraTurn);
        }
        if (extraTurn != null) {
            for (Trigger deltrig : extraTurn.getDelayedTriggers()) {
                game.getTriggerHandler().registerThisTurnDelayedTrigger(deltrig);
            }
            if (extraTurn.isSkipUntap()) {
                for (final Player member : nextTeam) {
                    SkipPhaseEffect.createSkipPhaseEffect(extraTurn.getSkipUntapSA(), member, null, null, "Untap");
                }
            }
            if (extraTurn.isCantSetSchemesInMotion()) {
                AddTurnEffect.createCantSetSchemesInMotionEffect(extraTurn.getCantSetSchemesInMotionSA());
            }
        }
        return nextPlayer;
    }

    public final synchronized boolean is(final PhaseType phase0, final Player player0) {
        return phase == phase0 && isPlayerTurn(player0);
    }
    public final synchronized boolean is(final PhaseType phase0) {
        return phase == phase0;
    }

    public final Player getNextTurn() {
        if (extraTurns.isEmpty()) {
            return getNextTurnPlayerAfter(playerTurn);
        }
        return getTurnLeader(extraTurns.peek().getPlayer());
    }

    public final ExtraTurn addExtraTurn(final Player player) {
        Player previous = null;
        // use a stack to handle extra turns, make sure the bottom of the stack restores original turn order
        if (extraTurns.isEmpty()) {
            extraTurns.push(new ExtraTurn(getNextTurnPlayerAfter(playerTurn)));
        } else {
            previous = extraTurns.peek().getPlayer();
        }

        ExtraTurn result = extraTurns.push(new ExtraTurn(player));
        for (final Player p : game.getPlayers()) {
            p.setExtraTurnCount(getExtraTurnForPlayer(p));
        }

        // get all players where the view should be updated
        List<Player> toUpdate = Lists.newArrayList(player);
        if (previous != null) {
            toUpdate.add(previous);
        }
        game.fireEvent(new GameEventPlayerStatsChanged(toUpdate));

        return result;
    }

    /**
     * Add an extra phase between afterPhase and nextPhase
     * @param afterPhase The phase to add extra phase after
     * @param extraPhaseList The list of extra phase(s) to be added
     * @param nextPhase The original next phase following afterPhase, after extra phase the flow will return to this phase
     * @return returns the added ExtraPhase object
     */
    public final ExtraPhase addExtraPhase(final PhaseType afterPhase, final List<PhaseType> extraPhaseList, PhaseType nextPhase) {
        // 500.8. Some effects can add phases to a turn. They do this by adding the phases directly after the specified phase.
        // If multiple extra phases are created after the same phase, the most recently created phase will occur first.
        for (int i = 0; i < extraPhaseList.size(); i++) {
            PhaseType extra = extraPhaseList.get(i);
            if (!extraPhases.containsKey(extra)) {
                extraPhases.put(extra, new Stack<>());
            }
            if (i < extraPhaseList.size() - 1 ) {
                extraPhases.get(extra).push(new ExtraPhase(extraPhaseList.get(i + 1)));
            } else {
                if (extraPhases.containsKey(afterPhase) && !extraPhases.get(afterPhase).isEmpty()) {
                    // Extra phase(s) was inserted already, link to the first step of inserted extra phase(s)
                    extraPhases.get(extra).push(extraPhases.get(afterPhase).pop());
                } else {
                    extraPhases.get(extra).push(new ExtraPhase(nextPhase));
                }
            }
        }
        if (!extraPhases.containsKey(afterPhase)) {
            extraPhases.put(afterPhase, new Stack<>());
        }
        return extraPhases.get(afterPhase).push(new ExtraPhase(extraPhaseList.get(0)));
    }

    public final boolean hasExtraPhaseAfter(final PhaseType afterPhase, final PhaseType extraPhase) {
        final Stack<ExtraPhase> phases = extraPhases.get(afterPhase);
        return phases != null && !phases.isEmpty() && phases.peek().getPhase() == extraPhase;
    }

    public final boolean isFirstCombat() {
        return nCombatsThisTurn == 1;
    }
    public final int getNumCombat() {
        return nCombatsThisTurn;
    }

    public final int getNumUpkeep() {
        return nUpkeepsThisTurn;
    }

    public final boolean isFirstUpkeep() {
        return is(PhaseType.UPKEEP) && nUpkeepsThisTurn == 0;
    }

    public final boolean isFirstUpkeepThisGame() {
        return is(PhaseType.UPKEEP) && nUpkeepsThisGame == 0;
    }

    public final int getNumMain() {
        return nMainsThisTurn;
    }

    public final boolean beforeFirstPostCombatMainEnd() {
        return nMainsThisTurn <= (is(PhaseType.MAIN2) ? 2 : 1);
    }

    public final boolean skippedDeclareBlockers() {
        return skipDamageSteps;
    }

    public final int getNumEndOfTurn() {
        return nEndOfTurnsThisTurn;
    }

    private final static boolean DEBUG_PHASES = false;

    public void setupFirstTurn(Player goesFirst, Runnable startGameHook) {
        if (phase != null) {
            throw new IllegalStateException("Turns already started, call this only once per game");
        }

        setPlayerTurn(getTurnLeader(goesFirst));
        advanceToNextPhase();
        onPhaseBegin();

        // don't even offer priority, because it's untap of 1st turn now
        givePriorityToPlayer = false;

        if (startGameHook != null) {
            startGameHook.run();
            givePriorityToPlayer = true;
        }
    }

    public void startFirstTurn(Player goesFirst) {
        startFirstTurn(goesFirst, null);
    }
    public void startFirstTurn(Player goesFirst, Runnable startGameHook) {
        setupFirstTurn(goesFirst, startGameHook);
        mainGameLoop();
    }

    public void mainGameLoop() {
        // MAIN GAME LOOP
        while (!game.isGameOver() && !(game.getAge() == GameStage.RestartedByKarn)) {
            mainLoopStep();
        }
    }

    public void mainLoopStep() {
        if (givePriorityToPlayer && isSharedTeamTurns() && getPriorityTeam(pPlayerPriority).size() > 1) {
            if (!teamPriorityStep()) {
                return;
            }
        } else if (givePriorityToPlayer) {
            if (DEBUG_PHASES) {
                sw.start();
            }

            List<SpellAbility> chosenSa = null;

            int loopCount = 0;
            do {
                if (checkStateBasedEffects()) {
                    // state-based effects check could lead to game over
                    return;
                }
                // CR 117.3 A player receives priority once state-based actions are done, and again after each thing they do
                game.fireEvent(new GameEventPlayerPriority(PlayerView.get(playerTurn), phase, PlayerView.get(getPriorityPlayer())));
                game.stashGameState();

                chosenSa = pPlayerPriority.getController().chooseSpellAbilityToPlay();

                // this needs to come after chosenSa so it sees you conceding on own turn
                if (playerTurn.hasLost() && pPlayerPriority.equals(playerTurn) && pFirstPriority.equals(playerTurn)) {
                    // If the active player has lost, and they have priority, set the next player to have priority
                    System.out.println("Active player is no longer in the game...");
                    pPlayerPriority = game.getNextPlayerAfter(getPriorityPlayer());
                    pFirstPriority = pPlayerPriority;
                }

                if (chosenSa == null) {
                    break; // that means 'I pass'
                }

                // An opponent (AI or remote human) acted: the player now knows something, so no undo past this point
                if (!game.getUndoManager().isOwner(pPlayerPriority)) {
                    game.getUndoManager().barrier("Opponent acted");
                }

                if (DEBUG_PHASES) {
                    System.out.print("... " + pPlayerPriority + " plays " + chosenSa);
                }

                playChosenSpellAbilities(chosenSa);
                loopCount++;
            } while (loopCount < 999 || !pPlayerPriority.getController().isAI());

            if (loopCount >= 999 && pPlayerPriority.getController().isAI()) {
                aiLog.warn("AI looped too much with: " + chosenSa);
            }

            if (DEBUG_PHASES) {
                sw.stop();
                System.out.print("... passed in " + sw.getTime()/1000f + " s\n");
                System.out.println("\t\tStack: " + game.getStack());
                sw.reset();
            }
        }
        else if (DEBUG_PHASES) {
            System.out.print(" >> (no priority given to " + getPriorityPlayer() + ")\n");
        }

        Player nextPlayer = getNextPriorityPlayerAfter(getPriorityPlayer());

        if (game.isGameOver() || nextPlayer == null) {
            // conceded?
            return;
        }

        if (DEBUG_PHASES) {
            System.out.println(TextUtil.concatWithSpace(playerTurn.toString(),TextUtil.addSuffix(phase.toString(),":"), pPlayerPriority.toString(),"is active, previous was", nextPlayer.toString()));
        }
        if (isSamePriorityUnit(pFirstPriority, nextPlayer)) {
            if (game.getStack().isEmpty()) {
                if (playerTurn.hasLost()) {
                    setPriority(game.getNextPlayerAfter(playerTurn));
                } else {
                    setPriority(playerTurn);
                }

                givePriorityToPlayer = true;
                onPhaseEnd();
                advanceToNextPhase();
                onPhaseBegin();
            }
            else if (!game.getStack().hasSimultaneousStackEntries()) {
                game.getStack().resolveStack();
            }
        } else {
            pPlayerPriority = nextPlayer;
        }

        // If ever the karn's ultimate resolved
        if (game.getAge() == GameStage.RestartedByKarn) {
            setPhase(null);
            game.updatePhaseForView();
            game.fireEvent(new GameEventGameRestarted(PlayerView.get(playerTurn)));
            return;
        }

        for (final Player p : game.getPlayers()) {
            p.setHasPriority(getPriorityPlayer() == p);
        }
    }

    /** The priority player plays what it chose (CR 117.3c: it receives priority afterward). */
    private void playChosenSpellAbilities(final List<SpellAbility> chosenSa) {
        boolean rollback = false;
        for (SpellAbility sa : chosenSa) {
            Card saHost = sa.getHostCard();
            final Zone originZone = saHost.getZone();
            final CardZoneTable triggerList = new CardZoneTable(game.getLastStateBattlefield(), game.getLastStateGraveyard());

            if (pPlayerPriority.getController().playChosenSpellAbility(sa)) {
                // 117.3c If a player has priority when they cast a spell, activate an ability, [play a land]
                // that player receives priority afterward.
                pFirstPriority = pPlayerPriority; // all opponents have to pass before stack is allowed to resolve
            } else if (game.EXPERIMENTAL_RESTORE_SNAPSHOT) {
                rollback = true;
            }

            saHost = game.getCardState(saHost);
            final Zone currentZone = saHost.getZone();

            // Need to check if Zone did change
            if (currentZone != null && originZone != null && !currentZone.equals(originZone) && (sa.isSpell() || sa.isLandAbility())) {
                // currently there can be only one Spell put on the Stack at once, or Land Abilities be played
                triggerList.put(originZone.getZoneType(), currentZone.getZoneType(), saHost);
                triggerList.triggerChangesZoneAll(game, sa);
            }
        }
        // Don't copy last state if we're in the middle of rolling back a spell...
        if (!rollback) {
            game.copyLastState();
        }
    }

    private record PriorityChoice(Player player, List<SpellAbility> chosen, Throwable error) {}

    /**
     * Shared team turns: the team of the priority player holds priority as a unit. Every head may act
     * while it holds it; the team passes once all heads have passed in a row. Heads that can be asked
     * concurrently (humans, each at their own screen) are prompted at the same time, the first one to
     * act plays; the others are asked again afterwards. Everything is still played one by one on
     * this thread, so the stack works as usual.
     *
     * @return false if the game ended (or state-based actions must be looked at again first)
     */
    private boolean teamPriorityStep() {
        final Player first = pPlayerPriority;
        final Set<Player> passed = new HashSet<>();
        int aiLoops = 0;
        try {
            while (true) {
                if (checkStateBasedEffects()) {
                    return false;
                }
                final List<Player> heads = getPriorityTeam(first);
                heads.removeIf(Player::hasLost);
                if (heads.isEmpty() || game.isGameOver()) {
                    return !game.isGameOver();
                }
                priorityTeam = Collections.unmodifiableList(heads);
                for (final Player p : game.getPlayers()) {
                    p.setHasPriority(heads.contains(p));
                }
                if (passed.containsAll(heads)) {
                    pPlayerPriority = first;
                    return true;
                }

                game.fireEvent(new GameEventPlayerPriority(PlayerView.get(playerTurn), phase, PlayerView.get(first)));
                game.stashGameState();

                // heads that can't be asked at the same time first, one by one (AI, shared screens)
                final List<Player> concurrent = Lists.newArrayList();
                final Set<Object> keys = new HashSet<>();
                PriorityChoice choice = null;
                for (final Player head : heads) {
                    if (passed.contains(head)) {
                        continue;
                    }
                    if (head.getController().canChooseSpellAbilityConcurrently()
                            && keys.add(head.getController().getConcurrentChoiceKey())) {
                        concurrent.add(head);
                        continue;
                    }
                    pPlayerPriority = head;
                    final List<SpellAbility> chosen = head.getController().chooseSpellAbilityToPlay();
                    if (chosen == null) {
                        passed.add(head);
                    } else {
                        choice = new PriorityChoice(head, chosen, null);
                        break;
                    }
                }
                if (choice == null && concurrent.size() == 1) {
                    final Player head = concurrent.get(0);
                    pPlayerPriority = head;
                    final List<SpellAbility> chosen = head.getController().chooseSpellAbilityToPlay();
                    if (chosen == null) {
                        passed.add(head);
                    } else {
                        choice = new PriorityChoice(head, chosen, null);
                    }
                } else if (choice == null && !concurrent.isEmpty()) {
                    choice = chooseConcurrently(concurrent, passed);
                }
                if (choice == null) {
                    continue;
                }
                if (game.isGameOver()) {
                    return false;
                }

                final Player actor = choice.player();
                pPlayerPriority = actor;
                if (choice.chosen().isEmpty()) {
                    continue; // e.g. an undo: just ask again
                }
                if (!game.getUndoManager().isOwner(actor)) {
                    game.getUndoManager().barrier("Opponent acted");
                }
                playChosenSpellAbilities(choice.chosen());
                // the game changed: every head may act again before the team passes
                passed.clear();
                if (actor.getController().isAI() && ++aiLoops >= 999) {
                    aiLog.warn("AI looped too much with: " + choice.chosen());
                    passed.add(actor);
                }
            }
        } finally {
            priorityTeam = Collections.emptyList();
        }
    }

    /**
     * Prompts the given heads at the same time. Returns the first decision to act (the other prompts
     * are interrupted and their answers dropped), or null if all of them passed.
     */
    private PriorityChoice chooseConcurrently(final List<Player> heads, final Set<Player> passed) {
        final java.util.concurrent.BlockingQueue<PriorityChoice> answers = new java.util.concurrent.LinkedBlockingQueue<>();
        for (final Player head : heads) {
            head.getController().setConcurrentSpellAbilityChoice(true);
        }
        try {
            for (final Player head : heads) {
                forge.util.ThreadUtil.invokeInGameThread(() -> {
                    List<SpellAbility> chosen = null;
                    Throwable error = null;
                    try {
                        chosen = head.getController().chooseSpellAbilityToPlay();
                    } catch (final Throwable t) {
                        error = t;
                    }
                    answers.add(new PriorityChoice(head, chosen, error));
                });
            }
            PriorityChoice winner = null;
            Throwable error = null;
            for (int open = heads.size(); open > 0; open--) {
                final PriorityChoice answer;
                try {
                    answer = answers.take();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                if (winner != null || error != null) {
                    continue; // too late: someone else acted first
                }
                if (answer.error() != null) {
                    error = answer.error();
                } else if (answer.chosen() == null) {
                    passed.add(answer.player());
                    continue;
                } else {
                    winner = answer;
                }
                final Card played = winner != null && !winner.chosen().isEmpty() ? winner.chosen().get(0).getHostCard() : null;
                for (final Player other : heads) {
                    if (other != answer.player()) {
                        other.getController().interruptSpellAbilityChoice(answer.player(), played);
                    }
                }
            }
            if (error instanceof RuntimeException re) {
                throw re;
            } else if (error instanceof Error e) {
                throw e;
            } else if (error != null) {
                throw new RuntimeException(error);
            }
            return winner;
        } finally {
            for (final Player head : heads) {
                head.getController().setConcurrentSpellAbilityChoice(false);
            }
        }
    }

    private boolean checkStateBasedEffects() {
        final Set<Card> allAffectedCards = new HashSet<>();
        do {
            // CR 704.3 Whenever a player would get priority, the game checks ... for state-based actions,
            game.getAction().checkStateEffects(false, allAffectedCards);
            if (game.isGameOver()) {
                // state-based effects check could lead to game over
                return true;
            }
        } while (game.getStack().addAllTriggeredAbilitiesToStack()); //loop so long as something was added to stack

        if (game.getStack().isEmpty()) {
            game.getStack().clearTriggerRepeats();
        }

        if (!allAffectedCards.isEmpty()) {
            game.fireEvent(new GameEventCardStatsChanged(allAffectedCards));
            // Update flashback views after static abilities have been recalculated,
            // so play-from-zone abilities (e.g. Bolas's Citadel) are reflected
            game.getPlayers().forEach(Player::updateFlashbackForView);
        }
        return false;
    }

    public final boolean devAdvanceToPhase(PhaseType targetPhase) {
        return devAdvanceToPhase(targetPhase, null);
    }
    public final boolean devAdvanceToPhase(PhaseType targetPhase, Runnable resolver) {
        boolean isTopsy = playerTurn.isPhasesReversed();
        while (phase.isBefore(targetPhase, isTopsy)) {
            if (checkStateBasedEffects()) {
                return false;
            }
            if (resolver != null) {
                resolver.run();
            }
            onPhaseEnd();
            advanceToNextPhase();
            onPhaseBegin();
        }
        checkStateBasedEffects();
        return true;
    }

    // this is a hack for the setup game state mode, do not use outside of devSetupGameState code
    // as it avoids calling any of the phase effects that may be necessary in a less enforced context
    public final void devModeSet(final PhaseType phase0, final Player player0, boolean endCombat, int cturn) {
        if (phase0 != null) {
            setPhase(phase0);
        }
        if (player0 != null) {
            setPlayerTurn(player0);
        }
        turn = cturn;

        game.fireEvent(new GameEventTurnPhase(playerTurn, phase, "dev"));
        if (endCombat) {
            endCombat(); // not-null can be created only when declare attackers phase begins
        }
    }
    public final void devModeSet(final PhaseType phase0, final Player player0) {
        devModeSet(phase0, player0, true, 1);
    }

    public final void devModeSet(final PhaseType phase0, final Player player0, int cturn) {
        devModeSet(phase0, player0, true, cturn);
    }

    public final void devModeSet(final PhaseType phase0, final Player player0, boolean endCombat) {
        devModeSet(phase0, player0, endCombat, 0);
    }

    public final void endCombatPhaseByEffect() {
        endCombat();
        game.getAction().checkStateEffects(true);
        setPhase(PhaseType.COMBAT_END);
        advanceToNextPhase();
    }

    public final void endTurnByEffect() {
        extraPhases.clear();
        setPhase(PhaseType.CLEANUP);
        game.fireEvent(new GameEventTurnPhase(playerTurn, phase, ""));
        onPhaseBegin();
    }

    public int getPlanarDiceSpecialActionThisTurn() {
        return planarDiceSpecialActionThisTurn;
    }
    public void incPlanarDiceSpecialActionThisTurn() {
        planarDiceSpecialActionThisTurn++;
    }

    public String debugPrintState(boolean hasPriority) {
        return String.format("%s's %s [%sP] %s", playerTurn, phase.nameForUi, hasPriority ? "+" : "-", getPriorityPlayer());
    }

    // just to avoid exposing variable to outer classes
    public void onStackResolved() {
        givePriorityToPlayer = true;
    }

    public void endCombat() {
        game.getEndOfCombat().executeUntil();
        game.getEndOfCombat().executeUntilEndOfPhase(playerTurn);
        if (inCombat()) {
            combat.endCombat();
            combat = null;
        }
        game.updateCombatForView();
    }

    public void setCombat(Combat combat) {
        this.combat = combat;
    }

    /**
     * returns the continuous extra turn count
     * @param p
     * @return int
     */
    public int getExtraTurnForPlayer(final Player p) {
        if (this.extraTurns.isEmpty() || this.extraTurns.size() < 2) {
            return 0;
        }

        int count = 0;
        // skip the first element
        for (final ExtraTurn et : extraTurns.subList(1, extraTurns.size())) {
            if (!et.getPlayer().equals(p) && !(isSharedTeamTurns() && et.getPlayer().sameTeam(p))) {
                break;
            }
            count += 1;
        }
        return count;
    }

    private void handleMultiplayerEffects() {
        // CR 800.4m When a player leaves the game, any continuous effects with durations that last until that
        // player’s next turn or until a specific point in that turn will last until that turn would have begun
        int oldPlayerIdx = game.getRegisteredPlayers().indexOf(playerPreviousTurn);
        final int playerIdx = game.getRegisteredPlayers().indexOf(playerTurn);
        final int direction = game.getTurnOrder().getShift();
        while (oldPlayerIdx != playerIdx) {
            oldPlayerIdx += direction;
            if (oldPlayerIdx < 0) {
                oldPlayerIdx = game.getRegisteredPlayers().size() - 1;
            } else if (oldPlayerIdx > game.getRegisteredPlayers().size() - 1) {
                oldPlayerIdx = 0;
            }
            Player p = game.getRegisteredPlayers().get(oldPlayerIdx);
            if (p.hasLost()) {
                // CR 702.26n
                Untap.doPhasing(p);

                game.getUntap().executeUntil(p);
                game.getUpkeep().executeUntil(p);
                game.getUpkeep().executeUntilEndOfPhase(p);
                game.getEndOfCombat().executeUntilEndOfPhase(p);
                game.getEndOfTurn().executeUntil(p);
                game.getEndOfTurn().executeUntilEndOfPhase(p);
                game.getCleanup().executeUntil(p);
            }
        }
    }
}
