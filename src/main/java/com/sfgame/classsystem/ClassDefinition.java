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
    private String gunId = "";
    private String ammoId = "";
    private int initialMagazine;
    private int reserveAmmo;
    private String fireMode = "UNKNOWN";
    private Map<String, String> attachments = new LinkedHashMap<>();
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
    public String gunId() { return gunId; }
    public String ammoId() { return ammoId; }
    public int initialMagazine() { return Math.max(0, initialMagazine); }
    public int reserveAmmo() { return Math.max(0, reserveAmmo); }
    public String fireMode() { return fireMode == null ? "UNKNOWN" : fireMode.toUpperCase(); }
    public Map<String, String> attachments() { return attachments == null ? Map.of() : attachments; }
    public List<ItemDefinition> inventory() { return inventory == null ? List.of() : inventory; }
    public Map<String, ItemDefinition> armor() { return armor == null ? Map.of() : armor; }
    public ItemDefinition offhand() { return offhand; }
    public List<EffectDefinition> effects() { return effects == null ? List.of() : effects; }
    public boolean allowDrop() { return allowDrop; }
}

