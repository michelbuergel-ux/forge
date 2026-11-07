package forge.ai;

import java.util.Arrays;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexPlayer;

/**
 * Team permanents (a Hex Campaign option): for what a card says about the permanents "you control", the whole team's
 * permanents count; each permanent keeps its real controller, so it is still one "you don't control" too.
 */
public class HexTeamPermanentsTest extends AITest {

    /** The 2-vs-2 team game (a1 and a2 against b1 and b2), with team permanents or without. */
    private Game teamGame(final boolean teamPermanents) {
        final Game game = initAndCreateTeamGame();
        game.getRules().setTeamSharedPermanents(teamPermanents);
        return game;
    }

    private static Player player(final Game game, final String name) {
        for (final Player p : game.getPlayers()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        throw new AssertionError(name);
    }

    @Test
    public void anAnthemPumpsTheTeamMatesCreatures() {
        final Game game = teamGame(true);
        final Player a1 = player(game, "a1");
        final Player a2 = player(game, "a2");
        final Player b1 = player(game, "b1");
        addCard("Glorious Anthem", a1);
        final Card matesBear = addCard("Grizzly Bears", a2);
        final Card foesBear = addCard("Grizzly Bears", b1);
        game.getAction().checkStateEffects(true);

        AssertJUnit.assertEquals("the team mate's creature counts as one a1 controls", 3, matesBear.getNetPower());
        AssertJUnit.assertEquals("the enemy's does not", 2, foesBear.getNetPower());
        AssertJUnit.assertEquals("its real controller stays", a2, matesBear.getController());
    }

    @Test
    public void withoutTheOptionOnlyOwnPermanentsCount() {
        final Game game = teamGame(false);
        final Player a1 = player(game, "a1");
        final Player a2 = player(game, "a2");
        final Card anthem = addCard("Glorious Anthem", a1);
        final Card ownBear = addCard("Grizzly Bears", a1);
        final Card matesBear = addCard("Grizzly Bears", a2);
        game.getAction().checkStateEffects(true);

        AssertJUnit.assertEquals(3, ownBear.getNetPower());
        AssertJUnit.assertEquals(2, matesBear.getNetPower());
        AssertJUnit.assertFalse(matesBear.isValid("Creature.YouCtrl", a1, anthem, null));
        AssertJUnit.assertFalse(a1.hasMetalcraft());
    }

    @Test
    public void aTeamMatesPermanentIsYoursAndStillAnotherPlayers() {
        final Game game = teamGame(true);
        final Player a1 = player(game, "a1");
        final Player a2 = player(game, "a2");
        final Player b1 = player(game, "b1");
        final Card source = addCard("Grizzly Bears", a1);
        final Card matesBear = addCard("Grizzly Bears", a2);
        final Card foesBear = addCard("Grizzly Bears", b1);

        // "target creature you control", "creatures you control"
        AssertJUnit.assertTrue(matesBear.isValid("Creature.YouCtrl", a1, source, null));
        AssertJUnit.assertFalse(foesBear.isValid("Creature.YouCtrl", a1, source, null));
        // "target creature you don't control", "another player controls": the real controller decides
        AssertJUnit.assertTrue(matesBear.isValid("Creature.YouDontCtrl", a1, source, null));
        AssertJUnit.assertFalse(matesBear.isValid("Creature.OppCtrl", a1, source, null));
        AssertJUnit.assertTrue(foesBear.isValid("Creature.OppCtrl", a1, source, null));
        // and the enemy team shares as well
        AssertJUnit.assertTrue(foesBear.isValid("Creature.YouCtrl", player(game, "b2"), source, null));
    }

    @Test
    public void countsOfWhatYouControlIncludeTheTeam() {
        final Game game = teamGame(true);
        final Player a1 = player(game, "a1");
        final Player a2 = player(game, "a2");
        addCards("Ornithopter", 2, a2);
        addCard("Ornithopter", a1);
        AssertJUnit.assertTrue("metalcraft: three artifacts among the team", a1.hasMetalcraft());
        AssertJUnit.assertTrue(a2.hasMetalcraft());
        AssertJUnit.assertFalse("not the enemy's", player(game, "b1").hasMetalcraft());
        AssertJUnit.assertEquals(3, a1.getPermanentsYouControl().size());
        AssertJUnit.assertEquals("its own stay its own", 1, a1.getCreaturesInPlay().size());
    }

    /** a1's Zulaport Cutthroat when the bear of a head dies: how many of its triggers go on the stack. */
    private int triggersWhenCreatureDies(final boolean teamPermanents, final String whose) {
        final Game game = teamGame(teamPermanents);
        addCard("Zulaport Cutthroat", player(game, "a1"));
        final Card bear = addCard("Grizzly Bears", player(game, whose));
        game.getAction().checkStateEffects(true);
        game.getAction().destroy(bear, null, false, null);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        return game.getStack().size();
    }

    @Test
    public void aDiesTriggerSeesTheTeamMatesCreature() {
        AssertJUnit.assertEquals("the own creature", 1, triggersWhenCreatureDies(false, "a1"));
        AssertJUnit.assertEquals("\"another creature you control dies\": the team mate's", 1, triggersWhenCreatureDies(true, "a2"));
        AssertJUnit.assertEquals("without the option, not", 0, triggersWhenCreatureDies(false, "a2"));
        AssertJUnit.assertEquals("nor the enemy's", 0, triggersWhenCreatureDies(true, "b1"));
    }

    @Test
    public void aCardThatLeftTheBattlefieldIsNoLongerYours() {
        final Game game = teamGame(true);
        final Player a1 = player(game, "a1");
        final Player a2 = player(game, "a2");
        final Card source = addCard("Grizzly Bears", a1);
        final Card inHand = addCardToZone("Grizzly Bears", a2, forge.game.zone.ZoneType.Hand);
        AssertJUnit.assertFalse("only permanents on the battlefield", inHand.isValid("Card.YouCtrl", a1, source, null));
    }

    @Test
    public void onlyTeamGamesShare() {
        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setTeamSharedPermanents(true);
        AssertJUnit.assertFalse("without shared team turns there is no team", rules.hasTeamSharedPermanents());
        rules.setSharedTeamTurns(true);
        AssertJUnit.assertTrue(rules.hasTeamSharedPermanents());
    }

    @Test
    public void theCampaignsChoiceGoesIntoItsFights() {
        final HexCampaign c = HexCampaign.create("Test", 11L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", true)));
        AssertJUnit.assertFalse("off unless chosen", HexBattle.createRules(c).hasTeamSharedPermanents());
        c.teamPermanents = true;
        AssertJUnit.assertTrue(HexBattle.createRules(c).hasTeamSharedPermanents());
    }
}
