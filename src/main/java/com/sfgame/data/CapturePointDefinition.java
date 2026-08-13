package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;

public record CapturePointDefinition(String id, CaptureRegion region, int order) {
    public CapturePointDefinition {
        if (id == null || !id.matches("[a-z][a-z0-9_]{0,31}")) throw new IllegalArgumentException("Invalid point id: " + id);
        if (region == null) throw new IllegalArgumentException("Capture region is required");
        if (order < 1) throw new IllegalArgumentException("Point order must be positive");
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
