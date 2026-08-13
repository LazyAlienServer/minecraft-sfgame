package com.sfgame.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public record ArenaPosition(String dimension, double x, double y, double z, float yaw, float pitch) {
    public static ArenaPosition from(ServerPlayer player) {
        return new ArenaPosition(player.level().dimension().location().toString(), player.getX(), player.getY(),
                player.getZ(), player.getYRot(), player.getXRot());
    }

    public boolean teleport(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) return false;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel level = server.getLevel(key);
        if (level == null) return false;
        player.teleportTo(level, x, y, z, yaw, pitch);
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", dimension);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        return tag;
    }

    public static ArenaPosition load(CompoundTag tag) {
        return new ArenaPosition(tag.getString("Dimension"), tag.getDouble("X"), tag.getDouble("Y"),
                tag.getDouble("Z"), tag.getFloat("Yaw"), tag.getFloat("Pitch"));
    }
}

