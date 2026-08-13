package com.sfgame.data;

import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;
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
    private String yellowTeam = "sfgame_yellow";
    private String greenTeam = "sfgame_green";
    private String selectedMode = GameModeRegistry.TEAM_DEATHMATCH;
    private String selectedMap = DEFAULT_MAP;
    private final Map<String, LinkedHashMap<String, ArenaMap>> modeMaps = new LinkedHashMap<>();
    private final Map<String, MatchRules> modeRules = new LinkedHashMap<>();

    public SFGameSavedData() {
        mapsFor(GameModeRegistry.TEAM_DEATHMATCH).put(DEFAULT_MAP, new ArenaMap(DEFAULT_MAP));
        GameModeRegistry.all().forEach(mode -> modeRules.put(mode.id(), new MatchRules(mode.id())));
    }

    public static SFGameSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SFGameSavedData::load, SFGameSavedData::new, DATA_NAME);
    }

    public String redTeam() { return redTeam; }
    public String blueTeam() { return blueTeam; }
    public void redTeam(String value) { redTeam = value; setDirty(); }
    public void blueTeam(String value) { blueTeam = value; setDirty(); }
    public String yellowTeam() { return yellowTeam; }
    public String greenTeam() { return greenTeam; }
    public void yellowTeam(String value) { yellowTeam = value; setDirty(); }
    public void greenTeam(String value) { greenTeam = value; setDirty(); }
    public String teamName(TeamSide side) {
        return switch (side) {
            case RED -> redTeam;
            case BLUE -> blueTeam;
            case YELLOW -> yellowTeam;
            case GREEN -> greenTeam;
            case NONE -> "";
        };
    }
    public void teamName(TeamSide side, String value) {
        switch (side) {
            case RED -> redTeam(value);
            case BLUE -> blueTeam(value);
            case YELLOW -> yellowTeam(value);
            case GREEN -> greenTeam(value);
            case NONE -> throw new IllegalArgumentException("NONE is not a playable team");
        }
    }
    public String selectedMode() { return selectedMode; }
    public String selectedMap() { return selectedMap; }
    public MatchRules rules() { return rules(selectedMode); }
    public MatchRules rules(String modeId) { return modeRules.computeIfAbsent(modeId, MatchRules::new); }

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
    public java.util.List<ArenaPosition> spawns(TeamSide side) { return activeMap() == null ? java.util.List.of() : activeMap().spawns(side); }
    public java.util.List<TeamSide> enabledTeams() { return activeMap() == null ? java.util.List.of() : activeMap().enabledTeams(); }
    @Nullable public ArenaPosition randomSpawn(TeamSide side) { return activeMap() == null ? null : activeMap().randomSpawn(side); }
    public void lobby(ArenaPosition value) { activeMapRequired().lobby(value); setDirty(); }
    public void addSpawn(TeamSide side, ArenaPosition value) { activeMapRequired().addSpawn(side, value); setDirty(); }
    public boolean removeSpawn(TeamSide side, int index) { boolean changed = activeMapRequired().removeSpawn(side, index); if (changed) setDirty(); return changed; }
    public void clearSpawns(TeamSide side) { activeMapRequired().clearSpawns(side); setDirty(); }
    public boolean isArenaConfigured() { return mapConfigured(activeMap(), selectedMode); }
    public boolean mapConfigured(ArenaMap map) { return mapConfigured(map, selectedMode); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", 5);
        tag.putString("RedTeam", redTeam);
        tag.putString("BlueTeam", blueTeam);
        tag.putString("YellowTeam", yellowTeam);
        tag.putString("GreenTeam", greenTeam);
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
        ListTag ruleList = new ListTag();
        modeRules.forEach((modeId, rules) -> {
            CompoundTag ruleTag = rules.save(); ruleTag.putString("Mode", modeId); ruleList.add(ruleTag);
        });
        tag.put("ModeRules", ruleList);
        return tag;
    }

    static SFGameSavedData load(CompoundTag tag) {
        SFGameSavedData data = new SFGameSavedData();
        if (tag.contains("RedTeam")) data.redTeam = tag.getString("RedTeam");
        if (tag.contains("BlueTeam")) data.blueTeam = tag.getString("BlueTeam");
        if (tag.contains("YellowTeam")) data.yellowTeam = tag.getString("YellowTeam");
        if (tag.contains("GreenTeam")) data.greenTeam = tag.getString("GreenTeam");
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
            if (tag.contains("RedSpawn")) legacy.addSpawn(TeamSide.RED, ArenaPosition.load(tag.getCompound("RedSpawn")));
            if (tag.contains("BlueSpawn")) legacy.addSpawn(TeamSide.BLUE, ArenaPosition.load(tag.getCompound("BlueSpawn")));
        }
        LinkedHashMap<String, ArenaMap> selectedMaps = data.mapsFor(data.selectedMode);
        if (selectedMaps.isEmpty()) selectedMaps.put(DEFAULT_MAP, new ArenaMap(DEFAULT_MAP));
        if (!selectedMaps.containsKey(data.selectedMap)) data.selectedMap = selectedMaps.keySet().iterator().next();
        if (tag.contains("ModeRules", Tag.TAG_LIST)) {
            ListTag ruleList = tag.getList("ModeRules", Tag.TAG_COMPOUND);
            for (int i = 0; i < ruleList.size(); i++) {
                CompoundTag ruleTag = ruleList.getCompound(i);
                String modeId = ruleTag.getString("Mode");
                if (GameModeRegistry.get(modeId).isPresent()) data.rules(modeId).load(ruleTag);
            }
        } else if (tag.contains("Rules")) {
            // Version 4 migration: the only rule set belonged to TDM.
            data.rules(GameModeRegistry.TEAM_DEATHMATCH).load(tag.getCompound("Rules"));
        }
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

    private static boolean mapConfigured(ArenaMap map, String modeId) {
        if (map == null || !map.configured()) return false;
        return !GameModeRegistry.DOMINATION.equals(modeId) || map.domination().configured();
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[a-z][a-z0-9_]{0,31}");
    }
}
