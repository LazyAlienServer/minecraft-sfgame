package com.sfgame.game;

import net.minecraft.ChatFormatting;

import java.util.List;

public enum TeamSide {
    RED("red", ChatFormatting.RED),
    BLUE("blue", ChatFormatting.BLUE),
    YELLOW("yellow", ChatFormatting.YELLOW),
    GREEN("green", ChatFormatting.GREEN),
    NONE("none", ChatFormatting.GRAY);

    public static final List<TeamSide> PLAYABLE = List.of(RED, BLUE, YELLOW, GREEN);

    private final String id;
    private final ChatFormatting color;

    TeamSide(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public String id() { return id; }
    public ChatFormatting color() { return color; }

    public static TeamSide fromId(String id) {
        if (id == null) return NONE;
        return PLAYABLE.stream().filter(side -> side.id.equalsIgnoreCase(id)).findFirst().orElse(NONE);
    }
}
