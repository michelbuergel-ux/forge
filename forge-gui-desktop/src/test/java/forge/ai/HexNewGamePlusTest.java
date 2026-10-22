package forge.ai;

import forge.card.CardRules;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gamemodes.hexcrawl.HexAdvantage;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexShopOffer;
import forge.gamemodes.hexcrawl.HexShopType;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.model.FModel;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** New Game Plus of the Hex Campaign, and the cards it leaves out because they need a commander. */
public class HexNewGamePlusTest extends AITest {

    private static HexCampaign wonCampaign() {
        final HexCampaign c = HexCampaign.create("Test", 5L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        c.won = true;
        c.defeatedBosses.addAll(Arrays.asList(HexColor.COLORS));
        c.defeatedBosses.add(HexColor.COLORLESS);
        return c;
    }

    private static HexEncounter enemy() {
        return HexContent.get().randomEncounter(HexColor.GREEN, 0.5, new Random(4));
    }

    @Test
    public void testNewGamePlusKeepsTheParty() {
        final HexCampaign c = wonCampaign();
        final HexPlayer ann = c.player("Ann");
        ann.gold = 123;
        ann.sideboard.add("Grizzly Bears");
        ann.relics.add("some relic");
        final List<String> deck = new ArrayList<>(ann.deck);
        final HexEncounter enc = enemy();
        final int life = c.enemyLife(enc);
        final int gold = c.gold(100);

        AssertJUnit.assertFalse("only after the victory", HexCampaign.create("X", 1L, 4, Arrays.asList(new HexPlayer("Ann", false))).startNewGamePlus());
        AssertJUnit.assertTrue(c.startNewGamePlus());
        AssertJUnit.assertEquals(2, c.cycle);
        AssertJUnit.assertFalse(c.won);
        AssertJUnit.assertTrue("the archmages are back", c.defeatedBosses.isEmpty());
        AssertJUnit.assertEquals(c.map.startQ, c.partyQ);
        AssertJUnit.assertEquals(c.map.startR, c.partyR);
        AssertJUnit.assertEquals(123, ann.gold);
        AssertJUnit.assertEquals(deck, ann.deck);
        AssertJUnit.assertEquals(Arrays.asList("Grizzly Bears"), ann.sideboard);
        AssertJUnit.assertEquals(Arrays.asList("some relic"), ann.relics);

        AssertJUnit.assertEquals("more life", life + HexCampaign.LIFE_PER_CYCLE, c.enemyLife(enc));
        AssertJUnit.assertEquals("more gold", gold * (100 + HexCampaign.GOLD_PERCENT_PER_CYCLE) / 100, c.gold(100));
        AssertJUnit.assertEquals(HexCampaign.ADVANTAGES_PER_CYCLE, c.newAdvantages.size());
        AssertJUnit.assertFalse("two different ones", c.newAdvantages.get(0).equals(c.newAdvantages.get(1)));
        AssertJUnit.assertEquals("no extra draw in the first New Game Plus", 0, c.advantage(HexAdvantage.DRAW));
        AssertJUnit.assertEquals(1 + HexCampaign.ADVANTAGES_PER_CYCLE, c.cycleAdvantages().size());
    }

    @Test
    public void testAdvantagesStopAtTheirCaps() {
        final HexCampaign c = wonCampaign();
        int total = 0;
        for (final HexAdvantage a : HexAdvantage.values()) {
            total += a.getCap();
        }
        for (int i = 0; i < 20; i++) {
            c.won = true;
            AssertJUnit.assertTrue(c.startNewGamePlus());
        }
        int steps = 0;
        for (final HexAdvantage a : HexAdvantage.values()) {
            AssertJUnit.assertEquals(a + " is at its cap", a.getCap(), c.advantage(a));
            steps += c.advantage(a);
        }
        AssertJUnit.assertEquals(total, steps);
        AssertJUnit.assertTrue("at the caps nothing more is rolled", c.newAdvantages.isEmpty());
        AssertJUnit.assertEquals(20 * HexCampaign.LIFE_PER_CYCLE, c.cycleLife());
        AssertJUnit.assertEquals(4, HexAdvantage.LANDS.getCap());
        AssertJUnit.assertEquals(4, HexAdvantage.HAND.getCap());
        AssertJUnit.assertEquals(4, HexAdvantage.BATTLEFIELD.getCap());
        AssertJUnit.assertEquals(3, HexAdvantage.DRAW.getCap());
    }

    @Test
    public void testEveryEnemyHeadGetsTheAdvantages() {
        final HexCampaign c = wonCampaign();
        AssertJUnit.assertTrue(c.startNewGamePlus());
        c.enemyAdvantages.put(HexAdvantage.LANDS.name(), 2);
        c.enemyAdvantages.put(HexAdvantage.HAND.name(), 3);
        c.enemyAdvantages.put(HexAdvantage.BATTLEFIELD.name(), 4);
        c.enemyAdvantages.put(HexAdvantage.DRAW.name(), 1);
        final HexEncounter enc = enemy();
        final List<RegisteredPlayer> players = HexBattle.createPlayers(c, enc, true);
        final RegisteredPlayer party = players.get(0);
        int heads = 0;
        for (final RegisteredPlayer rp : players) {
            if (rp.getTeamNumber() != HexBattle.ENEMY_TEAM) {
                continue;
            }
            heads++;
            AssertJUnit.assertEquals(party.getStartingHand() + 3, rp.getStartingHand());
            int lands = 0;
            int others = 0;
            for (final IPaperCard card : rp.getCardsOnBattlefield()) {
                if (card.getName().equals(enc.color.getBasicLand())) {
                    lands++;
                } else {
                    others++;
                    AssertJUnit.assertFalse(card.getName() + " is an Aura", card.getRules().getType().hasSubtype("Aura"));
                }
            }
            AssertJUnit.assertTrue("two lands of its colour", lands >= 2);
            AssertJUnit.assertTrue("four cards in play (and the equipment it brings)", others >= 4);
            AssertJUnit.assertEquals(1, rp.getExtraDrawsPerTurn());
        }
        AssertJUnit.assertEquals(c.enemyHeads(), heads);
    }

    @Test
    public void testAdvantageCardsAreEquipmentCreaturesOrEnchantments() {
        final HexContent content = HexContent.get();
        final Random rnd = new Random(9);
        boolean creature = false;
        boolean enchantment = false;
        for (int i = 0; i < 200; i++) {
            final IPaperCard card = content.advantageCard(HexColor.RED, rnd);
            AssertJUnit.assertNotNull(card);
            final CardRules rules = card.getRules();
            AssertJUnit.assertFalse(rules.getType().hasSubtype("Aura"));
            final byte color = rules.getColor().getColor();
            AssertJUnit.assertTrue(card.getName() + " is not red", color == forge.card.MagicColor.COLORLESS || rules.getColor().hasRed());
            if (card instanceof PaperCard && (rules.getType().isCreature() || rules.getType().isEnchantment()) && !rules.getType().isArtifact()) {
                AssertJUnit.assertTrue(card.getName() + " is not red", rules.getColor().hasRed());
                AssertJUnit.assertTrue(card.getName() + " costs too much", rules.getManaCost().getCMC() <= HexContent.ADVANTAGE_MAX_MANA_VALUE);
                creature |= rules.getType().isCreature();
                enchantment |= rules.getType().isEnchantment() && !rules.getType().isCreature();
            }
        }
        AssertJUnit.assertTrue("creatures come up", creature);
        AssertJUnit.assertTrue("enchantments come up", enchantment);
    }

    @Test
    public void testExtraDrawsInTheDrawStep() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(0); // not the active player: its turn is the next one
        fillLibrary(p, 10);
        p.getRegisteredPlayer().setExtraDrawsPerTurn(2);
        final int hand = p.getCardsIn(ZoneType.Hand).size();

        playUntilNextTurn(game);
        AssertJUnit.assertEquals(p, game.getPhaseHandler().getPlayerTurn());
        playUntilPhase(game, PhaseType.MAIN1);
        AssertJUnit.assertEquals("the normal draw and two more", hand + 3, p.getCardsIn(ZoneType.Hand).size());
    }

    @Test
    public void testCardsThatNeedACommanderAreLeftOut() {
        for (final String name : Arrays.asList("Command Tower", "Arcane Signet", "Thrasios, Triton Hero", "Candlekeep Sage")) {
            final PaperCard card = FModel.getMagicDb().getCommonCards().getCard(name);
            AssertJUnit.assertNotNull(name, card);
            AssertJUnit.assertTrue(name + " needs a commander", HexContent.needsCommander(card.getRules()));
        }
        AssertJUnit.assertFalse(HexContent.needsCommander(FModel.getMagicDb().getCommonCards().getCard("Grizzly Bears").getRules()));

        final HexContent content = HexContent.get();
        for (final HexColor color : HexColor.values()) {
            for (int seed = 0; seed < 5; seed++) {
                for (final HexShopOffer o : content.themeStock(color, null, new Random(seed))) {
                    if (o.shop != HexShopType.RELIC) {
                        AssertJUnit.assertFalse(o.card + " in a shop", HexContent.needsCommander(content.getCard(o.card).getRules()));
                    }
                }
            }
        }
        for (int seed = 0; seed < 20; seed++) {
            for (final String card : content.starterDeck(new Random(seed))) {
                AssertJUnit.assertFalse(card + " in a starter deck", HexContent.needsCommander(content.getCard(card).getRules()));
            }
        }
    }
}
