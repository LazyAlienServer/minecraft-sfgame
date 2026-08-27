package com.sfgame.client;

import com.sfgame.game.MatchPhase;
import com.sfgame.network.SquadSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SquadScreen extends Screen {
    private static final int TOP = 64;
    private static final int BOTTOM = 12;
    private static final int CARD_WIDTH = 380;
    private static final int MEMBER_HEIGHT = 48;
    private static final int CARD_HEADER = 28;
    private int scroll;
    private int contentHeight;
    private boolean rebuilding;
    private final List<CardLayout> cards = new ArrayList<>();

    public SquadScreen() {
        super(Component.translatable("sfgame.squad.title"));
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
        cards.clear();
        SquadSnapshot snapshot = ClientSquadState.snapshot();
        if (snapshot == null) {
            addRenderableWidget(new SFGameScreen.DarkButton(width / 2 - 60, 80, 120, 22,
                    Component.translatable("sfgame.squad.refresh"), ignored -> ClientSquadState.request()));
            contentHeight = 0;
            rebuilding = false;
            return;
        }
        boolean running = ClientMatchState.snapshot() != null
                && ClientMatchState.snapshot().phase() == MatchPhase.RUNNING;
        int y = 0;
        if (running && snapshot.currentSquadIndex() != null) {
            addRenderableWidget(new SFGameScreen.DarkButton(width / 2 + 90, screenY(0), 100, 22,
                    Component.translatable("sfgame.squad.leave"), ignored -> ClientSquadState.leave()));
        }
        y += 30;
        for (SquadSnapshot.SquadView squad : snapshot.squads()) {
            int height = CARD_HEADER + Math.max(1, squad.members().size()) * MEMBER_HEIGHT + 8;
            int x = width / 2 - CARD_WIDTH / 2;
            int screenY = screenY(y);
            cards.add(new CardLayout(x, screenY, CARD_WIDTH, height, squad));
            if (running && squad.memberCount() < snapshot.maxMembers()
                    && !Integer.valueOf(squad.index()).equals(snapshot.currentSquadIndex())) {
                addRenderableWidget(new SFGameScreen.DarkButton(x + CARD_WIDTH - 86, screenY + 4, 78, 22,
                        Component.translatable("sfgame.squad.join"),
                        ignored -> ClientSquadState.join(squad.index())));
            }
            y += height + 8;
        }
        contentHeight = y;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        rebuilding = false;
    }

    private int screenY(int contentY) {
        return TOP + contentY - scroll;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - Math.max(0, height - TOP - BOTTOM));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int before = scroll;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * 24));
        if (scroll != before) rebuild();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, Component.translatable("sfgame.squad.title"), width / 2, 20, 0xFFFFFF);
        SquadSnapshot snapshot = ClientSquadState.snapshot();
        boolean running = ClientMatchState.snapshot() != null
                && ClientMatchState.snapshot().phase() == MatchPhase.RUNNING;
        if (snapshot == null || !running) {
            graphics.drawCenteredString(font, Component.translatable("sfgame.squad.outside_match"), width / 2, 80, 0xCCCCCC);
        } else {
            Component summary = Component.translatable("sfgame.squad.summary", snapshot.factionPlayerCount(), snapshot.maxMembers());
            graphics.drawCenteredString(font, summary, width / 2, 38, 0xD0D0D0);
            if (snapshot.beaconMaxHealth() > 0.0F) {
                Component beacon = Component.translatable("sfgame.squad.beacon_health",
                        Math.round(snapshot.beaconHealth()), Math.round(snapshot.beaconMaxHealth()));
                graphics.drawCenteredString(font, beacon, width / 2, 52, 0xD0D0D0);
            }
            graphics.enableScissor(0, TOP, width, height - BOTTOM);
            try {
                for (CardLayout card : cards) renderCard(graphics, card);
            } finally {
                graphics.disableScissor();
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCard(GuiGraphics graphics, CardLayout card) {
        SquadSnapshot.SquadView squad = card.squad();
        graphics.fill(card.x(), card.y(), card.x() + card.width(), card.y() + card.height(), 0xD0181818);
        graphics.fill(card.x(), card.y(), card.x() + card.width(), card.y() + 1, 0xFF666666);
        graphics.drawString(font, Component.translatable("sfgame.squad.card", squad.index(), squad.memberCount()),
                card.x() + 8, card.y() + 9, 0xFFFFFF, true);
        if (squad.members().isEmpty()) {
            graphics.drawString(font, Component.translatable("sfgame.squad.empty"), card.x() + 10,
                    card.y() + CARD_HEADER + 14, 0xAAAAAA);
            return;
        }
        for (int i = 0; i < squad.members().size(); i++) {
            SquadSnapshot.MemberView member = squad.members().get(i);
            int y = card.y() + CARD_HEADER + i * MEMBER_HEIGHT + 4;
            graphics.fill(card.x() + 4, y, card.x() + card.width() - 4, y + MEMBER_HEIGHT - 2, 0xA0202020);
            drawMemberAvatar(graphics, member.uuid(), card.x() + 10, y + 6, 32);
            graphics.drawString(font, Component.literal(member.name()), card.x() + 50, y + 10, 0xFFFFFF);
            String statusKey = member.respawning() ? "sfgame.squad.member_respawning"
                    : member.participating() ? "sfgame.squad.member_ready" : "sfgame.squad.member_spectating";
            graphics.drawString(font, Component.translatable(statusKey), card.x() + 50, y + 26, 0xAAAAAA);
        }
    }

    static void drawMemberAvatar(GuiGraphics graphics, UUID uuid, int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(uuid);
        ResourceLocation skin = skinLocation(info, uuid);
        PlayerFaceRenderer.draw(graphics, skin, x, y, size, true, false);
    }

    static ResourceLocation skinLocation(PlayerInfo info, UUID uuid) {
        ResourceLocation skin = info == null ? null : info.getSkinLocation();
        return skin == null ? DefaultPlayerSkin.getDefaultSkin(uuid) : skin;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record CardLayout(int x, int y, int width, int height, SquadSnapshot.SquadView squad) { }
}
