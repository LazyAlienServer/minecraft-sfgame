package com.sfgame.data;

import com.sfgame.game.GameModeRegistry;
import net.minecraft.nbt.CompoundTag;

public final class MatchRules {
    public static final int DEFAULT_MAX_PLAYERS = 10;
    public static final int DEFAULT_SCORE_LIMIT = 50;
    public static final int DEFAULT_DOMINATION_SCORE_LIMIT = 100;
    public static final int DEFAULT_TIME_LIMIT_SECONDS = 600;
    public static final int DEFAULT_START_COUNTDOWN_SECONDS = 5;
    public static final int DEFAULT_RESPAWN_SECONDS = 5;
    public static final int DEFAULT_RESPAWN_PROTECTION_SECONDS = 3;
    public static final int DEFAULT_RESULT_SECONDS = 8;

    private final String modeId;
    private int maxPlayers;
    private int scoreLimit;
    private int timeLimitSeconds;
    private int startCountdownSeconds;
    private int respawnSeconds;
    private int respawnProtectionSeconds;
    private int resultSeconds;
    private int captureTimeSeconds;
    private boolean captureUsePlayerDifference;
    private double captureDifferenceCoefficient;
    private int captureMaxMultiplier;
    private int scoreIntervalSeconds;
    private int scorePerPoint;
    private int syncHoldSeconds;

    public MatchRules() { this(GameModeRegistry.TEAM_DEATHMATCH); }
    public MatchRules(String modeId) { this.modeId = modeId; reset(); }
    public String modeId() { return modeId; }
    public int maxPlayers() { return maxPlayers; }
    public int scoreLimit() { return scoreLimit; }
    public int timeLimitSeconds() { return timeLimitSeconds; }
    public int startCountdownSeconds() { return startCountdownSeconds; }
    public int respawnSeconds() { return respawnSeconds; }
    public int respawnProtectionSeconds() { return respawnProtectionSeconds; }
    public int resultSeconds() { return resultSeconds; }
    public int captureTimeSeconds() { return captureTimeSeconds; }
    public boolean captureUsePlayerDifference() { return captureUsePlayerDifference; }
    public double captureDifferenceCoefficient() { return captureDifferenceCoefficient; }
    public int captureMaxMultiplier() { return captureMaxMultiplier; }
    public int scoreIntervalSeconds() { return scoreIntervalSeconds; }
    public int scorePerPoint() { return scorePerPoint; }
    public int syncHoldSeconds() { return syncHoldSeconds; }

    public void maxPlayers(int value) { maxPlayers = clamp(value, 2, 128); }
    public void scoreLimit(int value) { scoreLimit = clamp(value, 1, 10000); }
    public void timeLimitSeconds(int value) { timeLimitSeconds = clamp(value, 30, 86400); }
    public void startCountdownSeconds(int value) { startCountdownSeconds = clamp(value, 0, 60); }
    public void respawnSeconds(int value) { respawnSeconds = clamp(value, 0, 60); }
    public void respawnProtectionSeconds(int value) { respawnProtectionSeconds = clamp(value, 0, 30); }
    public void resultSeconds(int value) { resultSeconds = clamp(value, 1, 60); }
    public void captureTimeSeconds(int value) { captureTimeSeconds = clamp(value, 1, 300); }
    public void captureUsePlayerDifference(boolean value) { captureUsePlayerDifference = value; }
    public void captureDifferenceCoefficient(double value) { captureDifferenceCoefficient = clamp(value, 0.1, 10.0); }
    public void captureMaxMultiplier(int value) { captureMaxMultiplier = clamp(value, 1, 64); }
    public void scoreIntervalSeconds(int value) { scoreIntervalSeconds = clamp(value, 1, 300); }
    public void scorePerPoint(int value) { scorePerPoint = clamp(value, 1, 1000); }
    public void syncHoldSeconds(int value) { syncHoldSeconds = clamp(value, 1, 3600); }

    public void reset() {
        maxPlayers = DEFAULT_MAX_PLAYERS;
        scoreLimit = GameModeRegistry.DOMINATION.equals(modeId) ? DEFAULT_DOMINATION_SCORE_LIMIT : DEFAULT_SCORE_LIMIT;
        timeLimitSeconds = DEFAULT_TIME_LIMIT_SECONDS;
        startCountdownSeconds = DEFAULT_START_COUNTDOWN_SECONDS;
        respawnSeconds = DEFAULT_RESPAWN_SECONDS;
        respawnProtectionSeconds = DEFAULT_RESPAWN_PROTECTION_SECONDS;
        resultSeconds = DEFAULT_RESULT_SECONDS;
        captureTimeSeconds = 10;
        captureUsePlayerDifference = true;
        captureDifferenceCoefficient = 1.0;
        captureMaxMultiplier = 4;
        scoreIntervalSeconds = 5;
        scorePerPoint = 1;
        syncHoldSeconds = 45;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MaxPlayers", maxPlayers); tag.putInt("ScoreLimit", scoreLimit);
        tag.putInt("TimeLimitSeconds", timeLimitSeconds); tag.putInt("StartCountdownSeconds", startCountdownSeconds);
        tag.putInt("RespawnSeconds", respawnSeconds); tag.putInt("RespawnProtectionSeconds", respawnProtectionSeconds);
        tag.putInt("ResultSeconds", resultSeconds); tag.putInt("CaptureTimeSeconds", captureTimeSeconds);
        tag.putBoolean("CaptureUsePlayerDifference", captureUsePlayerDifference);
        tag.putDouble("CaptureDifferenceCoefficient", captureDifferenceCoefficient);
        tag.putInt("CaptureMaxMultiplier", captureMaxMultiplier); tag.putInt("ScoreIntervalSeconds", scoreIntervalSeconds);
        tag.putInt("ScorePerPoint", scorePerPoint); tag.putInt("SyncHoldSeconds", syncHoldSeconds);
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
        if (tag.contains("CaptureTimeSeconds")) captureTimeSeconds(tag.getInt("CaptureTimeSeconds"));
        if (tag.contains("CaptureUsePlayerDifference")) captureUsePlayerDifference(tag.getBoolean("CaptureUsePlayerDifference"));
        if (tag.contains("CaptureDifferenceCoefficient")) captureDifferenceCoefficient(tag.getDouble("CaptureDifferenceCoefficient"));
        if (tag.contains("CaptureMaxMultiplier")) captureMaxMultiplier(tag.getInt("CaptureMaxMultiplier"));
        if (tag.contains("ScoreIntervalSeconds")) scoreIntervalSeconds(tag.getInt("ScoreIntervalSeconds"));
        if (tag.contains("ScorePerPoint")) scorePerPoint(tag.getInt("ScorePerPoint"));
        if (tag.contains("SyncHoldSeconds")) syncHoldSeconds(tag.getInt("SyncHoldSeconds"));
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
