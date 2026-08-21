package com.sfgame.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sfgame.SFGame;
import com.sfgame.data.MatchRules;
import com.sfgame.data.MapSnapshotMode;
import com.sfgame.data.SFGameId;
import com.sfgame.data.SFGameSavedData;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * JSON-backed per-map rule profiles.  Each mode owns one file with a base rule
 * set and optional map scopes.  Map scopes may inherit another scope in the
 * same file; an absent parent or {@code base} inherits the mode base.
 */
public final class RuleConfigRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> COMMON = Set.of("maxPlayers", "scoreLimit", "timeLimitSeconds",
            "startCountdownSeconds", "respawnSeconds", "respawnProtectionSeconds", "resultSeconds", "mapBlockBreaking",
            "mapSnapshotMode",
            "mapRestorePartitionDelayTicks", "mapRestoreAdaptiveThrottling", "mapRestoreTargetTickMillis",
            "mapRestoreMaxPartitionsPerTick");
    private static final Set<String> CAPTURE = Set.of("captureTimeSeconds", "captureUsePlayerDifference",
            "captureDifferenceCoefficient", "captureMaxMultiplier");
    private static final Set<String> DOMINATION = Set.of("scoreIntervalSeconds", "scorePerPoint", "syncHoldSeconds");
    private static final Set<String> BREAKTHROUGH = Set.of("attackerTickets", "sectorTransitionSeconds",
            "captainVoteSeconds", "captainReplacementVoteSeconds", "attackerCaptainGlowing",
            "attackerCaptainCaptureWeight", "defenderCaptureWeight");
    private static final Set<String> CTF = Set.of("attackerTickets", "ctfFlagReturnSeconds", "ctfHomeCaptureTimeSeconds");
    private static final Set<String> BOOLEAN_RULES = Set.of("captureUsePlayerDifference", "attackerCaptainGlowing",
            "mapBlockBreaking", "mapRestoreAdaptiveThrottling");
    private static final Set<String> STRING_RULES = Set.of("mapSnapshotMode");

    private Path directory;
    private volatile Map<String, Profile> profiles = Map.of();
    private volatile List<String> errors = List.of();

    public RuleConfigRegistry() {
        this.directory = null;
    }

    RuleConfigRegistry(Path directory) {
        this.directory = directory;
    }

    public synchronized void useConfigRoot(Path root) {
        directory = root.resolve("rules");
    }

    public synchronized List<String> reload(SFGameSavedData legacyData) {
        List<String> problems = new ArrayList<>();
        if (directory == null) return List.of("SFGame rule config root is not initialized");
        // Reload each mode independently. One malformed mode file must not
        // make every otherwise valid profile unavailable to commands or the
        // administrator GUI. Existing valid profiles remain the last-known-
        // good value for a mode whose edited file fails validation.
        Map<String, Profile> loaded = new LinkedHashMap<>(profiles);
        try {
            Files.createDirectories(directory);
            for (GameModeDefinition mode : GameModeRegistry.all()) {
                Path path = path(mode.id());
                if (!Files.exists(path)) writeDocument(path, defaultDocument(mode.id(), legacyData.rules(mode.id())));
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) throw new JsonParseException("root must be a JSON object");
                    Profile profile = parse(mode.id(), parsed.getAsJsonObject(), problems);
                    if (profile != null) loaded.put(mode.id(), profile);
                } catch (JsonParseException | IllegalStateException exception) {
                    problems.add(mode.id() + ": " + message(exception));
                }
            }
        } catch (IOException exception) {
            problems.add(message(exception));
            SFGame.LOGGER.error("Could not load rule profiles from {}", directory, exception);
        }
        profiles = Collections.unmodifiableMap(loaded);
        errors = List.copyOf(problems);
        return errors;
    }

    public MatchRules rules(String modeId, String mapId, MatchRules fallback) {
        Profile profile = profiles.get(modeId);
        if (profile == null) return fallback;
        String normalized = normalizeMap(mapId);
        return profile.effective().getOrDefault(normalized, profile.base());
    }

    public synchronized void setInt(String modeId, String mapId, String key, int value) {
        mutateRule(modeId, mapId, key, value);
    }

    public synchronized void setBoolean(String modeId, String mapId, String key, boolean value) {
        mutateRule(modeId, mapId, key, value);
    }

    public synchronized void setDouble(String modeId, String mapId, String key, double value) {
        mutateRule(modeId, mapId, key, value);
    }

    public synchronized void setString(String modeId, String mapId, String key, String value) {
        mutateRule(modeId, mapId, key, value);
    }

    public synchronized void resetMap(String modeId, String mapId) {
        mutate(modeId, document -> {
            JsonObject maps = object(document, "maps", true);
            String normalized = normalizeMap(mapId);
            if (!maps.has(normalized) || !maps.get(normalized).isJsonObject()) return;
            JsonObject scope = maps.getAsJsonObject(normalized);
            scope.remove("rules");
            String parent = string(scope, "parent", "base");
            if (scope.size() == 0 || scope.size() == 1 && "base".equals(parent)) maps.remove(normalized);
        });
    }

    public synchronized void setParent(String modeId, String mapId, String parent) {
        String normalizedMap = normalizeMap(mapId);
        String normalizedParent = parent == null ? "base" : parent.trim().toLowerCase(Locale.ROOT);
        if (!"base".equals(normalizedParent) && !SFGameId.isValid(normalizedParent)) {
            throw new IllegalArgumentException("Invalid rule parent: " + parent);
        }
        if (normalizedMap.equals(normalizedParent)) throw new IllegalArgumentException("A map cannot inherit itself");
        mutate(modeId, document -> {
            JsonObject maps = object(document, "maps", true);
            if (!"base".equals(normalizedParent) && !maps.has(normalizedParent)) {
                throw new IllegalArgumentException("Unknown rule parent map: " + normalizedParent);
            }
            JsonObject scope = scope(maps, normalizedMap);
            scope.addProperty("parent", normalizedParent);
        });
    }

    public String parent(String modeId, String mapId) {
        Profile profile = profiles.get(modeId);
        Scope scope = profile == null ? null : profile.scopes().get(normalizeMap(mapId));
        return scope == null ? "base" : scope.parent();
    }

    public List<String> errors() { return errors; }
    public Path directory() { return directory; }

    private void mutateRule(String modeId, String mapId, String key, Object value) {
        if (!allowed(modeId).contains(key)) throw new IllegalArgumentException(key + " is not available in " + modeId + " mode");
        mutate(modeId, document -> {
            JsonObject maps = object(document, "maps", true);
            JsonObject scope = scope(maps, normalizeMap(mapId));
            JsonObject rules = object(scope, "rules", true);
            if (value instanceof Boolean bool) rules.addProperty(key, bool);
            else if (value instanceof Integer integer) rules.addProperty(key, integer);
            else if (value instanceof Double decimal) rules.addProperty(key, decimal);
            else if (value instanceof String string) rules.addProperty(key, string);
            else throw new IllegalArgumentException("Unsupported rule value for " + key);
        });
    }

    private void mutate(String modeId, java.util.function.Consumer<JsonObject> mutation) {
        Profile current = profiles.get(modeId);
        if (current == null) current = loadProfile(modeId);
        JsonObject candidate = current.document().deepCopy();
        mutation.accept(candidate);
        List<String> problems = new ArrayList<>();
        Profile rebuilt = parse(modeId, candidate, problems);
        if (!problems.isEmpty() || rebuilt == null) throw new IllegalArgumentException(String.join("; ", problems));
        try {
            writeDocument(path(modeId), candidate);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save " + modeId + " rules: " + message(exception), exception);
        }
        Map<String, Profile> updated = new LinkedHashMap<>(profiles);
        updated.put(modeId, rebuilt);
        profiles = Collections.unmodifiableMap(updated);
        errors = List.of();
    }

    /**
     * Recover a single missing profile on demand. This covers integrated
     * server timing and partial reload failures without requiring a match to
     * start or an explicit /sfgame reload before the GUI can edit rules.
     */
    private Profile loadProfile(String modeId) {
        if (directory == null) throw new IllegalStateException("SFGame rule config root is not initialized");
        if (GameModeRegistry.get(modeId).isEmpty()) {
            throw new IllegalArgumentException("Unknown game mode: " + modeId);
        }
        Path target = path(modeId);
        List<String> problems = new ArrayList<>();
        try {
            Files.createDirectories(directory);
            if (!Files.exists(target)) writeDocument(target, defaultDocument(modeId, new MatchRules(modeId)));
            Profile loaded;
            try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) throw new JsonParseException("root must be a JSON object");
                loaded = parse(modeId, parsed.getAsJsonObject(), problems);
            }
            if (loaded == null || !problems.isEmpty()) {
                throw new IllegalStateException("Could not load rule profile for " + modeId + ": "
                        + String.join("; ", problems));
            }
            Map<String, Profile> updated = new LinkedHashMap<>(profiles);
            updated.put(modeId, loaded);
            profiles = Collections.unmodifiableMap(updated);
            return loaded;
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("Could not load rule profile for " + modeId + ": "
                    + message(exception), exception);
        }
    }

    private Profile parse(String modeId, JsonObject document, List<String> problems) {
        int initialErrors = problems.size();
        JsonObject baseObject = readObject(document, "rules", modeId + "/rules", problems);
        MatchRules base = new MatchRules(modeId);
        apply(baseObject, base, modeId, modeId + "/rules", problems);

        Map<String, Scope> scopes = new LinkedHashMap<>();
        JsonObject mapsObject = readObject(document, "maps", modeId + "/maps", problems);
        for (Map.Entry<String, JsonElement> entry : mapsObject.entrySet()) {
            String id = normalizeMap(entry.getKey());
            if (!SFGameId.isValid(id)) { problems.add(modeId + "/maps: invalid map id " + entry.getKey()); continue; }
            if (!entry.getValue().isJsonObject()) { problems.add(modeId + "/maps." + id + " must be an object"); continue; }
            JsonObject raw = entry.getValue().getAsJsonObject();
            String parent = string(raw, "parent", "base").toLowerCase(Locale.ROOT);
            if (!"base".equals(parent) && !SFGameId.isValid(parent)) {
                problems.add(modeId + "/maps." + id + ": invalid parent " + parent);
                parent = "base";
            }
            JsonObject overrides = readObject(raw, "rules", modeId + "/maps." + id + "/rules", problems);
            MatchRules validation = base.copy();
            apply(overrides, validation, modeId, modeId + "/maps." + id + "/rules", problems);
            scopes.put(id, new Scope(id, parent, overrides.deepCopy()));
        }
        validateParents(modeId, scopes, problems);
        if (problems.size() != initialErrors) return null;

        Map<String, MatchRules> effective = new LinkedHashMap<>();
        for (String id : scopes.keySet()) resolve(id, modeId, base, scopes, effective, new LinkedHashSet<>(), problems);
        if (problems.size() != initialErrors) return null;
        return new Profile(document.deepCopy(), base, Collections.unmodifiableMap(scopes), Collections.unmodifiableMap(effective));
    }

    private MatchRules resolve(String id, String modeId, MatchRules base, Map<String, Scope> scopes,
                               Map<String, MatchRules> resolved, Set<String> stack, List<String> problems) {
        MatchRules cached = resolved.get(id);
        if (cached != null) return cached;
        Scope scope = scopes.get(id);
        if (scope == null) return base;
        if (!stack.add(id)) { problems.add(modeId + ": rule inheritance cycle at " + id); return base; }
        MatchRules result = "base".equals(scope.parent()) ? base.copy()
                : resolve(scope.parent(), modeId, base, scopes, resolved, stack, problems).copy();
        apply(scope.rules(), result, modeId, modeId + "/maps." + id + "/rules", problems);
        stack.remove(id);
        resolved.put(id, result);
        return result;
    }

    private void validateParents(String modeId, Map<String, Scope> scopes, List<String> problems) {
        for (Scope scope : scopes.values()) {
            if (!"base".equals(scope.parent()) && !scopes.containsKey(scope.parent())) {
                problems.add(modeId + "/maps." + scope.id() + ": missing parent " + scope.parent());
            }
            Set<String> visited = new LinkedHashSet<>();
            Scope current = scope;
            while (current != null && !"base".equals(current.parent())) {
                if (!visited.add(current.id())) {
                    problems.add(modeId + ": rule inheritance cycle at " + current.id());
                    break;
                }
                current = scopes.get(current.parent());
            }
        }
    }

    private void apply(JsonObject source, MatchRules rules, String modeId, String label, List<String> problems) {
        Set<String> allowed = allowed(modeId);
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            if (!allowed.contains(key)) { problems.add(label + ": unknown or unavailable rule " + key); continue; }
            try {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive()
                        || BOOLEAN_RULES.contains(key) && !value.getAsJsonPrimitive().isBoolean()
                        || STRING_RULES.contains(key) && !value.getAsJsonPrimitive().isString()
                        || !BOOLEAN_RULES.contains(key) && !STRING_RULES.contains(key)
                        && !value.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("wrong JSON value type");
                }
                switch (key) {
                    case "maxPlayers" -> rules.maxPlayers(value.getAsInt());
                    case "scoreLimit" -> rules.scoreLimit(value.getAsInt());
                    case "timeLimitSeconds" -> rules.timeLimitSeconds(value.getAsInt());
                    case "startCountdownSeconds" -> rules.startCountdownSeconds(value.getAsInt());
                    case "respawnSeconds" -> rules.respawnSeconds(value.getAsInt());
                    case "respawnProtectionSeconds" -> rules.respawnProtectionSeconds(value.getAsInt());
                    case "resultSeconds" -> rules.resultSeconds(value.getAsInt());
                    case "captureTimeSeconds" -> rules.captureTimeSeconds(value.getAsInt());
                    case "captureUsePlayerDifference" -> rules.captureUsePlayerDifference(value.getAsBoolean());
                    case "captureDifferenceCoefficient" -> rules.captureDifferenceCoefficient(value.getAsDouble());
                    case "captureMaxMultiplier" -> rules.captureMaxMultiplier(value.getAsInt());
                    case "scoreIntervalSeconds" -> rules.scoreIntervalSeconds(value.getAsInt());
                    case "scorePerPoint" -> rules.scorePerPoint(value.getAsInt());
                    case "syncHoldSeconds" -> rules.syncHoldSeconds(value.getAsInt());
                    case "attackerTickets" -> rules.attackerTickets(value.getAsInt());
                    case "sectorTransitionSeconds" -> rules.sectorTransitionSeconds(value.getAsInt());
                    case "captainVoteSeconds" -> rules.captainVoteSeconds(value.getAsInt());
                    case "captainReplacementVoteSeconds" -> rules.captainReplacementVoteSeconds(value.getAsInt());
                    case "attackerCaptainGlowing" -> rules.attackerCaptainGlowing(value.getAsBoolean());
                    case "mapBlockBreaking" -> rules.mapBlockBreaking(value.getAsBoolean());
                    case "mapSnapshotMode" -> rules.mapSnapshotMode(MapSnapshotMode.parse(value.getAsString()));
                    case "mapRestorePartitionDelayTicks" -> rules.mapRestorePartitionDelayTicks(value.getAsInt());
                    case "mapRestoreAdaptiveThrottling" -> rules.mapRestoreAdaptiveThrottling(value.getAsBoolean());
                    case "mapRestoreTargetTickMillis" -> rules.mapRestoreTargetTickMillis(value.getAsInt());
                    case "mapRestoreMaxPartitionsPerTick" -> rules.mapRestoreMaxPartitionsPerTick(value.getAsInt());
                    case "attackerCaptainCaptureWeight" -> rules.attackerCaptainCaptureWeight(value.getAsDouble());
                    case "defenderCaptureWeight" -> rules.defenderCaptureWeight(value.getAsDouble());
                    case "ctfFlagReturnSeconds" -> rules.ctfFlagReturnSeconds(value.getAsInt());
                    case "ctfHomeCaptureTimeSeconds" -> rules.ctfHomeCaptureTimeSeconds(value.getAsInt());
                    default -> problems.add(label + ": unsupported rule " + key);
                }
            } catch (RuntimeException exception) {
                problems.add(label + ": invalid value for " + key);
            }
        }
    }

    private Set<String> allowed(String modeId) {
        Set<String> result = new LinkedHashSet<>(COMMON);
        if (GameModeRegistry.DOMINATION.equals(modeId)) { result.addAll(CAPTURE); result.addAll(DOMINATION); }
        if (GameModeRegistry.BREAKTHROUGH.equals(modeId)) { result.addAll(CAPTURE); result.addAll(BREAKTHROUGH); }
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId)) { result.addAll(CAPTURE); result.addAll(CTF); }
        return result;
    }

    private JsonObject defaultDocument(String modeId, MatchRules legacy) {
        JsonObject document = new JsonObject();
        document.add("rules", ruleObject(modeId, legacy));
        document.add("maps", new JsonObject());
        return document;
    }

    private JsonObject ruleObject(String modeId, MatchRules r) {
        JsonObject object = new JsonObject();
        object.addProperty("maxPlayers", r.maxPlayers());
        object.addProperty("scoreLimit", r.scoreLimit());
        object.addProperty("timeLimitSeconds", r.timeLimitSeconds());
        object.addProperty("startCountdownSeconds", r.startCountdownSeconds());
        object.addProperty("respawnSeconds", r.respawnSeconds());
        object.addProperty("respawnProtectionSeconds", r.respawnProtectionSeconds());
        object.addProperty("resultSeconds", r.resultSeconds());
        object.addProperty("mapBlockBreaking", r.mapBlockBreaking());
        object.addProperty("mapSnapshotMode", r.mapSnapshotMode().id());
        object.addProperty("mapRestorePartitionDelayTicks", r.mapRestorePartitionDelayTicks());
        object.addProperty("mapRestoreAdaptiveThrottling", r.mapRestoreAdaptiveThrottling());
        object.addProperty("mapRestoreTargetTickMillis", r.mapRestoreTargetTickMillis());
        object.addProperty("mapRestoreMaxPartitionsPerTick", r.mapRestoreMaxPartitionsPerTick());
        if (GameModeRegistry.DOMINATION.equals(modeId) || GameModeRegistry.BREAKTHROUGH.equals(modeId)
                || GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId)) {
            object.addProperty("captureTimeSeconds", r.captureTimeSeconds());
            object.addProperty("captureUsePlayerDifference", r.captureUsePlayerDifference());
            object.addProperty("captureDifferenceCoefficient", r.captureDifferenceCoefficient());
            object.addProperty("captureMaxMultiplier", r.captureMaxMultiplier());
        }
        if (GameModeRegistry.DOMINATION.equals(modeId)) {
            object.addProperty("scoreIntervalSeconds", r.scoreIntervalSeconds());
            object.addProperty("scorePerPoint", r.scorePerPoint());
            object.addProperty("syncHoldSeconds", r.syncHoldSeconds());
        }
        if (GameModeRegistry.BREAKTHROUGH.equals(modeId)) {
            object.addProperty("attackerTickets", r.attackerTickets());
            object.addProperty("sectorTransitionSeconds", r.sectorTransitionSeconds());
            object.addProperty("captainVoteSeconds", r.captainVoteSeconds());
            object.addProperty("captainReplacementVoteSeconds", r.captainReplacementVoteSeconds());
            object.addProperty("attackerCaptainGlowing", r.attackerCaptainGlowing());
            object.addProperty("attackerCaptainCaptureWeight", r.attackerCaptainCaptureWeight());
            object.addProperty("defenderCaptureWeight", r.defenderCaptureWeight());
        }
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId)) {
            object.addProperty("attackerTickets", r.attackerTickets());
            object.addProperty("ctfFlagReturnSeconds", r.ctfFlagReturnSeconds());
            object.addProperty("ctfHomeCaptureTimeSeconds", r.ctfHomeCaptureTimeSeconds());
        }
        return object;
    }

    private void writeDocument(Path target, JsonObject document) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path path(String modeId) { return directory.resolve(modeId + ".json"); }

    private static JsonObject object(JsonObject parent, String key, boolean create) {
        JsonElement existing = parent.get(key);
        if (existing != null && existing.isJsonObject()) return existing.getAsJsonObject();
        if (!create) return new JsonObject();
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    private static JsonObject readObject(JsonObject parent, String key, String label, List<String> problems) {
        JsonElement existing = parent.get(key);
        if (existing == null || existing.isJsonNull()) return new JsonObject();
        if (existing.isJsonObject()) return existing.getAsJsonObject();
        problems.add(label + " must be an object");
        return new JsonObject();
    }

    private static JsonObject scope(JsonObject maps, String mapId) {
        JsonElement existing = maps.get(mapId);
        if (existing != null && existing.isJsonObject()) return existing.getAsJsonObject();
        JsonObject created = new JsonObject();
        created.addProperty("parent", "base");
        created.add("rules", new JsonObject());
        maps.add(mapId, created);
        return created;
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException exception) { return fallback; }
    }

    private static String normalizeMap(String value) {
        return value == null || value.isBlank() ? "default" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private record Scope(String id, String parent, JsonObject rules) { }
    private record Profile(JsonObject document, MatchRules base, Map<String, Scope> scopes,
                           Map<String, MatchRules> effective) { }
}
