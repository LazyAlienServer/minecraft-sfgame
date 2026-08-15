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
import com.sfgame.game.GameModeRegistry;
import net.minecraftforge.fml.loading.FMLPaths;

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

public final class ClassRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int BREAKTHROUGH_CONFIG_VERSION = 4;

    private final Path legacyPath = FMLPaths.CONFIGDIR.get().resolve("sfgame").resolve("classes.json");
    private final Path profilesPath = FMLPaths.CONFIGDIR.get().resolve("sfgame").resolve("classes");
    private volatile Map<String, Profile> profiles = Map.of();
    private volatile List<String> loadErrors = List.of();

    public synchronized List<String> reload() {
        List<String> errors = new ArrayList<>();
        try {
            createLegacyFileIfMissing();
            createModeProfilesIfMissing();
            upgradeBreakthroughProfileIfNeeded();
            Map<String, RawProfile> raw = readProfiles(errors);
            Map<String, Profile> resolved = new LinkedHashMap<>();
            for (String id : raw.keySet()) resolve(id, raw, resolved, new LinkedHashSet<>(), errors);
            if (errors.isEmpty()) profiles = Collections.unmodifiableMap(new LinkedHashMap<>(resolved));
        } catch (IOException | JsonParseException exception) {
            errors.add(message(exception));
            SFGame.LOGGER.error("Could not load SFGame class profiles from {}", profilesPath, exception);
        }
        loadErrors = List.copyOf(errors);
        return loadErrors;
    }

    public Optional<ClassDefinition> get(String modeId, String id) { return get(modeId, id, false); }
    public Optional<ClassDefinition> getCaptain(String modeId, String id) { return get(modeId, id, true); }
    public Optional<ClassDefinition> get(String modeId, String id, boolean captain) {
        if (id == null) return Optional.empty();
        Profile profile = profiles.get(profileIdForMode(modeId));
        return profile == null ? Optional.empty() : Optional.ofNullable(
                (captain ? profile.captains : profile.classes).get(id.toLowerCase(Locale.ROOT)));
    }
    public boolean contains(String modeId, String id) { return get(modeId, id).isPresent(); }
    public boolean containsCaptain(String modeId, String id) { return getCaptain(modeId, id).isPresent(); }
    public Collection<ClassDefinition> all(String modeId) {
        Profile profile = profiles.get(profileIdForMode(modeId));
        return profile == null ? List.of() : profile.classes.values();
    }
    public Collection<ClassDefinition> captainClasses(String modeId) {
        Profile profile = profiles.get(profileIdForMode(modeId));
        return profile == null ? List.of() : profile.captains.values();
    }
    public Optional<ClassDefinition> defaultClass(String modeId) { return all(modeId).stream().findFirst(); }
    public Optional<ClassDefinition> defaultCaptainClass(String modeId) { return captainClasses(modeId).stream().findFirst(); }

    /** Compatibility accessors for code that explicitly wants the TDM profile. */
    public Optional<ClassDefinition> get(String id) { return get(GameModeRegistry.TEAM_DEATHMATCH, id); }
    public boolean contains(String id) { return contains(GameModeRegistry.TEAM_DEATHMATCH, id); }
    public Collection<ClassDefinition> all() { return all(GameModeRegistry.TEAM_DEATHMATCH); }
    public Optional<ClassDefinition> defaultClass() { return defaultClass(GameModeRegistry.TEAM_DEATHMATCH); }
    public List<String> loadErrors() { return loadErrors; }
    public Path configPath() { return profilesPath; }

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
                    String parent = file.parent();
                    if (parent != null) {
                        parent = parent.toLowerCase(Locale.ROOT);
                        if (!validProfileId(parent)) { errors.add(id + ": invalid parent profile " + parent); parent = null; }
                    }
                    result.put(id, new RawProfile(parent,
                            validateDefinitions(id, "classes", file.classes(), errors, parent == null),
                            validateDefinitions(id, "captainClasses", file.captainClasses(), errors)));
                } catch (JsonParseException exception) {
                    errors.add(id + ": " + message(exception));
                }
            }
        }
        for (var mode : GameModeRegistry.all()) if (!result.containsKey(mode.id())) errors.add("Missing class profile: " + mode.id() + ".json");
        return result;
    }

    private Profile resolve(String id, Map<String, RawProfile> raw, Map<String, Profile> resolved,
                            Set<String> stack, List<String> errors) {
        if (resolved.containsKey(id)) return resolved.get(id);
        RawProfile current = raw.get(id);
        if (current == null) { errors.add("Missing parent class profile: " + id); return Profile.EMPTY; }
        if (!stack.add(id)) { errors.add("Class profile inheritance cycle: " + String.join(" -> ", stack) + " -> " + id); return Profile.EMPTY; }
        LinkedHashMap<String, ClassDefinition> classes = new LinkedHashMap<>();
        LinkedHashMap<String, ClassDefinition> captains = new LinkedHashMap<>();
        if (current.parent != null) {
            Profile parent = resolve(current.parent, raw, resolved, stack, errors);
            classes.putAll(parent.classes); captains.putAll(parent.captains);
        }
        classes.putAll(current.classes); captains.putAll(current.captains);
        stack.remove(id);
        Profile profile = new Profile(Collections.unmodifiableMap(classes), Collections.unmodifiableMap(captains));
        resolved.put(id, profile);
        return profile;
    }

    private Map<String, ClassDefinition> validateDefinitions(String profile, String pool,
                                                              List<ClassDefinition> definitions, List<String> errors) {
        return validateDefinitions(profile, pool, definitions, errors, true);
    }

    private Map<String, ClassDefinition> validateDefinitions(String profile, String pool,
                                                              List<ClassDefinition> definitions, List<String> errors,
                                                              boolean requireNonEmpty) {
        Map<String, ClassDefinition> loaded = new LinkedHashMap<>();
        for (ClassDefinition definition : definitions) {
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
        JsonArray target = current.has(key) && current.get(key).isJsonArray()
                ? current.getAsJsonArray(key) : new JsonArray();
        if (!current.has(key) || !current.get(key).isJsonArray()) current.add(key, target);
        Map<String, JsonObject> existing = new LinkedHashMap<>();
        for (JsonElement element : target) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (object.has("id")) existing.put(object.get("id").getAsString().toLowerCase(Locale.ROOT), object);
        }
        for (JsonElement element : defaults.getAsJsonArray(key)) {
            JsonObject fallback = element.getAsJsonObject();
            String id = fallback.get("id").getAsString().toLowerCase(Locale.ROOT);
            JsonObject configured = existing.get(id);
            if (configured == null) {
                target.add(fallback.deepCopy());
                continue;
            }
            copyIfEmpty(configured, fallback, "inventory");
            copyIfEmpty(configured, fallback, "armor");
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
                        && oldName.equals(definition.has("displayName") ? definition.get("displayName").getAsString() : "")) {
                    definition.addProperty("displayName", newName);
                }
            }
        }
    }
    private static String profileIdForMode(String modeId) { return modeId == null ? GameModeRegistry.TEAM_DEATHMATCH : modeId; }
    private static boolean validProfileId(String id) { return SFGameId.isValid(id); }
    private static String message(Exception exception) { return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(); }

    private record RawProfile(String parent, Map<String, ClassDefinition> classes, Map<String, ClassDefinition> captains) { }
    private record Profile(Map<String, ClassDefinition> classes, Map<String, ClassDefinition> captains) {
        private static final Profile EMPTY = new Profile(Map.of(), Map.of());
    }
}
