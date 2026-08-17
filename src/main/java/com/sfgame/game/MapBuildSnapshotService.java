package com.sfgame.game;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.BoxCaptureRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * Stores a map baseline as chunk-sized structure files.  Splitting X/Z into
 * 16x16 partitions keeps a large arena from becoming one huge in-memory NBT.
 * Block entities are retained; normal entities are deliberately excluded.
 */
public final class MapBuildSnapshotService {
    private static final int FORMAT = 1;
    private static final int PARTITION_SIZE = 16;
    private static final String MANIFEST = "manifest.nbt";

    public static int save(MinecraftServer server, ArenaMap map) throws IOException {
        BoxCaptureRegion region = map.build().region();
        if (region == null) throw new IOException("Map build box is not set");
        ServerLevel level = level(server, region.dimension());
        if (level == null) throw new IOException("Build box dimension is unavailable");
        Bounds bounds = bounds(level, region);
        Path target = snapshotPath(server, map);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        deleteTree(temporary);
        Files.createDirectories(temporary);

        CompoundTag manifest = new CompoundTag();
        manifest.putInt("Format", FORMAT);
        manifest.putString("Dimension", region.dimension());
        manifest.putInt("MinX", bounds.origin().getX()); manifest.putInt("MinY", bounds.origin().getY());
        manifest.putInt("MinZ", bounds.origin().getZ()); manifest.putInt("SizeX", bounds.size().getX());
        manifest.putInt("SizeY", bounds.size().getY()); manifest.putInt("SizeZ", bounds.size().getZ());
        ListTag parts = new ListTag();
        int index = 0;
        int maxX = bounds.origin().getX() + bounds.size().getX() - 1;
        int maxZ = bounds.origin().getZ() + bounds.size().getZ() - 1;
        for (int x = bounds.origin().getX(); x <= maxX; x += PARTITION_SIZE) {
            for (int z = bounds.origin().getZ(); z <= maxZ; z += PARTITION_SIZE) {
                int sizeX = Math.min(PARTITION_SIZE, maxX - x + 1);
                int sizeZ = Math.min(PARTITION_SIZE, maxZ - z + 1);
                BlockPos origin = new BlockPos(x, bounds.origin().getY(), z);
                Vec3i size = new Vec3i(sizeX, bounds.size().getY(), sizeZ);
                StructureTemplate template = new StructureTemplate();
                template.fillFromWorld(level, origin, size, false, null);
                String file = String.format("part_%06d.nbt", index++);
                NbtIo.writeCompressed(template.save(new CompoundTag()), temporary.resolve(file).toFile());
                CompoundTag part = new CompoundTag();
                part.putString("File", file); part.putInt("X", x); part.putInt("Y", origin.getY()); part.putInt("Z", z);
                part.putInt("SizeX", sizeX); part.putInt("SizeY", size.getY()); part.putInt("SizeZ", sizeZ);
                parts.add(part);
            }
        }
        manifest.put("Parts", parts);
        NbtIo.writeCompressed(manifest, temporary.resolve(MANIFEST).toFile());
        deleteTree(target);
        Files.createDirectories(target.getParent());
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        map.build().snapshotSaved(true);
        return parts.size();
    }

    public static int restore(MinecraftServer server, ArenaMap map) throws IOException {
        Path directory = snapshotPath(server, map);
        Path manifestPath = directory.resolve(MANIFEST);
        if (!Files.exists(manifestPath)) throw new IOException("Map snapshot does not exist");
        CompoundTag manifest = NbtIo.readCompressed(manifestPath.toFile());
        if (!compatible(server, map, manifest)) throw new IOException("Map snapshot does not match the current build box; save it again");
        ServerLevel level = level(server, manifest.getString("Dimension"));
        if (level == null) throw new IOException("Snapshot dimension is unavailable");
        ListTag parts = manifest.getList("Parts", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < parts.size(); i++) {
            CompoundTag part = parts.getCompound(i);
            Path partPath = directory.resolve(part.getString("File")).normalize();
            if (!partPath.getParent().equals(directory.normalize()) || !Files.exists(partPath)) {
                throw new IOException("Snapshot partition is missing: " + part.getString("File"));
            }
            BlockPos origin = new BlockPos(part.getInt("X"), part.getInt("Y"), part.getInt("Z"));
            Vec3i size = new Vec3i(part.getInt("SizeX"), part.getInt("SizeY"), part.getInt("SizeZ"));
            clear(level, new Bounds(origin, size));
            CompoundTag templateTag = NbtIo.readCompressed(partPath.toFile());
            StructureTemplate template = new StructureTemplate();
            template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), templateTag);
            template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(), 3);
        }
        map.build().snapshotSaved(true);
        return parts.size();
    }

    public static boolean exists(MinecraftServer server, ArenaMap map) {
        Path manifest = snapshotPath(server, map).resolve(MANIFEST);
        if (!map.build().snapshotSaved() || !Files.exists(manifest)) return false;
        try { return compatible(server, map, NbtIo.readCompressed(manifest.toFile())); }
        catch (IOException exception) { return false; }
    }

    public static void clear(MinecraftServer server, ArenaMap map) throws IOException {
        deleteTree(snapshotPath(server, map));
        map.build().snapshotSaved(false);
    }

    public static Path snapshotPath(MinecraftServer server, ArenaMap map) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("sfgame").resolve("maps").resolve(map.id());
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
        return new Bounds(new BlockPos(minX, minY, minZ), new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1));
    }

    private static boolean compatible(MinecraftServer server, ArenaMap map, CompoundTag manifest) {
        BoxCaptureRegion region = map.build().region();
        if (region == null || !region.dimension().equals(manifest.getString("Dimension"))) return false;
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

    private static void clear(ServerLevel level, Bounds bounds) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos origin = bounds.origin(); Vec3i size = bounds.size();
        for (int x = 0; x < size.getX(); x++) for (int y = 0; y < size.getY(); y++) for (int z = 0; z < size.getZ(); z++) {
            cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!level.getBlockState(cursor).isAir()) level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private record Bounds(BlockPos origin, Vec3i size) { }
    private MapBuildSnapshotService() { }
}
