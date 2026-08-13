package com.sfgame.game;

public record ModeTickResult(boolean finished, TeamSide winner) {
    public static final ModeTickResult CONTINUE = new ModeTickResult(false, TeamSide.NONE);
    public static ModeTickResult finish(TeamSide winner) { return new ModeTickResult(true, winner); }
}
