package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/** Immutable map-owned supply payload. */
public record SupplyOfferDefinition(String id, String type, String item, int count, String nbt, String classId) {
    public static final String ITEM = "item";
    public static final String ELITE_CLASS = "elite_class";

    public SupplyOfferDefinition {
        id = SFGameId.normalize(id);
        type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!ITEM.equals(type) && !ELITE_CLASS.equals(type)) {
            throw new IllegalArgumentException("Unknown supply offer type: " + type);
        }
        item = item == null ? "" : item.trim();
        nbt = nbt == null ? "" : nbt.trim();
        classId = classId == null || classId.isBlank() ? "" : SFGameId.normalizeClass(classId.trim());
        if (ITEM.equals(type)) {
            if (ItemStrings.parse(item).id() == null) throw new IllegalArgumentException(id + ": invalid item selector " + item);
            if (count < 1 || count > 64) throw new IllegalArgumentException(id + ": item count must be 1..64");
            if (!classId.isEmpty()) throw new IllegalArgumentException(id + ": item offer cannot define classId");
        } else {
            if (classId.isEmpty()) throw new IllegalArgumentException(id + ": elite_class offer needs classId");
            if (!item.isEmpty() || !nbt.isEmpty()) throw new IllegalArgumentException(id + ": elite_class offer cannot define item data");
            count = 1;
        }
    }

    public static SupplyOfferDefinition item(String id, String item, int count, String nbt) {
        return new SupplyOfferDefinition(id, ITEM, item, count, nbt, "");
    }

    public static SupplyOfferDefinition elite(String id, String classId) {
        return new SupplyOfferDefinition(id, ELITE_CLASS, "", 1, "", classId);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Type", type);
        if (ITEM.equals(type)) {
            tag.putString("Item", item);
            tag.putInt("Count", count);
            if (!nbt.isEmpty()) tag.putString("Nbt", nbt);
        } else {
            tag.putString("ClassId", classId);
        }
        return tag;
    }

    static SupplyOfferDefinition load(CompoundTag tag) {
        return new SupplyOfferDefinition(tag.getString("Id"), tag.getString("Type"), tag.getString("Item"),
                tag.contains("Count") ? tag.getInt("Count") : 1, tag.getString("Nbt"), tag.getString("ClassId"));
    }
}
