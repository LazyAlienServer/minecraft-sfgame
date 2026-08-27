package com.sfgame.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

public final class BeaconPlacement {
    private BeaconPlacement() { }

    @Nullable
    public static BlockPos resolve(BlockPos clickedPos, Direction clickedFace,
                                   boolean clickedCanBeReplaced, boolean adjacentCanBeReplaced) {
        if (clickedCanBeReplaced) return clickedPos;
        return adjacentCanBeReplaced ? clickedPos.relative(clickedFace) : null;
    }
}
