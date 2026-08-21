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
                MatchManager.participantGameType(MatchPhase.RUNNING, true, true));
    }

    @Test
    void disabledOrSafePhaseEditingUsesAdventure() {
        assertEquals(GameType.ADVENTURE,
                MatchManager.participantGameType(MatchPhase.RUNNING, false, true));
        assertEquals(GameType.ADVENTURE,
                MatchManager.participantGameType(MatchPhase.RESULT, true, true));
        assertEquals(GameType.ADVENTURE,
                MatchManager.participantGameType(MatchPhase.RUNNING, true, false));
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
