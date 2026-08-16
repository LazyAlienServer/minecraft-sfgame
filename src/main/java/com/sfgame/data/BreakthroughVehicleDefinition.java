package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * A configurable vehicle slot used by Breakthrough maps.
 *
 * <p>The entity id is deliberately stored as a resource id instead of a
 * compile-time class reference.  This keeps SFGame optional with respect to
 * vehicle mods; the selected entity must simply be registered when a match is
 * validated.</p>
 */
public final class BreakthroughVehicleDefinition {
    public static final double DEFAULT_SPAWN_Y_OFFSET = 0.2D;
    public static final String DEFAULT_AMMO_ITEM = "superbwarfare:creative_ammo_box";
    public static final int MAX_AMMO_ENTRIES = 16;
    public static final int MAX_AMMO_COUNT = 4096;

    public record AmmoEntry(String itemId, int count) {
        public AmmoEntry {
            itemId = SFGameId.normalizeResource(itemId);
            if (count < 1 || count > MAX_AMMO_COUNT) {
                throw new IllegalArgumentException("Vehicle ammo count must be between 1 and " + MAX_AMMO_COUNT);
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Item", itemId);
            tag.putInt("Count", count);
            return tag;
        }
    }

    public enum Role {
        ATTACKER("attacker"),
        DEFENDER("defender");

        private final String id;

        Role(String id) { this.id = id; }
        public String id() { return id; }

        public static Role fromId(String value) {
            if (value == null) return null;
            for (Role role : values()) if (role.id.equalsIgnoreCase(value)) return role;
            return null;
        }
    }

    private final String id;
    private String entityId;
    private Role role;
    private ArenaPosition spawn;
    private int respawnSeconds;
    private double spawnYOffset = DEFAULT_SPAWN_Y_OFFSET;
    private int energyPercent = 100;
    private final List<AmmoEntry> ammo = new ArrayList<>();

    public BreakthroughVehicleDefinition(String id, String entityId, Role role,
                                         ArenaPosition spawn, int respawnSeconds) {
        this.id = SFGameId.normalize(id);
        this.entityId = SFGameId.normalizeResource(entityId);
        this.role = role == null ? Role.ATTACKER : role;
        if (spawn == null) throw new IllegalArgumentException("Vehicle spawn position is required");
        this.spawn = spawn;
        respawnSeconds(respawnSeconds);
        ammo.add(new AmmoEntry(DEFAULT_AMMO_ITEM, 1));
    }

    public String id() { return id; }
    public String entityId() { return entityId; }
    public Role role() { return role; }
    public ArenaPosition spawn() { return spawn; }
    public int respawnSeconds() { return respawnSeconds; }
    public double spawnYOffset() { return spawnYOffset; }
    public int energyPercent() { return energyPercent; }
    public List<AmmoEntry> ammo() { return List.copyOf(ammo); }

    public void entityId(String value) { entityId = SFGameId.normalizeResource(value); }
    public void role(Role value) { role = value == null ? Role.ATTACKER : value; }
    public void spawn(ArenaPosition value) {
        if (value == null) throw new IllegalArgumentException("Vehicle spawn position is required");
        spawn = value;
    }
    public void respawnSeconds(int value) {
        if (value < 1 || value > 3600) throw new IllegalArgumentException("Vehicle respawn seconds must be between 1 and 3600");
        respawnSeconds = value;
    }
    public void spawnYOffset(double value) {
        if (!Double.isFinite(value) || value < -64.0D || value > 64.0D) {
            throw new IllegalArgumentException("Vehicle spawn Y offset must be between -64 and 64");
        }
        spawnYOffset = value;
    }
    public void energyPercent(int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Vehicle energy percent must be between 0 and 100");
        }
        energyPercent = value;
    }
    public void setAmmo(String itemId, int count) {
        AmmoEntry entry = new AmmoEntry(itemId, count);
        ammo.removeIf(existing -> existing.itemId().equals(entry.itemId()));
        if (ammo.size() >= MAX_AMMO_ENTRIES) {
            throw new IllegalArgumentException("A vehicle can have at most " + MAX_AMMO_ENTRIES + " ammo entries");
        }
        ammo.add(entry);
    }
    public boolean removeAmmo(String itemId) {
        String normalized = SFGameId.normalizeResource(itemId);
        return ammo.removeIf(entry -> entry.itemId().equals(normalized));
    }
    public void clearAmmo() { ammo.clear(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Entity", entityId);
        tag.putString("Role", role.id());
        tag.put("Spawn", spawn.save());
        tag.putInt("RespawnSeconds", respawnSeconds);
        tag.putDouble("SpawnYOffset", spawnYOffset);
        tag.putInt("EnergyPercent", energyPercent);
        ListTag ammoList = new ListTag();
        ammo.forEach(entry -> ammoList.add(entry.save()));
        tag.put("Ammo", ammoList);
        return tag;
    }

    public static BreakthroughVehicleDefinition load(CompoundTag tag) {
        Role role = Role.fromId(tag.getString("Role"));
        if (role == null) role = Role.ATTACKER;
        BreakthroughVehicleDefinition definition = new BreakthroughVehicleDefinition(tag.getString("Id"),
                tag.getString("Entity"), role, ArenaPosition.load(tag.getCompound("Spawn")),
                Math.max(1, tag.getInt("RespawnSeconds")));
        if (tag.contains("SpawnYOffset", Tag.TAG_DOUBLE)) definition.spawnYOffset(tag.getDouble("SpawnYOffset"));
        if (tag.contains("EnergyPercent", Tag.TAG_INT)) definition.energyPercent(tag.getInt("EnergyPercent"));
        if (tag.contains("Ammo", Tag.TAG_LIST)) {
            definition.clearAmmo();
            ListTag list = tag.getList("Ammo", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_AMMO_ENTRIES, list.size()); i++) {
                CompoundTag entry = list.getCompound(i);
                definition.setAmmo(entry.getString("Item"), entry.getInt("Count"));
            }
        }
        return definition;
    }

    @Override
    public String toString() {
        return id + "=" + entityId + " (" + role.id() + ", " + respawnSeconds + "s, yOffset="
                + spawnYOffset + ", energy=" + energyPercent + "%, ammo=" + ammo + ")";
    }
}
