package forge.ai;

import java.util.Arrays;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexBattle;
import forge.gamemodes.hexcrawl.HexCampaign;
import forge.gamemodes.hexcrawl.HexColor;
import forge.gamemodes.hexcrawl.HexContent;
import forge.gamemodes.hexcrawl.HexEncounter;
import forge.gamemodes.hexcrawl.HexFightInfo;
import forge.gamemodes.hexcrawl.HexFightSummary;
import forge.gamemodes.hexcrawl.HexPlayer;
import forge.gamemodes.hexcrawl.HexPoi;
import forge.gamemodes.hexcrawl.HexTile;
import forge.screens.home.hexcrawl.HexCrawlPanel;
import forge.util.Localizer;

/** The banner of a campaign fight in the match screen: what it says about the enemy. */
public class HexFightInfoTest extends AITest {

    private static HexCampaign newCampaign() {
        return HexCampaign.create("Banner", 21L, 7, Arrays.asList(new HexPlayer("Ann", false), new HexPlayer("Bob", false)));
    }

    @Test
    public void noFightNoBanner() {
        final HexCampaign c = newCampaign();
        c.pendingEncounter = null;
        AssertJUnit.assertNull(HexCrawlPanel.fightInfo(c));
        AssertJUnit.assertNull(HexCrawlPanel.fightInfo(null));
    }

    @Test
    public void aBossWakesAtHalfItsLife() {
        final HexCampaign c = newCampaign();
        final HexEncounter boss = HexContent.get().bossEncounter(HexColor.WHITE);
        c.pendingEncounter = boss;
        final HexFightInfo info = HexCrawlPanel.fightInfo(c);
        AssertJUnit.assertEquals(boss.enemyName, info.enemyName);
        AssertJUnit.assertEquals(Localizer.getInstance().getMessage("lblHexFightKindArchmage"), info.kind);
        AssertJUnit.assertEquals(c.enemyLife(boss), info.startLife);
        AssertJUnit.assertEquals("Akroma Awakened", info.phaseCard);
        AssertJUnit.assertNotNull(info.scout);

        c.pendingEncounter = HexContent.get().bossEncounter(HexColor.COLORLESS);
        AssertJUnit.assertEquals(Localizer.getInstance().getMessage("lblHexFightKindBoss"), HexCrawlPanel.fightInfo(c).kind);
    }

    @Test
    public void anOrdinaryEnemyHasNoPhaseTwo() {
        final HexCampaign c = newCampaign();
        c.pendingEncounter = HexContent.get().randomEncounter(HexColor.RED, 0.5, new java.util.Random(1));
        final HexFightInfo info = HexCrawlPanel.fightInfo(c);
        AssertJUnit.assertNull(info.phaseCard);
        AssertJUnit.assertEquals(Localizer.getInstance().getMessage("lblHexFightKindEnemy"), info.kind);
    }

    @Test
    public void aDungeonSaysWhichFightItIsAndTheLifeCarriedOver() {
        final HexCampaign c = newCampaign();
        final HexTile t = c.dungeons().get(0);
        for (final HexTile n : c.map.neighbors(t)) {
            if (n.poi == HexPoi.NONE && c.questAt(n.q, n.r) == null) {
                c.partyQ = c.prevQ = n.q;
                c.partyR = c.prevR = n.r;
                break;
            }
        }
        for (final HexTile any : c.map.tiles.values()) {
            any.enemy = null;
            any.event = null;
        }
        c.moveTo(t.q, t.r);
        final HexFightSummary summary = new HexFightSummary();
        summary.enemyName = c.pendingEncounter.enemyName;
        summary.partyLife = 12;
        c.recordFight(summary);
        c.resolveBattle(HexBattle.Result.WON);
        c.pendingRewards.clear();
        c.goDeeper();
        final HexFightInfo info = HexCrawlPanel.fightInfo(c);
        AssertJUnit.assertEquals(Localizer.getInstance().getMessage("lblHexFightKindDungeon", "2", "3"), info.kind);
        AssertJUnit.assertTrue(info.pills.toString(),
                info.pills.contains(Localizer.getInstance().getMessage("lblHexFightPillDungeonLife", String.valueOf(12 + HexCampaign.DUNGEON_HEAL))));
    }
}
