package com.sfgame.game;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.BoxCaptureRegion;
import com.sfgame.data.SFGameId;
import com.sfgame.data.MapSnapshotMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores a map baseline as compressed 16x16 columns. Each column contains
 * independent 16-block-high restore partitions, so one server-tick step is
 * bounded while a tall arena does not create one file per vertical section.
 * Ordinary air is omitted from the structure payload and reconstructed by
 * clearing only cells that are absent from the saved partition.
 */
public final class MapBuildSnapshotService {
    private static final int FORMAT = 4;
    private static final int PARTITION_SIZE = 16;
    private static final int RESTORE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS;
    private static final String MANIFEST = "manifest.nbt";

    public static int save(MinecraftServer server, String modeId, ArenaMap map,
                           MapSnapshotMode snapshotMode) throws IOException {
        BoxCaptureRegion region = map.build().region();
        if (region == null) throw new IOException("Map build box is not set");
        ServerLevel level = level(server, region.dimension());
        if (level == null) throw new IOException("Build box dimension is unavailable");
        Bounds bounds = bounds(level, region);
        snapshotMode = snapshotMode == null ? MapSnapshotMode.ALLOWLIST : snapshotMode;
        Set<String> allowlist = Set.copyOf(map.build().allowedBlocks());
        Path target = snapshotPath(server, modeId, map);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.createDirectories(target.getParent());
        deleteTree(temporary);
        Files.createDirectories(temporary);

        CompoundTag manifest = new CompoundTag();
        manifest.putInt("Format", FORMAT);
        manifest.putInt("PartitionSize", PARTITION_SIZE);
        manifest.putString("Dimension", region.dimension());
        manifest.putString("SnapshotMode", snapshotMode.id());
        putAllowlist(manifest, allowlist);
        putBounds(manifest, bounds);
        ListTag columns = new ListTag();
        int totalPartitions = 0;
        int columnIndex = 0;
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();

        for (int x = bounds.origin().getX(); x <= maxX; x += PARTITION_SIZE) {
            for (int z = bounds.origin().getZ(); z <= maxZ; z += PARTITION_SIZE) {
                int sizeX = Math.min(PARTITION_SIZE, maxX - x + 1);
                int sizeZ = Math.min(PARTITION_SIZE, maxZ - z + 1);
                ListTag parts = new ListTag();
                for (int y = bounds.origin().getY(); y <= maxY; y += PARTITION_SIZE) {
                    int sizeY = Math.min(PARTITION_SIZE, maxY - y + 1);
                    BlockPos origin = new BlockPos(x, y, z);
                    Vec3i size = new Vec3i(sizeX, sizeY, sizeZ);
                    StructureTemplate template = new StructureTemplate();
                    // The overwhelmingly common air state is reconstructed at
                    // restore time instead of being serialized block-by-block.
                    template.fillFromWorld(level, origin, size, false, Blocks.AIR);
                    CompoundTag part = new CompoundTag();
                    putPartition(part, origin, size);
                    CompoundTag templateTag = template.save(new CompoundTag());
                    if (snapshotMode == MapSnapshotMode.ALLOWLIST) {
                        templateTag = filterTemplateForAllowlist(templateTag, allowlist);
                    }
                    part.put("Template", templateTag);
                    parts.add(part);
                    totalPartitions++;
                }

                String file = String.format("column_%07d.nbt", columnIndex++);
                CompoundTag columnFile = new CompoundTag();
                columnFile.putInt("Format", FORMAT);
                columnFile.putInt("X", x);
                columnFile.putInt("Z", z);
                columnFile.putInt("SizeX", sizeX);
                columnFile.putInt("SizeZ", sizeZ);
                columnFile.put("Parts", parts);
                NbtIo.writeCompressed(columnFile, temporary.resolve(file).toFile());

                CompoundTag column = new CompoundTag();
                column.putString("File", file);
                column.putInt("X", x);
                column.putInt("Z", z);
                column.putInt("SizeX", sizeX);
                column.putInt("SizeZ", sizeZ);
                column.putInt("PartCount", parts.size());
                columns.add(column);
            }
        }
        manifest.putInt("TotalPartitions", totalPartitions);
        manifest.put("Columns", columns);
        NbtIo.writeCompressed(manifest, temporary.resolve(MANIFEST).toFile());
        replaceDirectory(temporary, target);
        SnapshotStatus validation = status(server, modeId, map, snapshotMode);
        if (!validation.exists()) {
            map.build().snapshotSaved(false);
            throw new IOException("Saved snapshot failed validation: " + validation.detail());
        }
        map.build().snapshotSaved(true);
        return totalPartitions;
    }

    /** Synchronous restore retained for the explicit administrator command. */
    public static int restore(MinecraftServer server, String modeId, ArenaMap map,
                              MapSnapshotMode snapshotMode) throws IOException {
        RestoreSession session = beginRestore(server, modeId, map, snapshotMode);
        while (!session.complete()) session.restoreNext();
        return session.totalPartitions();
    }

    /** Creates a tick-driven restore session used during match preparation. */
    public static RestoreSession beginRestore(MinecraftServer server, String modeId, ArenaMap map,
                                              MapSnapshotMode expectedMode) throws IOException {
        Path directory = snapshotPath(server, modeId, map).normalize();
        Path manifestPath = directory.resolve(MANIFEST);
        if (!Files.isRegularFile(manifestPath)) throw new IOException("Map snapshot does not exist");
        CompoundTag manifest = NbtIo.readCompressed(manifestPath.toFile());
        if (!compatible(server, map, manifest, expectedMode)) {
            throw new IOException("Map snapshot does not match the current build box; save it again");
        }
        ServerLevel level = level(server, manifest.getString("Dimension"));
        if (level == null) throw new IOException("Snapshot dimension is unavailable");
        Bounds bounds = bounds(level, map.build().region());
        ListTag columns = manifest.getList("Columns", Tag.TAG_COMPOUND);
        int totalPartitions = validateManifest(directory, bounds, manifest, columns);
        MapSnapshotMode snapshotMode = MapSnapshotMode.byId(manifest.getString("SnapshotMode"));
        return new RestoreSession(level, directory, bounds, columns.copy(), totalPartitions,
                snapshotMode, readAllowlist(manifest));
    }

    public static boolean exists(MinecraftServer server, String modeId, ArenaMap map,
                                 MapSnapshotMode snapshotMode) {
        return status(server, modeId, map, snapshotMode).exists();
    }

    /**
     * The files and manifest are authoritative. SnapshotSaved is retained in
     * map NBT for compatibility and UI hints, but it must not hide a valid
     * snapshot after an integrated-server save or an interrupted data flush.
     */
    public static SnapshotStatus status(MinecraftServer server, String modeId, ArenaMap map,
                                        MapSnapshotMode snapshotMode) {
        Path manifest = snapshotPath(server, modeId, map).resolve(MANIFEST);
        if (map.build().region() == null) return new SnapshotStatus(false, "map build box is not set");
        if (!Files.isRegularFile(manifest)) return new SnapshotStatus(false, "manifest is missing");
        try {
            CompoundTag tag = NbtIo.readCompressed(manifest.toFile());
            if (!compatible(server, map, tag, snapshotMode)) {
                return new SnapshotStatus(false, "manifest does not match the current build box");
            }
            ServerLevel level = level(server, tag.getString("Dimension"));
            if (level == null) return new SnapshotStatus(false, "snapshot dimension is unavailable");
            Bounds bounds = bounds(level, map.build().region());
            int partitions = validateManifest(manifest.getParent(), bounds, tag,
                    tag.getList("Columns", Tag.TAG_COMPOUND));
            return new SnapshotStatus(true, partitions + " partition(s) ready, mode="
                    + snapshotMode.id());
        } catch (IOException | RuntimeException exception) {
            String message = exception.getMessage();
            return new SnapshotStatus(false, message == null ? exception.getClass().getSimpleName() : message);
        }
    }

    public static void clear(MinecraftServer server, String modeId, ArenaMap map) throws IOException {
        deleteTree(snapshotPath(server, modeId, map));
        map.build().snapshotSaved(false);
    }

    public static Path snapshotPath(MinecraftServer server, String modeId, ArenaMap map) {
        String safeMode = SFGameId.normalize(modeId);
        String safeMap = SFGameId.normalize(map.id());
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("sfgame").resolve("maps").resolve(safeMode).resolve(safeMap);
    }

    private static int validateManifest(Path directory, Bounds bounds, CompoundTag manifest,
                                        ListTag columns) throws IOException {
        int expectedColumnsX = Mth.ceil((double) bounds.size().getX() / PARTITION_SIZE);
        int expectedColumnsZ = Mth.ceil((double) bounds.size().getZ() / PARTITION_SIZE);
        int expectedPartsPerColumn = Mth.ceil((double) bounds.size().getY() / PARTITION_SIZE);
        int expectedColumns = expectedColumnsX * expectedColumnsZ;
        int expectedTotal = expectedColumns * expectedPartsPerColumn;
        if (columns.size() != expectedColumns || manifest.getInt("TotalPartitions") != expectedTotal) {
            throw new IOException("Snapshot partition manifest is incomplete");
        }

        Set<String> files = new HashSet<>();
        Set<Long> origins = new HashSet<>();
        int declaredTotal = 0;
        for (int i = 0; i < columns.size(); i++) {
            CompoundTag column = columns.getCompound(i);
            String file = column.getString("File");
            if (!file.matches("column_[0-9]{7}\\.nbt") || !files.add(file)) {
                throw new IOException("Snapshot contains an invalid or duplicate column file");
            }
            Path columnPath = resolveColumn(directory, file);
            if (!Files.isRegularFile(columnPath) || Files.size(columnPath) == 0L) {
                throw new IOException("Snapshot column is missing: " + file);
            }
            int x = column.getInt("X");
            int z = column.getInt("Z");
            long originKey = ((long) x << 32) ^ (z & 0xffffffffL);
            if (!origins.add(originKey) || x < bounds.origin().getX() || z < bounds.origin().getZ()
                    || (x - bounds.origin().getX()) % PARTITION_SIZE != 0
                    || (z - bounds.origin().getZ()) % PARTITION_SIZE != 0) {
                throw new IOException("Snapshot contains a duplicate or misaligned column");
            }
            int expectedSizeX = Math.min(PARTITION_SIZE, bounds.maxX() - x + 1);
            int expectedSizeZ = Math.min(PARTITION_SIZE, bounds.maxZ() - z + 1);
            if (expectedSizeX <= 0 || expectedSizeZ <= 0 || column.getInt("SizeX") != expectedSizeX
                    || column.getInt("SizeZ") != expectedSizeZ
                    || column.getInt("PartCount") != expectedPartsPerColumn) {
                throw new IOException("Snapshot column bounds do not match the build box");
            }
            declaredTotal += column.getInt("PartCount");
        }
        if (declaredTotal != expectedTotal) throw new IOException("Snapshot partition count is invalid");
        return expectedTotal;
    }

    private static void validateColumn(CompoundTag fileTag, CompoundTag metadata, Bounds bounds) throws IOException {
        if (fileTag.getInt("Format") != FORMAT || fileTag.getInt("X") != metadata.getInt("X")
                || fileTag.getInt("Z") != metadata.getInt("Z")
                || fileTag.getInt("SizeX") != metadata.getInt("SizeX")
                || fileTag.getInt("SizeZ") != metadata.getInt("SizeZ")) {
            throw new IOException("Snapshot column metadata does not match its manifest");
        }
        ListTag parts = fileTag.getList("Parts", Tag.TAG_COMPOUND);
        if (parts.size() != metadata.getInt("PartCount")) {
            throw new IOException("Snapshot column has an invalid partition count");
        }
        for (int i = 0; i < parts.size(); i++) validatePartitionMetadata(parts.getCompound(i), metadata, bounds, i);
    }

    private static void validatePartitionMetadata(CompoundTag part, CompoundTag column, Bounds bounds,
                                                  int verticalIndex) throws IOException {
        int expectedY = bounds.origin().getY() + verticalIndex * PARTITION_SIZE;
        int expectedSizeY = Math.min(PARTITION_SIZE, bounds.maxY() - expectedY + 1);
        if (part.getInt("X") != column.getInt("X") || part.getInt("Z") != column.getInt("Z")
                || part.getInt("Y") != expectedY || part.getInt("SizeX") != column.getInt("SizeX")
                || part.getInt("SizeZ") != column.getInt("SizeZ") || part.getInt("SizeY") != expectedSizeY
                || !part.contains("Template", Tag.TAG_COMPOUND)) {
            throw new IOException("Snapshot partition metadata is invalid");
        }
        CompoundTag template = part.getCompound("Template");
        ListTag size = template.getList("size", Tag.TAG_INT);
        if (size.size() != 3 || size.getInt(0) != part.getInt("SizeX")
                || size.getInt(1) != part.getInt("SizeY") || size.getInt(2) != part.getInt("SizeZ")) {
            throw new IOException("Snapshot structure size does not match its partition");
        }
    }

    private static void clearUnsavedCells(ServerLevel level, BlockPos origin, Vec3i size,
                                          boolean[] occupied) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int y = 0; y < size.getY(); y++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++, index++) {
                    if (occupied[index]) continue;
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), RESTORE_FLAGS);
                    }
                }
            }
        }
    }

    /** Clears only allowlisted blocks inside one restore partition. */
    private static void clearAllowlistedCells(ServerLevel level, BlockPos origin, Vec3i size,
                                              Set<String> allowlist) {
        if (allowlist.isEmpty()) return;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < size.getY(); y++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    var state = level.getBlockState(cursor);
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(state.getBlock()).toString();
                    if (allowlist.contains(id)) level.setBlock(cursor, Blocks.AIR.defaultBlockState(), RESTORE_FLAGS);
                }
            }
        }
    }

    /**
     * Keeps the structure palette intact but removes every block entry whose
     * palette state is not allowlisted. Block-entity NBT remains attached to
     * kept entries, so allowlisted containers restore with their contents.
     */
    static CompoundTag filterTemplateForAllowlist(CompoundTag template, Set<String> allowlist) throws IOException {
        CompoundTag filtered = template.copy();
        ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
        ListTag kept = new ListTag();
        if (blocks.isEmpty() || allowlist.isEmpty()) {
            filtered.put("blocks", kept);
            return filtered;
        }
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty()) throw new IOException("Snapshot structure palette is missing");
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            int stateIndex = block.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                throw new IOException("Snapshot block references an invalid palette state");
            }
            String blockId = palette.getCompound(stateIndex).getString("Name");
            if (allowlist.contains(blockId)) kept.add(block.copy());
        }
        filtered.put("blocks", kept);
        return filtered;
    }

    private static boolean[] occupiedCells(CompoundTag template, Vec3i size) throws IOException {
        boolean[] occupied = new boolean[size.getX() * size.getY() * size.getZ()];
        ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            ListTag position = blocks.getCompound(i).getList("pos", Tag.TAG_INT);
            if (position.size() != 3) throw new IOException("Snapshot block position is invalid");
            int x = position.getInt(0), y = position.getInt(1), z = position.getInt(2);
            if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ()) {
                throw new IOException("Snapshot block position is outside its partition");
            }
            occupied[(y * size.getZ() + z) * size.getX() + x] = true;
        }
        return occupied;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    private static Bounds bounds(ServerLevel level, BoxCaptureRegion region) {
        int minX = Mth.floor(region.minX()), maxX = Mth.floor(region.maxX());
        int minZ = Mth.floor(region.minZ()), maxZ = Mth.floor(region.maxZ());
        int minY = region.minY() == null ? level.getMinBuildHeight() : region.minY();
        int maxY = region.maxY() == null ? level.getMaxBuildHeight() - 1 : region.maxY();
        return new Bounds(new BlockPos(minX, minY, minZ),
                new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1));
    }

    private static boolean compatible(MinecraftServer server, ArenaMap map, CompoundTag manifest,
                                      MapSnapshotMode expectedMode) {
        BoxCaptureRegion region = map.build().region();
        if (manifest.getInt("Format") != FORMAT || manifest.getInt("PartitionSize") != PARTITION_SIZE
                || region == null || !region.dimension().equals(manifest.getString("Dimension"))) return false;
        expectedMode = expectedMode == null ? MapSnapshotMode.ALLOWLIST : expectedMode;
        String snapshotMode = manifest.getString("SnapshotMode");
        if (!expectedMode.id().equals(snapshotMode)) return false;
        if (expectedMode == MapSnapshotMode.ALLOWLIST
                && !map.build().allowedBlocks().equals(readAllowlist(manifest))) return false;
        ServerLevel level = level(server, region.dimension());
        if (level == null) return false;
        Bounds expected = bounds(level, region);
        return expected.origin().getX() == manifest.getInt("MinX")
                && expected.origin().getY() == manifest.getInt("MinY")
                && expected.origin().getZ() == manifest.getInt("MinZ")
                && expected.size().getX() == manifest.getInt("SizeX")
                && expected.size().getY() == manifest.getInt("SizeY")
                && expected.size().getZ() == manifest.getInt("SizeZ");
    }

    private static void putBounds(CompoundTag tag, Bounds bounds) {
        tag.putInt("MinX", bounds.origin().getX());
        tag.putInt("MinY", bounds.origin().getY());
        tag.putInt("MinZ", bounds.origin().getZ());
        tag.putInt("SizeX", bounds.size().getX());
        tag.putInt("SizeY", bounds.size().getY());
        tag.putInt("SizeZ", bounds.size().getZ());
    }

    private static void putPartition(CompoundTag tag, BlockPos origin, Vec3i size) {
        tag.putInt("X", origin.getX());
        tag.putInt("Y", origin.getY());
        tag.putInt("Z", origin.getZ());
        tag.putInt("SizeX", size.getX());
        tag.putInt("SizeY", size.getY());
        tag.putInt("SizeZ", size.getZ());
    }

    private static void putAllowlist(CompoundTag tag, Set<String> allowlist) {
        ListTag values = new ListTag();
        allowlist.stream().sorted().forEach(id -> values.add(net.minecraft.nbt.StringTag.valueOf(id)));
        tag.put("Allowlist", values);
    }

    private static Set<String> readAllowlist(CompoundTag tag) {
        ListTag values = tag.getList("Allowlist", Tag.TAG_STRING);
        Set<String> result = new HashSet<>();
        for (int i = 0; i < values.size(); i++) result.add(values.getString(i));
        return Set.copyOf(result);
    }

    private static Path resolveColumn(Path directory, String file) throws IOException {
        // Integrated servers may expose the world root with equivalent "."
        // path segments. Compare normalized paths on both sides so a valid
        // column is not mistaken for traversal outside the map directory.
        Path normalizedDirectory = directory.normalize();
        Path result = normalizedDirectory.resolve(file).normalize();
        if (result.getParent() == null || !result.getParent().equals(normalizedDirectory)) {
            throw new IOException("Snapshot column path escapes its map directory");
        }
        return result;
    }

    private static void replaceDirectory(Path source, Path target) throws IOException {
        Path backup = target.resolveSibling(target.getFileName() + ".backup");
        deleteTree(backup);
        boolean hadTarget = Files.exists(target);
        if (hadTarget) move(target, backup);
        try {
            move(source, target);
        } catch (IOException exception) {
            if (hadTarget && !Files.exists(target) && Files.exists(backup)) move(backup, target);
            throw exception;
        }
        deleteTree(backup);
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    public static final class RestoreSession {
        private final ServerLevel level;
        private final Path directory;
        private final Bounds bounds;
        private final ListTag columns;
        private final int totalPartitions;
        private final MapSnapshotMode snapshotMode;
        private final Set<String> allowlist;
        private final long startedNanos = System.nanoTime();
        private int nextColumn;
        private ListTag currentParts;
        private int nextPartInColumn;
        private int completedPartitions;
        private double lastPartitionMillis;

        private RestoreSession(ServerLevel level, Path directory, Bounds bounds, ListTag columns,
                               int totalPartitions, MapSnapshotMode snapshotMode, Set<String> allowlist) {
            this.level = level;
            this.directory = directory;
            this.bounds = bounds;
            this.columns = columns;
            this.totalPartitions = totalPartitions;
            this.snapshotMode = snapshotMode;
            this.allowlist = allowlist;
        }

        public void restoreNext() throws IOException {
            if (complete()) return;
            long started = System.nanoTime();
            ensureColumnLoaded();
            CompoundTag part = currentParts.getCompound(nextPartInColumn);
            BlockPos origin = new BlockPos(part.getInt("X"), part.getInt("Y"), part.getInt("Z"));
            Vec3i size = new Vec3i(part.getInt("SizeX"), part.getInt("SizeY"), part.getInt("SizeZ"));
            CompoundTag templateTag = part.getCompound("Template");
            StructureTemplate template = new StructureTemplate();
            template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), templateTag);
            if (!template.getSize().equals(size)) throw new IOException("Snapshot template size changed while loading");

            if (snapshotMode == MapSnapshotMode.ALLOWLIST) {
                clearAllowlistedCells(level, origin, size, allowlist);
            } else {
                clearUnsavedCells(level, origin, size, occupiedCells(templateTag, size));
            }
            if (templateTag.getList("blocks", Tag.TAG_COMPOUND).size() > 0) {
                StructurePlaceSettings settings = new StructurePlaceSettings()
                        .setIgnoreEntities(true).setKnownShape(true);
                if (!template.placeInWorld(level, origin, origin, settings, RandomSource.create(), RESTORE_FLAGS)) {
                    throw new IOException("Could not place snapshot partition at " + origin.toShortString());
                }
            }

            completedPartitions++;
            nextPartInColumn++;
            if (nextPartInColumn >= currentParts.size()) {
                currentParts = null;
                nextPartInColumn = 0;
                nextColumn++;
            }
            lastPartitionMillis = (System.nanoTime() - started) / 1_000_000.0;
        }

        private void ensureColumnLoaded() throws IOException {
            if (currentParts != null) return;
            if (nextColumn >= columns.size()) throw new IOException("Snapshot ended before all partitions were restored");
            CompoundTag metadata = columns.getCompound(nextColumn);
            Path path = resolveColumn(directory, metadata.getString("File"));
            CompoundTag fileTag = NbtIo.readCompressed(path.toFile());
            validateColumn(fileTag, metadata, bounds);
            currentParts = fileTag.getList("Parts", Tag.TAG_COMPOUND);
        }

        public boolean complete() { return completedPartitions >= totalPartitions; }
        public int completedPartitions() { return completedPartitions; }
        public int totalPartitions() { return totalPartitions; }
        public float progress() {
            return totalPartitions == 0 ? 1.0F
                    : Mth.clamp((float) completedPartitions / totalPartitions, 0.0F, 1.0F);
        }
        public long elapsedMillis() { return (System.nanoTime() - startedNanos) / 1_000_000L; }
        public double lastPartitionMillis() { return lastPartitionMillis; }
    }

    private record Bounds(BlockPos origin, Vec3i size) {
        int maxX() { return origin.getX() + size.getX() - 1; }
        int maxY() { return origin.getY() + size.getY() - 1; }
        int maxZ() { return origin.getZ() + size.getZ() - 1; }
    }

    public record SnapshotStatus(boolean exists, String detail) { }

    private MapBuildSnapshotService() {
    }
}
