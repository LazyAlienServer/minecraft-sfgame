package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

public record CapturePointDefinition(String id, CaptureRegion region, int order,
                                     @Nullable ArenaPosition respawnPosition,
                                     @Nullable ArenaPosition nearbyRespawnPosition) {
    public CapturePointDefinition(String id, CaptureRegion region, int order) {
        this(id, region, order, null, null);
    }

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

    public CapturePointDefinition withRegion(CaptureRegion value) {
        return new CapturePointDefinition(id, value, order, respawnPosition, nearbyRespawnPosition);
    }
    public CapturePointDefinition withOrder(int value) {
        return new CapturePointDefinition(id, region, value, respawnPosition, nearbyRespawnPosition);
    }
    public CapturePointDefinition withRespawnPosition(ArenaPosition value) {
        return new CapturePointDefinition(id, region, order, value, nearbyRespawnPosition);
    }
    public CapturePointDefinition withNearbyRespawnPosition(ArenaPosition value) {
        return new CapturePointDefinition(id, region, order, respawnPosition, value);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id); tag.putInt("Order", order); tag.put("Region", region.save());
        if (respawnPosition != null) tag.put("RespawnPosition", respawnPosition.save());
        if (nearbyRespawnPosition != null) tag.put("NearbyRespawnPosition", nearbyRespawnPosition.save());
        return tag;
    }

    public static CapturePointDefinition load(CompoundTag tag) {
        return new CapturePointDefinition(tag.getString("Id"), CaptureRegion.load(tag.getCompound("Region")), tag.getInt("Order"),
                tag.contains("RespawnPosition") ? ArenaPosition.load(tag.getCompound("RespawnPosition")) : null,
                tag.contains("NearbyRespawnPosition") ? ArenaPosition.load(tag.getCompound("NearbyRespawnPosition")) : null);
    }
}
