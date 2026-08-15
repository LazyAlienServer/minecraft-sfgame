package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;

/**
 * A configurable vehicle slot used by Breakthrough maps.
 *
 * <p>The entity id is deliberately stored as a resource id instead of a
 * compile-time class reference.  This keeps SFGame optional with respect to
 * vehicle mods; the selected entity must simply be registered when a match is
 * validated.</p>
 */
public final class BreakthroughVehicleDefinition {
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

    public BreakthroughVehicleDefinition(String id, String entityId, Role role,
                                         ArenaPosition spawn, int respawnSeconds) {
        this.id = SFGameId.normalize(id);
        this.entityId = SFGameId.normalizeResource(entityId);
        this.role = role == null ? Role.ATTACKER : role;
        if (spawn == null) throw new IllegalArgumentException("Vehicle spawn position is required");
        this.spawn = spawn;
        respawnSeconds(respawnSeconds);
    }

    public String id() { return id; }
    public String entityId() { return entityId; }
    public Role role() { return role; }
    public ArenaPosition spawn() { return spawn; }
    public int respawnSeconds() { return respawnSeconds; }

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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Entity", entityId);
        tag.putString("Role", role.id());
        tag.put("Spawn", spawn.save());
        tag.putInt("RespawnSeconds", respawnSeconds);
        return tag;
    }

    public static BreakthroughVehicleDefinition load(CompoundTag tag) {
        Role role = Role.fromId(tag.getString("Role"));
        if (role == null) role = Role.ATTACKER;
        return new BreakthroughVehicleDefinition(tag.getString("Id"), tag.getString("Entity"), role,
                ArenaPosition.load(tag.getCompound("Spawn")), Math.max(1, tag.getInt("RespawnSeconds")));
    }

    @Override
    public String toString() {
        return id + "=" + entityId + " (" + role.id() + ", " + respawnSeconds + "s)";
    }
}
