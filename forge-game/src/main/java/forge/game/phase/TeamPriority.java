package forge.game.phase;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.Lists;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.combat.CombatUtil;
import forge.game.event.GameEventPlayerPriority;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbility;
import forge.util.IHasForgeLog;

/**
 * Shared team turns (Multi-Headed Giant, Hex Campaign fights): the team that holds priority as a unit, and the heads
 * declaring at the same time. Kept apart from PhaseHandler, which calls in here only with shared team turns.
 */
final class TeamPriority implements IHasForgeLog {
    private final PhaseHandler ph;
    private final Game game;
    /** The team mates asked at the same time right now, whose OKs wait for each other; null if none. */
    private volatile TeamReady teamReady;

    TeamPriority(final PhaseHandler ph0, final Game game0) {
        ph = ph0;
        game = game0;
    }

    TeamReady getTeamReady() {
        return teamReady;
    }

    /**
     * The heads of a team whose attack breaks a rule that could make it legal on their own, the others' attackers
     * kept (all of them if none can): they declare again.
     */
    List<Player> headsToFixAttack(final List<Player> heads) {
        final forge.game.combat.AttackConstraints constraints = ph.getCombat().getAttackConstraints();
        final org.apache.commons.lang3.tuple.Pair<Map<Card, GameEntity>, Integer> best = constraints.getLegalAttackers();
        final List<Player> fixers = Lists.newArrayList();
        for (final Player head : heads) {
            final Map<Card, GameEntity> attack = new HashMap<>();
            ph.getCombat().getAttackersAndDefenders().forEach((c, d) -> {
                if (c.getController() != head) {
                    attack.put(c, d);
                }
            });
            best.getLeft().forEach((c, d) -> {
                if (c.getController() == head) {
                    attack.put(c, d);
                }
            });
            final int violations = constraints.countViolations(attack);
            if (violations != -1 && violations <= best.getRight()) {
                fixers.add(head);
            }
        }
        return fixers.isEmpty() ? heads : fixers;
    }

    /** Tells each head why the team's attack must change: its own (with the creatures that should attack), or a mate's. */
    void notifyAttackToFix(final List<Player> heads, final List<Player> fixers) {
        final forge.util.Localizer loc = forge.util.Localizer.getInstance();
        final List<String> names = Lists.newArrayList();
        for (final Player fixer : fixers) {
            names.add(fixer.getName());
        }
        for (final Player head : heads) {
            final String message;
            if (fixers.contains(head)) {
                final List<String> should = Lists.newArrayList();
                for (final Card c : head.getCreaturesInPlay()) {
                    if (CombatUtil.couldAttackButNotAttacking(ph.getCombat(), c)) {
                        should.add(c.getName());
                    }
                }
                message = loc.getMessage("lblTeamAttackFixYours")
                        + (should.isEmpty() ? "" : " " + loc.getMessage("lblTeamAttackShouldAttack", String.join(", ", should)));
            } else {
                message = loc.getMessage("lblTeamAttackFixMate", String.join(", ", names));
            }
            Objects.requireNonNullElse(head.getDeclaresAttackers(), head).getController().notifyOfValue(null, null, message);
        }
    }

    /**
     * Each head chooses among its own of the given cards (exert, enlist), the heads at screens of their own at the same
     * time; returns all the chosen cards.
     */
    List<Card> chooseTogether(final List<Player> heads, final List<Card> cards,
            final java.util.function.BiFunction<Player, List<Card>, List<Card>> choose) {
        final List<Card> chosen = Collections.synchronizedList(Lists.newArrayList());
        final List<Player> asked = Lists.newArrayList();
        for (final Player head : heads) {
            if (!CardLists.filterControlledBy(cards, head).isEmpty()) {
                asked.add(head);
            }
        }
        declareTogether(asked, p -> p, p -> chosen.addAll(choose.apply(p, CardLists.filterControlledBy(cards, p))));
        return Lists.newArrayList(chosen);
    }

    /**
     * Lets each of the players declare (attackers or blockers). With shared team turns the heads
     * that sit at screens of their own (the host's and network guests') are asked at the same
     * time; the others (AI heads, heads sharing one screen) first, one after another. Returns
     * once all of them are done.
     */
    void declareTogether(final List<Player> players, final java.util.function.Function<Player, Player> declarerOf,
            final java.util.function.Consumer<Player> declare) {
        final List<Player> together = Lists.newArrayList();
        if (ph.isSharedTeamTurns() && players.size() > 1) {
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
        // a head's OK waits for its mates' (and may be taken back until then)
        final TeamReady team = new TeamReady(Lists.transform(together, declarerOf::apply));
        teamReady = team;
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
                    team.answered(declarerOf.apply(p));
                    done.countDown();
                }
            });
        }
        try {
            done.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            team.close();
            teamReady = null;
        }
        if (error[0] instanceof RuntimeException re) {
            throw re;
        } else if (error[0] instanceof Error e) {
            throw e;
        } else if (error[0] != null) {
            throw new RuntimeException(error[0]);
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
    boolean step() {
        final Player first = ph.getPriorityPlayer();
        final Set<Player> passed = new HashSet<>();
        // the prompts of the heads asked at the same time: they stay open while a mate acts
        final OpenPrompts prompts = new OpenPrompts();
        int aiLoops = 0;
        try {
            while (true) {
                if (ph.checkStateBasedEffects()) {
                    return false;
                }
                final List<Player> heads = ph.getPriorityTeam(first);
                heads.removeIf(Player::hasLost);
                if (heads.isEmpty() || game.isGameOver()) {
                    return !game.isGameOver();
                }
                ph.setPriorityTeam(Collections.unmodifiableList(heads));
                for (final Player p : game.getPlayers()) {
                    p.setHasPriority(heads.contains(p));
                }
                if (passed.containsAll(heads)) {
                    ph.setPriorityPlayerOnly(first);
                    return true;
                }

                game.fireEvent(new GameEventPlayerPriority(PlayerView.get(ph.getPlayerTurn()), ph.getPhase(), PlayerView.get(first)));
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
                    ph.setPriorityPlayerOnly(head);
                    final List<SpellAbility> chosen = head.getController().chooseSpellAbilityToPlay();
                    if (chosen == null) {
                        passed.add(head);
                    } else {
                        choice = new PriorityChoice(head, chosen, null);
                        break;
                    }
                }
                if (choice == null && concurrent.size() == 1 && !prompts.isOpen(concurrent.get(0))) {
                    final Player head = concurrent.get(0);
                    ph.setPriorityPlayerOnly(head);
                    final List<SpellAbility> chosen = head.getController().chooseSpellAbilityToPlay();
                    if (chosen == null) {
                        passed.add(head);
                    } else {
                        choice = new PriorityChoice(head, chosen, null);
                    }
                } else if (choice == null && !concurrent.isEmpty()) {
                    choice = chooseConcurrently(concurrent, passed, prompts);
                }
                if (choice == null) {
                    continue;
                }
                if (game.isGameOver()) {
                    return false;
                }

                final Player actor = choice.player();
                ph.setPriorityPlayerOnly(actor);
                if (choice.chosen().isEmpty()) {
                    continue; // e.g. an undo: just ask again
                }
                if (!game.getUndoManager().isOwner(actor)) {
                    game.getUndoManager().barrier("Opponent acted");
                }
                ph.playChosenSpellAbilities(choice.chosen());
                // the game changed: every head may act again before the team passes
                passed.clear();
                if (actor.getController().isAI() && ++aiLoops >= 999) {
                    aiLog.warn("AI looped too much with: " + choice.chosen());
                    passed.add(actor);
                }
            }
        } finally {
            ph.setPriorityTeam(Collections.emptyList());
            prompts.closeAll();
        }
    }

    /**
     * The prompts of the heads asked at the same time during one team priority. A prompt stays open while a team
     * mate acts (the head may go on thinking, and even pick a card for afterwards); it is shown anew once the
     * mate's action is done. Used on the game thread only.
     */
    private final class OpenPrompts {
        private final java.util.concurrent.BlockingQueue<PriorityChoice> answers = new java.util.concurrent.LinkedBlockingQueue<>();
        /** The heads whose prompt runs (their answer not taken yet). */
        private final Set<Player> open = new HashSet<>();
        /** Every head asked, to be told when the team priority is over. */
        private final Set<Player> asked = new HashSet<>();

        boolean isOpen(final Player head) {
            return open.contains(head);
        }

        /** Opens the head's prompt on a thread of its own, unless it is open still. */
        void ask(final Player head) {
            if (!open.add(head)) {
                return;
            }
            asked.add(head);
            head.getController().setConcurrentSpellAbilityChoice(true);
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

        /** The next answer of an open prompt. */
        PriorityChoice take() {
            try {
                final PriorityChoice answer = answers.take();
                open.remove(answer.player());
                return answer;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        /**
         * Ends these heads' prompts without a decision (a mate acted first) and waits for them, so their empty
         * answers are not taken for passes. Answers of other prompts that come meanwhile stay for later.
         */
        void interrupt(final List<Player> heads, final Player actedFirst, final Card card) {
            final Set<Player> waiting = new HashSet<>();
            for (final Player head : heads) {
                if (open.contains(head)) {
                    head.getController().interruptSpellAbilityChoice(actedFirst, card);
                    waiting.add(head);
                }
            }
            final List<PriorityChoice> later = Lists.newArrayList();
            try {
                while (!waiting.isEmpty()) {
                    final PriorityChoice answer = answers.take();
                    if (waiting.remove(answer.player())) {
                        open.remove(answer.player());
                    } else {
                        later.add(answer);
                    }
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                answers.addAll(later);
            }
        }

        /** The team priority is over: the prompts still open end without a decision. */
        void closeAll() {
            for (final Player head : open) {
                head.getController().interruptSpellAbilityChoice(null, null);
            }
            final long until = System.currentTimeMillis() + 5000;
            while (!open.isEmpty() && System.currentTimeMillis() < until) {
                try {
                    final PriorityChoice answer = answers.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (answer != null) {
                        open.remove(answer.player());
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            for (final Player head : asked) {
                head.getController().setConcurrentSpellAbilityChoice(false);
            }
        }
    }

    /**
     * Prompts the given heads at the same time (those whose prompt is still open from before are shown anew).
     * Returns the first decision to act, or null if all of them passed. The other prompts stay open while the
     * decision is played: they are told who acts, and see the result when they are asked next.
     */
    private PriorityChoice chooseConcurrently(final List<Player> heads, final Set<Player> passed, final OpenPrompts prompts) {
        // a head's pass waits for its mates' (and may be taken back until then)
        final TeamReady team = new TeamReady(heads);
        teamReady = team;
        try {
            for (final Player head : heads) {
                if (prompts.isOpen(head)) {
                    head.getController().refreshSpellAbilityChoice(); // a mate's action is done: it shows the new state
                } else {
                    prompts.ask(head);
                }
            }
            while (true) {
                if (!heads.stream().anyMatch(prompts::isOpen)) {
                    return null; // all of them passed
                }
                final PriorityChoice answer = prompts.take();
                if (!heads.contains(answer.player())) {
                    continue; // a head no longer at the table
                }
                if (answer.error() != null) {
                    final Throwable error = answer.error();
                    if (error instanceof RuntimeException re) {
                        throw re;
                    } else if (error instanceof Error e) {
                        throw e;
                    }
                    throw new RuntimeException(error);
                }
                if (answer.chosen() == null) {
                    passed.add(answer.player());
                    team.answered(answer.player());
                    continue;
                }
                team.close(); // the decision is over: no more OKs to wait for
                final Card played = answer.chosen().isEmpty() ? null : answer.chosen().get(0).getHostCard();
                final List<Player> stopped = Lists.newArrayList();
                for (final Player other : heads) {
                    if (other == answer.player() || !prompts.isOpen(other)) {
                        continue;
                    }
                    if (other.getController().canKeepSpellAbilityChoiceOpen()) {
                        other.getController().mateActing(answer.player(), played); // it stays open, and shows what happens
                    } else {
                        stopped.add(other); // asked again afterwards
                    }
                }
                prompts.interrupt(stopped, answer.player(), played);
                return answer;
            }
        } finally {
            team.close();
            teamReady = null;
        }
    }
}
