package com.sfgame.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Path;

/** Resolves the per-save root used by SFGame's server-owned JSON configuration. */
public final class SFGameServerConfigPaths {
    private SFGameServerConfigPaths() {}

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("sfgame");
    }

}
