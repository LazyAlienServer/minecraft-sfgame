package com.sfgame.data;

import com.sfgame.game.TeamSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class BreakthroughMapConfig {
    public static final int MAX_SECTORS = 16;
    private BreakthroughVariant variant = BreakthroughVariant.NORMAL;
    private int legs = 1;
    private TeamSide attacker = TeamSide.RED;
    private TeamSide defender = TeamSide.BLUE;
    private final List<BreakthroughSectorDefinition> sectors = new ArrayList<>();

    public BreakthroughVariant variant() { return variant; }
    public void variant(BreakthroughVariant value) { variant = value == null ? BreakthroughVariant.NORMAL : value; }
    public int legs() { return legs; }
    public void legs(int value) {
        if (value != 1 && value != 2) throw new IllegalArgumentException("Breakthrough legs must be 1 or 2");
        legs = value;
    }
    public TeamSide attacker() { return attacker; }
    public TeamSide defender() { return defender; }
    public void roles(TeamSide attacker, TeamSide defender) {
        if (attacker == null || defender == null || attacker == TeamSide.NONE || defender == TeamSide.NONE || attacker == defender) {
            throw new IllegalArgumentException("Attacker and defender must be different playable teams");
        }
        this.attacker = attacker; this.defender = defender;
    }
    public List<TeamSide> teams() { return List.of(attacker, defender); }
    public List<BreakthroughSectorDefinition> sectors() {
        return sectors.stream().sorted(Comparator.comparingInt((BreakthroughSectorDefinition sector) -> sector.order())
                .thenComparing(BreakthroughSectorDefinition::id)).toList();
    }
    public Optional<BreakthroughSectorDefinition> sector(String id) {
        String normalized = BreakthroughSectorDefinition.normalizeId(id);
        return sectors.stream().filter(sector -> sector.id().equals(normalized)).findFirst();
    }
    public void addSector(BreakthroughSectorDefinition sector) {
        if (sectors.size() >= MAX_SECTORS) throw new IllegalArgumentException("A map can have at most 16 sectors");
        if (sector(sector.id()).isPresent()) throw new IllegalArgumentException("Duplicate sector id: " + sector.id());
        sectors.add(sector);
    }
    public boolean removeSector(String id) {
        String normalized = BreakthroughSectorDefinition.normalizeId(id);
        return sectors.removeIf(sector -> sector.id().equals(normalized));
    }
    public void clear() { sectors.clear(); }
    public boolean configured() { return validate().isEmpty(); }
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (attacker == TeamSide.NONE || defender == TeamSide.NONE || attacker == defender) errors.add("Breakthrough needs different attacker and defender teams");
        if (sectors.isEmpty()) errors.add("Breakthrough map needs at least one sector");
        if (sectors.size() > MAX_SECTORS) errors.add("Breakthrough map has more than 16 sectors");
        sectors().forEach(sector -> errors.addAll(sector.validate()));
        return errors;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Variant", variant.name()); tag.putInt("Legs", legs);
        tag.putString("Attacker", attacker.id()); tag.putString("Defender", defender.id());
        ListTag list = new ListTag(); sectors().forEach(sector -> list.add(sector.save())); tag.put("Sectors", list);
        return tag;
    }
    public static BreakthroughMapConfig load(CompoundTag tag) {
        BreakthroughMapConfig config = new BreakthroughMapConfig();
        try { config.variant = BreakthroughVariant.valueOf(tag.getString("Variant")); } catch (IllegalArgumentException ignored) { }
        if (tag.contains("Legs")) config.legs = tag.getInt("Legs") == 2 ? 2 : 1;
        TeamSide attacker = TeamSide.fromId(tag.getString("Attacker"));
        TeamSide defender = TeamSide.fromId(tag.getString("Defender"));
        if (attacker != TeamSide.NONE && defender != TeamSide.NONE && attacker != defender) config.roles(attacker, defender);
        if (tag.contains("Sectors", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Sectors", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_SECTORS, list.size()); i++) {
                try { config.addSector(BreakthroughSectorDefinition.load(list.getCompound(i))); } catch (IllegalArgumentException ignored) { }
            }
        }
        return config;
    }
}
