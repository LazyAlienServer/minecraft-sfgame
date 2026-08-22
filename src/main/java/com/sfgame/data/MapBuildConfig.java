package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import javax.annotation.Nullable;

/** Map-wide destructible-area configuration shared by every game mode. */
public final class MapBuildConfig {
    @Nullable private BoxCaptureRegion region;
    private boolean snapshotSaved;

    @Nullable public BoxCaptureRegion region() { return region; }
    public void region(BoxCaptureRegion value) { region = value; snapshotSaved = false; }
    public void clearRegion() { region = null; snapshotSaved = false; }
    public boolean snapshotSaved() { return snapshotSaved; }
    public void snapshotSaved(boolean value) { snapshotSaved = value; }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (region != null) tag.put("Region", region.save());
        tag.putBoolean("SnapshotSaved", snapshotSaved);
        return tag;
    }

    public static MapBuildConfig load(CompoundTag tag) {
        MapBuildConfig config = new MapBuildConfig();
        if (tag.contains("Region")) config.region = BoxCaptureRegion.load(tag.getCompound("Region"));
        config.snapshotSaved = tag.getBoolean("SnapshotSaved");
        return config;
    }
}
