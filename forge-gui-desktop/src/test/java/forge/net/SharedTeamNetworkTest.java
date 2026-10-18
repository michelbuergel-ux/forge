package forge.net;

import forge.deck.Deck;
import forge.gamemodes.net.server.FServerManager;
import forge.util.IHasForgeLog;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Multi-Headed Giant over a real network connection: two remote clients form a team against two
 * AI heads of the host. Covers shared team priority (team mates prompted at the same time),
 * team mates seeing each other's hands, card pings and the AI stand-in for a dropped player.
 */
public class SharedTeamNetworkTest implements IHasForgeLog {

    @BeforeClass
    public static void setUp() {
        TestUtils.ensureFModelInitialized();
    }

    private static List<Deck> decks() {
        return List.of(
                TestDeckLoader.createMinimalDeck("Mountain", 12),
                TestDeckLoader.createMinimalDeck("Forest", 12),
                TestDeckLoader.createMinimalDeck("Island", 12),
                TestDeckLoader.createMinimalDeck("Plains", 12));
    }

    @Test(timeOut = 180000, description = "Remote team mates share priority, see each other's hands and can ping cards")
    public void testRemoteTeamPlaysTogether() {
        // slot 0: host AI (team 1), slots 1+2: remote clients (team 0), slot 3: AI (team 1)
        final UnifiedNetworkHarness.GameResult result = new UnifiedNetworkHarness()
                .playerCount(4)
                .remoteClients(2)
                .useAiForRemotePlayers(false)
                .multiHeadedGiant(1, 0, 0, 1)
                .pingFromFirstClient(true)
                .decks(decks())
                .gameTimeout(150000)
                .execute();

        Assert.assertTrue(result.gameStarted, "Game should have started: " + result.toSummary());
        Assert.assertTrue(result.gameCompleted, "Game should have completed (no deadlock in shared priority): " + result.toSummary());
        Assert.assertEquals(result.priorityPromptsPerClient.size(), 2);
        for (final int prompts : result.priorityPromptsPerClient) {
            Assert.assertTrue(prompts > 0, "every remote head should have had priority: " + result.priorityPromptsPerClient);
        }
        Assert.assertTrue(result.teamMateHandCardsSeen > 0, "a remote head should see its team mate's hand");
        Assert.assertTrue(result.pingsReceived > 0, "the ping should have reached the team mate");
        Assert.assertEquals(result.sendErrors, 0, "send errors during the game");
    }

    @Test(timeOut = 180000, description = "An AI plays for a remote head that drops out, so the game goes on")
    public void testAiStandsInForDroppedPlayer() {
        FServerManager.getInstance().setAiStandInWhileDisconnected(true);
        try {
            final UnifiedNetworkHarness.GameResult result = new UnifiedNetworkHarness()
                    .playerCount(4)
                    .remoteClients(2)
                    .useAiForRemotePlayers(false)
                    .multiHeadedGiant(1, 0, 0, 1)
                    .disconnectSecondClientAfterPrompts(2)
                    .decks(decks())
                    .gameTimeout(150000)
                    .execute();

            Assert.assertTrue(result.gameStarted, "Game should have started: " + result.toSummary());
            // without the stand-in the game would wait 5 minutes for the dropped player
            Assert.assertTrue(result.gameCompleted, "Game should have completed with the AI standing in: " + result.toSummary());
        } finally {
            FServerManager.getInstance().setAiStandInWhileDisconnected(false);
        }
    }
}
