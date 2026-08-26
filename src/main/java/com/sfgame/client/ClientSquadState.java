package com.sfgame.client;

import com.sfgame.network.SFGameNetwork;
import com.sfgame.network.SquadActionPacket;
import com.sfgame.network.SquadSnapshot;
import net.minecraft.client.Minecraft;

public final class ClientSquadState {
    private static SquadSnapshot snapshot;

    private ClientSquadState() { }

    public static SquadSnapshot snapshot() { return snapshot; }

    public static void update(SquadSnapshot value) {
        snapshot = value;
        if (Minecraft.getInstance().screen instanceof SquadScreen screen) screen.refresh();
    }

    public static void clear() {
        snapshot = null;
    }

    public static void openScreen() {
        Minecraft.getInstance().setScreen(new SquadScreen());
        SFGameNetwork.sendToServer(new SquadActionPacket(SquadActionPacket.Action.REQUEST, 0));
    }

    public static void request() {
        SFGameNetwork.sendToServer(new SquadActionPacket(SquadActionPacket.Action.REQUEST, 0));
    }

    public static void join(int index) {
        SFGameNetwork.sendToServer(new SquadActionPacket(SquadActionPacket.Action.JOIN, index));
        request();
    }

    public static void leave() {
        SFGameNetwork.sendToServer(new SquadActionPacket(SquadActionPacket.Action.LEAVE, 0));
        request();
    }
}
