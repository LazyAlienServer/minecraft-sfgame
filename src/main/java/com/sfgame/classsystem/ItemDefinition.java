package com.sfgame.classsystem;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ItemDefinition {
    private String type = "item";
    private String item = "minecraft:air";
    private int count = 1;
    private int slot = -1;
    private String nbt = "";
    private String gunId = "";
    private int initialMagazine;
    private String fireMode = "UNKNOWN";
    private Map<String, String> attachments = new LinkedHashMap<>();
    private String ammoId = "";
    private int ammoCount;

    public String type() {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "gun" -> "gun";
            case "ammobox", "ammo_box" -> "ammo_box";
            default -> "item";
        };
    }
    public String item() { return item == null ? "minecraft:air" : item; }
    public int count() { return Math.max(1, count); }
    public int slot() { return slot; }
    public String nbt() { return nbt == null ? "" : nbt; }
    public String gunId() { return gunId == null ? "" : gunId; }
    public int initialMagazine() { return Math.max(0, initialMagazine); }
    public String fireMode() { return fireMode == null ? "UNKNOWN" : fireMode.toUpperCase(Locale.ROOT); }
    public Map<String, String> attachments() { return attachments == null ? Map.of() : attachments; }
    public String ammoId() { return ammoId == null ? "" : ammoId; }
    public int ammoCount() { return Math.max(0, ammoCount); }
    public boolean isGun() { return "gun".equals(type()); }
    public boolean isAmmoBox() { return "ammo_box".equals(type()); }
}

