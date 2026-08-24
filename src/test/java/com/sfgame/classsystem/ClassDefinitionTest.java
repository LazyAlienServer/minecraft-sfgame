package com.sfgame.classsystem;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                    {"type":"gun","gunId":"tacz:hk416d","initialMagazine":30,"fireMode":"AUTO"},
                    {"type":"ammoBox","ammoId":"tacz:556x45","ammoCount":180},
                    {"type":"gun","gunId":"tacz:glock_17","initialMagazine":17,"fireMode":"SEMI"},
                    {"type":"ammoBox","ammoId":"tacz:9mm","ammoCount":68},
                    {"type":"gun","gunId":"tacz:m870","initialMagazine":8,"fireMode":"SEMI"},
                    {"type":"ammoBox","ammoId":"tacz:12g","ammoCount":32}
                  ]
                }
                """, ClassDefinition.class);

        assertEquals("tacz:hk416d", definition.gunId());
        assertEquals(280, definition.reserveAmmo());
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
    void migratesLegacyTopLevelWeaponFieldsIntoInventory() {
        JsonObject document = JsonParser.parseString("""
                {
                  "classes": [{
                    "id": "legacy",
                    "gunId": "tacz:hk416d",
                    "ammoId": "tacz:556x45",
                    "initialMagazine": 30,
                    "reserveAmmo": 180,
                    "fireMode": "AUTO",
                    "attachments": {},
                    "inventory": [{"item":"minecraft:stone_sword"}]
                  }]
                }
                """).getAsJsonObject();

        assertTrue(ClassRegistry.migrateLegacyWeapons(document));
        JsonObject migrated = document.getAsJsonArray("classes").get(0).getAsJsonObject();
        assertFalse(migrated.has("gunId"));
        assertEquals("gun", migrated.getAsJsonArray("inventory").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("ammoBox", migrated.getAsJsonArray("inventory").get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("minecraft:stone_sword",
                migrated.getAsJsonArray("inventory").get(2).getAsJsonObject().get("item").getAsString());
        assertFalse(ClassRegistry.migrateLegacyWeapons(document));
    }
}
