package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;

public final class MatchRules {
    public static final int DEFAULT_MAX_PLAYERS = 10;
    public static final int DEFAULT_SCORE_LIMIT = 50;
    public static final int DEFAULT_TIME_LIMIT_SECONDS = 600;
    public static final int DEFAULT_START_COUNTDOWN_SECONDS = 5;
    public static final int DEFAULT_RESPAWN_SECONDS = 5;
    public static final int DEFAULT_RESPAWN_PROTECTION_SECONDS = 3;
    public static final int DEFAULT_RESULT_SECONDS = 8;

    private int maxPlayers = DEFAULT_MAX_PLAYERS;
    private int scoreLimit = DEFAULT_SCORE_LIMIT;
    private int timeLimitSeconds = DEFAULT_TIME_LIMIT_SECONDS;
    private int startCountdownSeconds = DEFAULT_START_COUNTDOWN_SECONDS;
    private int respawnSeconds = DEFAULT_RESPAWN_SECONDS;
    private int respawnProtectionSeconds = DEFAULT_RESPAWN_PROTECTION_SECONDS;
    private int resultSeconds = DEFAULT_RESULT_SECONDS;

    public int maxPlayers() { return maxPlayers; }
    public int scoreLimit() { return scoreLimit; }
    public int timeLimitSeconds() { return timeLimitSeconds; }
    public int startCountdownSeconds() { return startCountdownSeconds; }
    public int respawnSeconds() { return respawnSeconds; }
    public int respawnProtectionSeconds() { return respawnProtectionSeconds; }
    public int resultSeconds() { return resultSeconds; }

    public void maxPlayers(int value) { maxPlayers = clamp(value, 2, 128); }
    public void scoreLimit(int value) { scoreLimit = clamp(value, 1, 10000); }
    public void timeLimitSeconds(int value) { timeLimitSeconds = clamp(value, 30, 86400); }
    public void startCountdownSeconds(int value) { startCountdownSeconds = clamp(value, 0, 60); }
    public void respawnSeconds(int value) { respawnSeconds = clamp(value, 0, 60); }
    public void respawnProtectionSeconds(int value) { respawnProtectionSeconds = clamp(value, 0, 30); }
    public void resultSeconds(int value) { resultSeconds = clamp(value, 1, 60); }

    public void reset() {
        maxPlayers = DEFAULT_MAX_PLAYERS;
        scoreLimit = DEFAULT_SCORE_LIMIT;
        timeLimitSeconds = DEFAULT_TIME_LIMIT_SECONDS;
        startCountdownSeconds = DEFAULT_START_COUNTDOWN_SECONDS;
        respawnSeconds = DEFAULT_RESPAWN_SECONDS;
        respawnProtectionSeconds = DEFAULT_RESPAWN_PROTECTION_SECONDS;
        resultSeconds = DEFAULT_RESULT_SECONDS;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MaxPlayers", maxPlayers);
        tag.putInt("ScoreLimit", scoreLimit);
        tag.putInt("TimeLimitSeconds", timeLimitSeconds);
        tag.putInt("StartCountdownSeconds", startCountdownSeconds);
        tag.putInt("RespawnSeconds", respawnSeconds);
        tag.putInt("RespawnProtectionSeconds", respawnProtectionSeconds);
        tag.putInt("ResultSeconds", resultSeconds);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("MaxPlayers")) maxPlayers(tag.getInt("MaxPlayers"));
        if (tag.contains("ScoreLimit")) scoreLimit(tag.getInt("ScoreLimit"));
        if (tag.contains("TimeLimitSeconds")) timeLimitSeconds(tag.getInt("TimeLimitSeconds"));
        if (tag.contains("StartCountdownSeconds")) startCountdownSeconds(tag.getInt("StartCountdownSeconds"));
        if (tag.contains("RespawnSeconds")) respawnSeconds(tag.getInt("RespawnSeconds"));
        if (tag.contains("RespawnProtectionSeconds")) respawnProtectionSeconds(tag.getInt("RespawnProtectionSeconds"));
        if (tag.contains("ResultSeconds")) resultSeconds(tag.getInt("ResultSeconds"));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

