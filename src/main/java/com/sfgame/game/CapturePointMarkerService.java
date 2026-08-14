package com.sfgame.game;

import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.CaptureRegion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CapturePointMarkerService {
    private final Map<String, ArmorStand> markers = new HashMap<>();

    void refresh(MinecraftServer server, List<CapturePointDefinition> active) {
        List<String> ids = active.stream().map(CapturePointDefinition::id).toList();
        markers.entrySet().removeIf(entry -> {
            if (ids.contains(entry.getKey()) && entry.getValue().isAlive()) return false;
            entry.getValue().discard(); return true;
        });
        for (CapturePointDefinition point : active) {
            if (markers.containsKey(point.id())) continue;
            CaptureRegion region = point.region();
            ResourceLocation dimension = ResourceLocation.tryParse(region.dimension());
            ServerLevel level = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) continue;
            int surface = region.minY() == null
                    ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(region.centerX()), (int) Math.floor(region.centerZ()))
                    : region.minY();
            double y = surface + 3.0;
            if (region.maxY() != null) y = Math.min(y, region.maxY() + 1.5);
            ArmorStand marker = new ArmorStand(level, region.centerX(), y, region.centerZ());
            marker.setInvisible(true); setMarker(marker); marker.setNoGravity(true); marker.setInvulnerable(true);
            marker.setSilent(true); marker.setGlowingTag(true);
            marker.setCustomName(Component.literal(point.id().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            marker.setCustomNameVisible(true);
            if (level.addFreshEntity(marker)) markers.put(point.id(), marker);
        }
    }

    void clear() {
        markers.values().forEach(ArmorStand::discard);
        markers.clear();
    }

    private static void setMarker(ArmorStand stand) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        stand.saveWithoutId(tag); tag.putBoolean("Marker", true); stand.load(tag);
    }
}
