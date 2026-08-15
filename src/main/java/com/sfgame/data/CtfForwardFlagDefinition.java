package com.sfgame.data;

import com.sfgame.game.TeamSide;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

public record CtfForwardFlagDefinition(String id, TeamSide owner, CaptureRegion region,
                                       ArenaPosition stand, int order) {
    public CtfForwardFlagDefinition {
        id = SFGameId.normalize(id);
        if (owner == null || owner == TeamSide.NONE) throw new IllegalArgumentException("Forward flag owner must be playable");
        if (region == null || stand == null) throw new IllegalArgumentException("Forward flag region and stand are required");
        if (order < 1) throw new IllegalArgumentException("Forward flag order must be positive");
    }

    public CtfForwardFlagDefinition withRegion(CaptureRegion value) { return new CtfForwardFlagDefinition(id, owner, value, stand, order); }
    public CtfForwardFlagDefinition withStand(ArenaPosition value) { return new CtfForwardFlagDefinition(id, owner, region, value, order); }
    public CtfForwardFlagDefinition withOrder(int value) { return new CtfForwardFlagDefinition(id, owner, region, stand, value); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id); tag.putString("Owner", owner.id()); tag.putInt("Order", order);
        tag.put("Region", region.save()); tag.put("Stand", stand.save());
        return tag;
    }

    public static CtfForwardFlagDefinition load(CompoundTag tag) {
        return new CtfForwardFlagDefinition(tag.getString("Id"), TeamSide.fromId(tag.getString("Owner")),
                CaptureRegion.load(tag.getCompound("Region")), ArenaPosition.load(tag.getCompound("Stand")), tag.getInt("Order"));
    }
}
