package com.sfgame.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sfgame.SFGame;
import com.sfgame.data.ItemStrings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-side shop registry for every economy-enabled mode. */
public final class ShopRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> MODES = List.of(
            GameModeRegistry.BREAKTHROUGH, GameModeRegistry.CAPTURE_THE_FLAG, GameModeRegistry.DOMINATION);
    private final boolean validateRegistry;
    private Path root;
    private volatile Map<String, Map<String, ShopItem>> itemsByMode = Map.of();
    private volatile List<String> errors = List.of();
    public ShopRegistry() {
        this(true);
    }

    ShopRegistry(boolean validateRegistry) {
        this.validateRegistry = validateRegistry;
    }


    public synchronized List<String> reload() {
        List<String> problems = new ArrayList<>();
        if (root == null) return List.of("SFGame shop config root is not initialized");
        Map<String, Map<String, ShopItem>> next = new LinkedHashMap<>(itemsByMode);
        for (String modeId : MODES) {
            List<String> modeProblems = new ArrayList<>();
            try {
                Path path = path(modeId);
                createDefaultIfMissing(modeId, path);
                JsonObject document;
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    document = JsonParser.parseReader(reader).getAsJsonObject();
                }
                JsonArray array = document.has("items") && document.get("items").isJsonArray()
                        ? document.getAsJsonArray("items") : new JsonArray();
                Map<String, ShopItem> loaded = new LinkedHashMap<>();
                for (var element : array) {
                    if (!element.isJsonObject()) {
                        modeProblems.add("shop item must be an object");
                        continue;
                    }
                    try {
                        ShopItem item = GSON.fromJson(element, ShopItem.class).normalized();
                        if (loaded.putIfAbsent(item.id(), item) != null) {
                            modeProblems.add("duplicate shop item: " + item.id());
                        }
                        if (!valid(item)) modeProblems.add(item.id() + ": invalid item " + item.item());
                    } catch (RuntimeException exception) {
                        modeProblems.add("invalid shop item: " + exception.getMessage());
                    }
                }
                if (modeProblems.isEmpty()) next.put(modeId, Collections.unmodifiableMap(loaded));
            } catch (IOException | JsonParseException | IllegalStateException exception) {
                modeProblems.add(exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage());
                SFGame.LOGGER.error("Could not load {} shop from {}", modeId, path(modeId), exception);
            }
            modeProblems.forEach(problem -> problems.add(modeId + ": " + problem));
        }
        itemsByMode = Collections.unmodifiableMap(next);
        errors = List.copyOf(problems);
        return errors;
    }

    public List<ShopItem> items(String modeId) {
        return List.copyOf(itemsByMode.getOrDefault(modeId, Map.of()).values());
    }

    public ShopItem item(String modeId, String id) {
        return itemsByMode.getOrDefault(modeId, Map.of())
                .get(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    public List<String> errors() { return errors; }
    public Path path(String modeId) { return root.resolve("shop").resolve(modeId + ".json"); }

    public synchronized void useConfigRoot(Path root) {
        this.root = root;
    }

    private void createDefaultIfMissing(String modeId, Path path) throws IOException {
        if (Files.exists(path)) return;
        Files.createDirectories(path.getParent());
        try (InputStream input = ShopRegistry.class.getResourceAsStream("/defaults/shop/" + modeId + ".json")) {
            if (input == null) throw new IOException("Bundled defaults/shop/" + modeId + ".json is missing");
            Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean valid(ShopItem item) {
        ItemStrings.Parsed parsed = ItemStrings.parse(item.item());
        return parsed.id() != null && (!validateRegistry || !item.stack().isEmpty());
    }

    public record ShopItem(String id, String name, String icon, int price, String item, int count, String nbt) {
        ShopItem normalized() {
            String normalizedId = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            if (!normalizedId.matches("[a-z0-9][a-z0-9_]{0,31}")) {
                throw new IllegalArgumentException("invalid id " + id);
            }
            return new ShopItem(normalizedId, name == null ? normalizedId : name,
                    icon == null ? "minecraft:paper" : icon, Math.max(0, price),
                    item == null ? "minecraft:air" : item, Math.max(1, Math.min(64, count)),
                    nbt == null ? "" : nbt);
        }


        ItemStack stack() {
            ItemStrings.Parsed parsed = ItemStrings.parse(item);
            if (parsed.id() == null || !BuiltInRegistries.ITEM.containsKey(parsed.id())) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(parsed.id()), count);
            String tag = parsed.hasNbt() ? parsed.nbt() : nbt;
            return ItemStrings.applyTag(stack, tag) ? stack : ItemStack.EMPTY;
        }

        ItemStack iconStack() {
            return ItemStrings.stack(icon, 1, new ItemStack(Items.PAPER));
        }
    }
}
