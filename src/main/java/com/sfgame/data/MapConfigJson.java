package com.sfgame.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** JSON representation for the compact map document. */
public final class MapConfigJson {
    private MapConfigJson() {
    }

    public static JsonObject write(ArenaMap map) {
        return write(map.save());
    }

    public static ArenaMap read(JsonObject object) {
        return ArenaMap.load(readCompound(object));
    }

    public static JsonObject write(CompoundTag tag) {
        return (JsonObject) writeTag(tag);
    }

    public static CompoundTag readCompound(JsonObject object) {
        return (CompoundTag) readTag(object);
    }

    private static JsonElement writeTag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            JsonObject object = new JsonObject();
            for (String key : compound.getAllKeys()) {
                object.add(toJsonKey(key), writeTag(compound.get(key)));
            }
            return object;
        }
        if (tag instanceof ListTag list) {
            JsonArray array = new JsonArray();
            for (Tag child : list) array.add(writeTag(child));
            return array;
        }
        if (tag instanceof ByteArrayTag bytes) {
            JsonArray array = new JsonArray();
            for (byte value : bytes.getAsByteArray()) array.add(value);
            return array;
        }
        if (tag instanceof IntArrayTag ints) {
            JsonArray array = new JsonArray();
            for (int value : ints.getAsIntArray()) array.add(value);
            return array;
        }
        if (tag instanceof LongArrayTag longs) {
            JsonArray array = new JsonArray();
            for (long value : longs.getAsLongArray()) array.add(value);
            return array;
        }
        if (tag instanceof StringTag) return new JsonPrimitive(tag.getAsString());
        if (tag instanceof NumericTag numeric) {
            if (tag.getId() == Tag.TAG_BYTE && (numeric.getAsByte() == 0 || numeric.getAsByte() == 1)) {
                return new JsonPrimitive(numeric.getAsByte() == 1);
            }
            if (tag.getId() == Tag.TAG_FLOAT) return new JsonPrimitive(numeric.getAsFloat());
            if (tag.getId() == Tag.TAG_DOUBLE) return new JsonPrimitive(numeric.getAsDouble());
            if (tag.getId() == Tag.TAG_LONG) return new JsonPrimitive(numeric.getAsLong());
            if (tag.getId() == Tag.TAG_SHORT) return new JsonPrimitive(numeric.getAsShort());
            return new JsonPrimitive(numeric.getAsInt());
        }
        return new JsonPrimitive(tag.getAsString());
    }

    private static Tag readTag(JsonElement element) {
        if (element == null || element.isJsonNull()) return StringTag.valueOf("");
        if (element.isJsonObject()) {
            CompoundTag compound = new CompoundTag();
            for (var entry : element.getAsJsonObject().entrySet()) {
                compound.put(toNbtKey(entry.getKey()), readTag(entry.getValue()));
            }
            return compound;
        }
        if (element.isJsonArray()) {
            ListTag list = new ListTag();
            for (JsonElement child : element.getAsJsonArray()) list.add(readTag(child));
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean() ? ByteTag.ONE : ByteTag.ZERO;
        if (primitive.isNumber()) return DoubleTag.valueOf(primitive.getAsDouble());
        return StringTag.valueOf(primitive.getAsString());
    }

    private static String toJsonKey(String key) {
        if (key == null || key.isEmpty()) return key;
        return Character.toLowerCase(key.charAt(0)) + key.substring(1);
    }

    private static String toNbtKey(String key) {
        if (key == null || key.isEmpty()) return key;
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }
}
