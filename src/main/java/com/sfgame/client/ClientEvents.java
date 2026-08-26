package com.sfgame.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sfgame.SFGame;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final KeyMapping OPEN_MENU = new KeyMapping("key.sfgame.open_menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.sfgame");

    @Mod.EventBusSubscriber(modid = SFGame.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
        }
    }

    @Mod.EventBusSubscriber(modid = SFGame.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (OPEN_MENU.consumeClick()) {
                ClientMatchState.openScreen();
            }
        }

        @SubscribeEvent
        public static void renderScoreboard(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
            SFGameScoreboardOverlay.render(event.getGuiGraphics(),
                    event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(),
                    ClientMatchState.snapshot());
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientAdminState.clear();
            ClientMatchState.clear();
        }
    }

    private ClientEvents() {}
}
