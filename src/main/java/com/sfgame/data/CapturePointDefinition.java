package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;

public record CapturePointDefinition(String id, CaptureRegion region, int order) {
    public CapturePointDefinition {
        id = normalizeId(id);
        if (region == null) throw new IllegalArgumentException("Capture region is required");
        if (order < 1) throw new IllegalArgumentException("Point order must be positive");
    }

    public static String normalizeId(String id) {
        try {
            return SFGameId.normalize(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid point id: " + id, exception);
        }
    }

    public CapturePointDefinition withRegion(CaptureRegion value) { return new CapturePointDefinition(id, value, order); }
    public CapturePointDefinition withOrder(int value) { return new CapturePointDefinition(id, region, value); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id); tag.putInt("Order", order); tag.put("Region", region.save());
        return tag;
    }

    public static CapturePointDefinition load(CompoundTag tag) {
        return new CapturePointDefinition(tag.getString("Id"), CaptureRegion.load(tag.getCompound("Region")), tag.getInt("Order"));
    }
}
