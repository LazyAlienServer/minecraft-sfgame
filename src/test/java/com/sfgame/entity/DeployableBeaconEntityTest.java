package com.sfgame.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeployableBeaconEntityTest {
    @Test
    void damageNeverHealsAndReachesZeroAtOrBelowLethalDamage() {
        assertEquals(35.0F, BeaconHealth.afterDamage(50.0F, 15.0F));
        assertEquals(0.0F, BeaconHealth.afterDamage(50.0F, 50.0F));
        assertEquals(0.0F, BeaconHealth.afterDamage(50.0F, 75.0F));
        assertEquals(50.0F, BeaconHealth.afterDamage(50.0F, 0.0F));
    }

    @Test
    void maxHealthReductionClampsCurrentHealthWithoutHealing() {
        assertEquals(50.0F, BeaconHealth.clamp(75.0F, 50.0F));
        assertEquals(75.0F, BeaconHealth.clamp(75.0F, 100.0F));
        assertEquals(0.0F, BeaconHealth.clamp(-1.0F, 50.0F));
    }
}
