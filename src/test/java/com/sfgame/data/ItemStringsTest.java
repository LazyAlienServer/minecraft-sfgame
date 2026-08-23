package com.sfgame.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-string tests for {@link ItemStrings#parse(String)}.  The stack-level
 * entry points {@code stack}/{@code applyTag} are NOT unit-testable here:
 * touching {@code ItemStack} initializes {@code BuiltInRegistries}, which
 * fails headless ("not bootstrapped"), and {@code Bootstrap.bootStrap()}
 * fails under ForgeGradle's test JVM.  Those runtime paths (fallback on
 * malformed SNBT) are verified by the mapped-class compile plus the server
 * loadout/shop code paths.
 */
final class ItemStringsTest {
    @Test
    void splitsInlineNbtAndKeepsItsCase() {
        ItemStrings.Parsed parsed = ItemStrings.parse("tacz:modern_kinetic_gun{GunId:\"tacz:hk416d\"}");
        assertEquals("tacz:modern_kinetic_gun", parsed.id().toString());
        assertEquals("{GunId:\"tacz:hk416d\"}", parsed.nbt());
        assertTrue(parsed.hasNbt());
    }

    @Test
    void normalizesPlainResourceIdCaseOnly() {
        ItemStrings.Parsed parsed = ItemStrings.parse("Minecraft:Diamond");
        assertEquals("minecraft:diamond", parsed.id().toString());
        assertFalse(parsed.hasNbt());
    }

    @Test
    void bracesInsideQuotedSnbtAreNotTreatedAsSelectorStart() {
        ItemStrings.Parsed parsed = ItemStrings.parse("minecraft:written_book{pages:[\"{\\\"text\\\":\\\"hi\\\"}\"]}");
        assertEquals("minecraft:written_book", parsed.id().toString());
        assertEquals("{pages:[\"{\\\"text\\\":\\\"hi\\\"}\"]}", parsed.nbt());
    }

    @Test
    void invalidOrMissingSelectorsYieldNullId() {
        assertNull(ItemStrings.parse(null).id());
        assertNull(ItemStrings.parse("").id());
        assertNull(ItemStrings.parse("not an id").id());
    }

    @Test
    void malformedSnbtStillYieldsTheResourceIdForStackValidation() {
        ItemStrings.Parsed parsed = ItemStrings.parse("minecraft:diamond{Count:notanumber");
        assertEquals("minecraft:diamond", parsed.id().toString());
        assertEquals("{Count:notanumber", parsed.nbt());
        assertTrue(parsed.hasNbt());
    }
}
