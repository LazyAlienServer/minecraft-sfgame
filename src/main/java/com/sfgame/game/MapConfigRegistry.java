package com.sfgame.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sfgame.SFGame;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.MapConfigJson;
import com.sfgame.data.SFGameId;
import com.sfgame.data.SFGameSavedData;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns the file-backed map layout.  A map is deliberately a directory so
 * compact settings can live in {@code map.json} while long profiles such as
 * classes can sit beside it.
 *
 * <pre>
 * serverconfig/sfgame/maps/&lt;mode&gt;/&lt;map&gt;/map.json
 * serverconfig/sfgame/maps/&lt;mode&gt;/&lt;map&gt;/classes.json
 * </pre>
 */
public final class MapConfigRegistry {
    public static final String MAP_FILE = "map.json";
    public static final String MAPS_DIRECTORY = "maps";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path directory;
    private volatile List<String> errors = List.of();

    public synchronized void useConfigRoot(Path root) {
        directory = root.resolve(MAPS_DIRECTORY);
    }

    public synchronized List<String> reload(SFGameSavedData data) {
        List<String> problems = new ArrayList<>();
        if (directory == null) return List.of("SFGame map config root is not initialized");
        try {
            Files.createDirectories(directory);
            for (GameModeDefinition mode : GameModeRegistry.all()) reloadMode(mode.id(), data.maps(mode.id()), data, problems);
        } catch (IOException exception) {
            problems.add(message(exception));
            SFGame.LOGGER.error("Could not load SFGame map configurations from {}", directory, exception);
        }
        errors = List.copyOf(problems);
        return errors;
    }

    public List<String> errors() {
        return errors;
    }

    public Path directory() {
        return directory;
    }

    public Path mapDirectory(String modeId, String mapId) {
        return directory.resolve(normalize(modeId)).resolve(normalize(mapId));
    }

    public Path mapPath(String modeId, String mapId) {
        return mapDirectory(modeId, mapId).resolve(MAP_FILE);
    }

    /** Writes only the map portion and preserves rules or other companion data in map.json. */
    public synchronized void saveMap(String modeId, ArenaMap map) {
        if (directory == null || map == null) return;
        Path target = mapPath(modeId, map.id());
        try {
            JsonObject document = readDocument(target);
            JsonObject mapObject = MapConfigJson.write(map);
            for (Map.Entry<String, JsonElement> entry : document.entrySet()) {
                if (!mapObject.has(entry.getKey())) mapObject.add(entry.getKey(), entry.getValue().deepCopy());
            }
            writeDocument(target, mapObject);
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not save map " + modeId + "/" + map.id() + ": " + message(exception), exception);
        }
    }

    public synchronized void deleteMap(String modeId, String mapId) {
        if (directory == null) return;
        Path target = mapDirectory(modeId, mapId);
        if (!Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new MapConfigDeleteException(exception);
                }
            });
        } catch (IOException | MapConfigDeleteException exception) {
            Throwable cause = exception instanceof MapConfigDeleteException wrapped ? wrapped.getCause() : exception;
            throw new IllegalStateException("Could not delete map " + modeId + "/" + mapId + ": " + message(cause), cause);
        }
    }

    private void reloadMode(String modeId, Collection<ArenaMap> legacyMaps, SFGameSavedData data,
                            List<String> problems) throws IOException {
        Path modeDirectory = directory.resolve(modeId);
        Files.createDirectories(modeDirectory);
        Map<String, ArenaMap> loaded = new LinkedHashMap<>();
        try (var paths = Files.list(modeDirectory)) {
            for (Path mapDirectory : paths.filter(Files::isDirectory).sorted().toList()) {
                Path path = mapDirectory.resolve(MAP_FILE);
                if (!Files.isRegularFile(path)) continue;
                String folderId = mapDirectory.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!SFGameId.isValid(folderId)) {
                    problems.add(modeId + ": invalid map directory " + mapDirectory.getFileName());
                    continue;
                }
                try {
                    ArenaMap map = MapConfigJson.read(readDocument(path));
                    if (!folderId.equals(map.id())) {
                        problems.add(modeId + "/" + folderId + ": map.json id is " + map.id());
                        continue;
                    }
                    loaded.put(map.id(), map);
                } catch (JsonParseException | IllegalArgumentException exception) {
                    problems.add(modeId + "/" + folderId + ": " + message(exception));
                }
            }
        }

        // A world created before the JSON layout still has its maps in SavedData.
        // Keep those maps during the one-time migration, and write them immediately.
        for (ArenaMap legacy : legacyMaps) {
            if (loaded.containsKey(legacy.id())) continue;
            loaded.put(legacy.id(), legacy);
            saveMap(modeId, legacy);
        }
        if (loaded.isEmpty()) {
            ArenaMap fallback = new ArenaMap("default");
            loaded.put(fallback.id(), fallback);
            saveMap(modeId, fallback);
        }
        data.replaceMaps(modeId, loaded.values());
    }

    private JsonObject readDocument(Path path) throws IOException {
        if (!Files.exists(path)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new JsonParseException("root must be a JSON object");
            return parsed.getAsJsonObject();
        }
    }

    private void writeDocument(Path target, JsonObject document) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String normalize(String value) {
        return SFGameId.normalize(value == null ? "" : value);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private static final class MapConfigDeleteException extends RuntimeException {
        private MapConfigDeleteException(IOException cause) {
            super(cause);
        }
    }
}
