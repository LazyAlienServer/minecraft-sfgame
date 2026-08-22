package com.sfgame.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockAllowlistTest {
    @Test
    void normalizesBlockAndTagSelectors() {
        assertEquals("minecraft:white_wool", BlockAllowlist.normalize("MINECRAFT:WHITE_WOOL"));
        assertEquals("#minecraft:logs", BlockAllowlist.normalize("#MINECRAFT:LOGS"));
        assertThrows(IllegalArgumentException.class, () -> BlockAllowlist.normalize("#bad tag"));
    }

    @Test
    void compiledMatcherMatchesDirectBlockIdsWithoutRegistryBootstrap() {
        BlockAllowlist.Matcher matcher = BlockAllowlist.compile(List.of("minecraft:white_wool"));
        assertTrue(matcher.matches("minecraft:white_wool"));
        assertFalse(matcher.matches("minecraft:stone"));
    }
}
