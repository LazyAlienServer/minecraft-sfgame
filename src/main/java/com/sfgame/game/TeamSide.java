package com.sfgame.game;

public enum TeamSide {
    RED,
    BLUE,
    NONE;

    public TeamSide opponent() {
        return this == RED ? BLUE : this == BLUE ? RED : NONE;
    }
}

