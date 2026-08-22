package com.sfgame.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Normalizes and compiles block IDs and {@code #block_tag} selectors. */
public final class BlockAllowlist {
    public static String normalize(String value) {
        String trimmed = value == null ? "" : value.trim();
        boolean tag = trimmed.startsWith("#");
        String id = SFGameId.normalizeResource(tag ? trimmed.substring(1) : trimmed);
        return tag ? "#" + id : id;
    }

    public static Set<String> normalizeAll(Collection<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) values.forEach(value -> normalized.add(normalize(value)));
        return Collections.unmodifiableSet(normalized);
    }

    public static Matcher compile(Collection<String> values) {
        Set<String> blocks = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        for (String value : normalizeAll(values)) {
            boolean tag = value.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? value.substring(1) : value);
            if (id == null) continue;
            if (tag) tags.add(id.toString());
            else blocks.add(id.toString());
        }
        return new Matcher(Set.copyOf(blocks), Set.copyOf(tags));
    }

    public record Matcher(Set<String> blocks, Set<String> tags) {
        public boolean isEmpty() { return blocks.isEmpty() && tags.isEmpty(); }

        public boolean matches(BlockState state) {
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (blocks.contains(blockId)) return true;
            for (String tag : tags) {
                ResourceLocation tagId = ResourceLocation.tryParse(tag);
                if (tagId != null && state.is(TagKey.create(Registries.BLOCK, tagId))) return true;
            }
            return false;
        }

        public boolean matches(String blockId) {
            String normalized = SFGameId.normalizeResource(blockId);
            if (blocks.contains(normalized)) return true;
            if (tags.isEmpty()) return false;
            ResourceLocation id = ResourceLocation.tryParse(normalized);
            return id != null && BuiltInRegistries.BLOCK.containsKey(id)
                    && matches(BuiltInRegistries.BLOCK.get(id).defaultBlockState());
        }
    }

    private BlockAllowlist() {
    }
}
