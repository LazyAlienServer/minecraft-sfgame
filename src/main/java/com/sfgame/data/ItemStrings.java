package com.sfgame.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Shared parser for SFGame item selectors used by class icons, shop entries
 * and inventory definitions.  A selector is either a plain resource id or an
 * id followed by an SNBT compound, for example
 * {@code tacz:modern_kinetic_gun{GunId:"tacz:hk416d"}}.
 */
public final class ItemStrings {
    private ItemStrings() {
    }

    /** {@code id} is null when the selector has no valid resource id; {@code nbt} keeps its original case. */
    public record Parsed(@Nullable ResourceLocation id, String nbt) {
        public boolean hasNbt() { return nbt != null && !nbt.isBlank(); }
    }

    public static Parsed parse(@Nullable String value) {
        String trimmed = value == null ? "" : value.trim();
        // Vanilla accepts an empty namespace/path pair; SFGame treats blank selectors as unset.
        if (trimmed.isEmpty()) return new Parsed(null, "");
        int brace = nbtStart(trimmed);
        if (brace < 0) return new Parsed(ResourceLocation.tryParse(trimmed.toLowerCase(Locale.ROOT)), "");
        ResourceLocation id = ResourceLocation.tryParse(trimmed.substring(0, brace).trim().toLowerCase(Locale.ROOT));
        return new Parsed(id, trimmed.substring(brace));
    }

    /** Builds a display stack; unknown ids or broken NBT yield {@code fallback}. */
    public static ItemStack stack(@Nullable String value, int count, ItemStack fallback) {
        Parsed parsed = parse(value);
        if (parsed.id() == null) return fallback;
        return BuiltInRegistries.ITEM.getOptional(parsed.id())
                .filter(item -> item != Items.AIR)
                .map(item -> {
                    ItemStack stack = new ItemStack(item, Math.max(1, count));
                    return applyTag(stack, parsed.nbt()) ? stack : null;
                })
                .orElse(fallback);
    }

    /** Applies an NBT string ("{...}" compound) to an existing stack; false when unparsable. */
    public static boolean applyTag(ItemStack stack, @Nullable String nbt) {
        if (nbt == null || nbt.isBlank()) return true;
        try {
            CompoundTag tag = TagParser.parseTag(nbt);
            if (!tag.isEmpty()) stack.setTag(tag);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /** First '{' outside double quotes, or -1 when the selector carries no NBT. */
    private static int nbtStart(String value) {
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"') quoted = !quoted;
            else if (character == '{' && !quoted) return i;
        }
        return -1;
    }
}
