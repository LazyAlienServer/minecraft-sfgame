package com.sfgame.classsystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClassDefinition {
    private String id = "";
    private String displayName = "";
    private String description = "";
    private String icon = "minecraft:paper";
    private String iconRender = "item";
    private String iconTexture = "";
    private double maxHealth = 20.0;
    private double movementSpeedMultiplier = 1.0;
    private List<ItemDefinition> inventory = new ArrayList<>();
    private Map<String, ItemDefinition> armor = new LinkedHashMap<>();
    private ItemDefinition offhand;
    private List<EffectDefinition> effects = new ArrayList<>();
    private boolean allowDrop;

    public String id() { return id; }
    public String displayName() { return displayName == null || displayName.isBlank() ? id : displayName; }
    public String description() { return description == null ? "" : description; }
    public String icon() { return icon == null ? "minecraft:paper" : icon; }
    public String iconRender() {
        String value = iconRender == null ? "" : iconRender.trim().toLowerCase(Locale.ROOT);
        return "hud".equals(value) || "png".equals(value) ? value : "item";
    }
    public String iconTexture() { return iconTexture == null ? "" : iconTexture.trim(); }
    public double maxHealth() { return Math.max(1.0, Math.min(2048.0, maxHealth)); }
    public double movementSpeedMultiplier() { return Math.max(0.1, Math.min(5.0, movementSpeedMultiplier)); }
    public String gunId() {
        for (ItemDefinition item : inventory()) if (item != null && item.isGun()) return item.gunId();
        return "";
    }
    public int reserveAmmo() {
        long total = 0;
        for (ItemDefinition item : inventory()) {
            if (item != null && item.isAmmoBox()) total += item.ammoCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
    public List<ItemDefinition> inventory() { return inventory == null ? List.of() : inventory; }
    public Map<String, ItemDefinition> armor() { return armor == null ? Map.of() : armor; }
    public ItemDefinition offhand() { return offhand; }
    public List<EffectDefinition> effects() { return effects == null ? List.of() : effects; }
    public boolean allowDrop() { return allowDrop; }
}

