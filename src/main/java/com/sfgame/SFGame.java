package com.sfgame;

import com.mojang.logging.LogUtils;
import com.sfgame.config.SFGameConfig;
import com.sfgame.network.SFGameNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
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
        loadingContext.registerConfig(ModConfig.Type.COMMON, SFGameConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(SFGameNetwork::register);
    }
}
