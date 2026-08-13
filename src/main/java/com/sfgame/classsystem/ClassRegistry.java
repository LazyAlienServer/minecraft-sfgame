package com.sfgame.classsystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.sfgame.SFGame;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ClassRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath = FMLPaths.CONFIGDIR.get().resolve("sfgame").resolve("classes.json");
    private volatile Map<String, ClassDefinition> classes = Map.of();
    private volatile List<String> loadErrors = List.of();

    public synchronized List<String> reload() {
        List<String> errors = new ArrayList<>();
        try {
            createDefaultFileIfMissing();
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                ClassFile file = GSON.fromJson(reader, ClassFile.class);
                if (file == null) throw new JsonParseException("The root JSON object is missing");
                Map<String, ClassDefinition> loaded = new LinkedHashMap<>();
                for (ClassDefinition definition : file.classes()) {
                    String id = definition.id() == null ? "" : definition.id().trim().toLowerCase(Locale.ROOT);
                    if (!id.matches("[a-z][a-z0-9_]{1,63}")) {
                        errors.add("Invalid class id: " + id);
                        continue;
                    }
                    if (loaded.putIfAbsent(id, definition) != null) {
                        errors.add("Duplicate class id: " + id);
                    }
                }
                if (loaded.isEmpty()) errors.add("No valid classes were loaded");
                if (errors.isEmpty()) classes = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
            }
        } catch (IOException | JsonParseException exception) {
            errors.add(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            SFGame.LOGGER.error("Could not load SFGame classes from {}", configPath, exception);
        }
        loadErrors = List.copyOf(errors);
        return loadErrors;
    }

    public Optional<ClassDefinition> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(classes.get(id.toLowerCase(Locale.ROOT)));
    }

    public boolean contains(String id) {
        return get(id).isPresent();
    }

    public Collection<ClassDefinition> all() {
        return classes.values();
    }

    /** Returns the first enabled class in JSON order for players without a selection. */
    public Optional<ClassDefinition> defaultClass() {
        return classes.values().stream().findFirst();
    }

    public List<String> loadErrors() {
        return loadErrors;
    }

    public Path configPath() {
        return configPath;
    }

    private void createDefaultFileIfMissing() throws IOException {
        if (Files.exists(configPath)) return;
        Files.createDirectories(configPath.getParent());
        try (InputStream input = ClassRegistry.class.getResourceAsStream("/defaults/classes.json")) {
            if (input == null) throw new IOException("Bundled defaults/classes.json is missing");
            Files.copy(input, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
