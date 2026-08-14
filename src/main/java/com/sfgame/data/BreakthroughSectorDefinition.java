package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class BreakthroughSectorDefinition {
    public static final int MAX_POINTS = 16;
    private final String id;
    private int order;
    private final List<CapturePointDefinition> points = new ArrayList<>();
    private final List<ArenaPosition> attackerSpawns = new ArrayList<>();
    private final List<ArenaPosition> defenderSpawns = new ArrayList<>();

    public BreakthroughSectorDefinition(String id, int order) {
        this.id = normalizeId(id);
        if (order < 1) throw new IllegalArgumentException("Sector order must be positive");
        this.order = order;
    }

    public String id() { return id; }
    public int order() { return order; }
    public void order(int value) {
        if (value < 1) throw new IllegalArgumentException("Sector order must be positive");
        order = value;
    }
    public List<CapturePointDefinition> points() {
        return points.stream().sorted(Comparator.comparingInt(CapturePointDefinition::order)
                .thenComparing(CapturePointDefinition::id)).toList();
    }
    public Optional<CapturePointDefinition> point(String pointId) {
        String normalized = CapturePointDefinition.normalizeId(pointId);
        return points.stream().filter(point -> point.id().equals(normalized)).findFirst();
    }
    public void addPoint(CapturePointDefinition point) {
        if (points.size() >= MAX_POINTS) throw new IllegalArgumentException("A sector can have at most 16 capture points");
        if (point(point.id()).isPresent()) throw new IllegalArgumentException("Duplicate point id: " + point.id());
        validateNoOverlap(point, null);
        points.add(point);
    }
    public void replacePoint(String pointId, CapturePointDefinition replacement) {
        int index = indexOfPoint(pointId);
        validateNoOverlap(replacement, replacement.id());
        points.set(index, replacement);
    }
    public boolean removePoint(String pointId) {
        String normalized = CapturePointDefinition.normalizeId(pointId);
        return points.removeIf(point -> point.id().equals(normalized));
    }
    public List<ArenaPosition> spawns(boolean attacker) { return List.copyOf(attacker ? attackerSpawns : defenderSpawns); }
    public void addSpawn(boolean attacker, ArenaPosition position) { (attacker ? attackerSpawns : defenderSpawns).add(position); }
    public boolean removeSpawn(boolean attacker, int index) {
        List<ArenaPosition> list = attacker ? attackerSpawns : defenderSpawns;
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        return true;
    }
    public void clearSpawns(boolean attacker) { (attacker ? attackerSpawns : defenderSpawns).clear(); }
    @Nullable public ArenaPosition randomSpawn(boolean attacker) {
        List<ArenaPosition> list = attacker ? attackerSpawns : defenderSpawns;
        return list.isEmpty() ? null : list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (points.isEmpty()) errors.add("Sector " + id + " needs at least one capture point");
        if (points.size() > MAX_POINTS) errors.add("Sector " + id + " has more than 16 capture points");
        if (attackerSpawns.isEmpty()) errors.add("Sector " + id + " needs an attacker spawn");
        if (defenderSpawns.isEmpty()) errors.add("Sector " + id + " needs a defender spawn");
        for (int i = 0; i < points.size(); i++) for (int j = i + 1; j < points.size(); j++) {
            if (points.get(i).region().overlaps(points.get(j).region())) {
                errors.add("Sector " + id + " capture points overlap: " + points.get(i).id() + " and " + points.get(j).id());
            }
        }
        return errors;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id); tag.putInt("Order", order);
        ListTag pointList = new ListTag(); points().forEach(point -> pointList.add(point.save())); tag.put("Points", pointList);
        tag.put("AttackerSpawns", savePositions(attackerSpawns));
        tag.put("DefenderSpawns", savePositions(defenderSpawns));
        return tag;
    }

    public static BreakthroughSectorDefinition load(CompoundTag tag) {
        BreakthroughSectorDefinition sector = new BreakthroughSectorDefinition(tag.getString("Id"), Math.max(1, tag.getInt("Order")));
        if (tag.contains("Points", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Points", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_POINTS, list.size()); i++) {
                try { sector.addPoint(CapturePointDefinition.load(list.getCompound(i))); } catch (IllegalArgumentException ignored) { }
            }
        }
        loadPositions(tag, "AttackerSpawns", sector.attackerSpawns);
        loadPositions(tag, "DefenderSpawns", sector.defenderSpawns);
        return sector;
    }

    public static String normalizeId(String value) {
        try {
            return SFGameId.normalize(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid sector id: " + value, exception);
        }
    }

    private int indexOfPoint(String pointId) {
        String normalized = CapturePointDefinition.normalizeId(pointId);
        for (int i = 0; i < points.size(); i++) if (points.get(i).id().equals(normalized)) return i;
        throw new IllegalArgumentException("Unknown capture point: " + pointId);
    }
    private void validateNoOverlap(CapturePointDefinition candidate, String ignoredId) {
        for (CapturePointDefinition existing : points) {
            if (existing.id().equals(ignoredId)) continue;
            if (candidate.region().overlaps(existing.region())) throw new IllegalArgumentException("Capture point overlaps " + existing.id());
        }
    }
    private static ListTag savePositions(List<ArenaPosition> positions) {
        ListTag list = new ListTag(); positions.forEach(position -> list.add(position.save())); return list;
    }
    private static void loadPositions(CompoundTag tag, String key, List<ArenaPosition> destination) {
        if (!tag.contains(key, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) destination.add(ArenaPosition.load(list.getCompound(i)));
    }
}
