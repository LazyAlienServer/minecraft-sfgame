package com.sfgame.classsystem;

public final class ItemDefinition {
    private String item = "minecraft:air";
    private int count = 1;
    private String nbt = "";

    public String item() { return item; }
    public int count() { return Math.max(1, count); }
    public String nbt() { return nbt == null ? "" : nbt; }
}

