package com.sfgame;

import com.mojang.logging.LogUtils;
import com.sfgame.config.SFGameConfig;
import com.sfgame.network.SFGameNetwork;
import com.sfgame.registry.ModEntities;
import com.sfgame.registry.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SFGame.MOD_ID)
public final class SFGame {
    public static final String MOD_ID = "sfgame";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SFGame(FMLJavaModLoadingContext loadingContext) {
        IEventBus modBus = loadingContext.getModEventBus();
        modBus.addListener(this::commonSetup);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        loadingContext.registerConfig(ModConfig.Type.SERVER, SFGameConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SFGameNetwork.register();
            GameTestRegistry.register(com.sfgame.game.BeaconPlacementGameTest.class);
        });
    }
}
