package forge.game.mulligan;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import com.google.common.collect.Lists;

import forge.MulliganDefs;
import forge.StaticData;
import forge.game.Game;
import forge.game.GameType;
import forge.game.player.Player;
import forge.util.ThreadUtil;

public class MulliganService {
    Player firstPlayer;
    Game game;
    List<AbstractMulligan> mulligans = Lists.newArrayList();

    public MulliganService(Player player) {
        firstPlayer = player;
        game = firstPlayer.getGame();
    }

    public void perform() {
        initializeMulligans();
        runPlayerMulligans();
        runPostMulligans();
    }

    private void initializeMulligans() {
        List<Player> whoCanMulligan = Lists.newArrayList(game.getPlayers());
        int offset = whoCanMulligan.indexOf(firstPlayer);

        for (int i = 0; i < offset; i++) {
            whoCanMulligan.add(whoCanMulligan.remove(0));
        }

        // CR 810.5: in Two-Headed Giant the first mulligan is free, also for a team of one head
        boolean firstMullFree = game.getPlayers().size() > 2 || game.getRules().hasAppliedVariant(GameType.Brawl)
                || game.getRules().hasSharedTeamTurns();

        for (Player player : whoCanMulligan) {
            MulliganDefs.MulliganRule rule = StaticData.instance().getMulliganRule();
            AbstractMulligan mulligan;
            switch (rule) {
                case Original:
                    mulligan = new OriginalMulligan(player, firstMullFree);
                    break;
                case Paris:
                    mulligan = new ParisMulligan(player, firstMullFree);
                    break;
                case Vancouver:
                    mulligan = new VancouverMulligan(player, firstMullFree);
                    break;
                case London:
                    mulligan = new LondonMulligan(player, firstMullFree);
                    break;
                case Houston:
                    mulligan = new HoustonMulligan(player, firstMullFree);
                    break;
                default:
                    mulligan = new VancouverMulligan(player, firstMullFree);
                    break;
            }

            mulligans.add(mulligan);
            mulligan.beforeFirstMulligan();
        }
    }

    private void runPlayerMulligans() {
        if (game.getRules().hasSharedTeamTurns()) {
            runTeamMulligans();
            return;
        }
        boolean allKept;
        do {
            allKept = true;
            for (AbstractMulligan mulligan : mulligans) {
                if (mulligan.hasKept()) {
                    continue;
                }

                Player p = mulligan.getPlayer();

                boolean keep = !mulligan.canMulligan() ||
                        p.getController().mulliganKeepHand(
                                firstPlayer,
                                mulligan.tuckCardsDuringMulligan()
                        );

                if (game.isGameOver()) {
                    // conceded during mulligan prompt
                    return;
                }

                if (keep) {
                    mulligan.keep();
                    continue;
                }

                allKept = false;
                mulligan.mulligan();
            }
        } while (!allKept);
    }

    /**
     * Shared team turns: the heads of a team decide together (CR 810.5), each round every head that
     * hasn't kept answers before anyone's mulligan is carried out. Heads at their own screen are
     * asked at the same time; the others (AI, a shared screen) one by one.
     */
    private void runTeamMulligans() {
        boolean allKept;
        do {
            allKept = true;
            final List<List<AbstractMulligan>> teams = Lists.newArrayList();
            for (AbstractMulligan mulligan : mulligans) {
                if (mulligan.hasKept()) {
                    continue;
                }
                List<AbstractMulligan> team = null;
                for (List<AbstractMulligan> t : teams) {
                    if (t.get(0).getPlayer().sameTeam(mulligan.getPlayer())) {
                        team = t;
                        break;
                    }
                }
                if (team == null) {
                    team = Lists.newArrayList();
                    teams.add(team);
                }
                team.add(mulligan);
            }
            for (List<AbstractMulligan> team : teams) {
                final Map<AbstractMulligan, Boolean> answers = askTeam(team);
                if (game.isGameOver()) {
                    return;
                }
                for (AbstractMulligan mulligan : team) {
                    if (Boolean.TRUE.equals(answers.get(mulligan))) {
                        mulligan.keep();
                    } else {
                        allKept = false;
                        mulligan.mulligan();
                    }
                }
            }
        } while (!allKept);
    }

    private Map<AbstractMulligan, Boolean> askTeam(final List<AbstractMulligan> team) {
        final Map<AbstractMulligan, Boolean> answers = new ConcurrentHashMap<>();
        final List<AbstractMulligan> concurrent = Lists.newArrayList();
        final Set<Object> keys = new HashSet<>();
        for (AbstractMulligan mulligan : team) {
            final Player p = mulligan.getPlayer();
            if (!mulligan.canMulligan()) {
                answers.put(mulligan, true);
            } else if (p.getController().canChooseSpellAbilityConcurrently() && keys.add(p.getController().getConcurrentChoiceKey())) {
                concurrent.add(mulligan);
            } else {
                answers.put(mulligan, p.getController().mulliganKeepHand(firstPlayer, mulligan.tuckCardsDuringMulligan()));
                if (game.isGameOver()) {
                    return answers;
                }
            }
        }
        if (concurrent.size() == 1) {
            final AbstractMulligan mulligan = concurrent.get(0);
            answers.put(mulligan, mulligan.getPlayer().getController().mulliganKeepHand(firstPlayer, mulligan.tuckCardsDuringMulligan()));
        } else if (!concurrent.isEmpty()) {
            final CountDownLatch done = new CountDownLatch(concurrent.size());
            final Throwable[] error = new Throwable[1];
            for (AbstractMulligan mulligan : concurrent) {
                ThreadUtil.invokeInGameThread(() -> {
                    try {
                        answers.put(mulligan, mulligan.getPlayer().getController().mulliganKeepHand(firstPlayer, mulligan.tuckCardsDuringMulligan()));
                    } catch (Throwable t) {
                        error[0] = t;
                    } finally {
                        done.countDown();
                    }
                });
            }
            try {
                done.await();
            } catch (InterruptedException e) {
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
        return answers;
    }

    private void runPostMulligans() {
        for (AbstractMulligan mulligan : mulligans) {
            mulligan.afterMulligan();
        }
    }
}