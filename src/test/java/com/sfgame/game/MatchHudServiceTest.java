package com.sfgame.game;

import com.sfgame.data.MatchRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MatchHudServiceTest {
    @Test
    void formatsRemainingTimeAsHoursMinutesAndSeconds() {
        assertEquals("00:00:00", MatchHudService.formatRemainingTime(0));
        assertEquals("01:02:03", MatchHudService.formatRemainingTime(3_723));
        assertEquals("100:00:00", MatchHudService.formatRemainingTime(360_000));
        assertEquals("∞", MatchHudService.formatRemainingTime(MatchRules.UNLIMITED_TIME_SECONDS));
    }
}
