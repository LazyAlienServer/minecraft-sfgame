package com.sfgame.entity;

public final class BeaconHealth {
    private BeaconHealth() { }

    public static float afterDamage(float current, float amount) {
        return amount <= 0.0F ? current : Math.max(0.0F, current - amount);
    }

    public static float clamp(float value, float max) {
        return Math.max(0.0F, max > 0.0F ? Math.min(value, max) : value);
    }
}
