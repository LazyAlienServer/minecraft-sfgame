package com.sfgame.game;

import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.CaptureRegion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders the currently active Domination/Breakthrough objectives in-world.
 *
 * <p>A text display is used instead of an armor-stand custom name so the
 * marker can have a predictable, opaque background. Minecraft renders text
 * display glyphs at 0.025 blocks per pixel; the non-uniform scale below keeps
 * the background close to one block wide and one block high even for longer
 * point IDs.</p>
 */
final class CapturePointMarkerService {
    private static final int BACKGROUND_COLOR = 0xD0555555;
    private static final float TARGET_PIXEL_EXTENT = 40.0F;
    private static final float MARKER_HEIGHT_SCALE = 3.6F;
    private static final String MARKER_TAG = "sfgame_capture_point_marker";

    private final Map<String, MarkerEntry> markers = new HashMap<>();

    void refresh(MinecraftServer server, List<CapturePointDefinition> active,
                 Map<String, CapturePointState> states) {
        Set<String> ids = new HashSet<>();
        for (CapturePointDefinition point : active) ids.add(point.id());
        markers.entrySet().removeIf(entry -> {
            if (ids.contains(entry.getKey()) && entry.getValue().entity().isAlive()) return false;
            entry.getValue().entity().discard();
            return true;
        });

        for (CapturePointDefinition point : active) {
            CaptureRegion region = point.region();
            String signature = regionSignature(region);
            MarkerVisual visual = MarkerVisual.from(states.get(point.id()));
            MarkerEntry existing = markers.get(point.id());
            if (existing != null && !existing.signature().equals(signature)) {
                existing.entity().discard();
                markers.remove(point.id());
                existing = null;
            }
            if (existing != null) {
                if (!existing.visual().equals(visual)) {
                    configure(existing.entity(), point.id(), visual);
                    markers.put(point.id(), new MarkerEntry(existing.entity(), signature, visual));
                }
                continue;
            }

            ResourceLocation dimension = ResourceLocation.tryParse(region.dimension());
            ServerLevel level = dimension == null ? null
                    : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) continue;
            int surface = region.minY() == null
                    ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(region.centerX()), (int) Math.floor(region.centerZ()))
                    : region.minY();
            double y = surface + 3.0;
            if (region.maxY() != null) y = Math.min(y, region.maxY() + 1.5);

            Display.TextDisplay marker = EntityType.TEXT_DISPLAY.create(level);
            if (marker == null) continue;
            marker.moveTo(region.centerX(), y, region.centerZ(), 0.0F, 0.0F);
            marker.setNoGravity(true);
            marker.setInvulnerable(true);
            marker.setSilent(true);
            marker.addTag(MARKER_TAG);
            configure(marker, point.id(), visual);
            if (level.addFreshEntity(marker)) {
                markers.put(point.id(), new MarkerEntry(marker, signature, visual));
            }
        }
    }

    void clear() {
        markers.values().forEach(marker -> marker.entity().discard());
        markers.clear();
    }

    private static void configure(Display.TextDisplay marker, String pointId, MarkerVisual visual) {
        CompoundTag tag = new CompoundTag();
        marker.saveWithoutId(tag);
        String displayId = pointId.toUpperCase(Locale.ROOT);
        Component text = Component.literal(" " + displayId + " ")
                .withStyle(visual.formatting(), ChatFormatting.BOLD);
        tag.putString("text", Component.Serializer.toJson(text));
        tag.putInt("line_width", 1000);
        tag.putInt("background", BACKGROUND_COLOR);
        tag.putByte("text_opacity", (byte) 0xFF);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", true);
        tag.putBoolean("default_background", false);
        tag.putString("alignment", "center");
        tag.putString("billboard", "center");
        tag.putFloat("view_range", 4.0F);
        tag.putFloat("width", 1.0F);
        tag.putFloat("height", 1.0F);

        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", 15);
        brightness.putInt("sky", 15);
        tag.put("brightness", brightness);

        int estimatedWidth = 10 + displayId.length() * 6;
        float widthScale = Math.max(0.45F, Math.min(2.5F, TARGET_PIXEL_EXTENT / estimatedWidth));
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(0.0F, 0.0F, 0.0F));
        transformation.put("scale", floats(widthScale, MARKER_HEIGHT_SCALE, 1.0F));
        transformation.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        tag.put("transformation", transformation);
        marker.load(tag);
    }

    private static ListTag floats(float... values) {
        ListTag result = new ListTag();
        for (float value : values) result.add(FloatTag.valueOf(value));
        return result;
    }

    private static String regionSignature(CaptureRegion region) {
        return region.dimension() + '|' + region.centerX() + '|' + region.centerZ()
                + '|' + region.minY() + '|' + region.maxY();
    }

    private record MarkerEntry(Display.TextDisplay entity, String signature, MarkerVisual visual) {
    }

    private record MarkerVisual(TeamSide side, boolean contested) {
        private static MarkerVisual from(CapturePointState state) {
            if (state == null) return new MarkerVisual(TeamSide.NONE, false);
            TeamSide side = state.owner() != TeamSide.NONE ? state.owner() : state.contender();
            return new MarkerVisual(side, state.contested());
        }

        private ChatFormatting formatting() {
            if (contested) return ChatFormatting.GOLD;
            return switch (side) {
                case RED -> ChatFormatting.RED;
                case BLUE -> ChatFormatting.BLUE;
                case YELLOW -> ChatFormatting.YELLOW;
                case GREEN -> ChatFormatting.GREEN;
                case NONE -> ChatFormatting.WHITE;
            };
        }
    }
}
