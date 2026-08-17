package com.sfgame.client;

import com.sfgame.network.AdminSnapshot;
import net.minecraft.client.Minecraft;

public final class ClientAdminState {
    private static AdminSnapshot snapshot;

    private ClientAdminState() {
    }

    public static AdminSnapshot snapshot() {
        return snapshot;
    }

    public static void update(AdminSnapshot value, boolean openScreen) {
        snapshot = value;
        Minecraft minecraft = Minecraft.getInstance();
        if (openScreen) {
            minecraft.setScreen(new SFGameAdminScreen());
        } else if (minecraft.screen instanceof SFGameAdminScreen screen) {
            screen.refresh();
        } else if (minecraft.screen instanceof SFGameScreen screen) {
            screen.refresh();
        }
    }

    public static void clear() {
        snapshot = null;
    }
}
