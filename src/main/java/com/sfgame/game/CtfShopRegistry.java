package com.sfgame.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sfgame.SFGame;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-side CTF shop registry. The registry is deliberately mode-scoped. */
public final class CtfShopRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FMLPaths.CONFIGDIR.get().resolve("sfgame").resolve("shop").resolve("ctf.json");
    private volatile Map<String, ShopItem> items = Map.of();
    private volatile List<String> errors = List.of();

    public synchronized List<String> reload() {
        List<String> problems = new ArrayList<>();
        try {
            createDefaultIfMissing();
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonArray array = root.has("items") && root.get("items").isJsonArray()
                    ? root.getAsJsonArray("items") : new JsonArray();
            Map<String, ShopItem> loaded = new LinkedHashMap<>();
            for (var element : array) {
                if (!element.isJsonObject()) { problems.add("CTF shop item must be an object"); continue; }
                try {
                    ShopItem item = GSON.fromJson(element, ShopItem.class).normalized();
                    if (loaded.putIfAbsent(item.id(), item) != null) problems.add("Duplicate CTF shop item: " + item.id());
                    if (item.stack().isEmpty()) problems.add(item.id() + ": invalid item " + item.item());
                } catch (RuntimeException exception) {
                    problems.add("Invalid CTF shop item: " + exception.getMessage());
                }
            }
            if (problems.isEmpty()) items = Collections.unmodifiableMap(loaded);
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            problems.add(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            SFGame.LOGGER.error("Could not load CTF shop from {}", path, exception);
        }
        errors = List.copyOf(problems);
        return errors;
    }

    public List<ShopItem> items() { return List.copyOf(items.values()); }
    public ShopItem item(String id) { return items.get(id == null ? "" : id.toLowerCase(Locale.ROOT)); }
    public List<String> errors() { return errors; }
    public Path path() { return path; }

    private void createDefaultIfMissing() throws IOException {
        if (Files.exists(path)) return;
        Files.createDirectories(path.getParent());
        try (InputStream input = CtfShopRegistry.class.getResourceAsStream("/defaults/shop/ctf.json")) {
            if (input == null) throw new IOException("Bundled defaults/shop/ctf.json is missing");
            Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record ShopItem(String id, String name, String icon, int price, String item, int count, String nbt) {
        ShopItem normalized() {
            String normalizedId = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            if (!normalizedId.matches("[a-z0-9][a-z0-9_]{0,31}")) throw new IllegalArgumentException("invalid id " + id);
            return new ShopItem(normalizedId, name == null ? normalizedId : name,
                    icon == null ? "minecraft:paper" : icon, Math.max(0, price),
                    item == null ? "minecraft:air" : item, Math.max(1, Math.min(64, count)), nbt == null ? "" : nbt);
        }

        ItemStack stack() {
            ResourceLocation resource = ResourceLocation.tryParse(item);
            if (resource == null || !BuiltInRegistries.ITEM.containsKey(resource)) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(resource), count);
            if (!nbt.isBlank()) {
                try { stack.setTag(TagParser.parseTag(nbt)); }
                catch (Exception exception) { return ItemStack.EMPTY; }
            }
            return stack;
        }

        ItemStack iconStack() {
            ResourceLocation resource = ResourceLocation.tryParse(icon);
            return resource != null && BuiltInRegistries.ITEM.containsKey(resource)
                    ? new ItemStack(BuiltInRegistries.ITEM.get(resource)) : new ItemStack(Items.PAPER);
        }
    }
}
