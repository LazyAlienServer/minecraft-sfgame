package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class DominationMapConfig {
    public static final int MAX_POINTS = 16;
    private final List<CapturePointDefinition> points = new ArrayList<>();
    private PointActivationStrategy strategy = PointActivationStrategy.ASYNC;

    public PointActivationStrategy strategy() { return strategy; }
    public void strategy(PointActivationStrategy value) { strategy = value; }
    public List<CapturePointDefinition> points() {
        return points.stream().sorted(Comparator.comparingInt(CapturePointDefinition::order).thenComparing(CapturePointDefinition::id)).toList();
    }
    public Optional<CapturePointDefinition> point(String id) {
        String normalized = CapturePointDefinition.normalizeId(id);
        return points.stream().filter(point -> point.id().equals(normalized)).findFirst();
    }

    public void add(CapturePointDefinition point) {
        if (points.size() >= MAX_POINTS) throw new IllegalArgumentException("A map can have at most 16 capture points");
        if (point(point.id()).isPresent()) throw new IllegalArgumentException("Duplicate point id: " + point.id());
        validateNoOverlap(point, null);
        points.add(point);
    }

    public void replace(String id, CapturePointDefinition replacement) {
        int index = indexOf(id);
        validateNoOverlap(replacement, replacement.id());
        points.set(index, replacement);
    }

    public boolean remove(String id) {
        String normalized = CapturePointDefinition.normalizeId(id);
        return points.removeIf(point -> point.id().equals(normalized));
    }
    public void clear() { points.clear(); }
    public boolean configured() { return !points.isEmpty() && points.size() <= MAX_POINTS; }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (points.isEmpty()) errors.add("Domination map needs at least one capture point");
        if (points.size() > MAX_POINTS) errors.add("Domination map has more than 16 capture points");
        for (int i = 0; i < points.size(); i++) for (int j = i + 1; j < points.size(); j++) {
            if (points.get(i).region().overlaps(points.get(j).region())) {
                errors.add("Capture points overlap: " + points.get(i).id() + " and " + points.get(j).id());
            }
        }
        return errors;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putString("Strategy", strategy.name());
        ListTag list = new ListTag(); points().forEach(point -> list.add(point.save())); tag.put("Points", list);
        return tag;
    }

    public static DominationMapConfig load(CompoundTag tag) {
        DominationMapConfig config = new DominationMapConfig();
        try { config.strategy = PointActivationStrategy.valueOf(tag.getString("Strategy")); }
        catch (IllegalArgumentException ignored) { config.strategy = PointActivationStrategy.ASYNC; }
        if (tag.contains("Points", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Points", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_POINTS, list.size()); i++) {
                try { config.add(CapturePointDefinition.load(list.getCompound(i))); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        return config;
    }

    private int indexOf(String id) {
        String normalized = CapturePointDefinition.normalizeId(id);
        for (int i = 0; i < points.size(); i++) if (points.get(i).id().equals(normalized)) return i;
        throw new IllegalArgumentException("Unknown capture point: " + id);
    }

    private void validateNoOverlap(CapturePointDefinition candidate, String ignoredId) {
        for (CapturePointDefinition existing : points) {
            if (existing.id().equals(ignoredId)) continue;
            if (candidate.region().overlaps(existing.region())) {
                throw new IllegalArgumentException("Capture point overlaps " + existing.id());
            }
        }
    }
}
