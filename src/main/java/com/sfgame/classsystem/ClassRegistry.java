package com.sfgame.classsystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sfgame.SFGame;
import com.sfgame.data.SFGameId;
import com.sfgame.data.SFGameSavedData;
import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
import java.util.Optional;
import java.util.Set;

/**
 * Loads class pools from the selected map directory and its explicit parent
 * map chain, then applies the current map's team override. Legacy flat mode
 * files are used only while generating/upgrading a missing map profile.
 */
public final class ClassRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int BREAKTHROUGH_CONFIG_VERSION = 4;

    private Path legacyPath;
    private Path profilesPath;
    private Path mapProfilesPath;
    private volatile Map<String, Map<String, MapOverride>> mapOverrides = Map.of();
    private volatile Map<String, Profile> profiles = Map.of();
    private volatile List<String> loadErrors = List.of();

    public synchronized List<String> reload() {
        List<String> errors = new ArrayList<>();
        if (profilesPath == null) return List.of("SFGame class config root is not initialized");
        try {
            createLegacyFileIfMissing();
            createModeProfilesIfMissing();
            upgradeBreakthroughProfileIfNeeded();
            Map<String, RawProfile> raw = readProfiles(errors);
            Map<String, Profile> resolved = new LinkedHashMap<>();
            for (String id : raw.keySet()) resolve(id, raw, resolved, new LinkedHashSet<>(), errors);
            for (Map.Entry<String, Profile> entry : resolved.entrySet()) validateScopes(entry.getKey(), entry.getValue(), errors);
            if (errors.isEmpty()) profiles = Collections.unmodifiableMap(new LinkedHashMap<>(resolved));
        } catch (IOException | JsonParseException exception) {
            errors.add(message(exception));
            SFGame.LOGGER.error("Could not load SFGame class profiles from {}", profilesPath, exception);
        }
        loadErrors = List.copyOf(errors);
        return loadErrors;
    }
    public synchronized List<String> reload(SFGameSavedData data) {
        List<String> errors = new ArrayList<>();
        if (mapProfilesPath == null || data == null) return reload();
        try {
            Map<String, RawProfile> raw = readBundledProfiles(errors);
            Map<String, Profile> resolved = new LinkedHashMap<>();
            for (String id : raw.keySet()) resolve(id, raw, resolved, new LinkedHashSet<>(), errors);
            for (Map.Entry<String, Profile> entry : resolved.entrySet()) validateScopes(entry.getKey(), entry.getValue(), errors);
            if (errors.isEmpty()) profiles = Collections.unmodifiableMap(new LinkedHashMap<>(resolved));
        } catch (IOException | JsonParseException exception) {
            errors.add(message(exception));
            SFGame.LOGGER.error("Could not load bundled SFGame class profiles", exception);
        }

        Map<String, Map<String, MapOverride>> loaded = new LinkedHashMap<>();
        boolean mapErrors = false;
        try {
            for (var mode : GameModeRegistry.all()) {
                Map<String, MapOverride> modeOverrides = new LinkedHashMap<>();
                for (var map : data.maps(mode.id())) {
                    Path target = mapProfilesPath.resolve(mode.id()).resolve(map.id()).resolve("classes.json");
                    ensureMapProfile(target, mode.id(), map.id(), errors);
                    try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                        ClassFile file = GSON.fromJson(reader, ClassFile.class);
                        if (file == null) throw new JsonParseException("The root JSON object is missing");
                        String parent = normalizeParent(file.parent(), map.id(), "map", errors);
                        if (parent == null) parent = "base";
                        Pool pool = readPool(mode.id() + "/" + map.id(), "classes",
                                file.classes(), file.captainClasses(), errors, false);
                        Map<String, RawScope> teams = readScopes(mode.id() + "/" + map.id(), "teams",
                                file.teams(), errors, true);
                        modeOverrides.put(map.id(), new MapOverride(parent, pool, teams));
                    } catch (JsonParseException | IOException | IllegalStateException exception) {
                        errors.add(mode.id() + "/" + map.id() + ": " + message(exception));
                        mapErrors = true;
                    }
                }
                int beforeParentErrors = errors.size();
                validateMapOverrideParents(mode.id(), modeOverrides, errors);
                if (errors.size() != beforeParentErrors) mapErrors = true;
                loaded.put(mode.id(), Collections.unmodifiableMap(modeOverrides));
            }
        } catch (IOException exception) {
            errors.add(message(exception));
            mapErrors = true;
        }
        if (!mapErrors) mapOverrides = Collections.unmodifiableMap(loaded);
        loadErrors = List.copyOf(errors);
        return loadErrors;
    }

    public Optional<ClassDefinition> get(String modeId, String id) { return getForTeam(modeId, null, TeamSide.NONE, id, false); }
    public Optional<ClassDefinition> getCaptain(String modeId, String id) { return getForTeam(modeId, null, TeamSide.NONE, id, true); }
    public Optional<ClassDefinition> get(String modeId, String id, boolean captain) { return getForTeam(modeId, null, TeamSide.NONE, id, captain); }
    public Optional<ClassDefinition> getForTeam(String modeId, String mapId, TeamSide side, String id) {
        return getForTeam(modeId, mapId, side, id, false);
    }
    public Optional<ClassDefinition> getCaptainForTeam(String modeId, String mapId, TeamSide side, String id) {
        return getForTeam(modeId, mapId, side, id, true);
    }
    private Optional<ClassDefinition> getForTeam(String modeId, String mapId, TeamSide side, String id, boolean captain) {
        if (id == null) return Optional.empty();
        Scope scope = scope(modeId, mapId, side);
        return Optional.ofNullable((captain ? scope.captains : scope.classes).get(id.toLowerCase(Locale.ROOT)));
    }

    public boolean contains(String modeId, String id) { return get(modeId, id).isPresent(); }
    public boolean containsCaptain(String modeId, String id) { return getCaptain(modeId, id).isPresent(); }
    public boolean containsForTeam(String modeId, String mapId, TeamSide side, String id) { return getForTeam(modeId, mapId, side, id).isPresent(); }
    public boolean containsCaptainForTeam(String modeId, String mapId, TeamSide side, String id) { return getCaptainForTeam(modeId, mapId, side, id).isPresent(); }

    public Collection<ClassDefinition> all(String modeId) { return allForTeam(modeId, null, TeamSide.NONE); }
    public Collection<ClassDefinition> captainClasses(String modeId) { return captainClassesForTeam(modeId, null, TeamSide.NONE); }
    public Collection<ClassDefinition> allForTeam(String modeId, String mapId, TeamSide side) { return scope(modeId, mapId, side).classes.values(); }
    public Collection<ClassDefinition> captainClassesForTeam(String modeId, String mapId, TeamSide side) { return scope(modeId, mapId, side).captains.values(); }
    public Collection<ClassDefinition> allForMode(String modeId, String mapId) {
        LinkedHashMap<String, ClassDefinition> result = new LinkedHashMap<>();
        result.putAll(scope(modeId, mapId, TeamSide.NONE).classes);
        for (TeamSide side : TeamSide.PLAYABLE) result.putAll(scope(modeId, mapId, side).classes);
        return result.values();
    }
    public Collection<ClassDefinition> captainClassesForMode(String modeId, String mapId) {
        LinkedHashMap<String, ClassDefinition> result = new LinkedHashMap<>();
        result.putAll(scope(modeId, mapId, TeamSide.NONE).captains);
        for (TeamSide side : TeamSide.PLAYABLE) result.putAll(scope(modeId, mapId, side).captains);
        return result.values();
    }
    public Optional<ClassDefinition> defaultClass(String modeId) { return all(modeId).stream().findFirst(); }
    public Optional<ClassDefinition> defaultCaptainClass(String modeId) { return captainClasses(modeId).stream().findFirst(); }
    public Optional<ClassDefinition> defaultClassForTeam(String modeId, String mapId, TeamSide side) { return allForTeam(modeId, mapId, side).stream().findFirst(); }
    public Optional<ClassDefinition> defaultCaptainClassForTeam(String modeId, String mapId, TeamSide side) { return captainClassesForTeam(modeId, mapId, side).stream().findFirst(); }

    /** Compatibility accessors for code that explicitly wants the TDM profile. */
    public Optional<ClassDefinition> get(String id) { return get(GameModeRegistry.TEAM_DEATHMATCH, id); }
    public boolean contains(String id) { return contains(GameModeRegistry.TEAM_DEATHMATCH, id); }
    public Collection<ClassDefinition> all() { return all(GameModeRegistry.TEAM_DEATHMATCH); }
    public Optional<ClassDefinition> defaultClass() { return defaultClass(GameModeRegistry.TEAM_DEATHMATCH); }
    public List<String> loadErrors() { return loadErrors; }
    public Path configPath() { return profilesPath; }

    public synchronized void useConfigRoot(Path root) {
        legacyPath = root.resolve("classes.json");
        profilesPath = root.resolve("classes");
        mapProfilesPath = root.resolve("maps");
    }
    public synchronized void ensureMapProfile(String modeId, String mapId) {
        if (mapProfilesPath == null) return;
        try {
            ensureMapProfile(mapProfilesPath.resolve(modeId).resolve(mapId).resolve("classes.json"),
                    modeId, mapId, new ArrayList<>());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create map class profile: " + message(exception), exception);
        }
    }

    private void ensureMapProfile(Path target, String modeId, String mapId, List<String> errors) throws IOException {
        JsonObject defaults = defaultMapProfile(modeId);
        if (!Files.exists(target)) {
            writeMapProfile(target, defaults);
            return;
        }
        JsonObject current;
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new JsonParseException("root must be an object");
            current = parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            errors.add(modeId + "/" + mapId + ": " + message(exception));
            return;
        }
        boolean hasDefinitions = arraySize(current, "classes") > 0
                || arraySize(current, "captainClasses") > 0
                || current.has("teams") && current.get("teams").isJsonObject() && current.getAsJsonObject("teams").size() > 0;
        boolean hasParent = current.has("parent") || current.has("Parent");
        String parent = normalizeParent(string(current, "parent", null), mapId, "map", errors);
        if (parent == null) parent = "base";
        JsonObject normalized = "base".equals(parent) && (!hasParent || !hasDefinitions)
                ? mergeMapProfile(defaults, current) : current.deepCopy();
        normalized.addProperty("parent", parent);
        if (!normalized.has("classes")) normalized.add("classes", new JsonArray());
        if (!normalized.has("captainClasses")) normalized.add("captainClasses", new JsonArray());
        if (!normalized.has("teams")) normalized.add("teams", new JsonObject());
        if (!GSON.toJson(normalized).equals(GSON.toJson(current))) writeMapProfile(target, normalized);
    }

    private JsonObject defaultMapProfile(String modeId) throws IOException {
        JsonObject source = bundledProfile(modeId);
        if (source == null) {
            try (InputStream input = ClassRegistry.class.getResourceAsStream("/defaults/classes.json")) {
                if (input == null) throw new IOException("Bundled defaults/classes.json is missing");
                source = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        JsonObject object = new JsonObject();
        object.addProperty("parent", "base");
        JsonArray classes = new JsonArray(), captains = new JsonArray();
        String parent = source.has("parent") ? source.get("parent").getAsString() : "base";
        if (!isDefaultParent(parent)) {
            JsonObject inherited = defaultMapProfile(parent);
            classes = inherited.getAsJsonArray("classes").deepCopy();
            captains = inherited.getAsJsonArray("captainClasses").deepCopy();
        }
        classes = mergeDefinitions(classes, source.get("classes"));
        captains = mergeDefinitions(captains, source.get("captainClasses"));
        object.add("classes", classes);
        object.add("captainClasses", captains);
        object.add("teams", new JsonObject());
        return object;
    }

    private static int arraySize(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key).size() : 0;
    }

    private static JsonObject mergeMapProfile(JsonObject defaults, JsonObject current) {
        JsonObject merged = defaults.deepCopy();
        for (Map.Entry<String, JsonElement> entry : current.entrySet()) {
            if (!Set.of("parent", "classes", "captainClasses", "teams").contains(entry.getKey())) {
                merged.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        merged.add("classes", mergeDefinitions(defaults.getAsJsonArray("classes"), current.get("classes")));
        merged.add("captainClasses", mergeDefinitions(defaults.getAsJsonArray("captainClasses"), current.get("captainClasses")));
        if (current.has("teams")) merged.add("teams", current.get("teams").deepCopy());
        return merged;
    }

    private static JsonArray mergeDefinitions(JsonArray defaults, JsonElement overrides) {
        JsonArray merged = defaults == null ? new JsonArray() : defaults.deepCopy();
        if (overrides == null || !overrides.isJsonArray()) return merged;
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int i = 0; i < merged.size(); i++) {
            JsonElement element = merged.get(i);
            if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                positions.put(element.getAsJsonObject().get("id").getAsString().toLowerCase(Locale.ROOT), i);
            }
        }
        for (JsonElement element : overrides.getAsJsonArray()) {
            if (!element.isJsonObject() || !element.getAsJsonObject().has("id")) continue;
            JsonObject definition = element.getAsJsonObject();
            String id = definition.get("id").getAsString().toLowerCase(Locale.ROOT);
            Integer position = positions.get(id);
            if (position == null) {
                positions.put(id, merged.size());
                merged.add(definition.deepCopy());
            } else {
                merged.set(position, definition.deepCopy());
            }
        }
        return merged;
    }

    private void writeMapProfile(Path target, JsonObject object) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, GSON.toJson(object) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException exception) { return fallback; }
    }

    private static void validateMapOverrideParents(String modeId, Map<String, MapOverride> overrides,
                                                   List<String> errors) {
        for (Map.Entry<String, MapOverride> entry : overrides.entrySet()) {
            String parent = entry.getValue().parent;
            if (!"base".equals(parent) && !overrides.containsKey(parent)) {
                errors.add(modeId + "/" + entry.getKey() + ": missing class parent " + parent);
            }
            Set<String> visited = new LinkedHashSet<>();
            String current = entry.getKey();
            while (current != null && !"base".equals(overrides.get(current) == null ? "base" : overrides.get(current).parent)) {
                if (!visited.add(current)) {
                    errors.add(modeId + ": class inheritance cycle at " + current);
                    break;
                }
                MapOverride scope = overrides.get(current);
                current = scope == null || "base".equals(scope.parent) ? null : scope.parent;
            }
        }
    }
    private Scope scope(String modeId, String mapId, TeamSide side) {
        String profileId = profileIdForMode(modeId);
        String normalizedMap = mapId == null ? "" : mapId.trim().toLowerCase(Locale.ROOT);
        if (!normalizedMap.isBlank()) {
            Map<String, MapOverride> overrides = mapOverrides.getOrDefault(profileId, Map.of());
            if (overrides.isEmpty()) return Scope.EMPTY;
            MapOverride mapOverride = overrides.get(normalizedMap);
            if (mapOverride == null) return Scope.EMPTY;
            Scope mapScope = resolveMapOverride(normalizedMap, overrides, new LinkedHashSet<>());
            if (side == null || side == TeamSide.NONE) return mapScope;
            Map<String, RawScope> teams = new LinkedHashMap<>();
            collectMapOverrideTeams(normalizedMap, overrides, teams, new LinkedHashSet<>());
            RawScope team = teams.get(side.id());
            return team == null ? mapScope : resolveScope(team.name, teams, mapScope, new LinkedHashSet<>(), true);
        }
        Profile profile = profiles.get(profileId);
        if (profile == null) return Scope.EMPTY;
        return side == null || side == TeamSide.NONE
                ? profile.root : profile.teams.get(side.id()) == null ? profile.root
                : resolveScope(side.id(), profile.teams, profile.root, new LinkedHashSet<>(), true);
    }

    private Scope resolveMapOverride(String id, Map<String, MapOverride> overrides, Set<String> stack) {
        MapOverride current = overrides.get(id);
        if (current == null || !stack.add(id)) return Scope.EMPTY;
        Scope parent = "base".equals(current.parent) ? Scope.EMPTY
                : resolveMapOverride(current.parent, overrides, stack);
        Scope result = merge(parent, current.pool);
        stack.remove(id);
        return result;
    }

    private void collectMapOverrideTeams(String id, Map<String, MapOverride> overrides,
                                         Map<String, RawScope> destination, Set<String> stack) {
        MapOverride current = overrides.get(id);
        if (current == null || !stack.add(id)) return;
        if (!"base".equals(current.parent)) collectMapOverrideTeams(current.parent, overrides, destination, stack);
        destination.putAll(current.teams);
        stack.remove(id);
    }

    private void collectMapTeams(RawScope map, Map<String, RawScope> maps,
                                  Map<String, RawScope> destination, Set<String> stack) {
        if (!stack.add(map.name)) return;
        if (!isDefaultParent(map.parent)) {
            RawScope parent = maps.get(map.parent);
            if (parent != null) collectMapTeams(parent, maps, destination, stack);
        }
        destination.putAll(map.teams);
        stack.remove(map.name);
    }

    private Scope resolveScope(String id, Map<String, RawScope> scopes, Scope base, Set<String> stack, boolean teamScope) {
        RawScope current = scopes.get(id);
        if (current == null) return base;
        String key = (teamScope ? "team:" : "map:") + id;
        if (!stack.add(key)) return base;
        Scope parent = base;
        if (current.parent != null && !isDefaultParent(current.parent)) {
            RawScope inherited = scopes.get(current.parent);
            if (inherited != null) parent = resolveScope(inherited.name, scopes, base, stack, teamScope);
        }
        LinkedHashMap<String, ClassDefinition> classes = new LinkedHashMap<>(parent.classes);
        LinkedHashMap<String, ClassDefinition> captains = new LinkedHashMap<>(parent.captains);
        classes.putAll(current.pool.classes);
        captains.putAll(current.pool.captains);
        stack.remove(key);
        return new Scope(Collections.unmodifiableMap(classes), Collections.unmodifiableMap(captains));
    }

    private static boolean isDefaultParent(String parent) {
        return parent == null || parent.equalsIgnoreCase("default") || parent.equalsIgnoreCase("base");
    }

    private void validateScopes(String profileId, Profile profile, List<String> errors) {
        validateScopeParents(profileId + "/teams", profile.teams, errors);
        validateScopeParents(profileId + "/maps", profile.maps, errors);
        for (RawScope map : profile.maps.values()) {
            Map<String, RawScope> teams = new LinkedHashMap<>(profile.teams);
            collectMapTeams(map, profile.maps, teams, new LinkedHashSet<>());
            validateScopeParents(profileId + "/maps." + map.name + "/teams", teams, errors);
        }
    }

    private void validateScopeParents(String label, Map<String, RawScope> scopes, List<String> errors) {
        Set<String> reported = new LinkedHashSet<>();
        for (RawScope scope : scopes.values()) validateScopeParent(scope, scopes, label, new LinkedHashSet<>(), reported, errors);
    }

    private void validateScopeParent(RawScope current, Map<String, RawScope> scopes, String label,
                                     Set<String> stack, Set<String> reported, List<String> errors) {
        if (!stack.add(current.name)) {
            String key = label + ":cycle:" + current.name;
            if (reported.add(key)) errors.add(label + ": class scope inheritance cycle at " + current.name);
            return;
        }
        if (!isDefaultParent(current.parent)) {
            RawScope parent = scopes.get(current.parent);
            if (parent == null) {
                String key = label + ":missing:" + current.name + ":" + current.parent;
                if (reported.add(key)) errors.add(label + ": " + current.name + " inherits missing scope " + current.parent);
            } else {
                validateScopeParent(parent, scopes, label, stack, reported, errors);
            }
        }
        stack.remove(current.name);
    }

    private Map<String, RawProfile> readProfiles(List<String> errors) throws IOException {
        Map<String, RawProfile> result = new LinkedHashMap<>();
        try (var paths = Files.list(profilesPath)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String name = path.getFileName().toString();
                String id = name.substring(0, name.length() - 5).toLowerCase(Locale.ROOT);
                if (!validProfileId(id)) { errors.add("Invalid class profile filename: " + name); continue; }
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    ClassFile file = GSON.fromJson(reader, ClassFile.class);
                    if (file == null) throw new JsonParseException("The root JSON object is missing");
                    String parent = normalizeParent(file.parent(), id, "profile", errors);
                    Pool pool = readPool(id, "classes", file.classes(), file.captainClasses(), errors, false);
                    Map<String, RawScope> teams = readScopes(id, "teams", file.teams(), errors, true);
                    Map<String, RawScope> maps = readScopes(id, "maps", file.maps(), errors, false);
                    if (pool.classes.isEmpty() && parent == null && teams.isEmpty() && maps.isEmpty()) {
                        errors.add(id + ": no normal classes or team/map class pools were configured");
                    }
                    result.put(id, new RawProfile(parent, pool, teams, maps));
                } catch (JsonParseException exception) {
                    errors.add(id + ": " + message(exception));
                }
            }
        }
        for (var mode : GameModeRegistry.all()) if (!result.containsKey(mode.id())) errors.add("Missing class profile: " + mode.id() + ".json");
        return result;
    }
    private Map<String, RawProfile> readBundledProfiles(List<String> errors) throws IOException {
        Map<String, RawProfile> result = new LinkedHashMap<>();
        for (var mode : GameModeRegistry.all()) {
            String id = mode.id();
            JsonObject object = bundledProfile(id);
            if (object == null) {
                try (InputStream input = ClassRegistry.class.getResourceAsStream("/defaults/classes.json")) {
                    if (input == null) throw new IOException("Bundled defaults/classes.json is missing");
                    object = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
            ClassFile file = GSON.fromJson(object, ClassFile.class);
            if (file == null) throw new JsonParseException(id + ": the root JSON object is missing");
            String parent = normalizeParent(file.parent(), id, "profile", errors);
            Pool pool = readPool(id, "classes", file.classes(), file.captainClasses(), errors, false);
            Map<String, RawScope> teams = readScopes(id, "teams", file.teams(), errors, true);
            Map<String, RawScope> maps = readScopes(id, "maps", file.maps(), errors, false);
            result.put(id, new RawProfile(parent, pool, teams, maps));
        }
        return result;
    }

    private Map<String, RawScope> readScopes(String profile, String label, Map<String, ClassScopeFile> source,
                                              List<String> errors, boolean teamScope) {
        Map<String, RawScope> result = new LinkedHashMap<>();
        if (source == null) return result;
        for (Map.Entry<String, ClassScopeFile> entry : source.entrySet()) {
            String id = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!validProfileId(id)) { errors.add(profile + "/" + label + ": invalid id " + id); continue; }
            ClassScopeFile file = entry.getValue() == null ? new ClassScopeFile() : entry.getValue();
            String parent = normalizeParent(file.parent(), id, label, errors);
            Pool pool = readPool(profile + "/" + label + "." + id, "classes", file.classes(), file.captainClasses(), errors, false);
            Map<String, RawScope> children = teamScope ? Map.of() : readScopes(profile + "/" + label + "." + id,
                    "teams", file.teams(), errors, true);
            result.put(id, new RawScope(id, parent, pool, children));
        }
        return result;
    }

    private String normalizeParent(String parent, String id, String label, List<String> errors) {
        if (parent == null || parent.isBlank()) return null;
        String normalized = parent.trim().toLowerCase(Locale.ROOT);
        if (!isDefaultParent(normalized) && !validProfileId(normalized)) {
            errors.add(id + "/" + label + ": invalid parent " + normalized);
            return null;
        }
        return normalized;
    }

    private Pool readPool(String profile, String label, List<ClassDefinition> definitions,
                          List<ClassDefinition> captains, List<String> errors, boolean requireNonEmpty) {
        return new Pool(validateDefinitions(profile, label, definitions, errors, requireNonEmpty),
                validateDefinitions(profile, "captainClasses", captains, errors, false));
    }

    private Profile resolve(String id, Map<String, RawProfile> raw, Map<String, Profile> resolved,
                            Set<String> stack, List<String> errors) {
        if (resolved.containsKey(id)) return resolved.get(id);
        RawProfile current = raw.get(id);
        if (current == null) { errors.add("Missing parent class profile: " + id); return Profile.EMPTY; }
        if (!stack.add(id)) { errors.add("Class profile inheritance cycle: " + String.join(" -> ", stack) + " -> " + id); return Profile.EMPTY; }
        Pool parent = Pool.EMPTY;
        Map<String, RawScope> teams = new LinkedHashMap<>();
        Map<String, RawScope> maps = new LinkedHashMap<>();
        if (current.parent != null) {
            Profile inherited = resolve(current.parent, raw, resolved, stack, errors);
            parent = new Pool(inherited.root.classes, inherited.root.captains);
            teams.putAll(inherited.teams);
            maps.putAll(inherited.maps);
        }
        Pool root = merge(parent, current.pool);
        teams.putAll(current.teams);
        maps.putAll(current.maps);
        stack.remove(id);
        Profile profile = new Profile(new Scope(root.classes, root.captains), Collections.unmodifiableMap(teams), Collections.unmodifiableMap(maps));
        resolved.put(id, profile);
        return profile;
    }

    private static Pool merge(Pool parent, Pool child) {
        LinkedHashMap<String, ClassDefinition> classes = new LinkedHashMap<>(parent.classes);
        LinkedHashMap<String, ClassDefinition> captains = new LinkedHashMap<>(parent.captains);
        classes.putAll(child.classes); captains.putAll(child.captains);
        return new Pool(Collections.unmodifiableMap(classes), Collections.unmodifiableMap(captains));
    }
    private static Scope merge(Scope parent, Pool child) {
        Pool merged = merge(new Pool(parent.classes, parent.captains), child);
        return new Scope(merged.classes, merged.captains);
    }

    private Map<String, ClassDefinition> validateDefinitions(String profile, String pool,
                                                              List<ClassDefinition> definitions, List<String> errors,
                                                              boolean requireNonEmpty) {
        Map<String, ClassDefinition> loaded = new LinkedHashMap<>();
        if (definitions == null) definitions = List.of();
        for (ClassDefinition definition : definitions) {
            if (definition == null) continue;
            String id = definition.id() == null ? "" : definition.id().trim().toLowerCase(Locale.ROOT);
            if (!SFGameId.isValidClass(id)) { errors.add(profile + "/" + pool + ": invalid class id " + id); continue; }
            if (loaded.putIfAbsent(id, definition) != null) errors.add(profile + "/" + pool + ": duplicate class id " + id);
        }
        if ("classes".equals(pool) && requireNonEmpty && loaded.isEmpty()) errors.add(profile + ": no valid normal classes were loaded");
        return loaded;
    }

    private void createLegacyFileIfMissing() throws IOException {
        if (Files.exists(legacyPath)) return;
        Files.createDirectories(legacyPath.getParent());
        try (InputStream input = ClassRegistry.class.getResourceAsStream("/defaults/classes.json")) {
            if (input == null) throw new IOException("Bundled defaults/classes.json is missing");
            Files.copy(input, legacyPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private void createModeProfilesIfMissing() throws IOException {
        Files.createDirectories(profilesPath);
        String legacy = Files.readString(legacyPath, StandardCharsets.UTF_8);
        for (var mode : GameModeRegistry.all()) {
            Path target = profilesPath.resolve(mode.id() + ".json");
            if (Files.exists(target)) continue;
            JsonObject object = bundledProfile(mode.id());
            if (object == null) object = JsonParser.parseString(legacy).getAsJsonObject();
            if (!object.has("captainClasses")) object.add("captainClasses", new JsonArray());
            if (!object.has("teams")) object.add("teams", new JsonObject());
            if (!object.has("maps")) object.add("maps", new JsonObject());
            Files.writeString(target, GSON.toJson(object), StandardCharsets.UTF_8);
        }
    }

    private void upgradeBreakthroughProfileIfNeeded() throws IOException {
        Path target = profilesPath.resolve(GameModeRegistry.BREAKTHROUGH + ".json");
        if (!Files.exists(target)) return;
        JsonObject current = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
        int version = current.has("configVersion") ? current.get("configVersion").getAsInt() : 0;
        if (version >= BREAKTHROUGH_CONFIG_VERSION) return;
        JsonObject defaults = bundledProfile(GameModeRegistry.BREAKTHROUGH);
        if (defaults == null) throw new IOException("Bundled breakthrough class profile is missing");
        mergeDefaultDefinitions(current, defaults, "classes");
        mergeDefaultDefinitions(current, defaults, "captainClasses");
        renameDefaultClass(current, "smg_assault", "冲锋枪突击手", "冲锋手");
        renameDefaultClass(current, "captain_tank", "坦克（队长加强版）", "坦克");
        current.addProperty("configVersion", BREAKTHROUGH_CONFIG_VERSION);
        Files.writeString(target, GSON.toJson(current), StandardCharsets.UTF_8);
    }

    private JsonObject bundledProfile(String modeId) throws IOException {
        String resource = "/defaults/classes/" + modeId + ".json";
        try (InputStream input = ClassRegistry.class.getResourceAsStream(resource)) {
            if (input == null) return null;
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
    private static void mergeDefaultDefinitions(JsonObject current, JsonObject defaults, String key) {
        JsonArray target = current.has(key) && current.get(key).isJsonArray() ? current.getAsJsonArray(key) : new JsonArray();
        if (!current.has(key) || !current.get(key).isJsonArray()) current.add(key, target);
        Map<String, JsonObject> existing = new LinkedHashMap<>();
        for (JsonElement element : target) if (element.isJsonObject() && element.getAsJsonObject().has("id"))
            existing.put(element.getAsJsonObject().get("id").getAsString().toLowerCase(Locale.ROOT), element.getAsJsonObject());
        for (JsonElement element : defaults.getAsJsonArray(key)) {
            JsonObject fallback = element.getAsJsonObject();
            String id = fallback.get("id").getAsString().toLowerCase(Locale.ROOT);
            JsonObject configured = existing.get(id);
            if (configured == null) target.add(fallback.deepCopy());
            else { copyIfEmpty(configured, fallback, "inventory"); copyIfEmpty(configured, fallback, "armor"); }
        }
    }
    private static void copyIfEmpty(JsonObject target, JsonObject fallback, String key) {
        if (!fallback.has(key)) return;
        boolean empty = !target.has(key) || target.get(key).isJsonNull()
                || target.get(key).isJsonArray() && target.getAsJsonArray(key).size() == 0
                || target.get(key).isJsonObject() && target.getAsJsonObject(key).size() == 0;
        if (empty) target.add(key, fallback.get(key).deepCopy());
    }
    private static void renameDefaultClass(JsonObject profile, String id, String oldName, String newName) {
        for (String pool : List.of("classes", "captainClasses")) {
            if (!profile.has(pool) || !profile.get(pool).isJsonArray()) continue;
            for (JsonElement element : profile.getAsJsonArray(pool)) {
                if (!element.isJsonObject()) continue;
                JsonObject definition = element.getAsJsonObject();
                if (id.equalsIgnoreCase(definition.has("id") ? definition.get("id").getAsString() : "")
                        && oldName.equals(definition.has("displayName") ? definition.get("displayName").getAsString() : "")) definition.addProperty("displayName", newName);
            }
        }
    }

    private static String profileIdForMode(String modeId) { return modeId == null ? GameModeRegistry.TEAM_DEATHMATCH : modeId.toLowerCase(Locale.ROOT); }
    private static boolean validProfileId(String id) { return SFGameId.isValid(id); }
    private record MapOverride(String parent, Pool pool, Map<String, RawScope> teams) { }
    private static String message(Exception exception) { return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(); }

    private record Pool(Map<String, ClassDefinition> classes, Map<String, ClassDefinition> captains) {
        private static final Pool EMPTY = new Pool(Map.of(), Map.of());
    }
    private record RawScope(String name, String parent, Pool pool, Map<String, RawScope> teams) { }
    private record RawProfile(String parent, Pool pool, Map<String, RawScope> teams, Map<String, RawScope> maps) { }
    private record Scope(Map<String, ClassDefinition> classes, Map<String, ClassDefinition> captains) {
        private static final Scope EMPTY = new Scope(Map.of(), Map.of());
    }
    private record Profile(Scope root, Map<String, RawScope> teams, Map<String, RawScope> maps) {
        private static final Profile EMPTY = new Profile(Scope.EMPTY, Map.of(), Map.of());
    }
}
