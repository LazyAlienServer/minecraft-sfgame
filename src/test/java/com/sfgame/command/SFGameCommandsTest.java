package com.sfgame.command;

import com.sfgame.data.ArenaPosition;
import com.sfgame.data.BoxCaptureRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SFGameCommandsTest {
    @Test
    void selectedBoxIncludesBothTargetedBlocksAtNegativeCoordinates() {
        ArenaPosition first = new ArenaPosition("minecraft:overworld", -25, 73, -71, 0, 0);
        ArenaPosition second = new ArenaPosition("minecraft:overworld", -19, 75, -72, 0, 0);

        BoxCaptureRegion region = SFGameCommands.selectedBlockBox(first, second, null, null);

        assertEquals(-25.0, region.minX());
        assertEquals(-19, Math.floor(region.maxX()));
        assertEquals(-72.0, region.minZ());
        assertEquals(-71, Math.floor(region.maxZ()));
    }
}
