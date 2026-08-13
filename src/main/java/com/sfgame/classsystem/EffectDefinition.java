package com.sfgame.classsystem;

public final class EffectDefinition {
    private String id = "minecraft:speed";
    private int amplifier;
    private int durationSeconds = 3600;
    private boolean ambient;
    private boolean visible = true;

    public String id() { return id; }
    public int amplifier() { return Math.max(0, amplifier); }
    public int durationTicks() { return Math.max(1, durationSeconds) * 20; }
    public boolean ambient() { return ambient; }
    public boolean visible() { return visible; }
}

