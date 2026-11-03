package forge.screens.home.hexcrawl;

import java.util.Arrays;
import java.util.Collections;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexSession;
import forge.util.Localizer;

/** The party window of the campaign menu: who plays which head, as the host and as a guest see it. */
public class HexPartyDialogTest extends forge.ai.AITest {

    private static String t(final String key) {
        return Localizer.getInstance().getMessage(key);
    }

    @Test
    public void testTheHostAndAGuestSeeWhoPlaysWhichHead() {
        final HexCampaign c = HexCampaign.create("Party", 5L, 5, Arrays.asList(new HexPlayer("Ann", false),
                new HexPlayer("Bob", false), new HexPlayer("Cid", false), new HexPlayer("Pat", true)));
        final HexSession host = new HexSession(c);
        host.setListener(new HexSession.Listener() {
            @Override public void sessionChanged() { }
            @Override public void startFight() { }
        });
        host.setGuests(Collections.singletonList("Bob"));

        final String[] lines = new HexPartyDialog(host, () -> null).partyText(host).split("\n");
        AssertJUnit.assertEquals(4, lines.length);
        AssertJUnit.assertEquals("Ann – " + t("lblHexPartyHost"), lines[0]);
        AssertJUnit.assertEquals("Bob – " + t("lblHexPartyConnected"), lines[1]);
        AssertJUnit.assertEquals("Cid – " + t("lblHexAway"), lines[2]);
        AssertJUnit.assertEquals("Pat – " + t("lblHexCompanion"), lines[3]);

        // Bob's computer: the state from the host, Bob is "you" and Ann the host
        final HexSession guest = HexSession.forGuest();
        guest.applyState(host.toStateJson(), "Bob");
        final String[] seen = new HexPartyDialog(guest, () -> null).partyText(guest).split("\n");
        AssertJUnit.assertEquals("Ann – " + t("lblHexPartyTheHost"), seen[0]);
        AssertJUnit.assertEquals("Bob – " + t("lblHexYou"), seen[1]);
        AssertJUnit.assertEquals("Cid – " + t("lblHexAway"), seen[2]);
    }
}
