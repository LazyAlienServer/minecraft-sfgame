package com.sfgame.game;

import com.sfgame.data.BoxCaptureRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatchManagerGameModeTest {
    @TempDir Path directory;

    @Test
    void runningAllowlistEditingUsesSurvivalSoForgeReceivesBreakEvents() {
        assertEquals(GameType.SURVIVAL,
                MatchManager.participantGameTypeAtMatchStart(true));
    }

    @Test
    void disabledMapEditingStartsParticipantsInAdventure() {
        assertEquals(GameType.ADVENTURE,
                MatchManager.participantGameTypeAtMatchStart(false));
    }


    @Test
    void liveScoreMutationAcceptsPlayableTeamsAndBoundedValues() {
        MatchManager manager = new MatchManager();

        assertTrue(manager.setTeamScoreValue(TeamSide.RED, 42));
        assertEquals(42, manager.score(TeamSide.RED));
        assertTrue(manager.setTeamScoreValue(TeamSide.GREEN, MatchManager.MAX_LIVE_SCORE));
        assertEquals(MatchManager.MAX_LIVE_SCORE, manager.score(TeamSide.GREEN));
        assertFalse(manager.setTeamScoreValue(TeamSide.NONE, 1));
        assertFalse(manager.setTeamScoreValue(TeamSide.BLUE, -1));
        assertFalse(manager.setTeamScoreValue(TeamSide.BLUE, MatchManager.MAX_LIVE_SCORE + 1));
    }

    @Test
    void commonClockCanBeExtendedOrExpired() {
        MatchManager manager = new MatchManager();
        com.sfgame.data.MatchRules rules = new com.sfgame.data.MatchRules();

        manager.setCommonRemainingSeconds(rules, 1_200);
        assertEquals(1_200, manager.commonRemainingSeconds(rules));
        assertFalse(manager.commonTimeExpired(rules));

        manager.setCommonRemainingSeconds(rules, 0);
        assertEquals(0, manager.commonRemainingSeconds(rules));
        assertTrue(manager.commonTimeExpired(rules));
    }

    @Test
    void breakthroughRosterSwapExchangesPlayersNotTeamRoles() {
        assertEquals(TeamSide.BLUE,
                MatchManager.swappedBreakthroughSide(TeamSide.RED, TeamSide.RED, TeamSide.BLUE));
        assertEquals(TeamSide.RED,
                MatchManager.swappedBreakthroughSide(TeamSide.BLUE, TeamSide.RED, TeamSide.BLUE));
        assertEquals(TeamSide.YELLOW,
                MatchManager.swappedBreakthroughSide(TeamSide.YELLOW, TeamSide.RED, TeamSide.BLUE));
    }

    @Test
    void currencyMutationIsBoundedAndModeScoped() {
        PlayerMatchState state = new PlayerMatchState(java.util.UUID.randomUUID());
        MatchManager manager = new MatchManager();

        assertTrue(manager.setCurrencyValue(state, GameModeRegistry.BREAKTHROUGH, 750));
        assertEquals(750, state.currency(GameModeRegistry.BREAKTHROUGH));
        assertTrue(manager.setCurrencyValue(state, GameModeRegistry.DOMINATION, 751));
        assertEquals(751, state.currency(GameModeRegistry.DOMINATION));
        assertTrue(manager.setCurrencyValue(state, GameModeRegistry.CAPTURE_THE_FLAG, 752));
        assertEquals(752, state.currency(GameModeRegistry.CAPTURE_THE_FLAG));
        assertFalse(manager.setCurrencyValue(state, GameModeRegistry.TEAM_DEATHMATCH, 1));
        assertFalse(manager.setCurrencyValue(state, GameModeRegistry.CAPTURE_THE_FLAG, -1));
        assertFalse(manager.setCurrencyValue(state, GameModeRegistry.CAPTURE_THE_FLAG,
                MatchManager.MAX_LIVE_SCORE + 1));
    }
    @Test
    void killCurrencyIsLiveAndModeScopedAcrossEconomyModes() {
        com.sfgame.data.MatchRules breakthrough = new com.sfgame.data.MatchRules(GameModeRegistry.BREAKTHROUGH);
        breakthrough.killCurrency(41);
        com.sfgame.data.MatchRules domination = new com.sfgame.data.MatchRules(GameModeRegistry.DOMINATION);
        domination.killCurrency(42);
        com.sfgame.data.MatchRules ctf = new com.sfgame.data.MatchRules(GameModeRegistry.CAPTURE_THE_FLAG);
        ctf.killCurrency(43);

        assertEquals(41, MatchManager.killCurrencyFor(GameModeRegistry.BREAKTHROUGH, breakthrough));
        assertEquals(42, MatchManager.killCurrencyFor(GameModeRegistry.DOMINATION, domination));
        assertEquals(43, MatchManager.killCurrencyFor(GameModeRegistry.CAPTURE_THE_FLAG, ctf));
        assertEquals(0, MatchManager.killCurrencyFor(GameModeRegistry.TEAM_DEATHMATCH,
                new com.sfgame.data.MatchRules(GameModeRegistry.TEAM_DEATHMATCH)));
    }

    @Test
    void eliteAuthorizationResolvesAcrossDeploymentsAndClearsForNormalSelection() throws Exception {
        Files.createDirectories(directory.resolve("classes"));
        Files.writeString(directory.resolve("classes/tdm.json"), """
                {
                  "classes":[{"id":"normal"}],
                  "captainClasses":[],
                  "eliteClasses":[{"id":"elite"}],
                  "teams":{},
                  "maps":{}
                }
                """);
        MatchManager manager = new MatchManager();
        manager.classes().useConfigRoot(directory);
        assertTrue(manager.classes().reload().isEmpty(), manager.classes().loadErrors().toString());
        PlayerMatchState state = new PlayerMatchState(java.util.UUID.randomUUID());
        state.currentClass(GameModeRegistry.TEAM_DEATHMATCH, TeamSide.RED, "elite");
        state.pendingClass(GameModeRegistry.TEAM_DEATHMATCH, TeamSide.RED, "elite");
        state.grantedEliteClass(GameModeRegistry.TEAM_DEATHMATCH, TeamSide.RED, "elite");

        assertEquals("elite", manager.resolveDeploymentDefinition(
                GameModeRegistry.TEAM_DEATHMATCH, null, TeamSide.RED, state, false).orElseThrow().id());
        assertEquals("elite", manager.resolveDeploymentDefinition(
                GameModeRegistry.TEAM_DEATHMATCH, null, TeamSide.RED, state, false).orElseThrow().id());

        state.grantedEliteClass(GameModeRegistry.TEAM_DEATHMATCH, TeamSide.RED, null);
        state.pendingClass(GameModeRegistry.TEAM_DEATHMATCH, TeamSide.RED, "normal");
        assertEquals("normal", manager.resolveDeploymentDefinition(
                GameModeRegistry.TEAM_DEATHMATCH, null, TeamSide.RED, state, false).orElseThrow().id());
        state.grantedEliteClass(GameModeRegistry.TEAM_DEATHMATCH, TeamSide.RED, "elite");
        state.resetRoundStats();
        assertEquals(null, state.grantedEliteClass(GameModeRegistry.TEAM_DEATHMATCH, TeamSide.RED));
    }

    @Test
    void buildBoxUsesInclusiveBlockCoordinatesForFractionalAndNegativeCorners() {
        BoxCaptureRegion region = new BoxCaptureRegion(
                "minecraft:overworld", -26.3, 165.3, -353.8, -159.8, null, null);

        assertTrue(MatchManager.containsBuildBlock(region, "minecraft:overworld", new BlockPos(-27, -64, -354)));
        assertTrue(MatchManager.containsBuildBlock(region, "minecraft:overworld", new BlockPos(165, 319, -160)));
        assertFalse(MatchManager.containsBuildBlock(region, "minecraft:overworld", new BlockPos(-28, 64, -354)));
        assertFalse(MatchManager.containsBuildBlock(region, "minecraft:overworld", new BlockPos(165, 64, -159)));
        assertFalse(MatchManager.containsBuildBlock(region, "minecraft:the_nether", new BlockPos(0, 64, -200)));
    }
}
