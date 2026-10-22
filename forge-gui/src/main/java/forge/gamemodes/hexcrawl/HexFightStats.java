package forge.gamemodes.hexcrawl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.eventbus.Subscribe;

import forge.game.card.CardView;
import forge.game.event.GameEventCardDamaged;
import forge.game.event.GameEventLandPlayed;
import forge.game.event.GameEventPlayerDamaged;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;

/**
 * Counts, during a fight, what every head of the party does: damage dealt (to players and
 * creatures), spells cast, lands played, and which of its cards dealt the most damage. Listens
 * to the game's event bus on the host; the result goes into the campaign.
 */
public final class HexFightStats {
    private final Map<String, HexFightSummary.Head> heads = new LinkedHashMap<>();
    /** Damage per card, per head. */
    private final Map<String, Map<String, Integer>> cardDamage = new HashMap<>();

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

    private synchronized void damage(final CardView source, final int amount) {
        if (source == null || amount <= 0) {
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
        damage(ev.source(), ev.amount());
    }

    @Subscribe
    public void onCardDamaged(final GameEventCardDamaged ev) {
        damage(ev.source(), ev.amount());
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
