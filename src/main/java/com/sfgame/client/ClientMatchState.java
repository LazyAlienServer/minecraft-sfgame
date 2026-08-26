package com.sfgame.client;

import com.sfgame.network.ClientActionPacket;
import com.sfgame.network.MatchSnapshot;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.client.Minecraft;

public final class ClientMatchState {
    private static MatchSnapshot snapshot;

    private ClientMatchState() {}

    public static MatchSnapshot snapshot() { return snapshot; }

    public static void update(MatchSnapshot value) {
        snapshot = value;
        if (Minecraft.getInstance().screen instanceof SFGameScreen screen) screen.refresh();
    }

    public static void clear() {
        snapshot = null;
    }

    public static void openScreen() {
        Minecraft.getInstance().setScreen(new SFGameScreen());
        SFGameNetwork.sendToServer(new ClientActionPacket(ClientActionPacket.Action.REQUEST_SNAPSHOT, ""));
    }
}

