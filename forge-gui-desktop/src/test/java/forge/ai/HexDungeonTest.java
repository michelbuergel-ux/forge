package forge.ai;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexDefeat;
import forge.gamemodes.hexcrawl.HexDungeon;
import forge.gamemodes.hexcrawl.HexFightSummary;
import forge.gamemodes.hexcrawl.HexMap;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexReward;
import forge.gamemodes.hexcrawl.HexTile;
import forge.item.PaperCard;

/** Dungeons: three fights in a row, the party's life carried over, the decks locked, and a chest at the end. */
public class HexDungeonTest extends AITest {

    private static HexCampaign newCampaign(final long seed, final int radius) {
        return HexCampaign.create("Dungeon", seed, radius, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
    }

    /** Puts the party next to a dungeon, on a free field; the land around is emptied. */
    private static HexTile dungeonNextToParty(final HexCampaign c) {
        for (final HexTile dungeon : c.dungeons()) {
            for (final HexTile n : c.map.neighbors(dungeon)) {
                if (n.poi == HexPoi.NONE && c.questAt(n.q, n.r) == null) {
                    c.partyQ = c.prevQ = n.q;
                    c.partyR = c.prevR = n.r;
                    for (final HexTile any : c.map.tiles.values()) {
                        any.enemy = null;
                        any.event = null;
                    }
                    return dungeon;
                }
            }
        }
        throw new AssertionError("no dungeon with a free field next to it");
    }

    /** The fight ends with the party at this much life. */
    private static void win(final HexCampaign c, final int partyLife) {
        final HexFightSummary summary = new HexFightSummary();
        summary.enemyName = c.pendingEncounter.enemyName;
        summary.result = HexBattle.Result.WON.name();
        summary.partyLife = partyLife;
        c.recordFight(summary);
        c.resolveBattle(HexBattle.Result.WON);
    }

    @Test
    public void everyColourHasADungeonAwayFromTownsAndStrongholds() {
        for (final int radius : new int[] { 6, 7, 8 }) {
            for (long seed = 0; seed < 15; seed++) {
                final HexCampaign c = newCampaign(seed, radius);
                final String where = "radius " + radius + ", seed " + seed;
                final Set<HexColor> colours = new HashSet<>();
                for (final HexTile t : c.dungeons()) {
                    AssertJUnit.assertTrue("one per colour: " + where, colours.add(t.color));
                    AssertJUnit.assertNotNull(t.dungeon);
                    AssertJUnit.assertEquals(HexDungeon.FIGHTS, t.dungeon.stages.size());
                    AssertJUnit.assertFalse(t.q == c.map.startQ && t.r == c.map.startR);
                    for (final HexTile other : c.map.tiles.values()) {
                        if (other != t && (other.poi == HexPoi.TOWN || other.poi == HexPoi.DUNGEON)) {
                            AssertJUnit.assertTrue("not next to a town: " + where, HexMap.distance(t.q, t.r, other.q, other.r) >= 2);
                        }
                    }
                    AssertJUnit.assertSame(t.dungeon.stages.get(0), c.enemyAt(t));
                    AssertJUnit.assertTrue("the guardian is last", t.dungeon.stages.get(2).isGuardian());
                    AssertJUnit.assertTrue("the guardian has copies of itself as heads", t.dungeon.stages.get(2).headsAlike());
                }
                AssertJUnit.assertEquals(where, HexColor.COLORS.length, colours.size());
            }
        }
    }

    @Test
    public void threeFightsWithTheLifeCarriedOverThenTheChest() {
        final HexCampaign c = newCampaign(3L, 7);
        final HexTile t = dungeonNextToParty(c);
        final HexDungeon d = t.dungeon;
        final int full = c.fightLife();

        // the first fight: a last look at the decks, then they are locked
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.moveTo(t.q, t.r));
        AssertJUnit.assertEquals(1, c.pendingEncounter.dungeonStage);
        AssertJUnit.assertTrue(c.pendingEncounter.ambush);
        AssertJUnit.assertEquals(full, c.partyStartLife());
        AssertJUnit.assertFalse(c.deckLocked());
        win(c, 17);
        AssertJUnit.assertSame(t, c.tile());
        AssertJUnit.assertEquals(1, d.stage);
        AssertJUnit.assertEquals(Math.min(full, 17 + HexCampaign.DUNGEON_HEAL), d.lifeLeft);
        AssertJUnit.assertTrue(c.deckLocked());
        final HexPlayer ann = c.player("Ann");
        ann.sideboard.add("Lightning Bolt");
        AssertJUnit.assertFalse("the decks are locked", c.moveCard("Ann", "Lightning Bolt", false));
        AssertJUnit.assertFalse("rewards first", c.canGoDeeper());
        c.pendingRewards.clear();
        AssertJUnit.assertTrue(c.canGoDeeper());
        AssertJUnit.assertTrue(c.canEnter(t));

        // the second: with the life left
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.goDeeper());
        AssertJUnit.assertEquals(2, c.pendingEncounter.dungeonStage);
        AssertJUnit.assertEquals(d.lifeLeft, c.partyStartLife());
        win(c, 6);
        AssertJUnit.assertEquals(6 + HexCampaign.DUNGEON_HEAL, d.lifeLeft);
        c.pendingRewards.clear();

        // the guardian: tougher, and the chest behind it
        AssertJUnit.assertEquals(HexCampaign.MoveResult.ENCOUNTER, c.goDeeper());
        AssertJUnit.assertTrue(c.pendingEncounter.isGuardian());
        AssertJUnit.assertEquals(11, c.partyStartLife());
        final int guardianLife = c.enemyLife(c.pendingEncounter);
        c.pendingEncounter.dungeonStage = 0;
        final int plain = c.enemyLife(c.pendingEncounter);
        c.pendingEncounter.dungeonStage = HexDungeon.FIGHTS;
        AssertJUnit.assertEquals(plain + HexCampaign.DUNGEON_GUARDIAN_LIFE_PER_HEAD * 2, guardianLife);
        final int annGold = ann.gold;
        win(c, 3);
        AssertJUnit.assertTrue(d.cleared);
        AssertJUnit.assertFalse(c.deckLocked());
        AssertJUnit.assertNull("empty now", c.enemyAt(t));
        AssertJUnit.assertEquals(HexDefeat.Kind.DUNGEON, c.defeated.get(c.defeated.size() - 1).kind);
        final Set<String> treasures = new HashSet<>();
        for (final HexPlayer p : c.players) {
            final HexReward reward = c.pendingReward(p.name);
            AssertJUnit.assertNotNull(reward.chestCard);
            AssertJUnit.assertTrue("a different card for every head", treasures.add(reward.chestCard));
            AssertJUnit.assertTrue(p.sideboard.contains(reward.chestCard));
            AssertJUnit.assertTrue(reward.chestCard + " is valuable", HexContent.get().price(reward.chestCard) >= HexContent.TREASURE_PRICE);
            final PaperCard card = HexContent.get().getCard(reward.chestCard);
            final boolean colourless = card.getRules().getColor().isColorless();
            AssertJUnit.assertTrue(reward.chestCard + " fits the dungeon", colourless
                    ? card.getRules().getType().isArtifact() || card.getRules().getType().isLand()
                    : card.getRules().getColor().hasAnyColor(colourBit(d.color)));
            AssertJUnit.assertTrue(reward.chestGold > 0);
            AssertJUnit.assertNotNull(reward.chestRelic);
            AssertJUnit.assertTrue("worn, or paid in gold", p.relics.contains(reward.chestRelic) || reward.chestRelicGold > 0);
        }
        AssertJUnit.assertTrue("the chest's gold", ann.gold >= annGold + c.pendingReward("Ann").chestGold);
        c.pendingRewards.clear();
        AssertJUnit.assertFalse(c.canGoDeeper());
    }

    private static byte colourBit(final HexColor color) {
        switch (color) {
            case WHITE: return forge.card.MagicColor.WHITE;
            case BLUE: return forge.card.MagicColor.BLUE;
            case BLACK: return forge.card.MagicColor.BLACK;
            case RED: return forge.card.MagicColor.RED;
            default: return forge.card.MagicColor.GREEN;
        }
    }

    @Test
    public void leavingLosingOrFleeingStartsTheDungeonAnew() {
        final HexCampaign c = newCampaign(5L, 7);
        final HexTile t = dungeonNextToParty(c);
        final HexTile outside = c.tile();
        final HexDungeon d = t.dungeon;

        // leaving between two fights
        c.moveTo(t.q, t.r);
        win(c, 15);
        c.pendingRewards.clear();
        AssertJUnit.assertTrue(d.inRun());
        AssertJUnit.assertEquals(HexCampaign.MoveResult.NOTHING, c.moveTo(outside.q, outside.r));
        AssertJUnit.assertEquals(0, d.stage);
        AssertJUnit.assertEquals(0, d.lifeLeft);
        AssertJUnit.assertFalse(c.deckLocked());
        AssertJUnit.assertSame(d.stages.get(0), c.enemyAt(t));

        // losing the second fight: back outside
        for (final HexTile any : c.map.tiles.values()) {
            any.enemy = null;
            any.event = null;
        }
        c.moveTo(t.q, t.r);
        win(c, 15);
        c.pendingRewards.clear();
        c.goDeeper();
        c.resolveBattle(HexBattle.Result.LOST);
        AssertJUnit.assertSame(outside, c.tile());
        AssertJUnit.assertEquals(0, d.stage);
        AssertJUnit.assertFalse(d.cleared);

        // fleeing the first: the same
        for (final HexTile any : c.map.tiles.values()) {
            any.enemy = null;
            any.event = null;
        }
        c.moveTo(t.q, t.r);
        c.resolveBattle(HexBattle.Result.FLED);
        AssertJUnit.assertSame(outside, c.tile());
        AssertJUnit.assertEquals(0, d.stage);
    }

    @Test
    public void marchingGoesAroundDungeons() {
        final HexCampaign c = newCampaign(7L, 7);
        for (final HexTile t : c.map.tiles.values()) {
            t.explored = true;
        }
        for (final HexTile goal : c.map.tiles.values()) {
            final List<HexTile> way = c.route(goal);
            if (way == null) {
                continue;
            }
            for (int i = 0; i < way.size() - 1; i++) {
                AssertJUnit.assertFalse("no dungeon on the way to " + goal.key(), way.get(i).poi == HexPoi.DUNGEON);
            }
        }
    }

    @Test
    public void newGamePlusBringsNewDungeons() {
        final HexCampaign c = newCampaign(9L, 7);
        for (final HexTile t : c.dungeons()) {
            t.dungeon.cleared = true;
        }
        c.won = true;
        c.startNewGamePlus();
        AssertJUnit.assertEquals(HexColor.COLORS.length, c.dungeons().size());
        for (final HexTile t : c.dungeons()) {
            AssertJUnit.assertFalse(t.dungeon.cleared);
        }
    }

    @Test
    public void anOldSaveGetsDungeonsOnlyWhereThePartyHasNotBeen() {
        final HexCampaign c = newCampaign(11L, 7);
        // as a save from before there were dungeons
        for (final HexTile t : c.dungeons()) {
            t.poi = HexPoi.NONE;
            t.dungeon = null;
        }
        c.dungeonsPlaced = false;
        for (final HexTile t : c.map.tiles.values()) {
            if (HexMap.distance(t.q, t.r, 0, 0) == c.map.radius - 1) {
                t.explored = true; // the party has been all around the outer ring
            }
        }
        HexTile step = null;
        for (final HexTile n : c.map.neighbors(c.tile())) {
            if (n.poi == HexPoi.NONE && c.questAt(n.q, n.r) == null) {
                n.enemy = null;
                n.event = null;
                step = n;
                break;
            }
        }
        AssertJUnit.assertNotNull(step);
        c.moveTo(step.q, step.r);
        AssertJUnit.assertTrue(c.dungeonsPlaced);
        AssertJUnit.assertFalse(c.dungeons().isEmpty());
        for (final HexTile t : c.dungeons()) {
            AssertJUnit.assertFalse("only fields the party has not been to", HexMap.distance(t.q, t.r, 0, 0) == c.map.radius - 1);
        }
    }

    @Test
    public void everyColourHasEnoughTreasure() {
        for (final HexColor color : HexColor.values()) {
            final List<String> treasure = HexContent.get().treasureCards(color, 4);
            AssertJUnit.assertTrue(color + ": " + treasure.size(), treasure.size() >= 4);
            AssertJUnit.assertEquals(treasure.size(), new HashSet<>(treasure).size());
        }
    }
}
