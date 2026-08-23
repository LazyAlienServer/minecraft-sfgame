package com.sfgame.classsystem;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassDefinitionTest {
    private static final Gson GSON = new Gson();

    @Test
    void defaultsIconRenderingToItem() {
        ClassDefinition definition = GSON.fromJson("{}", ClassDefinition.class);

        assertEquals("item", definition.iconRender());
    }

    @Test
    void acceptsHudIconRenderingCaseInsensitively() {
        ClassDefinition definition = GSON.fromJson("{\"iconRender\":\" HUD \"}", ClassDefinition.class);

        assertEquals("hud", definition.iconRender());
    }

    @Test
    void acceptsPngTextureConfiguration() {
        ClassDefinition definition = GSON.fromJson(
                "{\"iconRender\":\"png\",\"iconTexture\":\"example:textures/gui/classes/hk416d.png\"}",
                ClassDefinition.class);

        assertEquals("png", definition.iconRender());
        assertEquals("example:textures/gui/classes/hk416d.png", definition.iconTexture());
    }

    @Test
    void unknownIconRenderingFallsBackToItem() {
        ClassDefinition definition = GSON.fromJson("{\"iconRender\":\"unknown\"}", ClassDefinition.class);

        assertEquals("item", definition.iconRender());
    }
}
