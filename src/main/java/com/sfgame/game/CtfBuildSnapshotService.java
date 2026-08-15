package com.sfgame.game;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.BoxCaptureRegion;
import com.sfgame.data.CaptureTheFlagMapConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stores only the configured CTF build box; entities are deliberately excluded. */
public final class CtfBuildSnapshotService {
    private static final int MAX_BLOCKS = 4_000_000;

    public static boolean save(MinecraftServer server, ArenaMap map) throws IOException {
        CaptureTheFlagMapConfig config = map.captureTheFlag();
        BoxCaptureRegion region = config.build().region();
        if (region == null) throw new IOException("CTF build box is not set");
        ServerLevel level = level(server, region.dimension());
        if (level == null) throw new IOException("Build box dimension is unavailable");
        Bounds bounds = bounds(level, region);
        if (bounds.volume() > MAX_BLOCKS) throw new IOException("CTF build box is too large (maximum " + MAX_BLOCKS + " blocks)");
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, bounds.origin(), bounds.size(), true, null);
        CompoundTag tag = template.save(new CompoundTag());
        tag.putString("Dimension", region.dimension()); tag.putInt("OriginX", bounds.origin().getX());
        tag.putInt("OriginY", bounds.origin().getY()); tag.putInt("OriginZ", bounds.origin().getZ());
        Path path = snapshotPath(server, map);
        Files.createDirectories(path.getParent()); NbtIo.writeCompressed(tag, path.toFile());
        config.build().snapshotSaved(true); return true;
    }

    public static boolean restore(MinecraftServer server, ArenaMap map) throws IOException {
        CaptureTheFlagMapConfig config = map.captureTheFlag();
        BoxCaptureRegion region = config.build().region();
        if (region == null) throw new IOException("CTF build box is not set");
        Path path = snapshotPath(server, map);
        if (!Files.exists(path)) throw new IOException("CTF snapshot does not exist");
        ServerLevel level = level(server, region.dimension());
        if (level == null) throw new IOException("Build box dimension is unavailable");
        CompoundTag tag = NbtIo.readCompressed(path.toFile());
        StructureTemplate template = new StructureTemplate();
        template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag);
        Bounds bounds = bounds(level, region);
        clear(level, bounds);
        template.placeInWorld(level, bounds.origin(), bounds.origin(), new StructurePlaceSettings(), RandomSource.create(), 3);
        config.build().snapshotSaved(true); return true;
    }

    public static boolean exists(MinecraftServer server, ArenaMap map) { return Files.exists(snapshotPath(server, map)); }
    public static void clear(MinecraftServer server, ArenaMap map) throws IOException {
        Files.deleteIfExists(snapshotPath(server, map)); map.captureTheFlag().build().snapshotSaved(false);
    }
    public static Path snapshotPath(MinecraftServer server, ArenaMap map) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve("sfgame").resolve("ctf").resolve(map.id() + ".nbt");
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension); if (id == null) return null;
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    private static Bounds bounds(ServerLevel level, BoxCaptureRegion region) {
        int minX = Mth.floor(region.minX()), maxX = Mth.floor(region.maxX());
        int minZ = Mth.floor(region.minZ()), maxZ = Mth.floor(region.maxZ());
        int minY = region.minY() == null ? level.getMinBuildHeight() : region.minY();
        int maxY = region.maxY() == null ? level.getMaxBuildHeight() - 1 : region.maxY();
        return new Bounds(new BlockPos(minX, minY, minZ), new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1));
    }

    private static void clear(ServerLevel level, Bounds bounds) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos origin = bounds.origin(); Vec3i size = bounds.size();
        for (int x = 0; x < size.getX(); x++) for (int y = 0; y < size.getY(); y++) for (int z = 0; z < size.getZ(); z++) {
            cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!level.getBlockState(cursor).is(Blocks.AIR)) level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private record Bounds(BlockPos origin, Vec3i size) {
        long volume() { return (long) size.getX() * size.getY() * size.getZ(); }
    }
}
