package com.sfgame.game;

import com.sfgame.data.BoxCaptureRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatchManagerGameModeTest {
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
    void ctfCurrencyMutationIsBoundedAndModeScoped() {
        PlayerMatchState state = new PlayerMatchState(java.util.UUID.randomUUID());
        MatchManager manager = new MatchManager();

        assertTrue(manager.setCurrencyValue(state, 750));
        assertEquals(750, state.currency(GameModeRegistry.CAPTURE_THE_FLAG));
        assertFalse(manager.setCurrencyValue(state, -1));
        assertFalse(manager.setCurrencyValue(state, MatchManager.MAX_LIVE_SCORE + 1));
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
