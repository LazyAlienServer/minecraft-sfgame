package com.sfgame.game;

import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.CaptureRegion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
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
 * <p>The one-metre background and label are separate display entities. This is
 * intentional: non-uniformly scaling one TextDisplay also distorts its glyphs.
 * A thin billboarded BlockDisplay provides the square background while the
 * TextDisplay can use a uniform scale and retain the normal Minecraft font.</p>
 */
final class CapturePointMarkerService {
    private static final float LABEL_SCALE = 4.0F;
    private static final double LABEL_VERTICAL_OFFSET = -0.65D;
    private static final String MARKER_TAG = "sfgame_capture_point_marker";

    private final Map<String, MarkerEntry> markers = new HashMap<>();

    void refresh(MinecraftServer server, List<CapturePointDefinition> active,
                 Map<String, CapturePointState> states) {
        Set<String> ids = new HashSet<>();
        for (CapturePointDefinition point : active) ids.add(point.id());
        markers.entrySet().removeIf(entry -> {
            if (ids.contains(entry.getKey()) && entry.getValue().isAlive()) return false;
            entry.getValue().discard();
            return true;
        });

        for (CapturePointDefinition point : active) {
            CaptureRegion region = point.region();
            String signature = regionSignature(region);
            MarkerVisual visual = MarkerVisual.from(states.get(point.id()));
            MarkerEntry existing = markers.get(point.id());
            if (existing != null && !existing.signature().equals(signature)) {
                existing.discard();
                markers.remove(point.id());
                existing = null;
            }
            if (existing != null) {
                if (!existing.visual().equals(visual)) {
                    configureLabel(existing.label(), point.id(), visual);
                    markers.put(point.id(), new MarkerEntry(existing.background(), existing.label(), signature, visual));
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

            Display.BlockDisplay background = EntityType.BLOCK_DISPLAY.create(level);
            Display.TextDisplay label = EntityType.TEXT_DISPLAY.create(level);
            if (background == null || label == null) continue;
            prepare(background, region.centerX(), y, region.centerZ());
            // BlockDisplay and TextDisplay use different visual origins. With
            // the same entity position the text sits on the square's top edge.
            // Offset the text entity itself so the glyph is visually centred.
            prepare(label, region.centerX(), y + LABEL_VERTICAL_OFFSET, region.centerZ());
            configureBackground(background);
            configureLabel(label, point.id(), visual);
            if (level.addFreshEntity(background) && level.addFreshEntity(label)) {
                markers.put(point.id(), new MarkerEntry(background, label, signature, visual));
            } else {
                background.discard();
                label.discard();
            }
        }
    }

    void clear() {
        markers.values().forEach(MarkerEntry::discard);
        markers.clear();
    }

    private static void prepare(Display marker, double x, double y, double z) {
        marker.moveTo(x, y, z, 0.0F, 0.0F);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setSilent(true);
        marker.addTag(MARKER_TAG);
    }

    private static void configureBackground(Display.BlockDisplay marker) {
        CompoundTag tag = new CompoundTag();
        marker.saveWithoutId(tag);
        tag.put("block_state", NbtUtils.writeBlockState(Blocks.GRAY_CONCRETE.defaultBlockState()));
        tag.putString("billboard", "center");
        tag.putFloat("view_range", 4.0F);
        tag.putFloat("width", 1.0F);
        tag.putFloat("height", 1.0F);
        putFullBrightness(tag);

        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(-0.5F, -0.5F, 0.04F));
        transformation.put("scale", floats(1.0F, 1.0F, 0.025F));
        transformation.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        tag.put("transformation", transformation);
        marker.load(tag);
    }

    private static void configureLabel(Display.TextDisplay marker, String pointId, MarkerVisual visual) {
        CompoundTag tag = new CompoundTag();
        marker.saveWithoutId(tag);
        String displayId = pointId.toUpperCase(Locale.ROOT);
        Component text = Component.literal(displayId).withStyle(visual.formatting());
        tag.putString("text", Component.Serializer.toJson(text));
        tag.putInt("line_width", 1000);
        tag.putInt("background", 0x00000000);
        tag.putByte("text_opacity", (byte) 0xFF);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", true);
        tag.putBoolean("default_background", false);
        tag.putString("alignment", "center");
        tag.putString("billboard", "center");
        tag.putFloat("view_range", 4.0F);
        tag.putFloat("width", 1.0F);
        tag.putFloat("height", 1.0F);
        putFullBrightness(tag);

        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(0.0F, -0.15F, 0.0F));
        transformation.put("scale", floats(LABEL_SCALE, LABEL_SCALE, 1.0F));
        transformation.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        tag.put("transformation", transformation);
        marker.load(tag);
    }

    private static void putFullBrightness(CompoundTag tag) {
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", 15);
        brightness.putInt("sky", 15);
        tag.put("brightness", brightness);
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

    private record MarkerEntry(Display.BlockDisplay background, Display.TextDisplay label,
                               String signature, MarkerVisual visual) {
        private boolean isAlive() {
            return background.isAlive() && label.isAlive();
        }

        private void discard() {
            background.discard();
            label.discard();
        }
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
