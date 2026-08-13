package com.sfgame.data;

import com.sfgame.game.GameModeRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SFGameSavedData extends SavedData {
    private static final String DATA_NAME = "sfgame";
    private static final String DEFAULT_MAP = "default";

    private String redTeam = "sfgame_red";
    private String blueTeam = "sfgame_blue";
    private String selectedMode = GameModeRegistry.TEAM_DEATHMATCH;
    private String selectedMap = DEFAULT_MAP;
    private final Map<String, LinkedHashMap<String, ArenaMap>> modeMaps = new LinkedHashMap<>();
    private final MatchRules rules = new MatchRules();

    public SFGameSavedData() {
        mapsFor(GameModeRegistry.TEAM_DEATHMATCH).put(DEFAULT_MAP, new ArenaMap(DEFAULT_MAP));
    }

    public static SFGameSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SFGameSavedData::load, SFGameSavedData::new, DATA_NAME);
    }

    public String redTeam() { return redTeam; }
    public String blueTeam() { return blueTeam; }
    public void redTeam(String value) { redTeam = value; setDirty(); }
    public void blueTeam(String value) { blueTeam = value; setDirty(); }
    public String selectedMode() { return selectedMode; }
    public String selectedMap() { return selectedMap; }
    public MatchRules rules() { return rules; }

    public boolean selectMode(String modeId) {
        if (GameModeRegistry.get(modeId).isEmpty()) return false;
        LinkedHashMap<String, ArenaMap> maps = mapsFor(modeId);
        if (maps.isEmpty()) maps.put(DEFAULT_MAP, new ArenaMap(DEFAULT_MAP));
        selectedMode = modeId;
        if (!maps.containsKey(selectedMap)) selectedMap = maps.keySet().iterator().next();
        setDirty();
        return true;
    }

    public boolean createMap(String mapId) {
        if (!validId(mapId)) return false;
        LinkedHashMap<String, ArenaMap> maps = mapsFor(selectedMode);
        if (maps.putIfAbsent(mapId, new ArenaMap(mapId)) != null) return false;
        selectedMap = mapId;
        setDirty();
        return true;
    }

    public boolean selectMap(String mapId) {
        if (!mapsFor(selectedMode).containsKey(mapId)) return false;
        selectedMap = mapId;
        setDirty();
        return true;
    }

    public boolean removeMap(String mapId) {
        LinkedHashMap<String, ArenaMap> maps = mapsFor(selectedMode);
        if (maps.size() <= 1 || maps.remove(mapId) == null) return false;
        if (selectedMap.equals(mapId)) selectedMap = maps.keySet().iterator().next();
        setDirty();
        return true;
    }

    public Collection<ArenaMap> maps() {
        return Collections.unmodifiableCollection(mapsFor(selectedMode).values());
    }

    @Nullable public ArenaMap activeMap() {
        return mapsFor(selectedMode).get(selectedMap);
    }

    @Nullable public ArenaPosition lobby() { return activeMap() == null ? null : activeMap().lobby(); }
    @Nullable public ArenaPosition redSpawn() { return activeMap() == null ? null : activeMap().redSpawn(); }
    @Nullable public ArenaPosition blueSpawn() { return activeMap() == null ? null : activeMap().blueSpawn(); }
    public void lobby(ArenaPosition value) { activeMapRequired().lobby(value); setDirty(); }
    public void redSpawn(ArenaPosition value) { activeMapRequired().redSpawn(value); setDirty(); }
    public void blueSpawn(ArenaPosition value) { activeMapRequired().blueSpawn(value); setDirty(); }
    public boolean isArenaConfigured() { return activeMap() != null && activeMap().configured(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", 2);
        tag.putString("RedTeam", redTeam);
        tag.putString("BlueTeam", blueTeam);
        tag.putString("SelectedMode", selectedMode);
        tag.putString("SelectedMap", selectedMap);
        ListTag modes = new ListTag();
        modeMaps.forEach((modeId, maps) -> {
            CompoundTag modeTag = new CompoundTag();
            modeTag.putString("Id", modeId);
            ListTag mapList = new ListTag();
            maps.values().forEach(map -> mapList.add(map.save()));
            modeTag.put("Maps", mapList);
            modes.add(modeTag);
        });
        tag.put("Modes", modes);
        tag.put("Rules", rules.save());
        return tag;
    }

    static SFGameSavedData load(CompoundTag tag) {
        SFGameSavedData data = new SFGameSavedData();
        if (tag.contains("RedTeam")) data.redTeam = tag.getString("RedTeam");
        if (tag.contains("BlueTeam")) data.blueTeam = tag.getString("BlueTeam");
        if (tag.contains("Modes", Tag.TAG_LIST)) {
            data.modeMaps.clear();
            ListTag modes = tag.getList("Modes", Tag.TAG_COMPOUND);
            for (int i = 0; i < modes.size(); i++) {
                CompoundTag modeTag = modes.getCompound(i);
                String modeId = modeTag.getString("Id");
                LinkedHashMap<String, ArenaMap> maps = data.mapsFor(modeId);
                ListTag mapList = modeTag.getList("Maps", Tag.TAG_COMPOUND);
                for (int j = 0; j < mapList.size(); j++) {
                    ArenaMap map = ArenaMap.load(mapList.getCompound(j));
                    if (validId(map.id())) maps.put(map.id(), map);
                }
            }
            data.selectedMode = GameModeRegistry.get(tag.getString("SelectedMode")).isPresent()
                    ? tag.getString("SelectedMode") : GameModeRegistry.TEAM_DEATHMATCH;
            data.selectedMap = tag.getString("SelectedMap");
        } else {
            // Version 1 migration: preserve the original single arena as tdm/default.
            ArenaMap legacy = data.mapsFor(GameModeRegistry.TEAM_DEATHMATCH).get(DEFAULT_MAP);
            if (tag.contains("Lobby")) legacy.lobby(ArenaPosition.load(tag.getCompound("Lobby")));
            if (tag.contains("RedSpawn")) legacy.redSpawn(ArenaPosition.load(tag.getCompound("RedSpawn")));
            if (tag.contains("BlueSpawn")) legacy.blueSpawn(ArenaPosition.load(tag.getCompound("BlueSpawn")));
        }
        LinkedHashMap<String, ArenaMap> selectedMaps = data.mapsFor(data.selectedMode);
        if (selectedMaps.isEmpty()) selectedMaps.put(DEFAULT_MAP, new ArenaMap(DEFAULT_MAP));
        if (!selectedMaps.containsKey(data.selectedMap)) data.selectedMap = selectedMaps.keySet().iterator().next();
        if (tag.contains("Rules")) data.rules.load(tag.getCompound("Rules"));
        return data;
    }

    private LinkedHashMap<String, ArenaMap> mapsFor(String modeId) {
        return modeMaps.computeIfAbsent(modeId, ignored -> new LinkedHashMap<>());
    }

    private ArenaMap activeMapRequired() {
        ArenaMap map = activeMap();
        if (map == null) throw new IllegalStateException("No active SFGame map");
        return map;
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[a-z][a-z0-9_]{0,31}");
    }
}
