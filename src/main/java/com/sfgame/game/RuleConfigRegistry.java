package com.sfgame.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sfgame.SFGame;
import com.sfgame.data.MatchRules;
import com.sfgame.data.BlockAllowlist;
import com.sfgame.data.MapSnapshotMode;
import com.sfgame.data.BreakthroughVariant;
import com.sfgame.data.CarrierRestriction;
import com.sfgame.data.CtfVariant;
import com.sfgame.data.PointActivationStrategy;
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * JSON-backed per-map rule profiles. Each map stores its rule document in
 * map.json; the document carries a parent map id or {@code base}.
 *
 * <p>The map-layout loader does not consult mode-level rule files.</p>
 */
public final class RuleConfigRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> COMMON = Set.of("maxPlayers", "scoreLimit", "timeLimitSeconds",
            "startCountdownSeconds", "respawnSeconds", "respawnProtectionSeconds", "resultSeconds", "mapBlockBreaking",
            "mapBlockAllowlist", "mapSnapshotMode",
            "mapRestorePartitionDelayTicks", "mapRestoreAdaptiveThrottling", "mapRestoreTargetTickMillis",
            "mapRestoreMaxPartitionsPerTick");
    private static final Set<String> CAPTURE = Set.of("captureTimeSeconds", "captureUsePlayerDifference",
            "captureDifferenceCoefficient", "captureMaxMultiplier");
    private static final Set<String> DOMINATION = Set.of("dominationStrategy", "scoreIntervalSeconds", "scorePerPoint", "syncHoldSeconds");
    private static final Set<String> BREAKTHROUGH = Set.of("breakthroughVariant", "breakthroughLegs",
            "breakthroughAttacker", "breakthroughDefender", "attackerTickets", "sectorTransitionSeconds",
            "captainVoteSeconds", "captainReplacementVoteSeconds", "attackerCaptainGlowing",
            "attackerCaptainCaptureWeight", "defenderCaptureWeight");
    private static final Set<String> CTF = Set.of("ctfVariant", "ctfAttacker", "ctfDefender", "ctfCarrierRestriction",
            "attackerTickets", "ctfFlagReturnSeconds", "ctfHomeCaptureTimeSeconds");
    private static final Set<String> BOOLEAN_RULES = Set.of("captureUsePlayerDifference", "attackerCaptainGlowing",
            "mapBlockBreaking", "mapRestoreAdaptiveThrottling");
    private static final Set<String> STRING_RULES = Set.of("mapSnapshotMode", "dominationStrategy",
            "breakthroughVariant", "breakthroughAttacker", "breakthroughDefender",
            "ctfVariant", "ctfAttacker", "ctfDefender", "ctfCarrierRestriction");

    private Path directory;
    private Path mapDirectory;
    private boolean mapLayout;
    private volatile Map<String, MapModeProfile> mapProfiles = Map.of();
    private volatile Map<String, MatchRules> mapBaseRules = Map.of();
    private volatile Map<String, Profile> profiles = Map.of();
    private volatile List<String> errors = List.of();

    public RuleConfigRegistry() {
        this.directory = null;
        this.mapDirectory = null;
        this.mapLayout = false;
    }

    RuleConfigRegistry(Path directory) {
        this.directory = directory;
        this.mapDirectory = null;
        this.mapLayout = false;
    }

    public synchronized void useConfigRoot(Path root) {
        directory = root.resolve("rules");
        mapDirectory = root.resolve(MapConfigRegistry.MAPS_DIRECTORY);
        mapLayout = true;
    }

    public synchronized List<String> reload(SFGameSavedData legacyData) {
        if (mapLayout) return reloadMapLayout(legacyData);
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
        if (mapLayout) {
            MapModeProfile profile = mapProfiles.get(modeId);
            if (profile == null) return fallback;
            return profile.effective().getOrDefault(normalizeMap(mapId), profile.base());
        }
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

    public synchronized void setStringSet(String modeId, String mapId, String key, Set<String> value) {
        mutateRule(modeId, mapId, key, BlockAllowlist.normalizeAll(value));
    }

    public synchronized void resetMap(String modeId, String mapId) {
        if (mapLayout) {
            mutateMap(modeId, mapId, document -> document.remove("rules"));
            return;
        }
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
        if (mapLayout) {
            MapParentRef parentRef = MapParentRef.parse(parent, modeId);
            MapParentRef currentRef = new MapParentRef(modeId, normalizedMap);
            if (currentRef.equals(parentRef)) throw new IllegalArgumentException("A map cannot inherit itself");
            if (!mapScopeExists(parentRef)) {
                throw new IllegalArgumentException("Unknown rule parent map: " + parentRef.canonical());
            }
            setMapParent(modeId, normalizedMap, parentRef);
            return;
        }
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
        if (mapLayout) {
            MapModeProfile profile = mapProfiles.get(modeId);
            Scope scope = profile == null ? null : profile.scopes().get(normalizeMap(mapId));
            return scope == null ? "base" : scope.parent();
        }
        Profile profile = profiles.get(modeId);
        Scope scope = profile == null ? null : profile.scopes().get(normalizeMap(mapId));
        return scope == null ? "base" : scope.parent();
    }
    public List<String> referencesTo(String modeId, String mapId) {
        String target = new MapParentRef(modeId, mapId).canonical();
        List<String> references = new ArrayList<>();
        for (Map.Entry<String, MapModeProfile> mode : mapProfiles.entrySet()) {
            for (Scope scope : mode.getValue().scopes().values()) {
                if (target.equals(scope.parent())) references.add(mode.getKey() + "/" + scope.id());
            }
        }
        return List.copyOf(references);
    }


    public List<String> errors() { return errors; }
    public Path directory() { return mapLayout ? mapDirectory : directory; }

    private void mutateRule(String modeId, String mapId, String key, Object value) {
        if (!allowed(modeId).contains(key)) throw new IllegalArgumentException(key + " is not available in " + modeId + " mode");
        if (mapLayout) {
            mutateMap(modeId, mapId, document -> putRule(object(document, "rules", true), key, value));
            return;
        }
        mutate(modeId, document -> {
            JsonObject maps = object(document, "maps", true);
            JsonObject scope = scope(maps, normalizeMap(mapId));
            JsonObject rules = object(scope, "rules", true);
            if (value instanceof Boolean bool) rules.addProperty(key, bool);
            else if (value instanceof Integer integer) rules.addProperty(key, integer);
            else if (value instanceof Double decimal) rules.addProperty(key, decimal);
            else if (value instanceof String string) rules.addProperty(key, string);
            else if (value instanceof Set<?> values) {
                JsonArray array = new JsonArray();
                values.forEach(entry -> array.add(String.valueOf(entry)));
                rules.add(key, array);
            }
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
    private List<String> reloadMapLayout(SFGameSavedData data) {
        List<String> problems = new ArrayList<>();
        if (mapDirectory == null) return List.of("SFGame map rule config root is not initialized");
        Map<String, MapModeProfile> loaded = new LinkedHashMap<>();
        try {
            Files.createDirectories(mapDirectory);
            for (GameModeDefinition mode : GameModeRegistry.all()) {
                String modeId = mode.id();
                ensureBaseRuleDocument(modeId);
                MatchRules base = readBaseRules(modeId, problems);
                Collection<com.sfgame.data.ArenaMap> knownMaps = data == null ? List.of() : data.maps(modeId);
                loaded.put(modeId, readMapMode(modeId, base, knownMaps, problems));
            }
            validateMapParents(loaded, problems);
            loaded = resolveMapProfiles(loaded, problems);
        } catch (IOException exception) {
            problems.add(message(exception));
            SFGame.LOGGER.error("Could not load map rule profiles from {}", mapDirectory, exception);
        }
        if (problems.isEmpty()) mapProfiles = Collections.unmodifiableMap(loaded);
        mapBaseRules = Collections.unmodifiableMap(loaded.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().base(),
                        (left, right) -> left, LinkedHashMap::new)));
        errors = List.copyOf(problems);
        return errors;
    }

    private MapModeProfile readMapMode(String modeId, MatchRules base,
                                       Collection<com.sfgame.data.ArenaMap> knownMaps,
                                       List<String> problems) throws IOException {
        Path modeDirectory = mapDirectory.resolve(modeId);
        Files.createDirectories(modeDirectory);
        Set<String> ids = new LinkedHashSet<>();
        if (knownMaps != null) for (var map : knownMaps) if (map != null) ids.add(map.id());
        try (var paths = Files.list(modeDirectory)) {
            paths.filter(Files::isDirectory).map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .filter(id -> !"base".equals(id) && SFGameId.isValid(id)).forEach(ids::add);
        }
        if (ids.isEmpty()) ids.add("default");
        Map<String, Scope> scopes = new LinkedHashMap<>();
        for (String id : ids) {
            Path path = mapPath(modeId, id);
            JsonObject document = readMapDocument(path, modeId, id, problems);
            String rawParent = stringAny(document, "parent", modeId + "/base");
            String parent;
            try {
                parent = MapParentRef.parse(rawParent, modeId).canonical();
            } catch (IllegalArgumentException exception) {
                problems.add(modeId + "/" + id + ": " + exception.getMessage());
                parent = modeId + "/base";
            }
            JsonObject overrides = readObjectAny(document, "rules", modeId + "/" + id + "/rules", problems);
            MatchRules validation = new MatchRules(modeId);
            apply(overrides, validation, modeId, modeId + "/" + id + "/rules", problems);
            scopes.put(id, new Scope(id, parent, overrides.deepCopy()));
        }
        return new MapModeProfile(base, Collections.unmodifiableMap(scopes), Map.of());
    }

    private void ensureBaseRuleDocument(String modeId) throws IOException {
        Path target = mapPath(modeId, "base");
        if (Files.exists(target)) return;
        JsonObject document = new JsonObject();
        document.add("rules", ruleObject(modeId, new MatchRules(modeId)));
        writeDocument(target, document);
    }

    private MatchRules readBaseRules(String modeId, List<String> problems) throws IOException {
        Path target = mapPath(modeId, "base");
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new JsonParseException("root must be a JSON object");
            JsonObject rules = readObjectAny(parsed.getAsJsonObject(), "rules", modeId + "/base/rules", problems);
            MatchRules result = new MatchRules(modeId);
            apply(rules, result, modeId, modeId + "/base/rules", problems);
            return result;
        } catch (JsonParseException exception) {
            problems.add(modeId + "/base: " + message(exception));
            return new MatchRules(modeId);
        }
    }

    private JsonObject readMapDocument(Path path, String modeId, String mapId, List<String> problems) throws IOException {
        if (!Files.exists(path)) {
            JsonObject document = new JsonObject();
            document.addProperty("parent", new MapParentRef(modeId, "base").canonical());
            writeDocument(path, document);
            return document;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new JsonParseException("root must be a JSON object");
            JsonObject document = parsed.getAsJsonObject();
            if (!document.has("parent") && !document.has("Parent")) {
                document.addProperty("parent", new MapParentRef(modeId, "base").canonical());
                writeDocument(path, document);
            }
            return document;
        } catch (JsonParseException exception) {
            problems.add(modeId + "/" + mapId + ": " + message(exception));
            return new JsonObject();
        }
    }

    private void validateMapParents(Map<String, MapModeProfile> profiles, List<String> problems) {
        for (Map.Entry<String, MapModeProfile> mode : profiles.entrySet()) {
            for (Scope scope : mode.getValue().scopes().values()) {
                String key = mode.getKey() + "/" + scope.id();
                MapParentRef parent;
                try {
                    parent = MapParentRef.parse(scope.parent(), mode.getKey());
                } catch (IllegalArgumentException exception) {
                    problems.add(key + ": " + exception.getMessage());
                    continue;
                }
                if (!parent.isBase() && !profiles.getOrDefault(parent.modeId(),
                        new MapModeProfile(new MatchRules(parent.modeId()), Map.of(), Map.of()))
                        .scopes().containsKey(parent.mapId())) {
                    problems.add(key + ": missing rule parent " + parent.canonical());
                }
                Set<String> visited = new LinkedHashSet<>();
                String current = key;
                while (current != null) {
                    if (!visited.add(current)) {
                        problems.add(key + ": rule inheritance cycle at " + current);
                        break;
                    }
                    String[] parts = current.split("/", 2);
                    Scope currentScope = profiles.getOrDefault(parts[0],
                            new MapModeProfile(new MatchRules(parts[0]), Map.of(), Map.of())).scopes().get(parts[1]);
                    if (currentScope == null) break;
                    MapParentRef next = MapParentRef.parse(currentScope.parent(), parts[0]);
                    current = next.isBase() ? null : next.canonical();
                }
            }
        }
    }

    private Map<String, MapModeProfile> resolveMapProfiles(Map<String, MapModeProfile> raw,
                                                           List<String> problems) {
        Map<String, MatchRules> cache = new LinkedHashMap<>();
        Map<String, MapModeProfile> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, MapModeProfile> mode : raw.entrySet()) {
            Map<String, MatchRules> effective = new LinkedHashMap<>();
            for (String mapId : mode.getValue().scopes().keySet()) {
                effective.put(mapId, resolveMapRule(new MapParentRef(mode.getKey(), mapId), raw,
                        cache, new LinkedHashSet<>(), problems));
            }
            resolved.put(mode.getKey(), new MapModeProfile(mode.getValue().base(), mode.getValue().scopes(),
                    Collections.unmodifiableMap(effective)));
        }
        return resolved;
    }

    private MatchRules resolveMapRule(MapParentRef ref, Map<String, MapModeProfile> profiles,
                                      Map<String, MatchRules> cache, Set<String> stack,
                                      List<String> problems) {
        MatchRules cached = cache.get(ref.canonical());
        if (cached != null) return cached;
        MapModeProfile profile = profiles.get(ref.modeId());
        if (profile == null) return new MatchRules(ref.modeId());
        if (ref.isBase()) return profile.base();
        Scope scope = profile.scopes().get(ref.mapId());
        if (scope == null || !stack.add(ref.canonical())) return new MatchRules(ref.modeId());
        MapParentRef parent = MapParentRef.parse(scope.parent(), ref.modeId());
        MatchRules parentRules = parent.isBase() ? profiles.get(parent.modeId()).base()
                : resolveMapRule(parent, profiles, cache, stack, problems);
        MatchRules result = inheritRules(parentRules, parent.modeId(), ref.modeId(), problems);
        apply(scope.rules(), result, ref.modeId(), ref.canonical() + "/rules", problems);
        stack.remove(ref.canonical());
        cache.put(ref.canonical(), result);
        return result;
    }

    private MatchRules inheritRules(MatchRules parent, String parentMode, String childMode,
                                    List<String> problems) {
        MatchRules result = new MatchRules(childMode);
        JsonObject compatible = ruleObject(parentMode, parent);
        compatible.entrySet().removeIf(entry -> !allowed(childMode).contains(entry.getKey()));
        apply(compatible, result, childMode, parentMode + "/inherited", problems);
        return result;
    }


    private void setMapParent(String modeId, String mapId, MapParentRef parent) {
        MatchRules current = rules(modeId, mapId, new MatchRules(modeId));
        MatchRules parentRules = rules(parent.modeId(), parent.mapId(), new MatchRules(parent.modeId()));
        List<String> problems = new ArrayList<>();
        MatchRules inherited = inheritRules(parentRules, parent.modeId(), modeId, problems);
        if (!problems.isEmpty()) throw new IllegalArgumentException(String.join("; ", problems));
        JsonObject currentObject = ruleObject(modeId, current);
        JsonObject inheritedObject = ruleObject(modeId, inherited);
        JsonObject overrides = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : currentObject.entrySet()) {
            if (!inheritedObject.has(entry.getKey()) || !inheritedObject.get(entry.getKey()).equals(entry.getValue())) {
                overrides.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        mutateMap(modeId, mapId, document -> {
            document.addProperty("parent", parent.canonical());
            document.add("rules", overrides);
        });
    }

    private synchronized void mutateMap(String modeId, String mapId,
                                         java.util.function.Consumer<JsonObject> mutation) {
        if (GameModeRegistry.get(modeId).isEmpty()) throw new IllegalArgumentException("Unknown game mode: " + modeId);
        String normalizedMap = normalizeMap(mapId);
        Path target = mapPath(modeId, normalizedMap);
        JsonObject previous;
        try {
            previous = readMapDocument(target, modeId, normalizedMap, new ArrayList<>());
            JsonObject candidate = previous.deepCopy();
            mutation.accept(candidate);
            writeDocument(target, candidate);
            List<String> problems = reloadMapLayout(null);
            if (!problems.isEmpty()) {
                writeDocument(target, previous);
                reloadMapLayout(null);
                throw new IllegalArgumentException(String.join("; ", problems));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save " + modeId + "/" + normalizedMap + " rules: "
                    + message(exception), exception);
        }
    }

    private boolean mapScopeExists(MapParentRef ref) {
        if (ref.isBase()) return Files.isRegularFile(mapPath(ref.modeId(), "base"));
        MapModeProfile profile = mapProfiles.get(ref.modeId());
        if (profile != null && profile.scopes().containsKey(ref.mapId())) return true;
        return Files.isRegularFile(mapPath(ref.modeId(), ref.mapId()));
    }

    private static boolean hasRules(JsonObject document) {
        JsonElement rules = document.has("rules") ? document.get("rules") : document.get("Rules");
        return rules != null && rules.isJsonObject() && rules.getAsJsonObject().size() > 0;
    }

    private static JsonObject readObjectAny(JsonObject parent, String key, String label, List<String> problems) {
        JsonElement existing = parent.has(key) ? parent.get(key) : parent.get(capitalize(key));
        if (existing == null || existing.isJsonNull()) return new JsonObject();
        if (existing.isJsonObject()) return existing.getAsJsonObject();
        problems.add(label + " must be an object");
        return new JsonObject();
    }

    private static String stringAny(JsonObject object, String key, String fallback) {
        JsonElement value = object.has(key) ? object.get(key) : object.get(capitalize(key));
        try { return value == null ? fallback : value.getAsString(); }
        catch (RuntimeException exception) { return fallback; }
    }

    private static String capitalize(String value) {
        return value == null || value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static void putRule(JsonObject rules, String key, Object value) {
        if (value instanceof Boolean bool) rules.addProperty(key, bool);
        else if (value instanceof Integer integer) rules.addProperty(key, integer);
        else if (value instanceof Double decimal) rules.addProperty(key, decimal);
        else if (value instanceof String string) rules.addProperty(key, string);
        else if (value instanceof Set<?> values) {
            JsonArray array = new JsonArray();
            values.forEach(entry -> array.add(String.valueOf(entry)));
            rules.add(key, array);
        } else throw new IllegalArgumentException("Unsupported rule value");
    }

    private Path mapPath(String modeId, String mapId) {
        return mapDirectory.resolve(modeId).resolve(mapId).resolve(MapConfigRegistry.MAP_FILE);
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
                if ("mapBlockAllowlist".equals(key)) {
                    if (!value.isJsonArray()) throw new IllegalArgumentException("wrong JSON value type");
                    List<String> selectors = new ArrayList<>();
                    for (JsonElement selector : value.getAsJsonArray()) {
                        if (!selector.isJsonPrimitive() || !selector.getAsJsonPrimitive().isString()) {
                            throw new IllegalArgumentException("wrong JSON value type");
                        }
                        selectors.add(BlockAllowlist.normalize(selector.getAsString()));
                    }
                    rules.mapBlockAllowlist(selectors);
                    continue;
                }
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
                    case "dominationStrategy" -> rules.dominationStrategy(PointActivationStrategy.parse(value.getAsString()));
                    case "breakthroughVariant" -> rules.breakthroughVariant(BreakthroughVariant.valueOf(
                            value.getAsString().toUpperCase(Locale.ROOT)));
                    case "breakthroughLegs" -> rules.breakthroughLegs(value.getAsInt());
                    case "breakthroughAttacker" -> rules.breakthroughAttacker(parseTeam(value.getAsString()));
                    case "breakthroughDefender" -> rules.breakthroughDefender(parseTeam(value.getAsString()));
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
                    case "ctfVariant" -> rules.ctfVariant(parseCtfVariant(value.getAsString()));
                    case "ctfAttacker" -> rules.ctfAttacker(parseTeam(value.getAsString()));
                    case "ctfDefender" -> rules.ctfDefender(parseTeam(value.getAsString()));
                    case "ctfCarrierRestriction" -> rules.ctfCarrierRestriction(parseCarrierRestriction(value.getAsString()));
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
        JsonArray allowlist = new JsonArray();
        r.mapBlockAllowlist().forEach(allowlist::add);
        object.add("mapBlockAllowlist", allowlist);
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
            object.addProperty("dominationStrategy", r.dominationStrategy().name().toLowerCase(Locale.ROOT));
            object.addProperty("scoreIntervalSeconds", r.scoreIntervalSeconds());
            object.addProperty("scorePerPoint", r.scorePerPoint());
            object.addProperty("syncHoldSeconds", r.syncHoldSeconds());
        }
        if (GameModeRegistry.BREAKTHROUGH.equals(modeId)) {
            object.addProperty("breakthroughVariant", r.breakthroughVariant().name().toLowerCase(Locale.ROOT));
            object.addProperty("breakthroughLegs", r.breakthroughLegs());
            object.addProperty("breakthroughAttacker", r.breakthroughAttacker().id());
            object.addProperty("breakthroughDefender", r.breakthroughDefender().id());
            object.addProperty("attackerTickets", r.attackerTickets());
            object.addProperty("sectorTransitionSeconds", r.sectorTransitionSeconds());
            object.addProperty("captainVoteSeconds", r.captainVoteSeconds());
            object.addProperty("captainReplacementVoteSeconds", r.captainReplacementVoteSeconds());
            object.addProperty("attackerCaptainGlowing", r.attackerCaptainGlowing());
            object.addProperty("attackerCaptainCaptureWeight", r.attackerCaptainCaptureWeight());
            object.addProperty("defenderCaptureWeight", r.defenderCaptureWeight());
        }
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId)) {
            object.addProperty("ctfVariant", r.ctfVariant().id());
            object.addProperty("ctfAttacker", r.ctfAttacker().id());
            object.addProperty("ctfDefender", r.ctfDefender().id());
            object.addProperty("ctfCarrierRestriction", r.ctfCarrierRestriction().id());
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
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
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

    private static TeamSide parseTeam(String value) {
        TeamSide side = TeamSide.fromId(value);
        if (side == TeamSide.NONE) throw new IllegalArgumentException("unknown team");
        return side;
    }

    private static CtfVariant parseCtfVariant(String value) {
        return CtfVariant.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static CarrierRestriction parseCarrierRestriction(String value) {
        return CarrierRestriction.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private record Scope(String id, String parent, JsonObject rules) { }
    private record MapModeProfile(MatchRules base, Map<String, Scope> scopes,
                                  Map<String, MatchRules> effective) { }
    private record Profile(JsonObject document, MatchRules base, Map<String, Scope> scopes,
                           Map<String, MatchRules> effective) { }
}
