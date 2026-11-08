package forge.gamemodes.hexcrawl;

import java.util.Arrays;
import java.util.List;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.deck.Deck;

/** Arzakon, the secret lord: behind Emrakul in a cycle whose five-colour dungeon was cleared; one try a cycle. */
public class HexArzakonTest extends AITest {

    private static HexTile centre(final HexCampaign c) {
        for (final HexTile t : c.map.tilesWith(HexPoi.BOSS)) {
            if (t.color == HexColor.COLORLESS) {
                return t;
            }
        }
        throw new AssertionError("no centre");
    }

    /** A campaign whose archmages are beaten, the party next to the centre, nothing else on the map. */
    private static HexCampaign beforeTheCentre(final boolean rainbowCleared) {
        final HexCampaign c = HexCampaign.create("Arzakon", 31L, 7, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        c.defeatedBosses.addAll(Arrays.asList(HexColor.COLORS));
        if (rainbowCleared) {
            c.battlesWon = c.rainbowAt();
            c.maybeOpenRainbowDungeon();
            c.rainbowDungeon().dungeon.cleared = true;
        }
        for (final HexTile any : c.map.tiles.values()) {
            any.enemy = null;
            any.event = null;
        }
        c.quests.clear();
        final HexTile centre = centre(c);
        for (final HexTile n : c.map.neighbors(centre)) {
            if (n.poi == HexPoi.NONE) {
                c.partyQ = c.prevQ = n.q;
                c.partyR = c.prevR = n.r;
                return c;
            }
        }
        throw new AssertionError("no free field next to the centre");
    }

    private static void fight(final HexCampaign c, final HexBattle.Result result) {
        final HexFightSummary summary = new HexFightSummary();
        summary.enemyName = c.pendingEncounter.enemyName;
        summary.result = result.name();
        summary.partyLife = result == HexBattle.Result.WON ? 10 : 0;
        c.recordFight(summary);
        c.resolveBattle(result);
        c.pendingRewards.clear();
    }

    /** Into the centre, and Emrakul beaten. */
    private static void beatEmrakul(final HexCampaign c) {
        final HexTile centre = centre(c);
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(centre.q, centre.r));
        AssertJUnit.assertEquals(HexContent.get().getBossName(HexColor.COLORLESS), c.pendingEncounter.enemyName);
        fight(c, HexBattle.Result.WON);
    }

    @Test
    public void heIsReadFromTheOldBorderPlane() {
        final HexContent content = HexContent.get();
        final HexEncounter enc = content.arzakonEncounter();
        AssertJUnit.assertNotNull(enc);
        AssertJUnit.assertTrue(enc.boss);
        AssertJUnit.assertTrue(enc.secretBoss);
        AssertJUnit.assertEquals(HexContent.ARZAKON, enc.enemyName);
        AssertJUnit.assertEquals(HexContent.ARZAKON_LIFE, enc.life);
        AssertJUnit.assertTrue("the planeswalker in play", enc.startCards.contains(HexContent.ARZAKON_CARD));
        final Deck deck = content.loadEnemyDeck(enc);
        AssertJUnit.assertTrue("his old Shandalar deck", deck.getMain().countAll() >= 60);
        AssertJUnit.assertNotNull("his sprite", content.getSpriteAtlas(enc.enemyName));
        final List<forge.item.IPaperCard> command = content.bossCommandCards(enc, 1);
        AssertJUnit.assertEquals("his presence and his awakening", 2, command.size());
        AssertJUnit.assertEquals("Arzakon Unleashed", content.phaseTwoCard(enc));
        AssertJUnit.assertNotNull("his trophy", content.trophy(HexContent.ARZAKON_TROPHY, 1));
        AssertJUnit.assertNotNull(content.trophy(HexContent.ARZAKON_TROPHY, 3));
    }

    @Test
    public void heFightsAloneWithSchemesAndKeepsHisLife() {
        final HexCampaign c = HexCampaign.create("Arzakon alone", 31L, 7, Arrays.asList(new HexPlayer("Ann", false),
                new HexPlayer("Bob", false), new HexPlayer("Cid", false)));
        final HexEncounter enc = HexContent.get().arzakonEncounter();
        AssertJUnit.assertTrue("archenemy style", enc.archenemy);
        AssertJUnit.assertEquals("one head, whatever the party", 1, c.enemyHeads(enc));
        int enemies = 0;
        for (final forge.game.player.RegisteredPlayer rp : HexBattle.createPlayers(c, enc, true)) {
            if (rp.getTeamNumber() == HexBattle.ENEMY_TEAM) {
                enemies++;
                AssertJUnit.assertTrue("with schemes", rp.getSchemes() != null && rp.getSchemes().iterator().hasNext());
            }
        }
        AssertJUnit.assertEquals(1, enemies);
        // his life as it was with his heads: an archenemy's extra life for the missing heads is not his
        final HexEncounter withHeads = HexContent.get().arzakonEncounter();
        withHeads.archenemy = false;
        AssertJUnit.assertEquals(c.enemyLife(withHeads), c.enemyLife(enc));
        // his angel by the cycle: Platinum, Darksteel, then Blightsteel
        final List<String> first = new java.util.ArrayList<>();
        HexContent.get().bossCommandCards(enc, 1).forEach(card -> first.add(card.getName()));
        final List<String> later = new java.util.ArrayList<>();
        HexContent.get().bossCommandCards(enc, 2).forEach(card -> later.add(card.getName()));
        AssertJUnit.assertTrue(first.toString(), first.contains("Arzakon's Presence"));
        AssertJUnit.assertTrue(later.toString(), later.contains("Arzakon's Dread Presence"));
        final List<String> third = new java.util.ArrayList<>();
        HexContent.get().bossCommandCards(enc, 3).forEach(card -> third.add(card.getName()));
        AssertJUnit.assertTrue(third.toString(), third.contains("Arzakon's Blighted Presence"));
    }

    @Test
    public void withoutTheFiveColourDungeonEmrakulEndsTheCycle() {
        final HexCampaign c = beforeTheCentre(false);
        beatEmrakul(c);
        AssertJUnit.assertTrue(c.won);
        AssertJUnit.assertFalse(c.arzakonAwake);
        AssertJUnit.assertNull(c.enemyAt(centre(c)));
        AssertJUnit.assertEquals(HexContent.get().getBossName(HexColor.COLORLESS), c.finalBossName());
    }

    @Test
    public void afterItEmrakulWasOnlyTheGatekeeper() {
        final HexCampaign c = beforeTheCentre(true);
        final HexTile centre = centre(c);
        beatEmrakul(c);
        AssertJUnit.assertFalse("not won yet", c.won);
        AssertJUnit.assertTrue(c.arzakonAwake);
        AssertJUnit.assertTrue(c.arzakonWaits(centre));
        AssertJUnit.assertTrue("thrown back", c.isAdjacent(centre));
        AssertJUnit.assertEquals(HexContent.ARZAKON, centre.name);
        AssertJUnit.assertTrue(c.enemyAt(centre).secretBoss);
        // he is the next fight on the centre
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(centre.q, centre.r));
        AssertJUnit.assertTrue(c.pendingEncounter.secretBoss);
        final HexFightSummary summary = new HexFightSummary();
        summary.enemyName = c.pendingEncounter.enemyName;
        summary.result = HexBattle.Result.WON.name();
        summary.partyLife = 10;
        c.recordFight(summary);
        c.resolveBattle(HexBattle.Result.WON);
        // every head gets the angel he conjured in this cycle, in its sideboard
        for (final HexReward reward : c.pendingRewards) {
            AssertJUnit.assertEquals("Arzakon's Platinum Angel", reward.bossCard);
            AssertJUnit.assertTrue(c.player(reward.playerName).sideboard.contains("Arzakon's Platinum Angel"));
        }
        AssertJUnit.assertEquals(c.players.size(), c.pendingRewards.size());
        c.pendingRewards.clear();
        AssertJUnit.assertTrue("the true ending", c.won);
        AssertJUnit.assertTrue(c.arzakonBeaten);
        AssertJUnit.assertFalse(c.arzakonAwake);
        AssertJUnit.assertEquals(HexContent.ARZAKON, c.finalBossName());
        for (final HexPlayer p : c.players) {
            AssertJUnit.assertEquals("his trophy", Integer.valueOf(1), p.trophies.get(HexContent.ARZAKON_TROPHY));
            AssertJUnit.assertNotNull(c.ownedTrophy(p, HexContent.ARZAKON_TROPHY));
        }
    }

    @Test
    public void hisAngelsAreARewardOnly() throws Exception {
        final HexContent content = HexContent.get();
        AssertJUnit.assertEquals("Arzakon's Platinum Angel", HexContent.arzakonAngel(1));
        AssertJUnit.assertEquals("Arzakon's Darksteel Angel", HexContent.arzakonAngel(2));
        AssertJUnit.assertEquals("Arzakon's Blightsteel Angel", HexContent.arzakonAngel(3));
        AssertJUnit.assertEquals("and from then on", "Arzakon's Blightsteel Angel", HexContent.arzakonAngel(7));
        for (int cycle = 1; cycle <= 3; cycle++) {
            final String angel = HexContent.arzakonAngel(cycle);
            AssertJUnit.assertNotNull("a card of the campaign: " + angel, content.getCard(angel));
            AssertJUnit.assertEquals("worth something", 1000 * cycle, content.price(angel));
        }
        // never in a shop
        final HexCampaign c = beforeTheCentre(false);
        for (final HexShopOffer offer : c.shop()) {
            AssertJUnit.assertFalse(String.valueOf(offer.card), HexContent.isArzakonAngel(offer.card));
        }
        // never lost in a fight, whatever the level lets go
        final HexPlayer ann = c.player("Ann");
        ann.deck.clear();
        for (int i = 0; i < 3; i++) {
            ann.deck.add("Arzakon's Darksteel Angel");
        }
        final java.lang.reflect.Method lose = HexCampaign.class.getDeclaredMethod("loseRandomCard", HexPlayer.class, int.class);
        lose.setAccessible(true);
        AssertJUnit.assertNull("nothing to lose", lose.invoke(c, ann, Integer.MAX_VALUE));
        AssertJUnit.assertEquals(3, java.util.Collections.frequency(ann.deck, "Arzakon's Darksteel Angel"));
    }

    @Test
    public void heGivesOneTryACycle() {
        final HexCampaign c = beforeTheCentre(true);
        final HexTile centre = centre(c);
        beatEmrakul(c);
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(centre.q, centre.r));
        fight(c, HexBattle.Result.LOST);
        AssertJUnit.assertTrue("Emrakul's fall still wins the cycle", c.won);
        AssertJUnit.assertFalse(c.arzakonAwake);
        AssertJUnit.assertFalse(c.arzakonBeaten);
        AssertJUnit.assertNull("he is gone", c.enemyAt(centre));
        // a new cycle: he sleeps again
        AssertJUnit.assertTrue(c.startNewGamePlus());
        AssertJUnit.assertFalse(c.arzakonAwake);
        AssertJUnit.assertFalse(c.arzakonBeaten);
    }

    @Test
    public void aGuestSeesHimWaiting() {
        final HexCampaign c = beforeTheCentre(true);
        beatEmrakul(c);
        final HexSession host = new HexSession(c);
        final HexSession guest = HexSession.forGuest();
        guest.applyState(host.toStateJson(), "Bob");
        final HexCampaign theirs = guest.getCampaign();
        AssertJUnit.assertTrue(theirs.arzakonAwake);
        AssertJUnit.assertTrue(theirs.arzakonWaits(centre(theirs)));
        AssertJUnit.assertTrue(theirs.enemyAt(centre(theirs)).secretBoss);
    }
}
