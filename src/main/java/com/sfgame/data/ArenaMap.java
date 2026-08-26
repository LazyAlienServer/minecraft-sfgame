package com.sfgame.data;

import com.sfgame.game.TeamSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ArenaMap {
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;
    private final String id;
    private String displayName;
    private ArenaPosition lobby;
    private final Map<TeamSide, List<ArenaPosition>> spawns = new EnumMap<>(TeamSide.class);
    private DominationMapConfig domination = new DominationMapConfig();
    private BreakthroughMapConfig breakthrough = new BreakthroughMapConfig();
    private CaptureTheFlagMapConfig captureTheFlag = new CaptureTheFlagMapConfig();
    private MapBuildConfig build = new MapBuildConfig();
    private SupplyMapConfig supply = new SupplyMapConfig();

    public ArenaMap(String id) {
        this.id = SFGameId.normalize(id);
        this.displayName = this.id;
        TeamSide.PLAYABLE.forEach(side -> spawns.put(side, new ArrayList<>()));
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public void displayName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("Map display name must be at most "
                    + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
        displayName = normalized.isEmpty() ? id : normalized;
    }
    @Nullable public ArenaPosition lobby() { return lobby; }
    public void lobby(ArenaPosition value) { lobby = value; }
    public void clearLobby() { lobby = null; }
    public List<ArenaPosition> spawns(TeamSide side) { return Collections.unmodifiableList(spawnList(side)); }
    public void addSpawn(TeamSide side, ArenaPosition value) { spawnList(side).add(value); }
    public boolean removeSpawn(TeamSide side, int index) {
        List<ArenaPosition> positions = spawnList(side);
        if (index < 0 || index >= positions.size()) return false;
        positions.remove(index);
        return true;
    }
    public void clearSpawns(TeamSide side) { spawnList(side).clear(); }
    @Nullable public ArenaPosition randomSpawn(TeamSide side) {
        List<ArenaPosition> positions = spawnList(side);
        return positions.isEmpty() ? null : positions.get(ThreadLocalRandom.current().nextInt(positions.size()));
    }
    public List<TeamSide> enabledTeams() {
        return TeamSide.PLAYABLE.stream().filter(side -> !spawnList(side).isEmpty()).toList();
    }
    public boolean configured() { return lobby != null && enabledTeams().size() >= 2; }
    public DominationMapConfig domination() { return domination; }
    public BreakthroughMapConfig breakthrough() { return breakthrough; }
    public CaptureTheFlagMapConfig captureTheFlag() { return captureTheFlag; }
    public MapBuildConfig build() { return build; }
    public SupplyMapConfig supply() { return supply; }
    public boolean hasLocalConfiguration() {
        return !displayName.equals(id) || lobby != null || enabledTeams().size() > 0 || !domination.points().isEmpty()
                || !breakthrough.sectors().isEmpty() || !breakthrough.vehicles().isEmpty()
                || !captureTheFlag.homes().isEmpty() || !captureTheFlag.forwardFlags().isEmpty()
                || build.region() != null || build.snapshotSaved() || supply.configured();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        if (lobby != null) tag.put("Lobby", lobby.save());
        TeamSide.PLAYABLE.forEach(side -> tag.put(spawnKey(side), savePositions(spawnList(side))));
        tag.put("Domination", domination.save());
        tag.put("Breakthrough", breakthrough.save());
        tag.put("CaptureTheFlag", captureTheFlag.save());
        tag.put("Build", build.save());
        tag.put("Supply", supply.save());
        return tag;
    }

    public static ArenaMap load(CompoundTag tag) {
        ArenaMap map = new ArenaMap(tag.getString("Id"));
        if (tag.contains("Lobby")) map.lobby = ArenaPosition.load(tag.getCompound("Lobby"));
        TeamSide.PLAYABLE.forEach(side -> loadPositions(tag, spawnKey(side), map.spawnList(side)));
        // Data version 2 maps stored one spawn per red/blue side.
        if (map.spawnList(TeamSide.RED).isEmpty() && tag.contains("RedSpawn")) {
            map.addSpawn(TeamSide.RED, ArenaPosition.load(tag.getCompound("RedSpawn")));
        }
        if (map.spawnList(TeamSide.BLUE).isEmpty() && tag.contains("BlueSpawn")) {
            map.addSpawn(TeamSide.BLUE, ArenaPosition.load(tag.getCompound("BlueSpawn")));
        }
        if (tag.contains("Domination")) map.domination = DominationMapConfig.load(tag.getCompound("Domination"));
        if (tag.contains("Breakthrough")) map.breakthrough = BreakthroughMapConfig.load(tag.getCompound("Breakthrough"));
        if (tag.contains("CaptureTheFlag")) map.captureTheFlag = CaptureTheFlagMapConfig.load(tag.getCompound("CaptureTheFlag"));
        if (tag.contains("Build")) map.build = MapBuildConfig.load(tag.getCompound("Build"));
        if (tag.contains("Supply")) map.supply = SupplyMapConfig.load(tag.getCompound("Supply"));
        return map;
    }

    private List<ArenaPosition> spawnList(TeamSide side) {
        List<ArenaPosition> positions = spawns.get(side);
        if (positions == null) throw new IllegalArgumentException("Not a playable team: " + side);
        return positions;
    }

    private static String spawnKey(TeamSide side) {
        return Character.toUpperCase(side.id().charAt(0)) + side.id().substring(1) + "Spawns";
    }

    private static ListTag savePositions(List<ArenaPosition> positions) {
        ListTag list = new ListTag();
        positions.forEach(position -> list.add(position.save()));
        return list;
    }

    private static void loadPositions(CompoundTag tag, String key, List<ArenaPosition> destination) {
        if (!tag.contains(key, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) destination.add(ArenaPosition.load(list.getCompound(i)));
    }
}
