package forge.ai;

import forge.game.Game;
import forge.game.GameStage;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.event.GameEventPlayerDamaged;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexDefeat;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexFightStats;
import forge.gamemodes.hexcrawl.HexFightSummary;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.gamemodes.hexcrawl.HexShopType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** The statistics of the Hex Campaign: what every head does in fights and in towns, and the enemies beaten. */
public class HexStatsTest extends AITest {

    private static HexCampaign party() {
        return HexCampaign.create("Test", 11L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", true)));
    }

    private static Player player(final Game game, final String name) {
        for (final Player p : game.getPlayers()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    @Test
    public void testAFightCountsDrawsLifeAndDamageToTheEnemy() {
        final HexCampaign c = party();
        final HexEncounter enc = HexContent.get().randomEncounter(HexColor.RED, 0.5, new Random(3));
        final List<RegisteredPlayer> players = HexBattle.createPlayers(c, enc, true);
        final Match match = new Match(HexBattle.createRules(), players, "Test");
        final Game game = new Game(players, HexBattle.createRules(), match);
        Player enemy = null;
        for (final Player p : game.getPlayers()) {
            p.initVariantsZones(p.getRegisteredPlayer());
            fillLibrary(p, 20);
            if (p.getRegisteredPlayer().getTeamNumber() == HexBattle.ENEMY_TEAM) {
                enemy = p;
            }
        }
        final Player ann = player(game, "Ann");
        final Player bob = player(game, "Bob");
        AssertJUnit.assertNotNull(ann);
        AssertJUnit.assertNotNull(enemy);

        final HexFightStats stats = new HexFightStats(Arrays.asList("Ann", "Bob"));
        stats.setGame(game);
        game.subscribeToEvents(stats);
        ann.drawCards(7); // the opening hand: not counted
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, enemy);
        playUntilNextTurn(game); // the enemy's turn ends: the party's begins, and it draws
        playUntilPhase(game, PhaseType.MAIN1);
        ann.gainLife(3, null, null);

        // Ann's bear hits the enemy (counts) and Bob (a team mate: does not)
        final Card bear = addCard("Grizzly Bears", ann);
        stats.onPlayerDamaged(new GameEventPlayerDamaged(enemy.getView(), bear.getView(), 2, true, false));
        stats.onPlayerDamaged(new GameEventPlayerDamaged(bob.getView(), bear.getView(), 5, false, false));
        playUntilPhase(game, PhaseType.CLEANUP);
        stats.countTurn();

        final HexFightSummary summary = stats.summary(enc.enemyName, "WON", game.getPhaseHandler().getTurn());
        final HexFightSummary.Head a = summary.head("Ann");
        AssertJUnit.assertEquals("one card drawn in the draw step, the opening hand not counted", 1, a.cardsDrawn);
        AssertJUnit.assertEquals(3, a.lifeGained);
        AssertJUnit.assertEquals("only the damage to the enemy", 2, a.damage);
        AssertJUnit.assertEquals("Grizzly Bears", a.topCard);
        AssertJUnit.assertEquals(0, summary.head("Bob").lifeGained);
    }

    @Test
    public void testTheCampaignAddsUpFightsAndDefeats() {
        final HexCampaign c = party();
        final HexEncounter enc = HexContent.get().randomEncounter(HexColor.RED, 0.5, new Random(3));
        c.pendingEncounter = enc;
        final HexFightSummary summary = new HexFightSummary();
        summary.enemyName = enc.enemyName;
        summary.result = "WON";
        summary.turns = 7;
        final HexFightSummary.Head ann = new HexFightSummary.Head();
        ann.name = "Ann";
        ann.damage = 15;
        ann.cardsDrawn = 4;
        ann.lifeGained = 2;
        ann.topCard = "Lightning Bolt";
        ann.topCardDamage = 9;
        final HexFightSummary.Head bob = new HexFightSummary.Head();
        bob.name = "Bob";
        bob.damage = 5;
        summary.heads.addAll(Arrays.asList(ann, bob));
        summary.mvp = "Ann";
        c.recordFight(summary);
        c.resolveBattle(HexBattle.Result.WON);

        final HexPlayer a = c.player("Ann");
        AssertJUnit.assertEquals(1, a.stats.fights);
        AssertJUnit.assertEquals(15, a.stats.damage);
        AssertJUnit.assertEquals(4, a.stats.cardsDrawn);
        AssertJUnit.assertEquals(1, a.stats.fightsMvp);
        AssertJUnit.assertEquals(0, c.player("Bob").stats.fightsMvp);
        AssertJUnit.assertEquals("Lightning Bolt", a.stats.favouriteCard());
        AssertJUnit.assertEquals(15, a.totalStats.damage);
        AssertJUnit.assertEquals(1, c.defeated.size());
        AssertJUnit.assertEquals(HexDefeat.Kind.ENEMY, c.defeated.get(0).kind);
        AssertJUnit.assertEquals(7, c.defeated.get(0).turns);
        AssertJUnit.assertEquals(1, c.defeatedTotal);
        AssertJUnit.assertEquals(1, c.cycleBattles(HexBattle.Result.WON));

        // the reward in gold is earned
        c.chooseReward("Ann", -1);
        AssertJUnit.assertTrue(a.stats.goldEarned > 0);

        // New Game Plus: the cycle's numbers start anew, the campaign's stay
        c.pendingRewards.clear();
        c.won = true;
        AssertJUnit.assertTrue(c.startNewGamePlus());
        AssertJUnit.assertEquals(0, a.stats.fights);
        AssertJUnit.assertEquals(1, a.totalStats.fights);
        AssertJUnit.assertTrue(c.defeated.isEmpty());
        AssertJUnit.assertEquals(1, c.defeatedTotal);
        AssertJUnit.assertEquals(1, c.cycleHistory.size());
        AssertJUnit.assertEquals(1, c.cycleHistory.get(0).defeated);
        AssertJUnit.assertEquals(0, c.cycleBattles(HexBattle.Result.WON));
    }

    @Test
    public void testGoldInTownIsTallied() {
        final HexCampaign c = party();
        AssertJUnit.assertTrue("the party starts in a town", c.inTown());
        final HexPlayer ann = c.player("Ann");
        ann.gold = 100000;
        HexShopOffer cardOffer = null;
        HexShopOffer relicOffer = null;
        for (final HexShopOffer o : c.shop()) {
            if (o.shop == HexShopType.RELIC && relicOffer == null && "Ann".equals(HexCampaign.ownerOfTab(o.tab))) {
                relicOffer = o;
            } else if (o.shop != HexShopType.RELIC && cardOffer == null && o.card != null && (o.tab == null || "Ann".equals(HexCampaign.ownerOfTab(o.tab)))) {
                cardOffer = o;
            }
        }
        AssertJUnit.assertNotNull(cardOffer);
        AssertJUnit.assertNotNull(relicOffer);
        int gold = ann.gold;
        AssertJUnit.assertTrue(c.buy("Ann", cardOffer));
        AssertJUnit.assertTrue(c.buy("Ann", relicOffer));
        AssertJUnit.assertEquals(gold - ann.gold, ann.stats.goldSpent - ann.stats.goldEarned);
        AssertJUnit.assertEquals(1, ann.stats.cardsBought);
        AssertJUnit.assertEquals(1, ann.stats.relicsBought);

        ann.sideboard.add("Grizzly Bears");
        final int earned = ann.stats.goldEarned;
        gold = ann.gold;
        AssertJUnit.assertTrue(c.sell("Ann", "Grizzly Bears", true));
        AssertJUnit.assertEquals(ann.gold - gold, ann.stats.goldEarned - earned);
        AssertJUnit.assertEquals(1, ann.stats.cardsSold);
        AssertJUnit.assertEquals(ann.stats.goldSpent, ann.totalStats.goldSpent);

        // gold given to another head is neither earned nor spent
        final int spent = ann.stats.goldSpent;
        c.giveGold("Ann", "Bob", 10);
        AssertJUnit.assertEquals(spent, ann.stats.goldSpent);
        AssertJUnit.assertEquals(0, c.player("Bob").stats.goldEarned);
    }
}
