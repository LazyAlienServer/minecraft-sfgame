package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public sealed interface CaptureRegion permits BoxCaptureRegion, SquareCaptureRegion {
    String dimension();
    Integer minY();
    Integer maxY();
    double centerX();
    double centerZ();
    boolean contains(ServerPlayer player);
    default boolean contains(ArenaPosition position) {
        if (!dimension().equals(position.dimension())) return false;
        double y = position.y();
        if (minY() != null && (y < minY() || y > maxY())) return false;
        if (this instanceof BoxCaptureRegion box) {
            return position.x() >= box.minX() && position.x() <= box.maxX()
                    && position.z() >= box.minZ() && position.z() <= box.maxZ();
        }
        SquareCaptureRegion square = (SquareCaptureRegion) this;
        return Math.abs(position.x() - square.centerX()) <= square.radius()
                && Math.abs(position.z() - square.centerZ()) <= square.radius();
    }
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
