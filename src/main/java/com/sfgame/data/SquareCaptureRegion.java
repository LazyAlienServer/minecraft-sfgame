package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public record SquareCaptureRegion(String dimension, double centerX, double centerZ, int radius,
                                  Integer minY, Integer maxY) implements CaptureRegion {
    public SquareCaptureRegion {
        if (radius < 1 || radius > 256) throw new IllegalArgumentException("Radius must be between 1 and 256");
        BoxCaptureRegion.validateHeight(minY, maxY);
    }

    public static SquareCaptureRegion centeredAt(ArenaPosition center, int radius) {
        return new SquareCaptureRegion(center.dimension(), center.x(), center.z(), radius, null, null);
    }

    @Override
    public boolean contains(ServerPlayer player) {
        if (!player.level().dimension().location().toString().equals(dimension)) return false;
        double y = player.getY();
        return Math.abs(player.getX() - centerX) <= radius && Math.abs(player.getZ() - centerZ) <= radius
                && (minY == null || y >= minY) && (maxY == null || y <= maxY);
    }

    @Override
    public boolean overlaps(CaptureRegion other) {
        if (!dimension.equals(other.dimension()) || !BoxCaptureRegion.heightOverlaps(this, other)) return false;
        BoxCaptureRegion.Bounds bounds = BoxCaptureRegion.bounds(other);
        return centerX - radius <= bounds.maxX() && centerX + radius >= bounds.minX()
                && centerZ - radius <= bounds.maxZ() && centerZ + radius >= bounds.minZ();
    }

    @Override
    public CaptureRegion withHeight(Integer newMinY, Integer newMaxY) {
        return new SquareCaptureRegion(dimension, centerX, centerZ, radius, newMinY, newMaxY);
    }

    public SquareCaptureRegion withCenter(ArenaPosition center) {
        return new SquareCaptureRegion(center.dimension(), center.x(), center.z(), radius, minY, maxY);
    }

    public SquareCaptureRegion withRadius(int newRadius) {
        return new SquareCaptureRegion(dimension, centerX, centerZ, newRadius, minY, maxY);
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", "square"); tag.putString("Dimension", dimension);
        tag.putDouble("CenterX", centerX); tag.putDouble("CenterZ", centerZ); tag.putInt("Radius", radius);
        BoxCaptureRegion.saveHeight(tag, minY, maxY);
        return tag;
    }

    public static SquareCaptureRegion load(CompoundTag tag) {
        return new SquareCaptureRegion(tag.getString("Dimension"), tag.getDouble("CenterX"), tag.getDouble("CenterZ"),
                tag.getInt("Radius"), BoxCaptureRegion.readMinY(tag), BoxCaptureRegion.readMaxY(tag));
    }
}
