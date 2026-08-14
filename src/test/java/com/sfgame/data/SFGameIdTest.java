package com.sfgame.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SFGameIdTest {
    @Test
    void acceptsSingleDigitsAndDigitPrefixedIds() {
        assertTrue(SFGameId.isValid("1"));
        assertTrue(SFGameId.isValid("1a"));
        assertTrue(SFGameId.isValid("1_test"));
        assertTrue(SFGameId.isValidClass("0"));
        assertEquals("1a", SFGameId.normalize("1A"));
    }

    @Test
    void preservesResourceStyleRestrictions() {
        assertFalse(SFGameId.isValid(""));
        assertFalse(SFGameId.isValid("_1"));
        assertFalse(SFGameId.isValid("1-a"));
        assertFalse(SFGameId.isValid("一"));
        assertThrows(IllegalArgumentException.class, () -> SFGameId.normalize("_1"));
    }
}
