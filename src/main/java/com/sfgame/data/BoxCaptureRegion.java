package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public record BoxCaptureRegion(String dimension, double minX, double maxX, double minZ, double maxZ,
                               Integer minY, Integer maxY) implements CaptureRegion {
    public BoxCaptureRegion {
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Invalid box bounds");
        validateHeight(minY, maxY);
    }

    public static BoxCaptureRegion between(ArenaPosition first, ArenaPosition second) {
        if (!first.dimension().equals(second.dimension())) throw new IllegalArgumentException("Corners must be in the same dimension");
        return new BoxCaptureRegion(first.dimension(), Math.min(first.x(), second.x()), Math.max(first.x(), second.x()),
                Math.min(first.z(), second.z()), Math.max(first.z(), second.z()), null, null);
    }

    @Override
    public boolean contains(ServerPlayer player) {
        if (!player.level().dimension().location().toString().equals(dimension)) return false;
        double y = player.getY();
        return player.getX() >= minX && player.getX() <= maxX && player.getZ() >= minZ && player.getZ() <= maxZ
                && (minY == null || y >= minY) && (maxY == null || y <= maxY);
    }

    @Override
    public boolean overlaps(CaptureRegion other) {
        if (!dimension.equals(other.dimension()) || !heightOverlaps(this, other)) return false;
        Bounds bounds = bounds(other);
        return minX <= bounds.maxX && maxX >= bounds.minX && minZ <= bounds.maxZ && maxZ >= bounds.minZ;
    }

    @Override
    public CaptureRegion withHeight(Integer newMinY, Integer newMaxY) {
        return new BoxCaptureRegion(dimension, minX, maxX, minZ, maxZ, newMinY, newMaxY);
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", "box");
        tag.putString("Dimension", dimension);
        tag.putDouble("MinX", minX); tag.putDouble("MaxX", maxX);
        tag.putDouble("MinZ", minZ); tag.putDouble("MaxZ", maxZ);
        saveHeight(tag, minY, maxY);
        return tag;
    }

    public static BoxCaptureRegion load(CompoundTag tag) {
        return new BoxCaptureRegion(tag.getString("Dimension"), tag.getDouble("MinX"), tag.getDouble("MaxX"),
                tag.getDouble("MinZ"), tag.getDouble("MaxZ"), readMinY(tag), readMaxY(tag));
    }

    static void validateHeight(Integer minY, Integer maxY) {
        if ((minY == null) != (maxY == null) || minY != null && minY > maxY) {
            throw new IllegalArgumentException("Invalid height bounds");
        }
    }

    static boolean heightOverlaps(CaptureRegion first, CaptureRegion second) {
        int firstMin = first.minY() == null ? Integer.MIN_VALUE : first.minY();
        int firstMax = first.maxY() == null ? Integer.MAX_VALUE : first.maxY();
        int secondMin = second.minY() == null ? Integer.MIN_VALUE : second.minY();
        int secondMax = second.maxY() == null ? Integer.MAX_VALUE : second.maxY();
        return firstMin <= secondMax && firstMax >= secondMin;
    }

    static Bounds bounds(CaptureRegion region) {
        if (region instanceof BoxCaptureRegion box) return new Bounds(box.minX, box.maxX, box.minZ, box.maxZ);
        SquareCaptureRegion square = (SquareCaptureRegion) region;
        return new Bounds(square.centerX() - square.radius(), square.centerX() + square.radius(),
                square.centerZ() - square.radius(), square.centerZ() + square.radius());
    }

    static void saveHeight(CompoundTag tag, Integer minY, Integer maxY) {
        if (minY != null) { tag.putInt("MinY", minY); tag.putInt("MaxY", maxY); }
    }

    static Integer readMinY(CompoundTag tag) { return tag.contains("MinY") ? tag.getInt("MinY") : null; }
    static Integer readMaxY(CompoundTag tag) { return tag.contains("MaxY") ? tag.getInt("MaxY") : null; }
    record Bounds(double minX, double maxX, double minZ, double maxZ) {}
}
