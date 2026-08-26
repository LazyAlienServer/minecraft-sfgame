package com.sfgame.client;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SquadScreenTest {
    @Test
    void absentPlayerInfoUsesDefaultSkin() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ResourceLocation expected = DefaultPlayerSkin.getDefaultSkin(id);
        assertEquals(expected, SquadScreen.skinLocation(null, id));
    }
}
