package forge.ai;

import forge.game.Game;
import forge.game.GameStage;
import forge.game.Match;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexQuest;
import forge.item.IPaperCard;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Archenemy tasks of the Hex Campaign: an enemy that fights alone, with a deck of schemes. */
public class HexArchenemyTest extends AITest {

    private static HexCampaign party(final long seed) {
        return HexCampaign.create("Test", seed, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", true)));
    }

    private static HexQuest task(final HexCampaign c, final boolean archenemy) {
        final HexQuest quest = HexContent.get().questEnemy(HexColor.RED, new Random(5));
        quest.id = 99;
        quest.q = c.tile().q;
        quest.r = c.tile().r;
        quest.archenemy = archenemy;
        return quest;
    }

    private static List<RegisteredPlayer> enemySide(final List<RegisteredPlayer> players) {
        final List<RegisteredPlayer> result = new ArrayList<>();
        for (final RegisteredPlayer rp : players) {
            if (rp.getTeamNumber() == HexBattle.ENEMY_TEAM) {
                result.add(rp);
            }
        }
        return result;
    }

    @Test
    public void testNoticeBoardsOfferArchenemiesNowAndThen() {
        int archenemies = 0;
        int tasks = 0;
        for (long seed = 1; seed <= 40; seed++) {
            final HexCampaign c = party(seed);
            c.shop();
            final List<HexQuest> board = c.questOffers.get(c.tile().key());
            int onBoard = 0;
            for (final HexQuest quest : board) {
                tasks++;
                if (quest.archenemy) {
                    onBoard++;
                    AssertJUnit.assertTrue("a bigger bounty: " + quest.gold, quest.gold >= (60 + 20) * HexCampaign.ARCHENEMY_BOUNTY_PERCENT / 100);
                }
            }
            AssertJUnit.assertTrue("one archenemy per board at most", onBoard <= 1);
            archenemies += onBoard;
        }
        AssertJUnit.assertTrue("some archenemies: " + archenemies + " of " + tasks, archenemies > 0 && archenemies < tasks / 2);
    }

    @Test
    public void testThreeHeadsMeetThreeEnemyHeads() {
        final HexCampaign c = HexCampaign.create("Test", 3, 4,
                Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false), new HexPlayer("Cid", false)));
        AssertJUnit.assertEquals(3, c.enemyHeads());
        final HexEncounter ordinary = HexContent.get().questEncounter(task(c, false));
        AssertJUnit.assertEquals(3, enemySide(HexBattle.createPlayers(c, ordinary, true)).size());
    }

    @Test
    public void testAnArchenemyFightsAloneWithSchemes() {
        final HexCampaign c = party(3);
        AssertJUnit.assertEquals("two heads against this party", 2, c.enemyHeads());
        final HexEncounter ordinary = HexContent.get().questEncounter(task(c, false));
        final HexEncounter archenemy = HexContent.get().questEncounter(task(c, true));
        AssertJUnit.assertTrue(archenemy.archenemy);
        AssertJUnit.assertEquals(1, c.enemyHeads(archenemy));
        AssertJUnit.assertEquals("life for the missing head", HexCampaign.ARCHENEMY_LIFE_PER_MISSING_HEAD,
                c.enemyLife(archenemy) - c.enemyLife(ordinary));

        AssertJUnit.assertEquals(2, enemySide(HexBattle.createPlayers(c, ordinary, true)).size());
        final List<RegisteredPlayer> enemies = enemySide(HexBattle.createPlayers(c, archenemy, true));
        AssertJUnit.assertEquals("alone", 1, enemies.size());
        final RegisteredPlayer enemy = enemies.get(0);
        AssertJUnit.assertEquals(c.enemyLife(archenemy), enemy.getStartingLife());
        final Set<String> schemes = new HashSet<>();
        for (final IPaperCard scheme : enemy.getSchemes()) {
            AssertJUnit.assertTrue(scheme.getName() + " is a scheme", scheme.getRules().getType().isScheme());
            schemes.add(scheme.getName());
        }
        AssertJUnit.assertEquals("20 different schemes", HexContent.SCHEMES_PER_ARCHENEMY, schemes.size());
    }

    @Test
    public void testTheArchenemySetsASchemeInMotion() {
        final HexCampaign c = party(3);
        final List<RegisteredPlayer> players = HexBattle.createPlayers(c, HexContent.get().questEncounter(task(c, true)), true);
        final Match match = new Match(HexBattle.createRules(), players, "Test");
        final Game game = new Game(players, HexBattle.createRules(), match);
        Player enemy = null;
        for (final Player p : game.getPlayers()) {
            p.initVariantsZones(p.getRegisteredPlayer());
            if (p.getRegisteredPlayer().getTeamNumber() == HexBattle.ENEMY_TEAM) {
                enemy = p;
            }
        }
        AssertJUnit.assertNotNull(enemy);
        AssertJUnit.assertTrue(enemy.isArchenemy());
        AssertJUnit.assertEquals(HexContent.SCHEMES_PER_ARCHENEMY, enemy.getZone(ZoneType.SchemeDeck).size());
        final String top = enemy.getZone(ZoneType.SchemeDeck).get(0).getName();

        // its first main phase begins: the top scheme is set in motion (into the command zone; one that is
        // not ongoing goes to the bottom of the scheme deck once it has done its work)
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.DRAW, enemy);
        playUntilPhase(game, PhaseType.MAIN1);
        AssertJUnit.assertTrue(game.getPhaseHandler().is(PhaseType.MAIN1));
        final boolean inCommand = enemy.getZone(ZoneType.Command).getCards().stream().anyMatch(card -> card.getType().isScheme());
        final boolean moved = !enemy.getZone(ZoneType.SchemeDeck).get(0).getName().equals(top);
        AssertJUnit.assertTrue("a scheme was set in motion", inCommand || moved);
    }
}
