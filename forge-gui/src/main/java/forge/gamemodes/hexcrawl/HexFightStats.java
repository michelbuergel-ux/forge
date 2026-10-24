package forge.gamemodes.hexcrawl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.eventbus.Subscribe;

import forge.game.Game;
import forge.game.card.CardView;
import forge.game.event.GameEventCardDamaged;
import forge.game.event.GameEventLandPlayed;
import forge.game.event.GameEventPlayerDamaged;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.event.GameEventTurnPhase;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;

/**
 * Counts, during a fight, what every head of the party does: damage dealt to the enemy side (its
 * heads and creatures), spells cast, lands played, cards drawn, life gained, and which of its cards
 * dealt the most damage. Listens to the game's event bus on the host; the result goes into the campaign.
 */
public final class HexFightStats {
    private final Map<String, HexFightSummary.Head> heads = new LinkedHashMap<>();
    /** Damage per card, per head. */
    private final Map<String, Map<String, Integer>> cardDamage = new HashMap<>();
    /** The game, for the per-turn counters of the players (cards drawn, life gained). */
    private Game game;
    /** The last turn whose draws and life gain were counted (each turn once). */
    private int countedTurn = -1;
    /** Cards in hand before the first turn (the opening hand): not draws. */
    private final Map<String, Integer> openingDraws = new HashMap<>();
    private boolean started;

    /** @param party the names of the party's heads, in seat order */
    public HexFightStats(final List<String> party) {
        for (final String name : party) {
            final HexFightSummary.Head h = new HexFightSummary.Head();
            h.name = name;
            heads.put(name, h);
            cardDamage.put(name, new HashMap<>());
        }
    }

    private HexFightSummary.Head headOf(final PlayerView p) {
        return p == null ? null : heads.get(p.getName());
    }

    /** The game whose players' per-turn counters are read (set when the fight begins). */
    public synchronized void setGame(final Game game0) {
        game = game0;
    }

    /**
     * Adds what the party's heads drew and gained in life this turn. The game resets these counters at the end of
     * every cleanup step, so they are read when the cleanup begins, and once more when the fight ends.
     */
    public synchronized void countTurn() {
        if (game == null) {
            return;
        }
        final int turn = game.getPhaseHandler().getTurn();
        if (turn == countedTurn) {
            return;
        }
        countedTurn = turn;
        for (final Player p : game.getRegisteredPlayers()) {
            final HexFightSummary.Head h = heads.get(p.getName());
            if (h != null) {
                h.cardsDrawn += Math.max(0, p.getNumDrawnThisTurn() - openingDraws.getOrDefault(p.getName(), 0));
                h.lifeGained += p.getLifeGainedThisTurn();
            }
        }
        openingDraws.clear(); // the opening hand only counts against the first turn
    }

    @Subscribe
    public synchronized void onPhase(final GameEventTurnPhase ev) {
        if (game == null) {
            return;
        }
        if (!started) {
            started = true; // the first phase of the first turn: what is in the hands now was not drawn
            for (final Player p : game.getRegisteredPlayers()) {
                openingDraws.put(p.getName(), p.getNumDrawnThisTurn());
            }
        }
        if (ev.phase() == PhaseType.CLEANUP) {
            countTurn();
        }
    }

    private synchronized void damage(final CardView source, final CardView targetCard, final PlayerView targetPlayer, final int amount) {
        if (source == null || amount <= 0) {
            return;
        }
        // only what hits the enemy side counts: not the party's own creatures or heads
        if (targetPlayer != null && heads.containsKey(targetPlayer.getName())) {
            return;
        }
        if (targetCard != null && targetCard.getController() != null && heads.containsKey(targetCard.getController().getName())) {
            return;
        }
        final HexFightSummary.Head h = headOf(source.getController());
        if (h == null) {
            return; // the enemy's damage
        }
        h.damage += amount;
        cardDamage.get(h.name).merge(source.getName(), amount, Integer::sum);
    }

    @Subscribe
    public void onPlayerDamaged(final GameEventPlayerDamaged ev) {
        damage(ev.source(), null, ev.target(), ev.amount());
    }

    @Subscribe
    public void onCardDamaged(final GameEventCardDamaged ev) {
        damage(ev.source(), ev.card(), null, ev.amount());
    }

    @Subscribe
    public synchronized void onCast(final GameEventSpellAbilityCast ev) {
        final StackItemView si = ev.si();
        if (si == null || si.isAbility() || si.isTrigger()) {
            return; // only spells count
        }
        final HexFightSummary.Head h = headOf(si.getActivatingPlayer());
        if (h != null) {
            h.spells++;
        }
    }

    @Subscribe
    public synchronized void onLand(final GameEventLandPlayed ev) {
        final HexFightSummary.Head h = headOf(ev.player());
        if (h != null) {
            h.lands++;
        }
    }

    /** The summary of the fight so far (at its end: of the whole fight). */
    public synchronized HexFightSummary summary(final String enemyName, final String result, final int turns) {
        final HexFightSummary s = new HexFightSummary();
        s.enemyName = enemyName;
        s.result = result;
        s.turns = turns;
        HexFightSummary.Head best = null;
        for (final HexFightSummary.Head h : heads.values()) {
            final HexFightSummary.Head copy = new HexFightSummary.Head();
            copy.name = h.name;
            copy.damage = h.damage;
            copy.spells = h.spells;
            copy.lands = h.lands;
            copy.cardsDrawn = h.cardsDrawn;
            copy.lifeGained = h.lifeGained;
            for (final Map.Entry<String, Integer> e : cardDamage.get(h.name).entrySet()) {
                if (e.getValue() > copy.topCardDamage || (e.getValue() == copy.topCardDamage && copy.topCard != null && e.getKey().compareTo(copy.topCard) < 0)) {
                    copy.topCard = e.getKey();
                    copy.topCardDamage = e.getValue();
                }
            }
            s.heads.add(copy);
            if (best == null || copy.damage > best.damage || (copy.damage == best.damage && copy.spells > best.spells)) {
                best = copy;
            }
        }
        s.mvp = best == null || (best.damage == 0 && best.spells == 0) ? null : best.name;
        return s;
    }
}
