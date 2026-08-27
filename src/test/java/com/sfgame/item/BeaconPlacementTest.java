package com.sfgame.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BeaconPlacementTest {
    private static final BlockPos CLICKED = new BlockPos(10, 64, -4);

    @Test
    void replacesClickedBlockBeforeUsingAdjacentFace() {
        assertEquals(CLICKED, BeaconPlacement.resolve(CLICKED, Direction.UP, true, true));
    }

    @Test
    void placesAgainstSolidClickedBlockInAdjacentReplaceableSpace() {
        assertEquals(CLICKED.above(), BeaconPlacement.resolve(CLICKED, Direction.UP, false, true));
    }

    @Test
    void rejectsTwoOccupiedPositions() {
        assertNull(BeaconPlacement.resolve(CLICKED, Direction.UP, false, false));
    }
}
