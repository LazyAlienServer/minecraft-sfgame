package com.sfgame.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapBuildSnapshotServiceTest {
    @Test
    void allowlistSnapshotKeepsOnlyMatchingStructureEntries() throws Exception {
        CompoundTag template = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:stone"));
        palette.add(paletteEntry("minecraft:white_wool"));
        template.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(blockEntry(0));
        blocks.add(blockEntry(1));
        template.put("blocks", blocks);

        CompoundTag filtered = MapBuildSnapshotService.filterTemplateForAllowlist(
                template, Set.of("minecraft:white_wool"));

        ListTag kept = filtered.getList("blocks", CompoundTag.TAG_COMPOUND);
        assertEquals(1, kept.size());
        assertEquals(1, kept.getCompound(0).getInt("state"));
        assertTrue(MapBuildSnapshotService.hasTemplateBlocks(filtered));
        assertEquals(2, template.getList("blocks", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void emptyAllowlistProducesAnEmptyPartition() throws Exception {
        CompoundTag template = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:stone"));
        template.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(blockEntry(0));
        template.put("blocks", blocks);

        CompoundTag filtered = MapBuildSnapshotService.filterTemplateForAllowlist(template, Set.of());
        assertEquals(0, filtered.getList("blocks", CompoundTag.TAG_COMPOUND).size());
        assertFalse(MapBuildSnapshotService.hasTemplateBlocks(filtered));
    }

    private static CompoundTag paletteEntry(String name) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Name", name);
        return entry;
    }

    private static CompoundTag blockEntry(int state) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("state", state);
        return entry;
    }
}
