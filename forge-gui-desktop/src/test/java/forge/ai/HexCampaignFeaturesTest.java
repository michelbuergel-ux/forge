package forge.ai;

import forge.game.player.RegisteredPlayer;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexEvent;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexPerk;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexReward;
import forge.gamemodes.hexcrawl.HexTile;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Rewards to choose from, perks, map events, trading, the ready check and fast travel of the Hex Campaign. */
public class HexCampaignFeaturesTest extends AITest {

    private static HexCampaign newCampaign() {
        return HexCampaign.create("Test", 11L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
    }

    private static HexEncounter enemy() {
        return HexContent.get().randomEncounter(HexColor.RED, 0.5, new Random(2));
    }

    private static void winFight(final HexCampaign c) {
        c.pendingEncounter = enemy();
        c.resolveBattle(true);
        c.pendingRewards.clear();
    }

    /** An ordinary field next to the party (no town, boss or task). */
    private static HexTile emptyNeighbour(final HexCampaign c) {
        for (final HexTile t : c.map.tiles.values()) {
            if (c.isAdjacent(t) && t.poi == HexPoi.NONE && c.questAt(t.q, t.r) == null) {
                t.enemy = null;
                return t;
            }
        }
        throw new AssertionError("no empty field next to the start");
    }

    @Test
    public void testRewardIsOneOfThreeCards() {
        final HexCampaign c = newCampaign();
        c.pendingEncounter = enemy();
        c.resolveBattle(true);
        final HexReward reward = c.pendingReward("Ann");
        final List<String> options = reward.options();
        AssertJUnit.assertEquals(HexCampaign.REWARD_CHOICES, options.size());
        AssertJUnit.assertEquals("three different cards", 3, options.stream().distinct().count());
        final String third = options.get(2);
        c.chooseReward("Ann", 2);
        AssertJUnit.assertTrue(c.player("Ann").deck.contains(third));
        final int gold = c.player("Bob").gold;
        final int rewardGold = c.pendingReward("Bob").gold;
        c.chooseReward("Bob", -1);
        AssertJUnit.assertEquals(gold + rewardGold, c.player("Bob").gold);
    }

    @Test
    public void testPerksEveryThreeWins() {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        winFight(c);
        winFight(c);
        AssertJUnit.assertTrue("no perk before three wins", ann.perkOffer.isEmpty());
        winFight(c);
        AssertJUnit.assertEquals(HexCampaign.PERK_CHOICES, ann.perkOffer.size());

        // each perk does what it says
        final int life = c.fightLife();
        ann.perks.add(HexPerk.TEAM_LIFE.name());
        AssertJUnit.assertEquals(life + HexPerk.TEAM_LIFE_PER_STACK, c.fightLife());
        ann.perks.add(HexPerk.DISCOUNT.name());
        AssertJUnit.assertEquals(90, c.priceFor(ann, 100));
        AssertJUnit.assertEquals(100, c.priceFor(c.player("Bob"), 100));
        ann.perks.add(HexPerk.GOLD_BONUS.name());
        AssertJUnit.assertEquals(120, c.withGoldBonus(ann, 100));
        ann.perks.clear();

        final String chosen = ann.perkOffer.get(0);
        AssertJUnit.assertTrue(c.choosePerk("Ann", chosen));
        AssertJUnit.assertEquals(Arrays.asList(chosen), ann.perks);
        AssertJUnit.assertTrue("nothing more due", ann.perkOffer.isEmpty());
        AssertJUnit.assertFalse(c.choosePerk("Ann", chosen));

        // an extra card in the opening hand reaches the fight
        ann.perks.add(HexPerk.EXTRA_CARD.name());
        final List<RegisteredPlayer> players = HexBattle.createPlayers(c, enemy(), true);
        AssertJUnit.assertEquals(players.get(1).getStartingHand() + 1, players.get(0).getStartingHand());
    }

    /** The enemy side of a fight, in seat order. */
    private static List<RegisteredPlayer> enemySide(final HexCampaign c, final HexEncounter enc) {
        final List<RegisteredPlayer> enemies = new java.util.ArrayList<>();
        for (final RegisteredPlayer rp : HexBattle.createPlayers(c, enc, true)) {
            if (rp.getTeamNumber() == HexBattle.ENEMY_TEAM) {
                enemies.add(rp);
            }
        }
        return enemies;
    }

    private static String cardsOnBattlefield(final RegisteredPlayer rp) {
        final List<String> names = new java.util.ArrayList<>();
        for (final forge.item.IPaperCard card : rp.getCardsOnBattlefield()) {
            names.add(card.getName());
        }
        java.util.Collections.sort(names);
        return names.toString();
    }

    @Test
    public void testBossAndTaskEnemiesFightWithCopiesOfThemselves() {
        final HexCampaign c = newCampaign();
        AssertJUnit.assertTrue("two heads meet more than one enemy head", c.enemyHeads() > 1);
        final HexEncounter boss = HexContent.get().bossEncounter(HexColor.RED);
        final HexEncounter task = enemy();
        task.questId = 7;
        for (final HexEncounter enc : Arrays.asList(boss, task)) {
            final List<RegisteredPlayer> enemies = enemySide(c, enc);
            AssertJUnit.assertEquals(c.enemyHeads(), enemies.size());
            final RegisteredPlayer first = enemies.get(0);
            for (final RegisteredPlayer rp : enemies) {
                AssertJUnit.assertTrue(rp.getPlayer().getName().startsWith(enc.enemyName));
                AssertJUnit.assertEquals("the same deck", first.getDeck().getMain().toCardList(", "), rp.getDeck().getMain().toCardList(", "));
                AssertJUnit.assertEquals("the same equipment", cardsOnBattlefield(first), cardsOnBattlefield(rp));
                AssertJUnit.assertEquals(first.getStartingLife(), rp.getStartingLife());
            }
        }

        // any other enemy brings followers
        final List<RegisteredPlayer> enemies = enemySide(c, enemy());
        AssertJUnit.assertFalse(enemies.get(1).getPlayer().getName().startsWith(enemy().enemyName));
    }

    @Test
    public void testEventsGiveAnEdgeForTheNextFight() {
        final HexCampaign c = newCampaign();
        final HexTile t = emptyNeighbour(c);
        t.event = HexEvent.SHRINE.name();
        final int life = c.fightLife();
        final int serial = c.eventSerial;
        AssertJUnit.assertEquals(HexCampaign.MoveResult.NOTHING, c.moveTo(t.q, t.r));
        AssertJUnit.assertNull("the event is used up", t.event);
        AssertJUnit.assertEquals(serial + 1, c.eventSerial);
        AssertJUnit.assertNotNull(c.lastEvent);
        AssertJUnit.assertEquals(life + HexEvent.SHRINE_LIFE, c.fightLife());
        AssertJUnit.assertFalse(c.fightBoosts().isEmpty());

        // used up by the next fight
        winFight(c);
        AssertJUnit.assertEquals(life, c.fightLife());
        AssertJUnit.assertTrue(c.fightBoosts().isEmpty());

        // a chest: gold for everybody (no enemy around that could step in the way)
        for (final HexTile any : c.map.tiles.values()) {
            any.enemy = null;
        }
        final HexTile next = emptyNeighbour(c);
        next.event = HexEvent.CHEST.name();
        final int gold = c.player("Bob").gold;
        c.moveTo(next.q, next.r);
        AssertJUnit.assertEquals(gold + c.gold(HexEvent.CHEST_GOLD), c.player("Bob").gold);
    }

    @Test
    public void testTradingInTown() {
        final HexCampaign c = newCampaign();
        AssertJUnit.assertTrue(c.inTown());
        final HexPlayer ann = c.player("Ann");
        final HexPlayer bob = c.player("Bob");
        final int annGold = ann.gold;
        AssertJUnit.assertTrue(c.giveGold("Ann", "Bob", 25));
        AssertJUnit.assertEquals(annGold - 25, ann.gold);
        AssertJUnit.assertFalse("more than she has", c.giveGold("Ann", "Bob", 100000));

        ann.sideboard.add("Lightning Bolt");
        AssertJUnit.assertTrue(c.giveCard("Ann", "Bob", "Lightning Bolt", true));
        AssertJUnit.assertTrue(bob.sideboard.contains("Lightning Bolt"));
        AssertJUnit.assertFalse(ann.sideboard.contains("Lightning Bolt"));

        // a deck never falls below its minimum size
        while (ann.deck.size() > HexCampaign.MIN_DECK_SIZE) {
            ann.deck.remove(0);
        }
        AssertJUnit.assertFalse(c.giveCard("Ann", "Bob", ann.deck.get(0), false));
    }

    @Test
    public void testReadyCheckAndFastTravel() {
        final HexCampaign c = newCampaign();
        c.markDeckChecked("Ann");
        AssertJUnit.assertTrue("only counts before a fight", c.deckChecked.isEmpty());
        c.pendingEncounter = enemy();
        c.markDeckChecked("Ann");
        AssertJUnit.assertEquals(Arrays.asList("Ann"), c.deckChecked);
        c.resolveBattle(true);
        AssertJUnit.assertTrue(c.deckChecked.isEmpty());
        c.pendingRewards.clear();

        // fast travel to another town the party has been to (not a besieged one)
        HexTile other = null;
        for (final HexTile t : c.map.tilesWith(HexPoi.TOWN)) {
            if (t != c.tile() && t.siege == null && HexMap.distance(t.q, t.r, c.partyQ, c.partyR) > 1) {
                other = t;
                break;
            }
        }
        AssertJUnit.assertNotNull(other);
        AssertJUnit.assertFalse("not been there yet", c.canFastTravel(other));
        other.explored = true;
        AssertJUnit.assertTrue(c.canFastTravel(other));
        final int gold = c.player("Ann").gold;
        AssertJUnit.assertTrue(c.fastTravel(other.q, other.r));
        AssertJUnit.assertSame(other, c.tile());
        AssertJUnit.assertEquals(gold - HexCampaign.FAST_TRAVEL_GOLD, c.player("Ann").gold);

        // everybody has to be able to pay
        c.player("Bob").gold = 0;
        final HexTile back = c.map.get(c.prevQ, c.prevR);
        AssertJUnit.assertFalse(c.canFastTravel(back));
    }
}
