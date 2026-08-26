package com.sfgame.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SquadServiceTest {
    @Test
    void requiredSquadCountUsesCeilingCapacityRule() {
        assertEquals(0, SquadService.requiredCount(0, 2));
        assertEquals(1, SquadService.requiredCount(1, 2));
        assertEquals(1, SquadService.requiredCount(2, 2));
        assertEquals(2, SquadService.requiredCount(3, 2));
        assertEquals(2, SquadService.requiredCount(4, 2));
        assertEquals(3, SquadService.requiredCount(9, 4));
    }
}
