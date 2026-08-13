package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public sealed interface CaptureRegion permits BoxCaptureRegion, SquareCaptureRegion {
    String dimension();
    Integer minY();
    Integer maxY();
    boolean contains(ServerPlayer player);
    boolean overlaps(CaptureRegion other);
    CaptureRegion withHeight(Integer minY, Integer maxY);
    CompoundTag save();

    static CaptureRegion load(CompoundTag tag) {
        return switch (tag.getString("Type")) {
            case "box" -> BoxCaptureRegion.load(tag);
            case "square" -> SquareCaptureRegion.load(tag);
            default -> throw new IllegalArgumentException("Unknown capture region type: " + tag.getString("Type"));
        };
    }
}
