package forge.ai;

import forge.card.MagicColor;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.event.GameEventPlayerDamaged;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexBiomeRule;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexEvent;
import forge.gamemodes.hexcrawl.HexFight;
import forge.gamemodes.hexcrawl.HexFightStats;
import forge.gamemodes.hexcrawl.HexFightSummary;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexTile;
import forge.item.IPaperCard;
import forge.model.FModel;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Boss phases, the fight summary, fleeing, event choices, region rules and roaming enemies of the Hex Campaign. */
public class HexBattleFeelTest extends AITest {

    private static HexCampaign newCampaign() {
        final HexCampaign c = HexCampaign.create("Test", 17L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        c.encounterChance = 0; // no enemies turn up by themselves: the tests place them
        c.encounterChanceExplored = 0;
        return c;
    }

    private static HexEncounter enemy() {
        return HexContent.get().randomEncounter(HexColor.RED, 0.5, new Random(2));
    }

    /** An ordinary field next to the party (no town, boss, task, enemy or event). */
    private static HexTile emptyNeighbour(final HexCampaign c) {
        for (final HexTile t : c.map.neighbors(c.tile())) {
            if (t.poi == HexPoi.NONE && c.questAt(t.q, t.r) == null) {
                t.enemy = null;
                t.event = null;
                return t;
            }
        }
        throw new AssertionError("no empty field next to the party");
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

    private static List<String> names(final Iterable<? extends IPaperCard> cards) {
        final List<String> result = new ArrayList<>();
        if (cards != null) {
            for (final IPaperCard card : cards) {
                result.add(card.getName());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ bosses

    @Test
    public void testBossesBringTheirPresenceAndPhaseTwo() {
        final HexContent content = HexContent.get();
        AssertJUnit.assertNotNull("Adventure cards are loaded", FModel.getMagicDb().getCommonCards().getCard("Akroma's Presence"));
        AssertJUnit.assertNotNull("the campaign's phase cards are loaded", FModel.getMagicDb().getCommonCards().getCard("Akroma Awakened"));

        final HexEncounter akroma = content.bossEncounter(HexColor.WHITE);
        AssertJUnit.assertEquals(Arrays.asList("Akroma's Presence", "Akroma Awakened"), names(content.bossCommandCards(akroma, 1)));
        AssertJUnit.assertFalse(content.bossIntro(akroma, new Random(1)).isEmpty());
        AssertJUnit.assertTrue(names(content.bossCommandCards(content.bossEncounter(HexColor.COLORLESS), 2)).contains("Emrakul's Presence"));
        AssertJUnit.assertTrue(content.bossCommandCards(enemy(), 1).isEmpty());
        for (final HexColor color : HexColor.values()) {
            AssertJUnit.assertEquals(color + ": presence and phase card", 2, content.bossCommandCards(content.bossEncounter(color), 1).size());
        }

        // only the lead head carries them
        final HexCampaign c = newCampaign();
        akroma.q = c.partyQ;
        akroma.r = c.partyR;
        final List<RegisteredPlayer> enemies = enemySide(HexBattle.createPlayers(c, akroma, true));
        AssertJUnit.assertTrue(enemies.size() > 1);
        AssertJUnit.assertEquals(Arrays.asList("Akroma's Presence", "Akroma Awakened"), names(enemies.get(0).getExtraCardsInCommandZone()));
        AssertJUnit.assertTrue(names(enemies.get(1).getExtraCardsInCommandZone()).isEmpty());
    }

    @Test
    public void testPhaseTwoAwakensOnceAtHalfLife() {
        final Game game = initAndCreateGame();
        final Player boss = game.getPlayers().get(0);
        final Card phase = addCardToZone("Akroma Awakened", boss, ZoneType.Command);
        game.getTriggerHandler().resetActiveTriggers();
        game.getAction().checkStateEffects(true);
        playUntilStackClear(game);
        AssertJUnit.assertEquals("asleep at full life", 0, countCardsWithName(game, "Angel", ZoneType.Battlefield));

        boss.setLife(boss.getStartingLife() / 2, null);
        game.getAction().checkStateEffects(true);
        playUntilStackClear(game);
        AssertJUnit.assertEquals(1, phase.getCounters(CounterEnumType.AWAKENING));
        AssertJUnit.assertEquals("two Angels", 2, countAngels(boss));

        boss.setLife(boss.getStartingLife() / 4, null);
        game.getAction().checkStateEffects(true);
        playUntilStackClear(game);
        AssertJUnit.assertEquals("only once", 2, countAngels(boss));
    }

    private static int countAngels(final Player p) {
        int n = 0;
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.getType().hasCreatureType("Angel")) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ the summary

    @Test
    public void testFightStatsNameTheBestHead() {
        final Game game = initAndCreateGame();
        final Player ann = game.getPlayers().get(1);
        final Player foe = game.getPlayers().get(0);
        final HexFightStats stats = new HexFightStats(Arrays.asList(ann.getName(), "Nobody"));
        final Card bolt = addCard("Grizzly Bears", ann);
        stats.onPlayerDamaged(new GameEventPlayerDamaged(foe.getView(), bolt.getView(), 3, true, false));
        stats.onPlayerDamaged(new GameEventPlayerDamaged(foe.getView(), bolt.getView(), 2, true, false));
        final Card enemyCard = addCard("Grizzly Bears", foe);
        stats.onPlayerDamaged(new GameEventPlayerDamaged(ann.getView(), enemyCard.getView(), 7, true, false));

        final HexFightSummary s = stats.summary("Foe", HexBattle.Result.WON.name(), 4);
        AssertJUnit.assertEquals(ann.getName(), s.mvp);
        AssertJUnit.assertEquals("only the party's damage counts", 5, s.head(ann.getName()).damage);
        AssertJUnit.assertEquals("Grizzly Bears", s.head(ann.getName()).topCard);
        AssertJUnit.assertEquals(0, s.head("Nobody").damage);

        final HexCampaign c = newCampaign();
        c.recordFight(s);
        AssertJUnit.assertEquals(1, c.lastFight.serial);
        c.recordFight(stats.summary("Foe", "WON", 4));
        AssertJUnit.assertEquals(2, c.lastFight.serial);
    }

    // ------------------------------------------------------------------ fleeing

    @Test
    public void testFleeingCostsGoldNotCards() {
        final HexCampaign c = newCampaign();
        final HexTile next = emptyNeighbour(c);
        final HexEncounter foe = enemy();
        next.enemy = foe;
        foe.q = next.q;
        foe.r = next.r;
        final int startQ = c.partyQ;
        final int startR = c.partyR;
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(next.q, next.r));
        final HexPlayer ann = c.player("Ann");
        ann.gold = 100;
        c.player("Bob").gold = 3;
        final List<String> deck = new ArrayList<>(ann.deck);

        c.resolveBattle(HexBattle.Result.FLED);
        AssertJUnit.assertEquals(100 - c.gold(HexFight.FLEE_GOLD), ann.gold);
        AssertJUnit.assertEquals("pays what it has", 0, c.player("Bob").gold);
        AssertJUnit.assertEquals("no card lost", deck, ann.deck);
        AssertJUnit.assertEquals(startQ, c.partyQ);
        AssertJUnit.assertEquals(startR, c.partyR);
        AssertJUnit.assertEquals(1, c.battlesFled);
        AssertJUnit.assertEquals(0, c.battlesLost);
        AssertJUnit.assertNull(c.pendingEncounter);
        AssertJUnit.assertTrue("no reward", c.pendingRewards.isEmpty());
    }

    // ------------------------------------------------------------------ events

    @Test
    public void testShrineAndCaravanAreChoices() {
        final HexCampaign c = HexCampaign.create("Test", 17L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Cid", true)));
        c.encounterChance = 0;
        c.encounterChanceExplored = 0;
        final HexTile t = emptyNeighbour(c);
        t.event = HexEvent.RUINED_SHRINE.name();
        c.moveTo(t.q, t.r);
        final HexPlayer ann = c.player("Ann");
        AssertJUnit.assertEquals(HexEvent.RUINED_SHRINE.name(), ann.eventOffer);
        AssertJUnit.assertNull("companions leave it", c.player("Cid").eventOffer);

        // a basic land is not taken; a card from the sideboard is
        AssertJUnit.assertFalse(c.chooseEvent("Ann", HexColor.RED.getBasicLand(), false));
        ann.sideboard.add("Grizzly Bears");
        final int relics = ann.relics.size();
        final int gold = ann.gold;
        AssertJUnit.assertTrue(c.chooseEvent("Ann", "Grizzly Bears", true));
        AssertJUnit.assertFalse(ann.sideboard.contains("Grizzly Bears"));
        AssertJUnit.assertTrue("a relic or, with all slots taken, gold", ann.relics.size() == relics + 1 || ann.gold > gold);
        AssertJUnit.assertNull(ann.eventOffer);
        AssertJUnit.assertFalse("only once", c.chooseEvent("Ann", "Grizzly Bears", true));

        // a caravan: two cards of the field's colour for one
        ann.eventOffer = HexEvent.CARAVAN.name();
        ann.eventColor = HexColor.GREEN;
        ann.sideboard.add("Lightning Bolt");
        final int side = ann.sideboard.size();
        AssertJUnit.assertTrue(c.chooseEvent("Ann", "Lightning Bolt", true));
        AssertJUnit.assertEquals(side - 1 + 2, ann.sideboard.size());

        // the deck keeps its minimum size; leaving it ends the offer
        ann.eventOffer = HexEvent.CARAVAN.name();
        while (ann.deck.size() > HexCampaign.MIN_DECK_SIZE) {
            ann.deck.remove(0);
        }
        String nonBasic = null;
        for (final String card : ann.deck) {
            if (!HexContent.get().isBasicLand(card)) {
                nonBasic = card;
                break;
            }
        }
        AssertJUnit.assertFalse(c.chooseEvent("Ann", nonBasic, false));
        c.skipEvent("Ann");
        AssertJUnit.assertNull(ann.eventOffer);
    }

    // ------------------------------------------------------------------ region rules

    @Test
    public void testRegionRulesOnlyInNewGamePlus() {
        final HexCampaign c = newCampaign();
        HexTile swamp = null;
        HexTile forest = null;
        HexTile wastes = null;
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi != HexPoi.NONE) {
                continue;
            }
            if (t.color == HexColor.BLACK && swamp == null) {
                swamp = t;
            } else if (t.color == HexColor.GREEN && forest == null) {
                forest = t;
            } else if (t.color == HexColor.COLORLESS && wastes == null) {
                wastes = t;
            }
        }
        AssertJUnit.assertNotNull(swamp);
        AssertJUnit.assertNotNull(forest);
        final HexEncounter foe = enemy();
        foe.q = swamp.q;
        foe.r = swamp.r;
        AssertJUnit.assertNull("not in the first run", HexBiomeRule.forFight(c, foe));

        c.cycle = 2;
        AssertJUnit.assertEquals(HexBiomeRule.SWAMP, HexBiomeRule.forFight(c, foe));
        c.pendingEncounter = foe;
        for (final RegisteredPlayer rp : enemySide(HexBattle.createPlayers(c, foe, true))) {
            AssertJUnit.assertTrue("a Zombie on every enemy head", names(rp.getCardsOnBattlefield()).stream().anyMatch(n -> n.contains("Zombie")));
        }

        // the forest gives every party head a land
        foe.q = forest.q;
        foe.r = forest.r;
        final int landsSwamp = countPartyCards(c, foe);
        c.cycle = 1;
        final int landsPlain = countPartyCards(c, foe);
        AssertJUnit.assertEquals(landsPlain + 1, landsSwamp);

        if (wastes != null) {
            c.cycle = 2;
            foe.q = wastes.q;
            foe.r = wastes.r;
            final int life = c.enemyLife(foe);
            c.cycle = 1;
            AssertJUnit.assertEquals(c.enemyLife(foe) + HexCampaign.LIFE_PER_CYCLE + HexBiomeRule.WASTES.enemyLife, life);
        }
    }

    private static int countPartyCards(final HexCampaign c, final HexEncounter foe) {
        c.pendingEncounter = foe;
        final RegisteredPlayer head = HexBattle.createPlayers(c, foe, true).get(0);
        return names(head.getCardsOnBattlefield()).size();
    }

    // ------------------------------------------------------------------ roaming enemies

    @Test
    public void testRoamersHuntTheParty() {
        final HexCampaign c = newCampaign();
        for (final HexTile t : c.map.tiles.values()) {
            t.enemy = null;
            t.event = null;
        }
        final HexTile first = emptyNeighbour(c);
        c.moveTo(first.q, first.r);
        final HexTile next = emptyNeighbour(c);

        // a hunter three fields from where the party goes, with a free way
        HexTile start = null;
        for (final HexTile t : c.map.tiles.values()) {
            if (t.poi == HexPoi.NONE && c.questAt(t.q, t.r) == null && HexMap.distance(t.q, t.r, next.q, next.r) == 3) {
                for (final HexTile n : c.map.neighbors(t)) {
                    if (n.poi == HexPoi.NONE && c.questAt(n.q, n.r) == null && HexMap.distance(n.q, n.r, next.q, next.r) == 2) {
                        start = t;
                        break;
                    }
                }
            }
            if (start != null) {
                break;
            }
        }
        AssertJUnit.assertNotNull(start);
        final HexEncounter hunter = enemy();
        hunter.roaming = true;
        start.enemy = hunter;
        hunter.q = start.q;
        hunter.r = start.r;

        AssertJUnit.assertEquals(HexCampaign.MoveResult.NOTHING, c.moveTo(next.q, next.r));
        AssertJUnit.assertNull("it left its field", start.enemy);
        AssertJUnit.assertEquals("one field closer", 2, HexMap.distance(hunter.q, hunter.r, c.partyQ, c.partyR));
        AssertJUnit.assertSame(hunter, c.map.get(hunter.q, hunter.r).enemy);
        AssertJUnit.assertEquals(1, c.roamers().size());

        // one next to the party catches it with the party's next step, unless it steps away
        final HexTile ahead = emptyNeighbour(c);
        HexTile beside = null;
        for (final HexTile t : c.map.neighbors(ahead)) {
            if (t != c.tile() && t.poi == HexPoi.NONE && t.enemy == null && c.questAt(t.q, t.r) == null) {
                beside = t;
                break;
            }
        }
        AssertJUnit.assertNotNull(beside);
        c.map.get(hunter.q, hunter.r).enemy = null; // the first hunter is gone
        final HexEncounter catcher = enemy();
        catcher.roaming = true;
        beside.enemy = catcher;
        catcher.q = beside.q;
        catcher.r = beside.r;
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(ahead.q, ahead.r));
        AssertJUnit.assertSame(catcher, c.pendingEncounter);
        AssertJUnit.assertEquals(ahead.q, catcher.q);
        AssertJUnit.assertEquals(ahead.r, catcher.r);
    }
}
