package com.sfgame.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SFGameAdminScreenTest {
    @Test
    void formatsDisjointMaxPlayerRangeWithoutImplyingZeroOrOne() {
        assertEquals("-1 | 2..1000000",
                SFGameAdminScreen.numericRangeText("maxPlayers", -1, 1_000_000));
    }

    @Test
    void formatsOrdinaryNumericRangesNormally() {
        assertEquals("0..60", SFGameAdminScreen.numericRangeText("respawnSeconds", 0, 60));
        assertEquals("0.1..10", SFGameAdminScreen.numericRangeText("captureDifferenceCoefficient", 0.1, 10));
    }
}
