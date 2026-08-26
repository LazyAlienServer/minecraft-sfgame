package com.sfgame.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

final class SFGameText {
    private static final int FALLBACK_COLOR = 0xFFFFFF;

    private SFGameText() {
    }

    static Component colored(String key, Object... args) {
        return Component.translatable(key, args);
    }

    static Component colored(String key, Component value) {
        return Component.translatable(key, value);
    }

    static int colorOf(String key) {
        return colorOf(Component.translatable(key));
    }

    static int colorOf(Component component) {
        String text = component.getString();
        for (int index = 0; index + 1 < text.length(); index++) {
            if (text.charAt(index) != '\u00A7') continue;
            ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(++index));
            if (formatting != null && formatting.getColor() != null) return formatting.getColor();
        }
        return FALLBACK_COLOR;
    }
}
