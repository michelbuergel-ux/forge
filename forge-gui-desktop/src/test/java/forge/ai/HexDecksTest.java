package forge.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.google.gson.Gson;

import forge.card.CardRules;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexCardGroups;
import forge.gamemodes.hexcrawl.HexCardGroups.Kind;
import forge.gamemodes.hexcrawl.HexCardGroups.Theme;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexDeck;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexSession;
import forge.gamemodes.hexcrawl.HexTile;
import forge.gamemodes.net.event.HexActionEvent;
import forge.model.FModel;

/** Several named decks per head, picked before every fight; and the groups of the deck window. */
public class HexDecksTest extends AITest {

    private static HexCampaign newCampaign() {
        final HexCampaign c = HexCampaign.create("Decks", 41L, 5, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        c.encounterChance = 0;
        c.encounterChanceExplored = 0;
        for (final HexTile t : c.map.tiles.values()) {
            t.enemy = null;
            t.event = null;
        }
        return c;
    }

    private static List<String> sorted(final List<String> cards) {
        final List<String> copy = new ArrayList<>(cards);
        Collections.sort(copy);
        return copy;
    }

    @Test
    public void testDecksShareTheCardsTheHeadOwns() {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        AssertJUnit.assertEquals("an older save's deck is the only one", 1, ann.deckCount());
        AssertJUnit.assertNull(ann.deckName(0));
        final List<String> main = sorted(ann.deck);
        ann.sideboard.add("Llanowar Elves");
        ann.sideboard.add("Llanowar Elves");
        final List<String> owned = sorted(ann.ownedCards());

        AssertJUnit.assertTrue(c.newDeck("Ann", "  Elfen   und  mehr "));
        AssertJUnit.assertEquals(2, ann.deckCount());
        AssertJUnit.assertEquals("a copy, played at once", 1, ann.activeDeckIndex());
        AssertJUnit.assertEquals("Elfen und mehr", ann.deckName(1));
        AssertJUnit.assertEquals(main, sorted(ann.deck));
        AssertJUnit.assertTrue(c.moveCard("Ann", "Llanowar Elves", false));
        AssertJUnit.assertTrue(c.moveCard("Ann", "Llanowar Elves", false));
        AssertJUnit.assertEquals(main.size() + 2, ann.deck.size());

        // back to the first: the elves go to the sideboard, nothing is lost or won
        AssertJUnit.assertTrue(c.selectDeck("Ann", 0));
        AssertJUnit.assertEquals(main, sorted(ann.deck));
        AssertJUnit.assertEquals(2, Collections.frequency(ann.sideboard, "Llanowar Elves"));
        AssertJUnit.assertEquals(owned, sorted(ann.ownedCards()));
        AssertJUnit.assertEquals("the other deck keeps its list", main.size() + 2, ann.deckSize(1));

        // and again: the same cards serve both decks
        AssertJUnit.assertTrue(c.selectDeck("Ann", 1));
        AssertJUnit.assertEquals(2, Collections.frequency(ann.deck, "Llanowar Elves"));
        AssertJUnit.assertEquals(owned, sorted(ann.ownedCards()));
        AssertJUnit.assertEquals(main.size(), ann.deckSize(0));
        AssertJUnit.assertFalse(c.selectDeck("Ann", 5));
    }

    @Test
    public void testADeckWhoseCardsAreGoneIsFilledWithBasicLands() {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        final HexDeck shortDeck = new HexDeck("Kurz");
        final List<String> nonBasics = new ArrayList<>();
        for (final String card : ann.deck) {
            if (!HexContent.get().isBasicLand(card)) {
                nonBasics.add(card);
            }
        }
        shortDeck.cards.addAll(nonBasics.subList(0, 20));
        shortDeck.cards.add("Forest");
        shortDeck.cards.add("Forest");
        shortDeck.cards.add("Llanowar Elves"); // sold long ago
        ann.decks.add(new HexDeck(null));
        ann.decks.add(shortDeck);
        ann.activeDeck = 0;
        ann.sideboard.add("Forest");
        ann.sideboard.add("Forest");
        ann.sideboard.add("Forest"); // three in the sideboard, the deck holds none of them while Kurz is not played
        final int owned = ann.ownedCards().size();
        final int forestsOwned = Collections.frequency(ann.ownedCards(), "Forest");
        AssertJUnit.assertEquals(22, ann.deckSize(1));

        AssertJUnit.assertTrue(c.selectDeck("Ann", 1));
        AssertJUnit.assertEquals(HexCampaign.MIN_DECK_SIZE, ann.deck.size());
        AssertJUnit.assertFalse(ann.deck.contains("Llanowar Elves"));
        AssertJUnit.assertEquals("the deck's own basic land fills it up", 2 + 18, Collections.frequency(ann.deck, "Forest"));
        // its own forests first, then new ones: the head owns as many more cards as forests had to be made
        final int made = 20 - forestsOwned;
        AssertJUnit.assertEquals(owned + Math.max(0, made), ann.ownedCards().size());
    }

    @Test
    public void testNamesDeletingAndTheLimit() {
        final HexCampaign c = newCampaign();
        final HexPlayer ann = c.player("Ann");
        AssertJUnit.assertFalse("no name", c.newDeck("Ann", "   "));
        AssertJUnit.assertFalse("never the last deck", c.deleteDeck("Ann", 0));
        AssertJUnit.assertTrue(c.newDeck("Ann", "B"));
        AssertJUnit.assertTrue(c.renameDeck("Ann", 0, "A very long name that goes on and on and on and on"));
        AssertJUnit.assertEquals(HexCampaign.DECK_NAME_MAX, ann.deckName(0).length());
        AssertJUnit.assertFalse(c.renameDeck("Ann", 0, ""));
        AssertJUnit.assertTrue(c.newDeck("Ann", "C"));
        AssertJUnit.assertEquals(2, ann.activeDeckIndex());
        // throwing away the deck played: the first other one is played
        final List<String> owned = sorted(ann.ownedCards());
        AssertJUnit.assertTrue(c.deleteDeck("Ann", 2));
        AssertJUnit.assertEquals(2, ann.deckCount());
        AssertJUnit.assertEquals(0, ann.activeDeckIndex());
        AssertJUnit.assertEquals(owned, sorted(ann.ownedCards()));
        // a deck before the one played
        AssertJUnit.assertTrue(c.selectDeck("Ann", 1));
        AssertJUnit.assertTrue(c.deleteDeck("Ann", 0));
        AssertJUnit.assertEquals(0, ann.activeDeckIndex());
        AssertJUnit.assertEquals("B", ann.deckName(0));
        while (ann.deckCount() < HexCampaign.MAX_DECKS) {
            AssertJUnit.assertTrue(c.newDeck("Ann", "D" + ann.deckCount()));
        }
        AssertJUnit.assertFalse("at most " + HexCampaign.MAX_DECKS, c.newDeck("Ann", "too many"));

        // the decks are saved with the campaign
        final HexCampaign copy = new Gson().fromJson(new Gson().toJson(c), HexCampaign.class);
        AssertJUnit.assertEquals(HexCampaign.MAX_DECKS, copy.player("Ann").deckCount());
        AssertJUnit.assertEquals("B", copy.player("Ann").deckName(0));
        AssertJUnit.assertEquals(ann.activeDeckIndex(), copy.player("Ann").activeDeckIndex());
    }

    @Test
    public void testInADungeonTheDeckCanBeSwitchedButNotChanged() {
        final HexCampaign c = HexCampaign.create("Dungeon", 3L, 7, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
        AssertJUnit.assertTrue(c.newDeck("Ann", "Second"));
        HexTile dungeon = null;
        for (final HexTile d : c.dungeons()) {
            for (final HexTile n : c.map.neighbors(d)) {
                if (dungeon == null && n.poi == HexPoi.NONE && c.questAt(n.q, n.r) == null) {
                    c.partyQ = c.prevQ = n.q;
                    c.partyR = c.prevR = n.r;
                    dungeon = d;
                }
            }
        }
        AssertJUnit.assertNotNull(dungeon);
        for (final HexTile any : c.map.tiles.values()) {
            any.enemy = null;
            any.event = null;
        }
        AssertJUnit.assertEquals(HexCampaign.MoveResult.DUNGEON, c.moveTo(dungeon.q, dungeon.r));
        AssertJUnit.assertTrue(c.deckLocked());
        c.player("Ann").sideboard.add("Lightning Bolt");
        AssertJUnit.assertFalse(c.moveCard("Ann", "Lightning Bolt", false));
        AssertJUnit.assertTrue("switching is allowed", c.selectDeck("Ann", 0));
        AssertJUnit.assertEquals(0, c.player("Ann").activeDeckIndex());
    }

    @Test
    public void testAFightWaitsForTheDeckOfAHeadWithSeveralDecks() {
        final HexCampaign c = newCampaign();
        final AtomicInteger fights = new AtomicInteger();
        final HexSession s = new HexSession(c);
        s.setListener(new HexSession.Listener() {
            @Override public void sessionChanged() { }
            @Override public void startFight() { fights.incrementAndGet(); }
        });
        s.setGuests(Collections.singletonList("Bob"));
        // Bob (a guest) makes a second deck over the network, and may not touch Ann's
        s.handle("Bob", new HexActionEvent(HexActionEvent.DECK, HexActionEvent.DECK_NEW, 0, "Bobs zweites", false, "Bob"));
        AssertJUnit.assertEquals(2, c.player("Bob").deckCount());
        s.handle("Bob", new HexActionEvent(HexActionEvent.DECK, HexActionEvent.DECK_NEW, 0, "fremd", false, "Ann"));
        AssertJUnit.assertEquals(1, c.player("Ann").deckCount());

        HexTile field = null;
        for (final HexTile t : c.map.neighbors(c.tile())) {
            if (field == null && t.poi == HexPoi.NONE && c.questAt(t.q, t.r) == null) {
                field = t;
            }
        }
        AssertJUnit.assertNotNull(field);
        final HexEncounter enemy = HexContent.get().randomEncounter(HexColor.RED, 0.5, new Random(5));
        field.enemy = enemy;
        enemy.q = field.q;
        enemy.r = field.r;
        s.handle("Ann", new HexActionEvent(HexActionEvent.VOTE, field.q, field.r, null, false, null));
        s.handle("Bob", new HexActionEvent(HexActionEvent.VOTE, field.q, field.r, null, false, null));
        AssertJUnit.assertNotNull(c.pendingEncounter);
        AssertJUnit.assertFalse("Bob picks his deck first", s.isFightRunning());
        AssertJUnit.assertTrue(s.mustChooseDeck("Bob"));
        AssertJUnit.assertFalse("one deck: nothing to pick", s.mustChooseDeck("Ann"));

        s.handle("Bob", new HexActionEvent(HexActionEvent.DECK, HexActionEvent.DECK_PICK, 0, null, false, "Bob"));
        AssertJUnit.assertEquals(0, c.player("Bob").activeDeckIndex());
        AssertJUnit.assertFalse(s.isFightRunning());
        s.handle("Bob", new HexActionEvent(HexActionEvent.FIGHT, 0, 0, null, true, null));
        AssertJUnit.assertTrue(s.isFightRunning());
        AssertJUnit.assertEquals(1, fights.get());
        AssertJUnit.assertFalse(s.mustChooseDeck("Bob"));
        // not while it runs
        s.handle("Bob", new HexActionEvent(HexActionEvent.DECK, HexActionEvent.DECK_PICK, 1, null, false, "Bob"));
        AssertJUnit.assertEquals(0, c.player("Bob").activeDeckIndex());
    }

    // ------------------------------------------------------------------ the groups of the deck window

    private static CardRules rules(final String name) {
        final forge.item.PaperCard card = FModel.getMagicDb().getCommonCards().getCard(name);
        AssertJUnit.assertNotNull(name, card);
        return card.getRules();
    }

    private static void assertGroups(final Theme theme, final String card, final String... groups) {
        AssertJUnit.assertEquals(card + " in " + theme, Arrays.asList(groups), HexCardGroups.groupsOf(theme, rules(card)));
    }

    @Test
    public void testKindsOfCards() {
        AssertJUnit.assertEquals(Kind.CREATURE, HexCardGroups.kindOf(rules("Llanowar Elves")));
        AssertJUnit.assertEquals(Kind.CREATURE, HexCardGroups.kindOf(rules("Dryad Arbor")));
        AssertJUnit.assertEquals(Kind.PLANESWALKER, HexCardGroups.kindOf(rules("Liliana of the Veil")));
        AssertJUnit.assertEquals(Kind.BATTLE, HexCardGroups.kindOf(rules("Invasion of Tarkir")));
        AssertJUnit.assertEquals(Kind.INSTANT, HexCardGroups.kindOf(rules("Lightning Bolt")));
        AssertJUnit.assertEquals(Kind.SORCERY, HexCardGroups.kindOf(rules("Divination")));
        AssertJUnit.assertEquals(Kind.ARTIFACT, HexCardGroups.kindOf(rules("Sol Ring")));
        AssertJUnit.assertEquals(Kind.ENCHANTMENT, HexCardGroups.kindOf(rules("Pacifism")));
        AssertJUnit.assertEquals(Kind.LAND, HexCardGroups.kindOf(rules("Forest")));
        for (final Kind k : Kind.values()) {
            AssertJUnit.assertTrue(k.labelKey(), forge.util.Localizer.getInstance().getMessage(k.labelKey()).length() > 0);
        }
    }

    @Test
    public void testThemesAndTheirFactions() {
        assertGroups(Theme.TEAMWORK, "Pir, Imaginative Rascal", "PARTNER", "YOUR_TEAM");
        assertGroups(Theme.TEAMWORK, "Huddle Up", "ASSIST");
        assertGroups(Theme.TEAMWORK, "Excavation Technique", "DEMONSTRATE");
        assertGroups(Theme.TEAMWORK, "Gladehart Cavalry", "SUPPORT");
        assertGroups(Theme.TEAMWORK, "Crush of Tentacles", "SURGE");
        assertGroups(Theme.TEAMWORK, "Minds Aglow", "JOIN_FORCES");
        assertGroups(Theme.TEAMWORK, "Expropriate", "COUNCIL");
        assertGroups(Theme.TEAMWORK, "Rushblade Commander", "YOUR_TEAM");
        assertGroups(Theme.TEAMWORK, "Combo Attack", "YOUR_TEAM");
        assertGroups(Theme.TEAMWORK, "Dwarven Lightsmith", "ASSIST", "YOUR_TEAM");

        assertGroups(Theme.RAVNICA, "Lightning Helix", "BOROS");
        assertGroups(Theme.RAVNICA, "Azorius Guildgate", "AZORIUS");
        assertGroups(Theme.RAVNICA, "Golgari Thug", "GOLGARI");

        assertGroups(Theme.TARKIR, "Abzan Charm", "ABZAN");
        assertGroups(Theme.TARKIR, "Dromoka's Command", "DROMOKA");
        assertGroups(Theme.TARKIR, "Monastery Swiftspear", "JESKAI");

        assertGroups(Theme.ALARA, "Bant Charm", "BANT");
        assertGroups(Theme.ALARA, "Sedraxis Specter", "GRIXIS");

        assertGroups(Theme.STRIXHAVEN, "Lorehold Command", "LOREHOLD");

        assertGroups(Theme.PHYREXIANS, "Elesh Norn, Grand Cenobite", "PHYREXIAN");
        assertGroups(Theme.PHYREXIANS, "Gitaxian Probe", "PHYREXIAN_MANA");
        assertGroups(Theme.PHYREXIANS, "Glistener Elf", "PHYREXIAN", "POISON");

        for (final Theme theme : Theme.values()) {
            assertGroups(theme, "Llanowar Elves");
        }
    }

    @Test
    public void testTheThemesAreShopThemes() {
        final HexContent content = HexContent.get();
        for (final Theme group : Theme.values()) {
            final forge.gamemodes.hexcrawl.HexTheme theme = forge.gamemodes.hexcrawl.HexTheme.get(group.name().toLowerCase(java.util.Locale.ROOT));
            AssertJUnit.assertNotNull(group.name(), theme);
            AssertJUnit.assertTrue("only its own cards", theme.isStrict());
            AssertJUnit.assertFalse(theme.getLabel(), theme.getLabel().startsWith("lbl"));
            AssertJUnit.assertEquals(group != Theme.TEAMWORK && group != Theme.PHYREXIANS, theme.isMulticoloured());
        }
        AssertJUnit.assertEquals("a people, among the tribes", forge.gamemodes.hexcrawl.HexTheme.Group.TRIBES,
                forge.gamemodes.hexcrawl.HexTheme.get("phyrexians").group());
        AssertJUnit.assertEquals(forge.gamemodes.hexcrawl.HexTheme.Group.WORLDS, forge.gamemodes.hexcrawl.HexTheme.get("tarkir").group());
        // partner's reminder text names commanders, the card itself needs none: it may be sold; a real commander card not
        AssertJUnit.assertFalse(HexContent.needsCommander(rules("Thrasios, Triton Hero")));
        AssertJUnit.assertTrue(HexContent.needsCommander(rules("Arcane Signet")));
        AssertJUnit.assertTrue("teamwork in a white town: " + content.themeCardCount(HexColor.WHITE, forge.gamemodes.hexcrawl.HexTheme.get("teamwork")),
                content.themeCardCount(HexColor.WHITE, forge.gamemodes.hexcrawl.HexTheme.get("teamwork")) >= 40);
        AssertJUnit.assertTrue(forge.gamemodes.hexcrawl.HexTheme.get("ravnica").fits(rules("Lightning Helix")));
        AssertJUnit.assertFalse(forge.gamemodes.hexcrawl.HexTheme.get("ravnica").fits(rules("Llanowar Elves")));
        // the older themes keep their places (saves and the network know them by them)
        AssertJUnit.assertEquals(0, forge.gamemodes.hexcrawl.HexTheme.indexOf("tokens"));
        AssertJUnit.assertTrue(forge.gamemodes.hexcrawl.HexTheme.indexOf("copy") < forge.gamemodes.hexcrawl.HexTheme.indexOf("teamwork"));

        // a red town can offer the guilds; its shelves hold only guild cards, many of them multicoloured
        final forge.gamemodes.hexcrawl.HexTheme ravnica = forge.gamemodes.hexcrawl.HexTheme.get("ravnica");
        AssertJUnit.assertTrue(content.themesFor(HexColor.RED).contains(ravnica));
        int gold = 0;
        int all = 0;
        for (int seed = 0; seed < 5; seed++) {
            for (final forge.gamemodes.hexcrawl.HexShopOffer offer : content.themeStock(HexColor.RED, ravnica, new Random(seed))) {
                final CardRules r = rules(offer.card);
                AssertJUnit.assertTrue(offer.card, ravnica.fits(r));
                all++;
                if (r.getColor().countColors() > 1) {
                    gold++;
                }
            }
        }
        AssertJUnit.assertTrue("stocked: " + all, all > 20);
        AssertJUnit.assertTrue("multicoloured: " + gold + " of " + all, gold * 3 >= all);
    }
}
