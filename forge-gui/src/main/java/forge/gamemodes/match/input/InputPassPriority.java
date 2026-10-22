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
package forge.gamemodes.match.input;

import forge.game.Game;
import forge.game.GameView;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.player.actions.PassPriorityAction;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.StackItemView;
import forge.game.undo.UndoManager;
import forge.gamemodes.match.DeclineScope;
import forge.gamemodes.match.SuggestionType;
import forge.gamemodes.match.YieldController;
import forge.gamemodes.match.YieldUpdate;
import forge.gamemodes.net.server.HostingServer;
import forge.gamemodes.net.server.HostingServer.AfkTimeout;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.util.collect.FCollectionView;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.player.PlayerControllerHuman;
import forge.util.ITriggerEvent;
import forge.util.Localizer;
import forge.util.ThreadUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * <p>
 * Input_PassPriority class.
 * </p>
 * 
 * @author Forge
 * @version $Id: InputPassPriority.java 24769 2014-02-09 13:56:04Z Hellfish $
 */
public class InputPassPriority extends InputSyncronizedBase {
    /** Constant <code>serialVersionUID=-581477682214137181L</code>. */
    private static final long serialVersionUID = -581477682214137181L;

    private List<SpellAbility> chosenSa;
    private volatile boolean interrupted;

    private SuggestionType pendingSuggestion = null;
    private String pendingSuggestionMessage = null;

    /** Team games with streamlined priority: a team mate's spell or ability is OK'd by itself after this many seconds. */
    public static final int PARTNER_AUTO_OK_SECONDS = 5;
    /** The running countdown for a team mate's stack item, if any. */
    private ScheduledFuture<?> partnerCountdown;
    private int partnerSecondsLeft;
    /** The player pressed Hold (or started to act): no countdown for this prompt any more. */
    private volatile boolean partnerHeld;

    public InputPassPriority(final PlayerControllerHuman controller) {
        super(controller);
    }

    @Override
    protected void onStop() {
        cancelPartnerCountdown();
        getController().clearActionableCards();
    }

    @Override
    public void showAndWait() {
        final AfkTimeout timeout = HostingServer.armAfkTimeout(getController(), this);
        try {
            super.showAndWait();
        } finally {
            timeout.cancel();
            // interrupted before it was even shown: don't leave it behind on the queue
            if (interrupted && getController().getInputQueue().getInput() == this) {
                getController().getInputQueue().removeInput(this);
            }
        }
    }

    /** Shared team priority: a team mate acted first, this prompt ends without a decision. */
    public void interrupt() {
        interrupted = true;
        cancelPartnerCountdown();
        if (isFinished()) return;
        chosenSa = null;
        stop();
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    /** {@inheritDoc} */
    @Override
    public final void showMessage() {
        if (!getController().isMacroActive() && !isAlreadyYielding()) {
            // Suppress one prompt after a yield ends — avoids "yielded → ended → yield again?" loop
            if (getController().getYieldController().getBoolPref(FPref.YIELD_SUPPRESS_AFTER_END)
                    && getController().getYieldController().didYieldJustEnd()) {
                showNormalPrompt();
                return;
            }

            if (getController().getYieldController().getBoolPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS)) {
                showNormalPrompt();
                return;
            }

            // Both scopes NEVER → skip including stack-transition tracking (no decline state to maintain)
            DeclineScope stackScope = getController().getYieldController().getDeclineScope(FPref.YIELD_DECLINE_SCOPE_STACK_YIELD);
            DeclineScope noActionsScope = getController().getYieldController().getDeclineScope(FPref.YIELD_DECLINE_SCOPE_NO_ACTIONS);
            if (stackScope == DeclineScope.NEVER && noActionsScope == DeclineScope.NEVER) {
                showNormalPrompt();
                return;
            }

            GameView gvForStack = getGameView();
            boolean stackNonEmpty = gvForStack != null && gvForStack.getStack() != null
                    && !gvForStack.getStack().isEmpty();
            getController().getYieldController().onPriorityReceived(stackNonEmpty);

            Localizer loc = Localizer.getInstance();

            if (!getController().getYieldController().isSuggestionDeclined(SuggestionType.STACK_YIELD)
                    && shouldShowStackYieldPrompt()) {
                pendingSuggestion = SuggestionType.STACK_YIELD;
                pendingSuggestionMessage = loc.getMessage("lblCannotRespondToStackYieldPrompt");
                showYieldSuggestionPrompt();
                return;
            }
            if (!getController().getYieldController().isSuggestionDeclined(SuggestionType.NO_ACTIONS)
                    && shouldShowNoActionsPrompt()) {
                pendingSuggestion = SuggestionType.NO_ACTIONS;
                pendingSuggestionMessage = loc.getMessage("lblNoActionsAvailableYieldPrompt");
                showYieldSuggestionPrompt();
                return;
            }
        }

        showNormalPrompt();
    }

    /** Ends this prompt without an action; the game thread then performs a pending undo. */
    public void restartPrompt() {
        if (isFinished()) return;
        chosenSa = new ArrayList<>();   // non-null: no end-turn auto-pass, no awaitNextInput
        stop();
    }

    private void showYieldSuggestionPrompt() {
        // State may have flipped between the initial check and now (e.g. async multiplayer click).
        if (isAlreadyYielding()) {
            pendingSuggestion = null;
            pendingSuggestionMessage = null;
            showNormalPrompt();
            return;
        }

        Localizer loc = Localizer.getInstance();
        String fullMessage = pendingSuggestionMessage;
        DeclineScope scope = getController().getYieldController().getDeclineScope(pendingSuggestion.scopePref());
        if (scope == DeclineScope.STACK) {
            fullMessage += "\n" + loc.getMessage("lblYieldSuggestionDeclineHintStack");
        } else if (scope == DeclineScope.TURN) {
            fullMessage += "\n" + loc.getMessage("lblYieldSuggestionDeclineHint");
        }
        showMessage(fullMessage);
        chosenSa = null;
        getController().getGui().updateButtons(getOwner(),
                loc.getMessage("lblAccept"),
                loc.getMessage("lblDecline"),
                true, true, true);
        getController().getGui().alertUser();
    }

    private void showNormalPrompt() {
        pendingSuggestion = null;
        pendingSuggestionMessage = null;

        getController().pushActionableCards(false);
        final SpellAbilityStackInstance partnerItem = partnerItem();
        if (partnerItem != null) {
            showPartnerPrompt(partnerItem);
            return;
        }
        showMessage(getTurnPhasePriorityMessage(getController().getGame()));
        chosenSa = null;
        Localizer localizer = Localizer.getInstance();
        /*if (getController().canUndoLastAction()) { //allow undoing with cancel button if can undo last action
            getController().getGui().updateButtons(getOwner(), localizer.getMessage("lblOK"), localizer.getMessage("lblUndo") + " (" + getController().getGame().getStack().getUndoStackSize() + ")", true, true, true);
        }*/
        if (getController().canUndoLastAction()) {
            final UndoManager um = getController().getGame().getUndoManager();
            final int n = um.undosLeft();
            final String undoLabel = localizer.getMessage("lblUndo") + (n > 0 ? " (" + n + ")" : "");
            getController().getGui().updateButtons(getOwner(), localizer.getMessage("lblOK"), undoLabel, true, true, true);
        }
        else { //otherwise allow ending turn with cancel button
            getController().getGui().updateButtons(getOwner(), localizer.getMessage("lblOK"), localizer.getMessage("lblEndTurn"), true, true, true);
        }

        getController().getGui().alertUser();
    }

    // ------------------------------------------------------------------ a team mate's spell or ability

    /**
     * The item on top of the stack if a team mate (not this player, not an opponent) put it there,
     * in a game with streamlined priority (Hex Campaign fights); otherwise null.
     */
    private SpellAbilityStackInstance partnerItem() {
        final Game game = getController().getGame();
        final Player me = getController().getPlayer();
        if (game == null || me == null || !game.getRules().hasStreamlinedPriority() || game.getStack().isEmpty()) {
            return null;
        }
        final SpellAbilityStackInstance top = game.getStack().peek();
        final Player by = top == null ? null : top.getActivatingPlayer();
        return by == null || by.equals(me) || by.isOpponentOf(me) ? null : top;
    }

    /** Is the player's "Always OK my partner" on (for this fight)? */
    private boolean alwaysOkPartner() {
        return getController().getYieldController().getBoolPref(FPref.YIELD_AUTO_OK_PARTNER);
    }

    /**
     * A team mate's spell or ability waits for this player's OK: it is shown highlighted with its
     * card, and OK'd by itself after a few seconds (at once with "Always OK my partner"), unless the
     * player holds or starts to act.
     */
    private void showPartnerPrompt(final SpellAbilityStackInstance item) {
        final Localizer loc = Localizer.getInstance();
        final Card source = item.getSourceCard();
        final String what = source == null ? item.getStackDescription() : source.getName();
        final String hint;
        if (partnerHeld) {
            hint = loc.getMessage("lblPartnerHeld");
        } else if (alwaysOkPartner()) {
            hint = loc.getMessage("lblPartnerAlwaysOk");
        } else {
            hint = loc.getMessage("lblPartnerAutoOk", String.valueOf(PARTNER_AUTO_OK_SECONDS));
        }
        // a coloured box on top of the prompt (the desktop prompt shows HTML)
        final String box = "<table width=\"100%\" cellpadding=\"6\" bgcolor=\"#1f6f43\"><tr><td><font color=\"#ffffff\"><b>"
                + escape(loc.getMessage("lblPartnerAction", item.getActivatingPlayer().getName(), what)) + "</b><br>"
                + escape(hint) + "</font></td></tr></table>";
        showMessage(box + getTurnPhasePriorityMessage(getController().getGame()), source == null ? null : source.getView());
        chosenSa = null;
        if (source != null) {
            getController().getGui().setCard(source.getView()); // the card big in the picture panel
        }
        if (partnerHeld) {
            getController().getGui().updateButtons(getOwner(), loc.getMessage("lblOK"), loc.getMessage("lblHold"), true, false, true);
        } else if (alwaysOkPartner()) {
            getController().getGui().updateButtons(getOwner(), loc.getMessage("lblOK"), loc.getMessage("lblHold"), true, true, true);
            startPartnerCountdown(0);
        } else {
            startPartnerCountdown(PARTNER_AUTO_OK_SECONDS);
        }
        getController().getGui().alertUser();
    }

    private static String escape(final String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private synchronized void startPartnerCountdown(final int seconds) {
        cancelPartnerCountdown();
        partnerSecondsLeft = seconds;
        if (seconds <= 0) {
            partnerCountdown = ThreadUtil.delay(0, this::partnerTick);
            return;
        }
        showCountdownButtons();
        partnerCountdown = ThreadUtil.delay(1000, this::partnerTick);
    }

    private void showCountdownButtons() {
        final Localizer loc = Localizer.getInstance();
        getController().getGui().updateButtons(getOwner(), loc.getMessage("lblOK") + " (" + partnerSecondsLeft + ")",
                loc.getMessage("lblHold"), true, true, true);
    }

    /** One second of the countdown has passed; at zero the team mate's item is OK'd. */
    private void partnerTick() {
        synchronized (this) {
            if (partnerCountdown == null || isFinished() || partnerHeld) {
                return;
            }
            partnerSecondsLeft--;
            if (partnerSecondsLeft > 0) {
                showCountdownButtons();
                partnerCountdown = ThreadUtil.delay(1000, this::partnerTick);
                return;
            }
            partnerCountdown = null;
        }
        if (partnerItem() != null) {
            passPriority();
        }
    }

    private synchronized void cancelPartnerCountdown() {
        if (partnerCountdown != null) {
            partnerCountdown.cancel(false);
            partnerCountdown = null;
        }
    }

    /** The player starts to act (picks a card or an ability): the countdown must not pass for them. */
    private void holdForPartner() {
        partnerHeld = true;
        cancelPartnerCountdown();
    }

    /** "Always OK my partner" was switched on while a team mate's item waits: OK it now. */
    public void onAlwaysOkPartnerChanged() {
        if (!isFinished() && !partnerHeld && alwaysOkPartner() && partnerItem() != null) {
            startPartnerCountdown(0);
        }
    }

    private boolean isAlreadyYielding() {
        return getController().getYieldController().isYieldActive();
    }

    private GameView getGameView() {
        return getController().getGui().getGameView();
    }

    private boolean checkHasAvailableActions() {
        Player player = getController().getPlayer();
        if (player == null) return false;
        // Freshened upstream in chooseSpellAbilityToPlay; don't recompute
        return player.getView().hasAvailableActions();
    }

    private boolean shouldShowStackYieldPrompt() {
        GameView gv = getGameView();
        if (gv == null) return false;
        FCollectionView<StackItemView> stack = gv.getStack();
        if (stack == null || stack.isEmpty()) return false;
        return !checkHasAvailableActions();
    }

    private boolean shouldShowNoActionsPrompt() {
        GameView gv = getGameView();
        PlayerView pv = getOwner();
        if (gv == null || pv == null) return false;
        FCollectionView<StackItemView> stack = gv.getStack();
        if (stack != null && !stack.isEmpty()) return false;
        PlayerView currentTurn = gv.getPlayerTurn();
        if (currentTurn != null && (currentTurn.equals(pv) || gv.isPlayerTurn(pv))) {
            // Always suppress on player's first turn (no lands/mana yet)
            if (gv.getTurn() <= gv.getPlayers().size()) return false;
            if (getController().getYieldController().getBoolPref(FPref.YIELD_SUPPRESS_ON_OWN_TURN)) return false;
        }
        return !checkHasAvailableActions();
    }

    /** {@inheritDoc} */
    @Override
    protected final void onOk() {
        if (pendingSuggestion != null) {
            // Defensive: state may have flipped (e.g. async multiplayer click).
            if (isAlreadyYielding()) {
                pendingSuggestion = null;
                pendingSuggestionMessage = null;
                stop();
                return;
            }
            // APINA flipped on between display and accept — drop the now-redundant accept (host-local only)
            if (!getController().isRemoteClient()
                    && getController().getYieldController().getBoolPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS)) {
                pendingSuggestion = null;
                pendingSuggestionMessage = null;
                stop();
                return;
            }
            SuggestionType accepted = pendingSuggestion;
            pendingSuggestion = null;
            pendingSuggestionMessage = null;

            PlayerView self = getOwner();
            YieldController yc = getController().getYieldController();
            if (accepted == SuggestionType.STACK_YIELD) {
                yc.setAutoPassUntilStackEmpty(true, true);
                if (self != null) getController().getGui().applyYieldUpdate(
                        new YieldUpdate.StackYield(self, true, true));
            } else if (accepted == SuggestionType.NO_ACTIONS) {
                // UPKEEP because UNTAP has no priority pass — a marker on UNTAP could never fire.
                if (self != null) {
                    boolean atOrPast = YieldController.isPriorityAtOrPastMarker(
                            getGameView(), self, PhaseType.UPKEEP);
                    yc.setMarker(self, PhaseType.UPKEEP, atOrPast);
                    if (getController().isRemoteClient()) getController().getGui().applyYieldUpdate(
                            new YieldUpdate.SetMarker(self, PhaseType.UPKEEP, atOrPast));
                }
            }
            if (isAlreadyYielding()) {
                stop();
            } else {
                showNormalPrompt();
            }
            return;
        }

        passPriority();
    }

    /** Distinct from clicking OK, which accepts a yield suggestion when one is on screen. */
    public void passPriority() {
        if (isFinished()) return;
        passPriority(() -> {
            getController().macros().addRememberedAction(new PassPriorityAction(
                    getController().getGame().getStack().isEmpty(),
                    getController().getGame().getPhaseHandler().getPhase()));
            stop();
        });
    }

    /** {@inheritDoc} */
    @Override
    protected final void onCancel() {
        if (partnerItem() != null && !partnerHeld) {
            // "Hold": no countdown, the player wants to respond to the team mate's spell
            holdForPartner();
            showNormalPrompt();
            return;
        }
        if (pendingSuggestion != null) {
            getController().getYieldController().declineSuggestion(pendingSuggestion);
            pendingSuggestion = null;
            pendingSuggestionMessage = null;
            showNormalPrompt();
            return;
        }

        if (!getController().tryUndoLastAction()) { //undo if possible
            //otherwise end turn
            passPriority(() -> {
                getController().autoPassUntilEndOfTurn();
                stop();
            });
        }
    }

    @Override
    protected boolean allowAwaitNextInput() {
        return chosenSa == null && !getController().mayAutoPass(); //don't allow awaiting next input if player chose to end the turn or if a spell/ability is chosen
    }

    private void passPriority(final Runnable runnable) {
        if (FModel.getPreferences().getPrefBoolean(FPref.UI_MANA_LOST_PROMPT)) {
            //if gui player has mana floating that will be lost if phase ended right now, prompt before passing priority
            final Game game = getController().getGame();
            if (game.getStack().isEmpty()) { //phase can't end right now if stack isn't empty
                Player player = getController().getPlayer();
                if (player != null && player.getManaPool().willManaBeLostAtEndOfPhase() && player.getLobbyPlayer() == GamePlayerUtil.getGuiPlayer()) {
                    //must invoke in game thread so dialog can be shown on mobile game
                    ThreadUtil.invokeInGameThread(() -> {
                        Localizer localizer = Localizer.getInstance();
                        String message = localizer.getMessage("lblYouHaveManaFloatingInYourManaPoolCouldBeLostIfPassPriority");
                        if (player.getManaPool().hasBurn()) {
                            message += " " + localizer.getMessage("lblYouWillTakeManaBurnDamageEqualAmountFloatingManaLostThisWay");
                        }
                        if (getController().getGui().showConfirmDialog(message, localizer.getMessage("lblManaFloating"), localizer.getMessage("lblOK"), localizer.getMessage("lblCancel"))) {
                            runnable.run();
                        }
                    });
                    return;
                }
            }
        }
        runnable.run(); //just pass priority immediately if no mana floating that would be lost
    }

    public List<SpellAbility> getChosenSa() { return chosenSa; }

    @Override
    protected boolean onCardSelected(final Card card, final List<Card> otherCardsToSelect, final ITriggerEvent triggerEvent) {
        // remove unplayable unless triggerEvent specified, in which case unplayable may be shown as disabled options
        // (so shortcuts are constant regardless of game state)
        List<SpellAbility> abilities = card.getAllPossibleAbilities(getController().getPlayer(), triggerEvent == null); 
        if (abilities.isEmpty()) {
            return false;
        }
        holdForPartner();

        final SpellAbility ability = getController().getAbilityToPlay(card, abilities, triggerEvent);
        if (ability != null) {
            chosenSa = new ArrayList<>();
            chosenSa.add(ability);
            if (otherCardsToSelect != null && ability.isManaAbility()) {
                //if mana ability activated, activate same ability on other cards to select if possible
                String abStr = ability.toUnsuppressedString();
                for (Card c : otherCardsToSelect) {
                    for (SpellAbility ab : c.getAllPossibleAbilities(getController().getPlayer(), true)) {
                        if (ab.toUnsuppressedString().equals(abStr)) {
                            chosenSa.add(ab);
                            break;
                        }
                    }
                }
            }
            stop();
    	}
        return true; //still return true if user cancelled selecting an ability to prevent selecting another card
    }

    @Override
    public String getActivateAction(final Card card) {
        final List<SpellAbility> abilities = card.getAllPossibleAbilities(getController().getPlayer(), true); 
        if (abilities.isEmpty()) {
            return null;
        }
        final SpellAbility sa = abilities.get(0);
        if (sa.isSpell()) {
            return Localizer.getInstance().getMessage("lblCastSpell");
        }
        if (sa.isLandAbility()) {
            return Localizer.getInstance().getMessage("lblPlayLand");
        }
        return Localizer.getInstance().getMessage("lblActivateAbility");
    }

    @Override
    public boolean selectAbility(final SpellAbility ab) {
    	if (ab != null) {
            holdForPartner();
    	    chosenSa = new ArrayList<>();
            chosenSa.add(ab);
            stop();
            return true;
        }
    	return false;
    }
}
