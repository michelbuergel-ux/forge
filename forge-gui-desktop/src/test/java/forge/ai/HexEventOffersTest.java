package forge.ai;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.MagicColor;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEvent;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexTheme;
import forge.gamemodes.hexcrawl.HexTile;
import forge.gamemodes.net.event.HexActionEvent;
import forge.item.IPaperCard;
import forge.item.PaperCard;

/** The events that give cards and choices: cards that fit the head's deck, a camp to choose from, a caravan's stall, ... */
public class HexEventOffersTest extends AITest {

    /** A red deck: 17 mountains and red spells. */
    private static final List<String> RED_DECK = new ArrayList<>();
    static {
        for (int i = 0; i < 17; i++) {
            RED_DECK.add("Mountain");
        }
        for (final String card : new String[] { "Lightning Bolt", "Shock", "Goblin Guide", "Monastery Swiftspear", "Raging Goblin" }) {
            for (int i = 0; i < 5; i++) {
                RED_DECK.add(card);
            }
        }
    }

    private static HexCampaign newCampaign() {
        final HexCampaign c = HexCampaign.create("Events", 23L, 4, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false),
                new HexPlayer("Cid", true)));
        c.encounterChance = 0;
        c.encounterChanceExplored = 0;
        return c;
    }

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

    private static void stepOnto(final HexCampaign c, final HexEvent event) {
        final HexTile t = emptyNeighbour(c);
        t.event = event.name();
        c.moveTo(t.q, t.r);
    }

    private static byte colours(final PaperCard card) {
        final CardRules rules = card.getRules();
        return rules.getType().isLand() ? rules.getColorIdentity().getColor() : rules.getColor().getColor();
    }

    // ------------------------------------------------------------------ the cards

    @Test
    public void testTheCardsFitTheDeck() {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        ann.deck = new ArrayList<>(RED_DECK);
        // a red deck on a blue field: red or colourless cards, not blue ones
        final List<String> cards = HexContent.get().eventCards(ann, HexColor.BLUE, HexContent.EVENT_RARITIES, 40, null, new Random(5));
        AssertJUnit.assertEquals(40, cards.size());
        AssertJUnit.assertEquals("different cards", 40, new java.util.HashSet<>(cards).size());
        for (final String name : cards) {
            final PaperCard card = HexContent.get().getCard(name);
            AssertJUnit.assertTrue(name + " is red or colourless", (colours(card) & ~MagicColor.RED) == 0);
            AssertJUnit.assertFalse(name, card.getRules().getType().isBasicLand());
            AssertJUnit.assertTrue(name + " is uncommon or better", card.getRarity() == CardRarity.Uncommon
                    || card.getRarity() == CardRarity.Rare || card.getRarity() == CardRarity.MythicRare);
            AssertJUnit.assertFalse("not one the head owns", ann.ownedCards().contains(name));
        }
        // about half are uncommon, the rest rare or mythic
        int rare = 0;
        for (final String name : HexContent.get().eventCards(ann, HexColor.RED, HexContent.EVENT_RARITIES, 200, null, new Random(8))) {
            final CardRarity rarity = HexContent.get().getCard(name).getRarity();
            if (rarity == CardRarity.Rare || rarity == CardRarity.MythicRare) {
                rare++;
            }
        }
        AssertJUnit.assertTrue(rare + " of 200 rare or mythic", rare > 60 && rare < 130);
        // a red and green deck on a red field: red cards (some of them with green), or colourless ones
        ann.deck.addAll(Collections.nCopies(5, "Llanowar Elves"));
        for (final String name : HexContent.get().eventCards(ann, HexColor.RED, HexContent.EVENT_RARITIES, 40, null, new Random(6))) {
            final byte bits = colours(HexContent.get().getCard(name));
            AssertJUnit.assertTrue(name, bits == 0 || (bits & MagicColor.RED) != 0 && (bits & ~(MagicColor.RED | MagicColor.GREEN)) == 0);
        }
    }

    @Test
    public void testAPickedThemeMakesItsCardsLikelier() {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        ann.deck = new ArrayList<>(RED_DECK);
        final HexTheme burn = HexTheme.get("burn");
        AssertJUnit.assertNotNull(burn);
        final int without = fitting(HexContent.get().eventCards(ann, HexColor.RED, HexContent.EVENT_RARITIES, 60, null, new Random(9)), burn);
        ann.themes[0] = "burn";
        final int with = fitting(HexContent.get().eventCards(ann, HexColor.RED, HexContent.EVENT_RARITIES, 60, null, new Random(9)), burn);
        AssertJUnit.assertTrue("burn cards: " + without + " without the theme, " + with + " with it", with >= without + 12 && with * 2 > 60);
    }

    private static int fitting(final List<String> cards, final HexTheme theme) {
        int n = 0;
        for (final String name : cards) {
            if (theme.fits(HexContent.get().getCard(name).getRules())) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ the camp

    @Test
    public void testACampOffersThreeRareCardsOrGold() {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        final HexPlayer bob = c.player("Bob");
        final HexPlayer cid = c.player("Cid");
        final int cidSide = cid.sideboard.size();
        stepOnto(c, HexEvent.CAMP);
        AssertJUnit.assertEquals(HexEvent.CAMP.name(), ann.eventOffer);
        AssertJUnit.assertEquals(HexEvent.CAMP_CARDS, ann.eventCards.size());
        for (final String name : ann.eventCards) {
            final CardRarity rarity = HexContent.get().getCard(name).getRarity();
            AssertJUnit.assertTrue(name + " is rare or mythic", rarity == CardRarity.Rare || rarity == CardRarity.MythicRare);
        }
        AssertJUnit.assertTrue(ann.eventGold > 0);
        AssertJUnit.assertEquals("the companion took a card", cidSide + 1, cid.sideboard.size());
        AssertJUnit.assertEquals(Collections.singletonList("Cid"), c.lastEventOwners);

        // Ann takes the second card into her deck
        final String second = ann.eventCards.get(1);
        final int deck = ann.deck.size();
        AssertJUnit.assertFalse("no such card", c.chooseEvent("Ann", null, false, 7));
        AssertJUnit.assertTrue(c.chooseEvent("Ann", null, false, 2));
        AssertJUnit.assertEquals(deck + 1, ann.deck.size());
        AssertJUnit.assertTrue(ann.deck.contains(second));
        AssertJUnit.assertNull(ann.eventOffer);
        AssertJUnit.assertTrue(ann.eventCards.isEmpty());
        AssertJUnit.assertFalse("only once", c.chooseEvent("Ann", null, false, 1));

        // Bob takes the gold
        final int gold = bob.gold;
        final int offered = bob.eventGold;
        AssertJUnit.assertTrue(c.chooseEvent("Bob", null, false, HexEvent.PICK_GOLD));
        AssertJUnit.assertEquals(gold + offered, bob.gold);
    }

    @Test
    public void testACampsCardIntoTheSideboardOverTheNetwork() {
        final HexCampaign c = newCampaign();
        final HexSession s = new HexSession(c);
        s.setGuests(Collections.singletonList("Bob"));
        stepOnto(c, HexEvent.CAMP);
        final HexPlayer bob = c.player("Bob");
        final String first = bob.eventCards.get(0);
        final int side = bob.sideboard.size();
        s.handle("Bob", new HexActionEvent(HexActionEvent.EVENT_CHOICE, 0, 1, null, true, "Bob"));
        AssertJUnit.assertEquals(side + 1, bob.sideboard.size());
        AssertJUnit.assertTrue(bob.sideboard.contains(first));
        AssertJUnit.assertNull(bob.eventOffer);
    }

    // ------------------------------------------------------------------ the ley line

    @Test
    public void testALeyLineGivesACardOrALandOfTheDeck() throws Exception {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        final HexPlayer bob = c.player("Bob");
        stepOnto(c, HexEvent.LEY_LINE);
        AssertJUnit.assertEquals(HexEvent.LEY_LINE.name(), ann.eventOffer);
        AssertJUnit.assertEquals("the companion takes the land", 1, c.player("Cid").boostDeckLand);
        AssertJUnit.assertTrue(c.chooseEvent("Ann", null, false, HexEvent.PICK_HAND));
        AssertJUnit.assertTrue(c.chooseEvent("Bob", null, false, HexEvent.PICK_LAND));
        AssertJUnit.assertEquals(1, ann.boostHand);
        AssertJUnit.assertEquals(0, ann.boostDeckLand);
        AssertJUnit.assertEquals(1, bob.boostDeckLand);
        AssertJUnit.assertEquals(3, c.fightBoosts().size());
        AssertJUnit.assertFalse("not the old land for everybody", c.boostLand);

        // in the fight: a land of Bob's own deck, taken out of it, starts in play
        bob.deck = new ArrayList<>(RED_DECK);
        bob.deck.set(0, "Ghitu Encampment");
        final RegisteredPlayer rp = new RegisteredPlayer(HexContent.get().toDeck("Bob", bob.deck));
        final int cards = rp.getDeck().getMain().countAll();
        final Method lands = HexBattle.class.getDeclaredMethod("landsOfDeckInPlay", HexCampaign.class, HexPlayer.class, RegisteredPlayer.class);
        lands.setAccessible(true);
        AssertJUnit.assertEquals("a land was found", 0, lands.invoke(null, c, bob, rp));
        AssertJUnit.assertEquals(cards - 1, rp.getDeck().getMain().countAll());
        final List<IPaperCard> inPlay = new ArrayList<>();
        rp.getCardsOnBattlefield().forEach(inPlay::add);
        AssertJUnit.assertEquals(1, inPlay.size());
        AssertJUnit.assertTrue(inPlay.get(0).getRules().getType().isLand());

        // a deck without lands: a basic land, as before
        bob.deck = new ArrayList<>(Collections.nCopies(40, "Lightning Bolt"));
        final RegisteredPlayer none = new RegisteredPlayer(HexContent.get().toDeck("Bob", bob.deck));
        AssertJUnit.assertEquals(1, lands.invoke(null, c, bob, none));

        // used up by the next fight
        c.pendingEncounter = HexContent.get().randomEncounter(HexColor.RED, 0.5, new Random(2));
        c.resolveBattle(HexBattle.Result.WON);
        AssertJUnit.assertEquals(0, ann.boostHand);
        AssertJUnit.assertEquals(0, bob.boostDeckLand);
        AssertJUnit.assertTrue(c.fightBoosts().isEmpty());
    }

    // ------------------------------------------------------------------ the caravan

    @Test
    public void testACaravansStall() {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        stepOnto(c, HexEvent.CARAVAN);
        AssertJUnit.assertEquals(HexEvent.STALL_CARDS, ann.eventCards.size());
        final String wanted = ann.eventCards.get(2);
        ann.sideboard.add("Grizzly Bears");
        final int surcharge = HexCampaign.stallSurcharge(wanted, "Grizzly Bears");
        AssertJUnit.assertEquals(Math.max(0, HexContent.get().price(wanted) - HexContent.get().price("Grizzly Bears")), surcharge);
        if (surcharge > 0) {
            // too little gold: nothing happens
            ann.gold = surcharge - 1;
            AssertJUnit.assertFalse(c.chooseEvent("Ann", "Grizzly Bears", true, 3));
            AssertJUnit.assertTrue(ann.sideboard.contains("Grizzly Bears"));
            AssertJUnit.assertEquals(HexEvent.CARAVAN.name(), ann.eventOffer);
        }
        ann.gold = surcharge + 10;
        final int serial = ann.giftSerial;
        AssertJUnit.assertTrue(c.chooseEvent("Ann", "Grizzly Bears", true, 3));
        AssertJUnit.assertEquals(10, ann.gold);
        AssertJUnit.assertFalse(ann.sideboard.contains("Grizzly Bears"));
        AssertJUnit.assertTrue(ann.sideboard.contains(wanted));
        AssertJUnit.assertEquals(Collections.singletonList(wanted), ann.gifts);
        AssertJUnit.assertEquals(surcharge, ann.giftPaid);
        AssertJUnit.assertEquals("Grizzly Bears", ann.giftGiven);
        AssertJUnit.assertEquals(serial + 1, ann.giftSerial);
        AssertJUnit.assertNull(ann.eventOffer);
    }

    @Test
    public void testADearCardGivenCostsNothingOnTop() {
        AssertJUnit.assertEquals(0, HexCampaign.stallSurcharge("Grizzly Bears", "Black Lotus"));
    }

    // ------------------------------------------------------------------ the ruined shrine

    @Test
    public void testARuinedShrineShowsItsRelics() {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        ann.relics.clear();
        stepOnto(c, HexEvent.RUINED_SHRINE);
        AssertJUnit.assertEquals(HexEvent.SHRINE_RELICS, ann.eventRelics.size());
        AssertJUnit.assertEquals("different relics", HexEvent.SHRINE_RELICS, new java.util.HashSet<>(ann.eventRelics).size());
        final String chosen = ann.eventRelics.get(1);
        ann.sideboard.add("Grizzly Bears");
        AssertJUnit.assertTrue(c.chooseEvent("Ann", "Grizzly Bears", true, 2));
        AssertJUnit.assertTrue(ann.relics.contains(chosen));
        AssertJUnit.assertEquals(chosen, ann.giftRelic);
        AssertJUnit.assertTrue(ann.eventRelics.isEmpty());
    }

    // ------------------------------------------------------------------ Arzakon

    @Test
    public void testNoEventsWhileArzakonWaits() {
        final HexCampaign c = newCampaign();
        c.arzakonAwake = true;
        for (int i = 0; i < 20; i++) {
            c.reshuffleEnemies();
            for (final HexTile t : c.map.tiles.values()) {
                AssertJUnit.assertNull(t.event);
            }
        }
        c.arzakonAwake = false;
        boolean any = false;
        for (int i = 0; i < 20 && !any; i++) {
            c.reshuffleEnemies();
            for (final HexTile t : c.map.tiles.values()) {
                any |= t.event != null;
            }
        }
        AssertJUnit.assertTrue("events again once he is gone", any);
    }
}
