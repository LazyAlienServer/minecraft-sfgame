package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

public final class ArenaMap {
    private final String id;
    private ArenaPosition lobby;
    private ArenaPosition redSpawn;
    private ArenaPosition blueSpawn;

    public ArenaMap(String id) {
        this.id = id;
    }

    public String id() { return id; }
    @Nullable public ArenaPosition lobby() { return lobby; }
    @Nullable public ArenaPosition redSpawn() { return redSpawn; }
    @Nullable public ArenaPosition blueSpawn() { return blueSpawn; }
    public void lobby(ArenaPosition value) { lobby = value; }
    public void redSpawn(ArenaPosition value) { redSpawn = value; }
    public void blueSpawn(ArenaPosition value) { blueSpawn = value; }
    public boolean configured() { return lobby != null && redSpawn != null && blueSpawn != null; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        if (lobby != null) tag.put("Lobby", lobby.save());
        if (redSpawn != null) tag.put("RedSpawn", redSpawn.save());
        if (blueSpawn != null) tag.put("BlueSpawn", blueSpawn.save());
        return tag;
    }

    public static ArenaMap load(CompoundTag tag) {
        ArenaMap map = new ArenaMap(tag.getString("Id"));
        if (tag.contains("Lobby")) map.lobby = ArenaPosition.load(tag.getCompound("Lobby"));
        if (tag.contains("RedSpawn")) map.redSpawn = ArenaPosition.load(tag.getCompound("RedSpawn"));
        if (tag.contains("BlueSpawn")) map.blueSpawn = ArenaPosition.load(tag.getCompound("BlueSpawn"));
        return map;
    }
}
