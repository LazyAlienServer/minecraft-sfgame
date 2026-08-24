package com.sfgame.classsystem;

import com.sfgame.data.SFGameSavedData;
import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClassRegistryTest {
    @TempDir Path directory;

    @Test
    void elitePoolsInheritAndOverrideWithoutEnteringNormalClasses() throws Exception {
        Files.createDirectories(directory.resolve("classes"));
        Files.writeString(directory.resolve("classes/tdm.json"), """
                {
                  "classes":[{"id":"normal"}],
                  "captainClasses":[],
                  "eliteClasses":[{"id":"base_elite","displayName":"Base"}],
                  "teams":{"red":{"eliteClasses":[{"id":"red_elite","displayName":"Red"}]}}
                }
                """);
        Files.writeString(directory.resolve("classes/domination.json"), """
                {
                  "parent":"tdm",
                  "classes":[],
                  "captainClasses":[],
                  "eliteClasses":[{"id":"mode_elite","displayName":"Mode"}],
                  "teams":{"red":{"eliteClasses":[{"id":"red_mode_elite","displayName":"Red Mode"}]}},
                  "maps":{}
                }
                """);

        ClassRegistry registry = new ClassRegistry();
        registry.useConfigRoot(directory);
        assertTrue(registry.reload().isEmpty(), registry.loadErrors().toString());

        assertTrue(registry.containsEliteForTeam(GameModeRegistry.DOMINATION, null, TeamSide.RED, "base_elite"));
        assertTrue(registry.containsEliteForTeam(GameModeRegistry.DOMINATION, null, TeamSide.RED, "mode_elite"));
        assertFalse(registry.containsEliteForTeam(GameModeRegistry.DOMINATION, null, TeamSide.RED, "red_elite"));
        assertTrue(registry.containsEliteForTeam(GameModeRegistry.DOMINATION, null, TeamSide.RED, "red_mode_elite"));
        assertFalse(registry.containsEliteForTeam(GameModeRegistry.DOMINATION, null, TeamSide.BLUE, "red_mode_elite"));

        assertFalse(registry.getForTeam(GameModeRegistry.DOMINATION, null, TeamSide.RED, "base_elite").isPresent());
        assertFalse(registry.allForTeam(GameModeRegistry.DOMINATION, null, TeamSide.RED).stream()
                .anyMatch(definition -> definition.id().contains("elite")));
        assertTrue(registry.eliteClassesForMode(GameModeRegistry.DOMINATION, null).stream()
                .anyMatch(definition -> definition.id().equals("red_mode_elite")));
    }

    @Test
    void bundledEliteDefaultsExposeTheRequestedWeapons() {
        ClassRegistry registry = new ClassRegistry();
        registry.useConfigRoot(directory);
        SFGameSavedData data = new SFGameSavedData();

        assertTrue(registry.reload(data).isEmpty(), registry.loadErrors().toString());

        ClassDefinition flamethrower = registry.getEliteForTeam(
                GameModeRegistry.CAPTURE_THE_FLAG, null, TeamSide.RED, "elite_flamethrower").orElseThrow();
        ClassDefinition tankHunter = registry.getEliteForTeam(
                GameModeRegistry.CAPTURE_THE_FLAG, null, TeamSide.RED, "elite_tank_hunter").orElseThrow();
        assertEquals("bf1:m2_2", flamethrower.gunId());
        assertEquals("bf1:tg1918", tankHunter.gunId());
        assertTrue(registry.allForTeam(GameModeRegistry.CAPTURE_THE_FLAG, null, TeamSide.RED).stream()
                .noneMatch(definition -> definition.id().equals("elite_flamethrower")
                        || definition.id().equals("elite_tank_hunter")));
    }
}
