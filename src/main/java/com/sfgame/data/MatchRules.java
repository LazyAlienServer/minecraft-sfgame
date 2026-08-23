package com.sfgame.data;

import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

public final class MatchRules {
    public static final int DEFAULT_MAX_PLAYERS = 10;
    public static final int DEFAULT_SCORE_LIMIT = 50;
    public static final int DEFAULT_DOMINATION_SCORE_LIMIT = 100;
    public static final int DEFAULT_CTF_SCORE_LIMIT = 3;
    public static final int DEFAULT_TIME_LIMIT_SECONDS = 600;
    public static final int DEFAULT_START_COUNTDOWN_SECONDS = 5;
    public static final int DEFAULT_RESPAWN_SECONDS = 5;
    public static final int DEFAULT_BREAKTHROUGH_RESPAWN_SECONDS = 10;
    public static final int DEFAULT_RESPAWN_PROTECTION_SECONDS = 3;
    public static final int DEFAULT_RESULT_SECONDS = 20;
    public static final int DEFAULT_MAP_RESTORE_DELAY_TICKS = 0;
    public static final int DEFAULT_MAP_RESTORE_TARGET_TICK_MILLIS = 40;
    public static final int DEFAULT_MAP_RESTORE_MAX_PARTITIONS_PER_TICK = 8;

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
    private int attackerTickets;
    private int sectorTransitionSeconds;
    private int captainVoteSeconds;
    private int captainReplacementVoteSeconds;
    private boolean attackerCaptainGlowing;
    private boolean mapBlockBreaking;
    private Set<String> mapBlockAllowlist;
    private BlockAllowlist.Matcher mapBlockMatcher;
    private MapSnapshotMode mapSnapshotMode;
    private int mapRestorePartitionDelayTicks;
    private boolean mapRestoreAdaptiveThrottling;
    private int mapRestoreTargetTickMillis;
    private int mapRestoreMaxPartitionsPerTick;
    private double attackerCaptainCaptureWeight;
    private double defenderCaptureWeight;
    private int ctfFlagReturnSeconds;
    private int ctfHomeCaptureTimeSeconds;
    private PointActivationStrategy dominationStrategy;
    private BreakthroughVariant breakthroughVariant;
    private int breakthroughLegs;
    private TeamSide breakthroughAttacker;
    private TeamSide breakthroughDefender;
    private CtfVariant ctfVariant;
    private TeamSide ctfAttacker;
    private TeamSide ctfDefender;
    private CarrierRestriction ctfCarrierRestriction;

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
    public int attackerTickets() { return attackerTickets; }
    public int sectorTransitionSeconds() { return sectorTransitionSeconds; }
    public int captainVoteSeconds() { return captainVoteSeconds; }
    public int captainReplacementVoteSeconds() { return captainReplacementVoteSeconds; }
    public boolean attackerCaptainGlowing() { return attackerCaptainGlowing; }
    public boolean mapBlockBreaking() { return mapBlockBreaking; }
    public Set<String> mapBlockAllowlist() { return mapBlockAllowlist; }
    public boolean allowsMapBlock(net.minecraft.world.level.block.state.BlockState state) {
        return mapBlockMatcher.matches(state);
    }
    public MapSnapshotMode mapSnapshotMode() { return mapSnapshotMode; }
    public int mapRestorePartitionDelayTicks() { return mapRestorePartitionDelayTicks; }
    public boolean mapRestoreAdaptiveThrottling() { return mapRestoreAdaptiveThrottling; }
    public int mapRestoreTargetTickMillis() { return mapRestoreTargetTickMillis; }
    public int mapRestoreMaxPartitionsPerTick() { return mapRestoreMaxPartitionsPerTick; }
    public double attackerCaptainCaptureWeight() { return attackerCaptainCaptureWeight; }
    public double defenderCaptureWeight() { return defenderCaptureWeight; }
    public int ctfFlagReturnSeconds() { return ctfFlagReturnSeconds; }
    public int ctfHomeCaptureTimeSeconds() { return ctfHomeCaptureTimeSeconds; }
    public PointActivationStrategy dominationStrategy() { return dominationStrategy; }
    public BreakthroughVariant breakthroughVariant() { return breakthroughVariant; }
    public int breakthroughLegs() { return breakthroughLegs; }
    public TeamSide breakthroughAttacker() { return breakthroughAttacker; }
    public TeamSide breakthroughDefender() { return breakthroughDefender; }
    public CtfVariant ctfVariant() { return ctfVariant; }
    public TeamSide ctfAttacker() { return ctfAttacker; }
    public TeamSide ctfDefender() { return ctfDefender; }
    public CarrierRestriction ctfCarrierRestriction() { return ctfCarrierRestriction; }

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
    public void attackerTickets(int value) { attackerTickets = clamp(value, 1, 10000); }
    public void sectorTransitionSeconds(int value) { sectorTransitionSeconds = clamp(value, 0, 60); }
    public void captainVoteSeconds(int value) { captainVoteSeconds = clamp(value, 1, 120); }
    public void captainReplacementVoteSeconds(int value) { captainReplacementVoteSeconds = clamp(value, 1, 120); }
    public void attackerCaptainGlowing(boolean value) { attackerCaptainGlowing = value; }
    public void mapBlockBreaking(boolean value) { mapBlockBreaking = value; }
    public void mapBlockAllowlist(java.util.Collection<String> value) {
        mapBlockAllowlist = BlockAllowlist.normalizeAll(value);
        mapBlockMatcher = BlockAllowlist.compile(mapBlockAllowlist);
    }
    public void mapSnapshotMode(MapSnapshotMode value) {
        mapSnapshotMode = value == null ? MapSnapshotMode.ALLOWLIST : value;
    }
    public void mapRestorePartitionDelayTicks(int value) { mapRestorePartitionDelayTicks = clamp(value, 0, 200); }
    public void mapRestoreAdaptiveThrottling(boolean value) { mapRestoreAdaptiveThrottling = value; }
    public void mapRestoreTargetTickMillis(int value) { mapRestoreTargetTickMillis = clamp(value, 10, 50); }
    public void mapRestoreMaxPartitionsPerTick(int value) { mapRestoreMaxPartitionsPerTick = clamp(value, 1, 64); }
    public void attackerCaptainCaptureWeight(double value) { attackerCaptainCaptureWeight = clamp(value, 1.0, 10.0); }
    public void defenderCaptureWeight(double value) { defenderCaptureWeight = clamp(value, 0.1, 10.0); }
    public void ctfFlagReturnSeconds(int value) { ctfFlagReturnSeconds = clamp(value, 5, 600); }
    public void ctfHomeCaptureTimeSeconds(int value) { ctfHomeCaptureTimeSeconds = clamp(value, 1, 600); }
    public void dominationStrategy(PointActivationStrategy value) {
        dominationStrategy = value == null ? PointActivationStrategy.ASYNC : value;
    }
    public void breakthroughVariant(BreakthroughVariant value) {
        breakthroughVariant = value == null ? BreakthroughVariant.NORMAL : value;
    }
    public void breakthroughLegs(int value) { breakthroughLegs = clamp(value, 1, 2); }
    public void breakthroughAttacker(TeamSide value) { breakthroughAttacker = playable(value, TeamSide.RED); }
    public void breakthroughDefender(TeamSide value) { breakthroughDefender = playable(value, TeamSide.BLUE); }
    public void ctfVariant(CtfVariant value) { ctfVariant = value == null ? CtfVariant.CLASSIC : value; }
    public void ctfAttacker(TeamSide value) { ctfAttacker = playable(value, TeamSide.RED); }
    public void ctfDefender(TeamSide value) { ctfDefender = playable(value, TeamSide.BLUE); }
    public void ctfCarrierRestriction(CarrierRestriction value) {
        ctfCarrierRestriction = value == null ? CarrierRestriction.NORMAL : value;
    }

    public void reset() {
        maxPlayers = DEFAULT_MAX_PLAYERS;
        scoreLimit = GameModeRegistry.DOMINATION.equals(modeId) ? DEFAULT_DOMINATION_SCORE_LIMIT
                : GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId) ? DEFAULT_CTF_SCORE_LIMIT : DEFAULT_SCORE_LIMIT;
        timeLimitSeconds = DEFAULT_TIME_LIMIT_SECONDS;
        startCountdownSeconds = DEFAULT_START_COUNTDOWN_SECONDS;
        respawnSeconds = GameModeRegistry.BREAKTHROUGH.equals(modeId)
                ? DEFAULT_BREAKTHROUGH_RESPAWN_SECONDS : DEFAULT_RESPAWN_SECONDS;
        respawnProtectionSeconds = DEFAULT_RESPAWN_PROTECTION_SECONDS;
        resultSeconds = DEFAULT_RESULT_SECONDS;
        captureTimeSeconds = 10;
        captureUsePlayerDifference = true;
        captureDifferenceCoefficient = 1.0;
        captureMaxMultiplier = 4;
        scoreIntervalSeconds = 1;
        scorePerPoint = 1;
        syncHoldSeconds = 45;
        attackerTickets = 100;
        sectorTransitionSeconds = 10;
        captainVoteSeconds = 15;
        captainReplacementVoteSeconds = 10;
        attackerCaptainGlowing = true;
        mapBlockBreaking = false;
        mapBlockAllowlist(Set.of());
        mapSnapshotMode = MapSnapshotMode.ALLOWLIST;
        mapRestorePartitionDelayTicks = DEFAULT_MAP_RESTORE_DELAY_TICKS;
        mapRestoreAdaptiveThrottling = true;
        mapRestoreTargetTickMillis = DEFAULT_MAP_RESTORE_TARGET_TICK_MILLIS;
        mapRestoreMaxPartitionsPerTick = DEFAULT_MAP_RESTORE_MAX_PARTITIONS_PER_TICK;
        attackerCaptainCaptureWeight = 2.0;
        defenderCaptureWeight = 1.4;
        ctfFlagReturnSeconds = 30;
        ctfHomeCaptureTimeSeconds = 15;
        dominationStrategy = PointActivationStrategy.ASYNC;
        breakthroughVariant = BreakthroughVariant.NORMAL;
        breakthroughLegs = 1;
        breakthroughAttacker = TeamSide.RED;
        breakthroughDefender = TeamSide.BLUE;
        ctfVariant = CtfVariant.CLASSIC;
        ctfAttacker = TeamSide.RED;
        ctfDefender = TeamSide.BLUE;
        ctfCarrierRestriction = CarrierRestriction.NORMAL;
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
        tag.putInt("AttackerTickets", attackerTickets); tag.putInt("SectorTransitionSeconds", sectorTransitionSeconds);
        tag.putInt("CaptainVoteSeconds", captainVoteSeconds); tag.putInt("CaptainReplacementVoteSeconds", captainReplacementVoteSeconds);
        tag.putBoolean("AttackerCaptainGlowing", attackerCaptainGlowing);
        tag.putBoolean("MapBlockBreaking", mapBlockBreaking);
        ListTag allowlist = new ListTag();
        mapBlockAllowlist.forEach(value -> allowlist.add(net.minecraft.nbt.StringTag.valueOf(value)));
        tag.put("MapBlockAllowlist", allowlist);
        tag.putString("MapSnapshotMode", mapSnapshotMode.id());
        tag.putInt("MapRestorePartitionDelayTicks", mapRestorePartitionDelayTicks);
        tag.putBoolean("MapRestoreAdaptiveThrottling", mapRestoreAdaptiveThrottling);
        tag.putInt("MapRestoreTargetTickMillis", mapRestoreTargetTickMillis);
        tag.putInt("MapRestoreMaxPartitionsPerTick", mapRestoreMaxPartitionsPerTick);
        tag.putDouble("AttackerCaptainCaptureWeight", attackerCaptainCaptureWeight);
        tag.putDouble("DefenderCaptureWeight", defenderCaptureWeight);
        tag.putInt("CtfFlagReturnSeconds", ctfFlagReturnSeconds);
        tag.putInt("CtfHomeCaptureTimeSeconds", ctfHomeCaptureTimeSeconds);
        tag.putString("DominationStrategy", dominationStrategy.name().toLowerCase(java.util.Locale.ROOT));
        tag.putString("BreakthroughVariant", breakthroughVariant.name().toLowerCase(java.util.Locale.ROOT));
        tag.putInt("BreakthroughLegs", breakthroughLegs);
        tag.putString("BreakthroughAttacker", breakthroughAttacker.id());
        tag.putString("BreakthroughDefender", breakthroughDefender.id());
        tag.putString("CtfVariant", ctfVariant.id());
        tag.putString("CtfAttacker", ctfAttacker.id());
        tag.putString("CtfDefender", ctfDefender.id());
        tag.putString("CtfCarrierRestriction", ctfCarrierRestriction.id());
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
        if (tag.contains("AttackerTickets")) attackerTickets(tag.getInt("AttackerTickets"));
        if (tag.contains("SectorTransitionSeconds")) sectorTransitionSeconds(tag.getInt("SectorTransitionSeconds"));
        if (tag.contains("CaptainVoteSeconds")) captainVoteSeconds(tag.getInt("CaptainVoteSeconds"));
        if (tag.contains("CaptainReplacementVoteSeconds")) captainReplacementVoteSeconds(tag.getInt("CaptainReplacementVoteSeconds"));
        if (tag.contains("AttackerCaptainGlowing")) attackerCaptainGlowing(tag.getBoolean("AttackerCaptainGlowing"));
        if (tag.contains("MapBlockBreaking")) mapBlockBreaking(tag.getBoolean("MapBlockBreaking"));
        if (tag.contains("MapBlockAllowlist", Tag.TAG_LIST)) {
            ListTag allowlist = tag.getList("MapBlockAllowlist", Tag.TAG_STRING);
            java.util.List<String> values = new java.util.ArrayList<>();
            for (int i = 0; i < allowlist.size(); i++) values.add(allowlist.getString(i));
            mapBlockAllowlist(values);
        }
        if (tag.contains("MapSnapshotMode")) mapSnapshotMode(MapSnapshotMode.byId(tag.getString("MapSnapshotMode")));
        if (tag.contains("MapRestorePartitionDelayTicks")) mapRestorePartitionDelayTicks(tag.getInt("MapRestorePartitionDelayTicks"));
        if (tag.contains("MapRestoreAdaptiveThrottling")) mapRestoreAdaptiveThrottling(tag.getBoolean("MapRestoreAdaptiveThrottling"));
        if (tag.contains("MapRestoreTargetTickMillis")) mapRestoreTargetTickMillis(tag.getInt("MapRestoreTargetTickMillis"));
        if (tag.contains("MapRestoreMaxPartitionsPerTick")) mapRestoreMaxPartitionsPerTick(tag.getInt("MapRestoreMaxPartitionsPerTick"));
        if (tag.contains("AttackerCaptainCaptureWeight")) attackerCaptainCaptureWeight(tag.getDouble("AttackerCaptainCaptureWeight"));
        if (tag.contains("DefenderCaptureWeight")) defenderCaptureWeight(tag.getDouble("DefenderCaptureWeight"));
        if (tag.contains("CtfFlagReturnSeconds")) ctfFlagReturnSeconds(tag.getInt("CtfFlagReturnSeconds"));
        if (tag.contains("CtfHomeCaptureTimeSeconds")) ctfHomeCaptureTimeSeconds(tag.getInt("CtfHomeCaptureTimeSeconds"));
        if (tag.contains("DominationStrategy")) dominationStrategy(PointActivationStrategy.parse(tag.getString("DominationStrategy")));
        if (tag.contains("BreakthroughVariant")) breakthroughVariant(BreakthroughVariant.valueOf(tag.getString("BreakthroughVariant").toUpperCase(java.util.Locale.ROOT)));
        if (tag.contains("BreakthroughLegs")) breakthroughLegs(tag.getInt("BreakthroughLegs"));
        if (tag.contains("BreakthroughAttacker")) breakthroughAttacker(TeamSide.fromId(tag.getString("BreakthroughAttacker")));
        if (tag.contains("BreakthroughDefender")) breakthroughDefender(TeamSide.fromId(tag.getString("BreakthroughDefender")));
        if (tag.contains("CtfVariant")) ctfVariant(CtfVariant.fromId(tag.getString("CtfVariant")));
        if (tag.contains("CtfAttacker")) ctfAttacker(TeamSide.fromId(tag.getString("CtfAttacker")));
        if (tag.contains("CtfDefender")) ctfDefender(TeamSide.fromId(tag.getString("CtfDefender")));
        if (tag.contains("CtfCarrierRestriction")) ctfCarrierRestriction(CarrierRestriction.fromId(tag.getString("CtfCarrierRestriction")));
    }

    public MatchRules copy() {
        MatchRules copy = new MatchRules(modeId);
        copy.load(save());
        return copy;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static TeamSide playable(TeamSide value, TeamSide fallback) {
        return value == null || value == TeamSide.NONE ? fallback : value;
    }
}
