package com.sfgame.client;

import com.sfgame.game.TeamSide;
import com.sfgame.network.ClientActionPacket;
import com.sfgame.network.MatchSnapshot;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SFGameScreen extends Screen {
    private boolean rebuilding;

    public SFGameScreen() {
        super(Component.translatable("sfgame.menu.title"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    public void refresh() {
        if (minecraft != null && minecraft.screen == this) rebuild();
    }

    private void rebuild() {
        if (rebuilding) return;
        rebuilding = true;
        clearWidgets();
        MatchSnapshot snapshot = ClientMatchState.snapshot();
        int center = width / 2;
        int y = 70;
        if (snapshot == null) {
            addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> requestRefresh())
                    .bounds(center - 60, y, 120, 20).build());
            rebuilding = false;
            return;
        }
        Component joinLabel = snapshot.queued() ? Component.translatable("sfgame.menu.queued") : Component.translatable("sfgame.menu.join");
        addRenderableWidget(Button.builder(joinLabel, b -> action(ClientActionPacket.Action.JOIN, ""))
                .bounds(center - 125, y, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("sfgame.menu.leave"), b -> action(ClientActionPacket.Action.LEAVE, ""))
                .bounds(center + 5, y, 120, 20).build());
        y += 32;
        if (snapshot.electionSeconds() > 0 && snapshot.side() == snapshot.attacker()) {
            for (int i = 0; i < snapshot.captainCandidates().size(); i++) {
                MatchSnapshot.CaptainCandidate candidate = snapshot.captainCandidates().get(i);
                int column = i % 2, row = i / 2;
                addRenderableWidget(Button.builder(Component.literal(candidate.name()),
                                b -> action(ClientActionPacket.Action.CAPTAIN_VOTE, candidate.uuid()))
                        .bounds(center - 150 + column * 152, y + row * 24, 148, 20).build());
            }
            y += ((snapshot.captainCandidates().size() + 1) / 2) * 24;
            addRenderableWidget(Button.builder(Component.translatable("sfgame.menu.captain.abstain"),
                            b -> action(ClientActionPacket.Action.CAPTAIN_ABSTAIN, ""))
                    .bounds(center - 75, y, 150, 20).build());
            y += 30;
        }
        java.util.List<MatchSnapshot.ClassView> classPool = snapshot.captain() ? snapshot.captainClasses() : snapshot.classes();
        String pendingClass = snapshot.captain() ? snapshot.pendingCaptainClass() : snapshot.pendingClass();
        ClientActionPacket.Action classAction = snapshot.captain()
                ? ClientActionPacket.Action.SELECT_CAPTAIN_CLASS : ClientActionPacket.Action.SELECT_CLASS;
        for (MatchSnapshot.ClassView view : classPool) {
            boolean selected = view.id().equals(pendingClass);
            Component label = Component.literal((selected ? "✓ " : "") + view.name() + " · " + view.gunId());
            int rowY = y;
            addRenderableWidget(Button.builder(label, b -> action(classAction, view.id()))
                    .bounds(center - 150, rowY, 300, 22).build());
            y += 28;
        }
        rebuilding = false;
    }

    private void action(ClientActionPacket.Action action, String value) {
        SFGameNetwork.sendToServer(new ClientActionPacket(action, value));
        requestRefresh();
    }

    private void requestRefresh() {
        SFGameNetwork.sendToServer(new ClientActionPacket(ClientActionPacket.Action.REQUEST_SNAPSHOT, ""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        MatchSnapshot snapshot = ClientMatchState.snapshot();
        if (snapshot != null) {
            boolean breakthrough = "breakthrough".equals(snapshot.modeId());
            String scores = breakthrough
                    ? "ATTACK " + snapshot.attacker().id().toUpperCase() + " · DEFEND " + snapshot.defender().id().toUpperCase()
                    + " · TICKETS " + snapshot.attackerTickets()
                    : TeamSide.PLAYABLE.stream().filter(team -> snapshot.players(team) > 0)
                    .map(team -> team.id().toUpperCase() + " " + snapshot.score(team)).collect(java.util.stream.Collectors.joining("  ·  "));
            graphics.drawCenteredString(font, Component.literal(scores).withStyle(ChatFormatting.BOLD), width / 2, 36, 0xFFFFFF);
            String side = snapshot.side().id().toUpperCase();
            String status = snapshot.modeId().toUpperCase() + " · " + snapshot.phase().name() + " · " + side + " · "
                    + String.format("%02d:%02d", snapshot.remainingSeconds() / 60, snapshot.remainingSeconds() % 60)
                    + " · " + TeamSide.PLAYABLE.stream().filter(team -> snapshot.players(team) > 0)
                    .map(team -> team.id().substring(0, 1).toUpperCase() + snapshot.players(team))
                    .collect(java.util.stream.Collectors.joining("/"));
            if (breakthrough) status += " · LEG " + snapshot.leg() + " · SECTOR " + snapshot.sector() + "/" + snapshot.sectorCount()
                    + (snapshot.captainName() == null ? "" : " · CAPTAIN " + snapshot.captainName());
            graphics.drawCenteredString(font, status, width / 2, 51, 0xB8B8B8);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
