package com.sfgame.classsystem;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void readsPrimaryAndTwoSecondaryWeaponsFromInventory() {
        ClassDefinition definition = GSON.fromJson("""
                {
                  "id": "assault",
                  "inventory": [
                    {"type":"gun","slot":0,"gunId":"tacz:hk416d","initialMagazine":30,"fireMode":"AUTO"},
                    {"type":"ammoBox","slot":9,"ammoId":"tacz:556x45","ammoCount":180},
                    {"type":"gun","slot":1,"gunId":"tacz:glock_17","initialMagazine":17,"fireMode":"SEMI"},
                    {"type":"ammoBox","slot":10,"ammoId":"tacz:9mm","ammoCount":68},
                    {"type":"gun","slot":2,"gunId":"tacz:m870","initialMagazine":8,"fireMode":"SEMI"},
                    {"type":"ammoBox","slot":11,"ammoId":"tacz:12g","ammoCount":32}
                  ]
                }
                """, ClassDefinition.class);

        assertEquals("tacz:hk416d", definition.gunId());
        assertEquals(280, definition.reserveAmmo());
        assertEquals(9, definition.inventory().get(1).slot());
        assertTrue(LoadoutService.validateInventoryShape(definition).isEmpty());
    }

    @Test
    void rejectsMoreThanTwoSecondaryWeapons() {
        ClassDefinition definition = GSON.fromJson("""
                {
                  "id": "overloaded",
                  "inventory": [
                    {"type":"gun"},{"type":"ammoBox"},
                    {"type":"gun"},{"type":"ammoBox"},
                    {"type":"gun"},{"type":"ammoBox"},
                    {"type":"gun"},{"type":"ammoBox"}
                  ]
                }
                """, ClassDefinition.class);

        List<String> errors = LoadoutService.validateInventoryShape(definition);

        assertTrue(errors.stream().anyMatch(error -> error.contains("at most two secondary guns")));
    }

    @Test
    void requiresOneConfiguredAmmoBoxPerWeapon() {
        ClassDefinition definition = GSON.fromJson("""
                {"id":"missing_ammo","inventory":[{"type":"gun"},{"type":"gun"},{"type":"ammoBox"}]}
                """, ClassDefinition.class);

        assertTrue(LoadoutService.validateInventoryShape(definition).stream()
                .anyMatch(error -> error.contains("one ammo box per gun")));
    }

    @Test
    void rejectsDuplicateAndOutOfRangeInventorySlots() {
        ClassDefinition definition = GSON.fromJson("""
                {
                  "id": "bad_slots",
                  "inventory": [
                    {"type":"gun","slot":0},
                    {"type":"ammoBox","slot":9},
                    {"item":"minecraft:stone","slot":9},
                    {"item":"minecraft:dirt","slot":36}
                  ]
                }
                """, ClassDefinition.class);

        List<String> errors = LoadoutService.validateInventoryShape(definition);

        assertTrue(errors.stream().anyMatch(error -> error.contains("slot 9 is configured more than once")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("between 0 and 35")));
    }

    @Test
    void rejectsLegacyTopLevelWeaponFields() {
        ClassDefinition definition = GSON.fromJson("""
                {"id":"legacy","gunId":"tacz:hk416d","ammoId":"tacz:556x45"}
                """, ClassDefinition.class);

        assertTrue(LoadoutService.validateInventoryShape(definition).stream()
                .anyMatch(error -> error.contains("inventory must contain a primary gun")));
    }
}
