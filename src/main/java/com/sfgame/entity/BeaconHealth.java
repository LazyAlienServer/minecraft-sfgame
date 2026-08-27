package com.sfgame.entity;

public final class BeaconHealth {
    private BeaconHealth() { }

    public static float afterDamage(float current, float amount) {
        return amount <= 0.0F ? current : Math.max(0.0F, current - amount);
    }

    public static float clamp(float value, float max) {
        return Math.max(0.0F, max > 0.0F ? Math.min(value, max) : value);
    }

    /**
     * Returns the vanilla destroy-stage index for the current health, or
     * {@code -1} while the beacon is still at full health.
     */
    public static int damageStage(float current, float max) {
        if (!Float.isFinite(current) || !Float.isFinite(max) || max <= 0.0F || current >= max) return -1;
        float damageRatio = (max - Math.max(0.0F, current)) / max;
        return Math.min(9, (int) (damageRatio * 10.0F));
    }
}
