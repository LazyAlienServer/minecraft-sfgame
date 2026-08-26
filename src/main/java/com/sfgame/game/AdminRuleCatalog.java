package com.sfgame.game;

import com.sfgame.data.MatchRules;
import com.sfgame.data.MapSnapshotMode;
import com.sfgame.data.BreakthroughVariant;
import com.sfgame.data.CarrierRestriction;
import com.sfgame.data.CtfVariant;
import com.sfgame.data.PointActivationStrategy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Server-authoritative metadata used by the administrator rule editor.
 *
 * <p>The command implementation remains the authority for applying values;
 * this catalog only describes the controls and whether a setting is safe to
 * change during a live round.  Keeping the metadata server side means a
 * modified client cannot expose or write a rule that is unavailable in the
 * selected mode.</p>
 */
public final class AdminRuleCatalog {
    public enum ValueType { INTEGER, DECIMAL, BOOLEAN, ENUM }

    public record Definition(String key, ValueType type, double minimum, double maximum,
                             boolean hotReload, List<String> modes) {
        public boolean supports(String modeId) {
            return modes.isEmpty() || modes.contains(modeId);
        }

        public boolean exists() {
            try {
                MatchRules.class.getMethod(key);
                return true;
            } catch (NoSuchMethodException ignored) {
                return false;
            }
        }

        public String value(MatchRules rules) {
            try {
                Method getter = MatchRules.class.getMethod(key);
                Object result = getter.invoke(rules);
                if (result instanceof MapSnapshotMode mode) return mode.id();
                if (result instanceof PointActivationStrategy strategy) return strategy.name().toLowerCase(Locale.ROOT);
                if (result instanceof BreakthroughVariant variant) return variant.name().toLowerCase(Locale.ROOT);
                if (result instanceof CtfVariant variant) return variant.id();
                if (result instanceof CarrierRestriction restriction) return restriction.id();
                if (result instanceof TeamSide side) return side.id();
                return result == null ? "" : result.toString();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not read rule " + key, exception);
            }
        }
    }

    private static final List<String> CAPTURE_MODES = List.of(
            GameModeRegistry.DOMINATION, GameModeRegistry.BREAKTHROUGH, GameModeRegistry.CAPTURE_THE_FLAG);
    private static final List<Definition> DEFINITIONS = List.of(
            // Live-safe common rules.
            integer("maxPlayers", MatchRules.UNLIMITED_PLAYERS, MatchRules.MAX_PLAYER_LIMIT, true),
            integer("scoreLimit", 1, 10_000, true),
            integer("timeLimitSeconds", MatchRules.UNLIMITED_TIME_SECONDS, 86_400, true),
            bool("showUnlimitedTime", false),
            integer("respawnSeconds", 0, 60, true),
            integer("respawnProtectionSeconds", 0, 30, true),
            bool("mapBlockBreaking", false),
            enumeration("mapSnapshotMode", false),

            // Capture rules shared by domination, breakthrough and CTF territory.
            integer("captureTimeSeconds", 1, 300, true, CAPTURE_MODES),
            bool("captureUsePlayerDifference", true, CAPTURE_MODES),
            decimal("captureDifferenceCoefficient", 0.1, 10.0, true, CAPTURE_MODES),
            integer("captureMaxMultiplier", 1, 64, true, CAPTURE_MODES),
            integer("squadMaxMembers", MatchRules.MIN_SQUAD_MAX_MEMBERS, MatchRules.MAX_SQUAD_MAX_MEMBERS, true, CAPTURE_MODES),
            integer("respawnBeaconHealth", MatchRules.MIN_RESPAWN_BEACON_HEALTH, MatchRules.MAX_RESPAWN_BEACON_HEALTH, true, CAPTURE_MODES),
            // Economy.
            integer("killCurrency", 0, 100_000, true, CAPTURE_MODES),
            bool("economyEnabled", false, CAPTURE_MODES),


            // Domination.
            enumeration("dominationStrategy", false, GameModeRegistry.DOMINATION),
            integer("scoreIntervalSeconds", 1, 300, true, GameModeRegistry.DOMINATION),
            integer("scorePerPoint", 1, 1_000, true, GameModeRegistry.DOMINATION),
            integer("syncHoldSeconds", 1, 3_600, true, GameModeRegistry.DOMINATION),

            // Breakthrough.
            enumeration("breakthroughVariant", false, GameModeRegistry.BREAKTHROUGH),
            integer("breakthroughAttackRounds", 0, 100, false, GameModeRegistry.BREAKTHROUGH),
            integer("breakthroughLegs", 0, 1, false, GameModeRegistry.BREAKTHROUGH),
            enumeration("breakthroughAttacker", false, GameModeRegistry.BREAKTHROUGH),
            enumeration("breakthroughDefender", false, GameModeRegistry.BREAKTHROUGH),
            integer("attackerTickets", MatchRules.UNLIMITED_TICKETS, 10_000, true,
                    GameModeRegistry.BREAKTHROUGH, GameModeRegistry.CAPTURE_THE_FLAG),
            bool("showUnlimitedTickets", false, GameModeRegistry.BREAKTHROUGH, GameModeRegistry.CAPTURE_THE_FLAG),
            integer("sectorTransitionSeconds", 0, 60, true, GameModeRegistry.BREAKTHROUGH),
            integer("captainVoteSeconds", 1, 120, true,
                    GameModeRegistry.DOMINATION, GameModeRegistry.BREAKTHROUGH, GameModeRegistry.CAPTURE_THE_FLAG),
            integer("captainReplacementVoteSeconds", 1, 120, true,
                    GameModeRegistry.DOMINATION, GameModeRegistry.BREAKTHROUGH, GameModeRegistry.CAPTURE_THE_FLAG),
            bool("attackerCaptainGlowing", true, GameModeRegistry.BREAKTHROUGH),
            decimal("attackerCaptainCaptureWeight", 1.0, 10.0, true, GameModeRegistry.BREAKTHROUGH),
            decimal("defenderCaptureWeight", 0.1, 10.0, true, GameModeRegistry.BREAKTHROUGH),

            // CTF.
            enumeration("ctfVariant", false, GameModeRegistry.CAPTURE_THE_FLAG),
            enumeration("ctfAttacker", false, GameModeRegistry.CAPTURE_THE_FLAG),
            enumeration("ctfDefender", false, GameModeRegistry.CAPTURE_THE_FLAG),
            enumeration("ctfCarrierRestriction", false, GameModeRegistry.CAPTURE_THE_FLAG),
            integer("ctfFlagReturnSeconds", 5, 600, true, GameModeRegistry.CAPTURE_THE_FLAG),
            integer("ctfHomeCaptureTimeSeconds", 1, 600, true, GameModeRegistry.CAPTURE_THE_FLAG),
            integer("ctfTerritoryUnlockCurrency", 0, 100_000, true, GameModeRegistry.CAPTURE_THE_FLAG),
            integer("ctfForwardFlagReplantCurrency", 0, 100_000, true, GameModeRegistry.CAPTURE_THE_FLAG),
            integer("ctfForwardFlagCaptureCurrency", 0, 100_000, true, GameModeRegistry.CAPTURE_THE_FLAG),
            integer("ctfHomeFlagCaptureCurrency", 0, 100_000, true, GameModeRegistry.CAPTURE_THE_FLAG),

            // These values define the next state transition and deliberately
            // remain locked while a round is active.
            integer("startCountdownSeconds", 0, 60, false),
            integer("resultSeconds", 1, 60, false),

            // The restore worker reads these values every tick, so an
            // administrator may tune throughput while a large map is loading.
            integer("mapRestorePartitionDelayTicks", 0, 200, true),
            bool("mapRestoreAdaptiveThrottling", true),
            integer("mapRestoreTargetTickMillis", 10, 50, true),
            integer("mapRestoreMaxPartitionsPerTick", 1, 64, true)
    );

    private AdminRuleCatalog() {
    }

    public static List<Definition> forMode(String modeId) {
        List<Definition> result = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            if (definition.supports(modeId) && definition.exists()) result.add(definition);
        }
        return List.copyOf(result);
    }

    public static Optional<Definition> find(String modeId, String key) {
        if (key == null) return Optional.empty();
        String normalized = key.trim();
        return forMode(modeId).stream().filter(definition -> definition.key().equals(normalized)).findFirst();
    }

    public static Object parse(Definition definition, String input) {
        String value = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        return switch (definition.type()) {
            case BOOLEAN -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new IllegalArgumentException("Expected true or false");
                }
                yield Boolean.parseBoolean(value);
            }
            case INTEGER -> {
                int parsed;
                try {
                    parsed = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Expected a whole number");
                }
                boolean invalidPlayerLimit = definition.key().equals("maxPlayers")
                        && parsed != MatchRules.UNLIMITED_PLAYERS
                        && parsed < MatchRules.MIN_PLAYER_LIMIT;
                if (parsed < definition.minimum() || parsed > definition.maximum() || invalidPlayerLimit) {
                    throw rangeError(definition);
                }
                yield parsed;
            }
            case DECIMAL -> {
                double parsed;
                try {
                    parsed = Double.parseDouble(value);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Expected a decimal number");
                }
                if (!Double.isFinite(parsed) || parsed < definition.minimum() || parsed > definition.maximum()) {
                    throw rangeError(definition);
                }
                yield parsed;
            }
            case ENUM -> {
                List<String> values = enumValues(definition.key());
                if (!values.contains(value)) {
                    throw new IllegalArgumentException("Expected one of: " + String.join(", ", values));
                }
                yield value;
            }
        };
    }

    public static List<String> enumValues(String key) {
        return switch (key) {
            case "mapSnapshotMode" -> List.of("allowlist", "full");
            case "dominationStrategy" -> List.of("async", "sync");
            case "breakthroughVariant" -> List.of("normal", "captain");
            case "ctfVariant" -> List.of("classic", "assault", "territory");
            case "ctfCarrierRestriction" -> List.of("normal", "movement_limited", "no_weapons");
            case "breakthroughAttacker", "breakthroughDefender", "ctfAttacker", "ctfDefender" ->
                    TeamSide.PLAYABLE.stream().map(TeamSide::id).toList();
            default -> List.of();
        };
    }

    private static IllegalArgumentException rangeError(Definition definition) {
        if (definition.key().equals("maxPlayers")) {
            return new IllegalArgumentException("Value must be -1 or between "
                    + MatchRules.MIN_PLAYER_LIMIT + " and " + MatchRules.MAX_PLAYER_LIMIT);
        }
        return new IllegalArgumentException("Value must be between " + format(definition.minimum())
                + " and " + format(definition.maximum()));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static Definition integer(String key, int min, int max, boolean hot, String... modes) {
        return new Definition(key, ValueType.INTEGER, min, max, hot, List.of(modes));
    }

    private static Definition integer(String key, int min, int max, boolean hot, List<String> modes) {
        return new Definition(key, ValueType.INTEGER, min, max, hot, List.copyOf(modes));
    }

    private static Definition decimal(String key, double min, double max, boolean hot, String... modes) {
        return new Definition(key, ValueType.DECIMAL, min, max, hot, List.of(modes));
    }

    private static Definition decimal(String key, double min, double max, boolean hot, List<String> modes) {
        return new Definition(key, ValueType.DECIMAL, min, max, hot, List.copyOf(modes));
    }

    private static Definition bool(String key, boolean hot, String... modes) {
        return new Definition(key, ValueType.BOOLEAN, 0, 1, hot, List.of(modes));
    }

    private static Definition bool(String key, boolean hot, List<String> modes) {
        return new Definition(key, ValueType.BOOLEAN, 0, 1, hot, List.copyOf(modes));
    }

    private static Definition enumeration(String key, boolean hot, String... modes) {
        return new Definition(key, ValueType.ENUM, 0, 0, hot, List.of(modes));
    }
}
