package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Map-wide destructible-area configuration shared by every game mode. */
public final class MapBuildConfig {
    @Nullable private BoxCaptureRegion region;
    private final Set<String> allowedBlocks = new LinkedHashSet<>();
    private boolean snapshotSaved;

    @Nullable public BoxCaptureRegion region() { return region; }
    public void region(BoxCaptureRegion value) { region = value; snapshotSaved = false; }
    public void clearRegion() { region = null; snapshotSaved = false; }
    public boolean snapshotSaved() { return snapshotSaved; }
    public void snapshotSaved(boolean value) { snapshotSaved = value; }
    public Set<String> allowedBlocks() { return Collections.unmodifiableSet(allowedBlocks); }
    public void allow(String id) { allowedBlocks.add(SFGameId.normalizeResource(id)); }
    public boolean disallow(String id) { return allowedBlocks.remove(SFGameId.normalizeResource(id)); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (region != null) tag.put("Region", region.save());
        tag.putBoolean("SnapshotSaved", snapshotSaved);
        ListTag blocks = new ListTag();
        allowedBlocks.forEach(id -> blocks.add(net.minecraft.nbt.StringTag.valueOf(id)));
        tag.put("AllowedBlocks", blocks);
        return tag;
    }

    public static MapBuildConfig load(CompoundTag tag) {
        MapBuildConfig config = new MapBuildConfig();
        if (tag.contains("Region")) config.region = BoxCaptureRegion.load(tag.getCompound("Region"));
        config.snapshotSaved = tag.getBoolean("SnapshotSaved");
        if (tag.contains("AllowedBlocks", Tag.TAG_LIST)) {
            ListTag blocks = tag.getList("AllowedBlocks", Tag.TAG_STRING);
            for (int i = 0; i < blocks.size(); i++) {
                try { config.allowedBlocks.add(SFGameId.normalizeResource(blocks.getString(i))); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        return config;
    }
}
