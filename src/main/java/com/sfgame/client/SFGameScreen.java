package com.sfgame.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import com.sfgame.SFGame;
import com.sfgame.data.ItemStrings;
import com.sfgame.game.MatchPhase;
import com.sfgame.game.MatchManager;
import com.sfgame.game.TeamSide;
import com.sfgame.network.ClientActionPacket;
import com.sfgame.network.MatchSnapshot;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SFGameScreen extends Screen {
    static final int CONTENT_TOP = 64;
    static final int CONTENT_BOTTOM_MARGIN = 12;
    private static final int CLASS_CARD_SIZE = 44;
    private static final int CLASS_CARD_GAP = 8;
    private static final int TALL_CARD_WIDTH = 72;
    private static final int TALL_CARD_HEIGHT = 96;
    private static final int TALL_CARD_GAP = 8;

    private boolean rebuilding;
    private int classScrollOffset;
    private int contentScrollOffset;
    private int contentHeight;
    private String lastModeId;
    private int classHeadingX;
    private int classHeadingY = -1;
    private int classStripLeft;
    private int classStripRight;
    private int classStripY = -1;
    private int visibleClassCount;
    private int totalClassCount;
    private final List<ClassCardButton> classCards = new ArrayList<>();
    private int captainVoteHeadingX;
    private int captainVoteHeadingY = -1;
    private int respawnHeadingX;
    private int respawnHeadingY = -1;
    private int supplyHeadingX;
    private int supplyHeadingY = -1;
    private int shopHeadingX;
    private int shopHeadingY = -1;
    private int cardStripLeft;
    private int cardStripRight;
    private final List<TallCardButton> tallCards = new ArrayList<>();

    public SFGameScreen() {
        super(Component.translatable("sfgame.menu.title"));
    }

    @Override
    protected void init() {
        rebuild();
        // The server only answers this request for permission level 2.  A
        // normal player never receives administrator state or mutation access.
        SFGameNetwork.sendToServer(com.sfgame.network.AdminActionPacket.request(false));
    }

    public void refresh() {
        if (minecraft != null && minecraft.screen == this) rebuild();
    }

    private void rebuild() {
        if (rebuilding) return;
        rebuilding = true;
        clearWidgets();
        resetLayoutMarkers();

        MatchSnapshot snapshot = ClientMatchState.snapshot();
        int center = width / 2;
        if (snapshot == null) {
            addRenderableWidget(new DarkButton(center - 60, 70, 120, 22, Component.literal("Refresh"),
                    button -> requestRefresh()));
            rebuilding = false;
            return;
        }
        if (!snapshot.modeId().equals(lastModeId)) {
            lastModeId = snapshot.modeId();
            contentScrollOffset = 0;
            classScrollOffset = 0;
        }
        if (ClientAdminState.snapshot() != null) {
            addRenderableWidget(new DarkButton(width - 88, 6, 72, 20,
                    Component.translatable("sfgame.admin.open"),
                    button -> SFGameNetwork.sendToServer(com.sfgame.network.AdminActionPacket.request(true))));
        }

        boolean activeMatch = snapshot.phase() == MatchPhase.PREPARING
                || snapshot.phase() == MatchPhase.COUNTDOWN
                || snapshot.phase() == MatchPhase.RUNNING
                || snapshot.phase() == MatchPhase.RESULT;
        boolean attackerElectionLocked = snapshot.phase() == MatchPhase.PREPARING
                && snapshot.electionSeconds() > 0 && snapshot.side() == snapshot.attacker();
        boolean showJoin = !snapshot.participating() && !attackerElectionLocked;
        boolean showLeave = !activeMatch;
        boolean joinedLobby = showLeave && snapshot.side() != TeamSide.NONE;
        boolean showElection = snapshot.electionSeconds() > 0 && snapshot.side() == snapshot.attacker();
        boolean economy = MatchManager.supportsEconomy(snapshot.modeId()) && snapshot.phase() == MatchPhase.RUNNING;
        int supplyCount = economy && snapshot.side() != TeamSide.NONE ? snapshot.supplyItems().size() : 0;
        int shopCount = economy ? snapshot.shopItems().size() : 0;

        int prefixHeight = 0;
        if (showJoin || showLeave) prefixHeight += 34;
        if (snapshot.awaitingRespawnSelection()) {
            prefixHeight += 16 + ((snapshot.respawnOptions().size() + 1) / 2) * 26 + 12;
        }
        if (showElection) {
            prefixHeight += 16 + ((snapshot.captainCandidates().size() + 1) / 2) * 26 + 32;
        }
        BodyLayout layout = layoutBody(width, prefixHeight, supplyCount, shopCount);
        contentHeight = layout.contentHeight();
        contentScrollOffset = Math.max(0, Math.min(contentScrollOffset,
                maxContentScroll(contentHeight, height)));

        int contentY = 0;
        if (showJoin) {
            Component joinLabel = snapshot.queued()
                    ? Component.translatable("sfgame.menu.queued") : Component.translatable("sfgame.menu.join");
            DarkButton join = new DarkButton(showLeave ? center - 125 : center - 60, contentToScreen(contentY),
                    120, 22, joinLabel, button -> action(ClientActionPacket.Action.JOIN, ""));
            join.active = !snapshot.queued() && !joinedLobby;
            addBodyWidget(join);
        }
        if (showLeave) {
            DarkButton leave = new DarkButton(showJoin ? center + 5 : center - 60, contentToScreen(contentY),
                    120, 22, Component.translatable("sfgame.menu.leave"),
                    button -> action(ClientActionPacket.Action.LEAVE, ""));
            leave.active = joinedLobby;
            addBodyWidget(leave);
        }
        if (showJoin || showLeave) contentY += 34;

        if (snapshot.awaitingRespawnSelection()) {
            respawnHeadingX = center - 150;
            respawnHeadingY = visibleHeadingY(contentY);
            contentY += 16;
            for (int i = 0; i < snapshot.respawnOptions().size(); i++) {
                MatchSnapshot.RespawnOption option = snapshot.respawnOptions().get(i);
                Component label = option.pointId().isEmpty()
                        ? Component.translatable("sfgame.respawn.base")
                        : Component.translatable("sfgame.respawn.point", option.pointId().toUpperCase(Locale.ROOT));
                int column = i % 2;
                int row = i / 2;
                addBodyWidget(new DarkButton(center - 150 + column * 152,
                        contentToScreen(contentY + row * 26), 148, 22, label,
                        button -> action(ClientActionPacket.Action.SELECT_RESPAWN, option.id())));
            }
            contentY += ((snapshot.respawnOptions().size() + 1) / 2) * 26 + 12;
        }

        if (showElection) {
            captainVoteHeadingX = center - 150;
            captainVoteHeadingY = visibleHeadingY(contentY);
            contentY += 16;
            for (int i = 0; i < snapshot.captainCandidates().size(); i++) {
                MatchSnapshot.CaptainCandidate candidate = snapshot.captainCandidates().get(i);
                int column = i % 2;
                int row = i / 2;
                addBodyWidget(new DarkButton(center - 150 + column * 152,
                        contentToScreen(contentY + row * 26), 148, 22, Component.literal(candidate.name()),
                        button -> action(ClientActionPacket.Action.CAPTAIN_VOTE, candidate.uuid())));
            }
            contentY += ((snapshot.captainCandidates().size() + 1) / 2) * 26;
            addBodyWidget(new DarkButton(center - 75, contentToScreen(contentY), 150, 22,
                    Component.translatable("sfgame.menu.captain.abstain"),
                    button -> action(ClientActionPacket.Action.CAPTAIN_ABSTAIN, "")));
        }

        List<MatchSnapshot.ClassView> classPool = snapshot.captain()
                ? snapshot.captainClasses() : snapshot.classes();
        String pendingClass = snapshot.captain()
                ? snapshot.pendingCaptainClass() : snapshot.pendingClass();
        ClientActionPacket.Action classAction = snapshot.captain()
                ? ClientActionPacket.Action.SELECT_CAPTAIN_CLASS : ClientActionPacket.Action.SELECT_CLASS;
        classHeadingX = center - Math.min(240, Math.max(CLASS_CARD_SIZE, width - 40)) / 2;
        classHeadingY = visibleHeadingY(layout.classHeading());
        addClassCards(classPool, pendingClass, classAction, center, contentToScreen(layout.classStrip()));

        cardStripLeft = center - layout.contentWidth() / 2;
        cardStripRight = cardStripLeft + layout.contentWidth();
        if (supplyCount > 0) {
            supplyHeadingX = cardStripLeft;
            supplyHeadingY = visibleHeadingY(layout.supplyHeading());
            addSupplyCards(snapshot.supplyItems(), layout);
        }
        if (shopCount > 0) {
            shopHeadingX = cardStripLeft;
            shopHeadingY = visibleHeadingY(layout.shopHeading());
            addShopCards(snapshot.shopItems(), layout);
        }
        rebuilding = false;
    }

    static BodyLayout layoutBody(int width, int prefixHeight, int supplyCount, int shopCount) {
        int availableWidth = Math.max(TALL_CARD_WIDTH, Math.min(480, width - 40));
        int columns = Math.max(1, availableWidth / (TALL_CARD_WIDTH + TALL_CARD_GAP));
        int contentWidth = columns * TALL_CARD_WIDTH + (columns - 1) * TALL_CARD_GAP;
        int classHeading = prefixHeight;
        int classStrip = classHeading + 16;
        int cursor = classStrip + CLASS_CARD_SIZE + 16;
        int supplyHeading = -1;
        int supplyStart = -1;
        int supplyRows = 0;
        if (supplyCount > 0) {
            supplyHeading = cursor;
            supplyStart = cursor + 16;
            supplyRows = (supplyCount + columns - 1) / columns;
            cursor = supplyStart + supplyRows * TALL_CARD_HEIGHT
                    + Math.max(0, supplyRows - 1) * TALL_CARD_GAP + 16;
        }
        int shopHeading = -1;
        int shopStart = -1;
        int shopRows = 0;
        if (shopCount > 0) {
            shopHeading = cursor;
            shopStart = cursor + 16;
            shopRows = (shopCount + columns - 1) / columns;
            cursor = shopStart + shopRows * TALL_CARD_HEIGHT
                    + Math.max(0, shopRows - 1) * TALL_CARD_GAP + 4;
        }
        return new BodyLayout(classHeading, classStrip, supplyHeading, supplyStart, supplyRows,
                shopHeading, shopStart, shopRows, cursor, columns, contentWidth);
    }

    static int maxContentScroll(int contentHeight, int height) {
        return Math.max(0, contentHeight - Math.max(0, height - CONTENT_TOP - CONTENT_BOTTOM_MARGIN));
    }

    private int contentToScreen(int contentY) { return CONTENT_TOP + contentY - contentScrollOffset; }
    private int visibleHeadingY(int contentY) {
        if (contentY < 0) return -1;
        int y = contentToScreen(contentY);
        return y >= CONTENT_TOP && y + 9 <= height - CONTENT_BOTTOM_MARGIN ? y : -1;
    }
    private boolean fullyVisible(int y, int widgetHeight) {
        return y >= CONTENT_TOP && y + widgetHeight <= height - CONTENT_BOTTOM_MARGIN;
    }
    private void addBodyWidget(Button button) {
        if (fullyVisible(button.getY(), button.getHeight())) addRenderableWidget(button);
    }

    private void addSupplyCards(List<MatchSnapshot.SupplyView> items, BodyLayout layout) {
        for (int i = 0; i < items.size(); i++) {
            MatchSnapshot.SupplyView item = items.get(i);
            int x = cardStripLeft + i % layout.columns() * (TALL_CARD_WIDTH + TALL_CARD_GAP);
            int y = contentToScreen(layout.supplyStart()
                    + i / layout.columns() * (TALL_CARD_HEIGHT + TALL_CARD_GAP));
            TallCardButton card = TallCardButton.supply(x, y, item,
                    button -> action(ClientActionPacket.Action.SUPPLY_CLAIM, item.id()));
            if (fullyVisible(y, TALL_CARD_HEIGHT)) {
                tallCards.add(card);
                addRenderableWidget(card);
            }
        }
    }

    private void addShopCards(List<MatchSnapshot.ShopView> items, BodyLayout layout) {
        for (int i = 0; i < items.size(); i++) {
            MatchSnapshot.ShopView item = items.get(i);
            int x = cardStripLeft + i % layout.columns() * (TALL_CARD_WIDTH + TALL_CARD_GAP);
            int y = contentToScreen(layout.shopStart()
                    + i / layout.columns() * (TALL_CARD_HEIGHT + TALL_CARD_GAP));
            TallCardButton card = TallCardButton.shop(x, y, item,
                    button -> action(ClientActionPacket.Action.SHOP_BUY, item.id()));
            if (fullyVisible(y, TALL_CARD_HEIGHT)) {
                tallCards.add(card);
                addRenderableWidget(card);
            }
        }
    }

    static record BodyLayout(int classHeading, int classStrip, int supplyHeading, int supplyStart,
                             int supplyRows, int shopHeading, int shopStart, int shopRows,
                             int contentHeight, int columns, int contentWidth) { }

    private void addClassCards(List<MatchSnapshot.ClassView> classPool, String pendingClass,
                               ClientActionPacket.Action classAction, int center, int y) {
        totalClassCount = classPool.size();
        if (classPool.isEmpty()) {
            classScrollOffset = 0;
            return;
        }

        int availableWidth = Math.max(CLASS_CARD_SIZE, Math.min(480, width - 40));
        visibleClassCount = Math.max(1,
                Math.min(totalClassCount, (availableWidth + CLASS_CARD_GAP) / (CLASS_CARD_SIZE + CLASS_CARD_GAP)));
        int maxOffset = Math.max(0, totalClassCount - visibleClassCount);
        classScrollOffset = Math.max(0, Math.min(classScrollOffset, maxOffset));
        int contentWidth = visibleClassCount * CLASS_CARD_SIZE + (visibleClassCount - 1) * CLASS_CARD_GAP;

        classStripLeft = center - contentWidth / 2;
        classStripRight = classStripLeft + contentWidth;
        classStripY = fullyVisible(y, CLASS_CARD_SIZE) ? y : -1;
        if (classStripY < 0) return;
        for (int visibleIndex = 0; visibleIndex < visibleClassCount; visibleIndex++) {
            MatchSnapshot.ClassView view = classPool.get(classScrollOffset + visibleIndex);
            boolean selected = view.id().equals(pendingClass);
            int x = classStripLeft + visibleIndex * (CLASS_CARD_SIZE + CLASS_CARD_GAP);
            ClassCardButton card = new ClassCardButton(x, classStripY, view, selected,
                    button -> action(classAction, view.id()));
            classCards.add(card);
            addRenderableWidget(card);
        }
    }

    private void resetLayoutMarkers() {
        classHeadingY = -1;
        classStripY = -1;
        visibleClassCount = 0;
        totalClassCount = 0;
        classCards.clear();
        captainVoteHeadingY = -1;
        respawnHeadingY = -1;
        supplyHeadingY = -1;
        shopHeadingY = -1;
        tallCards.clear();
    }

    private void action(ClientActionPacket.Action action, String value) {
        SFGameNetwork.sendToServer(new ClientActionPacket(action, value));
        if (action == ClientActionPacket.Action.SELECT_RESPAWN && minecraft != null) {
            minecraft.setScreen(null);
            return;
        }
        requestRefresh();
    }

    private void requestRefresh() {
        SFGameNetwork.sendToServer(new ClientActionPacket(ClientActionPacket.Action.REQUEST_SNAPSHOT, ""));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean overClass = totalClassCount > visibleClassCount && classStripY >= 0
                && mouseX >= classStripLeft - 8 && mouseX <= classStripRight + 8
                && mouseY >= classStripY - 4 && mouseY <= classStripY + CLASS_CARD_SIZE + 8;
        ScrollResult result = scrollOffsets(overClass, totalClassCount, visibleClassCount, classScrollOffset,
                contentScrollOffset, maxContentScroll(contentHeight, height), delta);
        if (result.classOffset() != classScrollOffset || result.contentOffset() != contentScrollOffset) {
            classScrollOffset = result.classOffset();
            contentScrollOffset = result.contentOffset();
            rebuild();
        }
        if (result.consumed()) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    static ScrollResult scrollOffsets(boolean overClass, int totalClasses, int visibleClasses,
                                      int classOffset, int contentOffset, int maxContentOffset, double delta) {
        if (delta == 0) return new ScrollResult(classOffset, contentOffset, false);
        if (overClass && totalClasses > visibleClasses) {
            int next = Math.max(0, Math.min(totalClasses - visibleClasses,
                    classOffset + (delta < 0 ? 1 : -1)));
            return new ScrollResult(next, contentOffset, true);
        }
        if (maxContentOffset > 0) {
            int next = Math.max(0, Math.min(maxContentOffset,
                    contentOffset + (delta < 0 ? 24 : -24)));
            return new ScrollResult(classOffset, next, true);
        }
        return new ScrollResult(classOffset, contentOffset, false);
    }

    static record ScrollResult(int classOffset, int contentOffset, boolean consumed) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        MatchSnapshot snapshot = ClientMatchState.snapshot();
        if (snapshot != null) {
            boolean breakthrough = "breakthrough".equals(snapshot.modeId());
            boolean ctf = "ctf".equals(snapshot.modeId());
            String scores = breakthrough
                    ? "ATTACK " + snapshot.attacker().id().toUpperCase(Locale.ROOT)
                    + " · DEFEND " + snapshot.defender().id().toUpperCase(Locale.ROOT)
                    + " · TICKETS " + snapshot.attackerTickets()
                    : ctf ? "CTF " + (snapshot.ctfVariant() == null ? "" : snapshot.ctfVariant().toUpperCase(Locale.ROOT))
                    + " · " + TeamSide.PLAYABLE.stream().filter(team -> snapshot.players(team) > 0)
                    .map(team -> team.id().toUpperCase(Locale.ROOT) + " " + snapshot.score(team))
                    .collect(java.util.stream.Collectors.joining("  ·  "))
                    : TeamSide.PLAYABLE.stream().filter(team -> snapshot.players(team) > 0)
                    .map(team -> team.id().toUpperCase(Locale.ROOT) + " " + snapshot.score(team))
                    .collect(java.util.stream.Collectors.joining("  ·  "));
            graphics.drawCenteredString(font, Component.literal(scores).withStyle(ChatFormatting.BOLD),
                    width / 2, 36, 0xFFFFFF);
            if (MatchManager.supportsEconomy(snapshot.modeId()) && snapshot.phase() == MatchPhase.RUNNING) {
                graphics.drawCenteredString(font,
                        Component.translatable("sfgame.menu.currency", snapshot.currency()).withStyle(ChatFormatting.GOLD),
                        width / 2, 52, 0xFFFFFF);
            }
        }
        if (respawnHeadingY >= 0) {
            graphics.drawString(font, Component.translatable("sfgame.respawn.choose"),
                    respawnHeadingX, respawnHeadingY, 0xFFFFFF, true);
        }
        if (captainVoteHeadingY >= 0) {
            graphics.drawString(font, Component.translatable("sfgame.menu.captain.vote_title"),
                    captainVoteHeadingX, captainVoteHeadingY, 0xFFFFFF, true);
        }
        if (classHeadingY >= 0) {
            graphics.drawString(font, Component.translatable("sfgame.menu.class_select"),
                    classHeadingX, classHeadingY, 0xFFFFFF, true);
        }
        if (classStripY >= 0 && totalClassCount > visibleClassCount) {
            if (classScrollOffset > 0) {
                graphics.drawCenteredString(font, "‹", classStripLeft - 10,
                        classStripY + CLASS_CARD_SIZE / 2 - 4, 0xFFFFFF);
            }
            if (classScrollOffset + visibleClassCount < totalClassCount) {
                graphics.drawCenteredString(font, "›", classStripRight + 10,
                        classStripY + CLASS_CARD_SIZE / 2 - 4, 0xFFFFFF);
            }
        }
        if (supplyHeadingY >= 0) {
            graphics.drawString(font, Component.translatable("sfgame.menu.supply"),
                    supplyHeadingX, supplyHeadingY, 0xFFFFFF, true);
        }
        if (shopHeadingY >= 0) {
            graphics.drawString(font, Component.translatable("sfgame.menu.shop"),
                    shopHeadingX, shopHeadingY, 0xFFFFFF, true);
        }
        int maxScroll = maxContentScroll(contentHeight, height);
        if (contentScrollOffset > 0) {
            graphics.drawCenteredString(font, "↑", width - 12, CONTENT_TOP + 2, 0xFFFFFF);
        }
        if (contentScrollOffset < maxScroll) {
            graphics.drawCenteredString(font, "↓", width - 12,
                    height - CONTENT_BOTTOM_MARGIN - 10, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderClassTooltip(graphics, mouseX, mouseY);
        renderTallCardTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ItemStack iconStack(String iconId) {
        return ItemStrings.stack(iconId, 1, new ItemStack(Items.BARRIER));
    }

    private static TextureCrop gunHud(ItemStack icon) {
        if (!(icon.getItem() instanceof IGun)) return null;
        ResourceLocation texture = TimelessAPI.getGunDisplay(icon)
                .map(display -> display.getHUDTexture())
                .orElse(null);
        return texture == null ? null : textureCrop(texture);
    }

    private static TextureCrop pngIcon(String textureId) {
        ResourceLocation texture = ResourceLocation.tryParse(textureId);
        if (texture == null) {
            SFGame.LOGGER.warn("Invalid profession icon texture: {}", textureId);
            return null;
        }
        return textureCrop(texture);
    }

    private static TextureCrop textureCrop(ResourceLocation texture) {
        try (InputStream input = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(texture).open();
             NativeImage image = NativeImage.read(input)) {
            int minX = image.getWidth();
            int minY = image.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getPixelRGBA(x, y) >>> 24) == 0) continue;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
            if (maxX < minX || maxY < minY) return null;
            return new TextureCrop(texture, image.getWidth(), image.getHeight(),
                    minX, minY, maxX - minX + 1, maxY - minY + 1);
        } catch (IOException | RuntimeException exception) {
            SFGame.LOGGER.warn("Could not read profession icon texture {}", texture, exception);
            return null;
        }
    }

    private static void renderIcon(GuiGraphics graphics, ItemStack icon, TextureCrop texture,
                                   int x, int y, int size) {
        if (texture != null) {
            float scale = Math.min((float) size / texture.width(), (float) size / texture.height());
            int width = Math.max(1, Math.round(texture.width() * scale));
            int height = Math.max(1, Math.round(texture.height() * scale));
            graphics.blit(texture.texture(), x + (size - width) / 2, y + (size - height) / 2,
                    width, height, texture.x(), texture.y(), texture.width(), texture.height(),
                    texture.textureWidth(), texture.textureHeight());
            return;
        }

        float scale = size / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 100.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(icon, 0, 0);
        graphics.pose().popPose();
        RenderSystem.applyModelViewMatrix();
    }


    private record TextureCrop(ResourceLocation texture, int textureWidth, int textureHeight,
                               int x, int y, int width, int height) {
    }


    private void renderClassTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (ClassCardButton card : classCards) {
            if (!card.isMouseOver(mouseX, mouseY)) continue;
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : card.tooltipLines) lines.addAll(font.split(line, 220));
            graphics.renderTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }

    private void renderTallCardTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (TallCardButton card : tallCards) {
            if (!card.isMouseOver(mouseX, mouseY)) continue;
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : card.tooltipLines) lines.addAll(font.split(line, 220));
            graphics.renderTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }

    private static List<Component> classTooltip(MatchSnapshot.ClassView view) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(view.name()).withStyle(ChatFormatting.YELLOW));
        if (!view.description().isBlank()) lines.add(Component.literal(view.description()));
        if (!view.gunId().isBlank()) lines.add(Component.literal(view.gunId()).withStyle(ChatFormatting.GRAY));
        return List.copyOf(lines);
    }

    private static List<Component> shopTooltip(MatchSnapshot.ShopView view) {
        return List.of(Component.literal(view.name()).withStyle(ChatFormatting.YELLOW),
                Component.translatable("sfgame.shop.price", view.price()).withStyle(ChatFormatting.GOLD),
                Component.translatable("sfgame.shop.buy").withStyle(ChatFormatting.GRAY));
    }

    private static List<Component> supplyTooltip(MatchSnapshot.SupplyView view) {
        return List.of(Component.literal(view.name()).withStyle(ChatFormatting.YELLOW),
                Component.translatable("sfgame.supply.quantity", view.quantity()).withStyle(ChatFormatting.AQUA),
                Component.translatable("sfgame.supply.claim").withStyle(ChatFormatting.GRAY));
    }

    private static class DarkButton extends Button {
        private DarkButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            boolean pressed = isHovered && GLFW.glfwGetMouseButton(
                    Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            int background = !active ? 0xD0202020 : pressed ? 0xF0101010 : hovered ? 0xF0383838 : 0xE0181818;
            int border = hovered && active ? 0xFFFFFFFF : 0xFF666666;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, border);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
            graphics.fill(getX(), getY(), getX() + 1, getY() + height, border);
            graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);
            int textColor = active ? 0xFFFFFFFF : 0xFF8A8A8A;
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2, textColor);
        }
    }

    private static final class ClassCardButton extends DarkButton {
        private final ItemStack icon;
        private final TextureCrop texture;
        private final boolean selected;
        private final List<Component> tooltipLines;

        private ClassCardButton(int x, int y, MatchSnapshot.ClassView view, boolean selected, OnPress onPress) {
            super(x, y, CLASS_CARD_SIZE, CLASS_CARD_SIZE, Component.empty(), onPress);
            this.icon = iconStack(view.icon());
            this.texture = switch (view.iconRender()) {
                case "hud" -> gunHud(icon);
                case "png" -> pngIcon(view.iconTexture());
                default -> null;
            };
            this.selected = selected;
            this.tooltipLines = classTooltip(view);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            renderIcon(graphics, icon, texture, getX() + 6, getY() + 6, 32);
            if (selected) {
                int color = 0xFFFFD54F;
                graphics.fill(getX(), getY(), getX() + width, getY() + 2, color);
                graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height, color);
                graphics.fill(getX(), getY(), getX() + 2, getY() + height, color);
                graphics.fill(getX() + width - 2, getY(), getX() + width, getY() + height, color);
            }
        }
    }

    private static final class TallCardButton extends DarkButton {
        private final ItemStack icon;
        private final String name;
        private final Component detail;
        private final int detailColor;
        private final List<Component> tooltipLines;

        private TallCardButton(int x, int y, String icon, String name, Component detail,
                               int detailColor, List<Component> tooltipLines, OnPress onPress) {
            super(x, y, TALL_CARD_WIDTH, TALL_CARD_HEIGHT, Component.empty(), onPress);
            this.icon = iconStack(icon);
            this.name = name;
            this.detail = detail;
            this.detailColor = detailColor;
            this.tooltipLines = tooltipLines;
        }

        static TallCardButton shop(int x, int y, MatchSnapshot.ShopView view, OnPress onPress) {
            return new TallCardButton(x, y, view.icon(), view.name(),
                    Component.translatable("sfgame.shop.price", view.price()).withStyle(ChatFormatting.GOLD),
                    0xFFFFD54F, shopTooltip(view), onPress);
        }

        static TallCardButton supply(int x, int y, MatchSnapshot.SupplyView view, OnPress onPress) {
            return new TallCardButton(x, y, view.icon(), view.name(),
                    Component.translatable("sfgame.supply.quantity", view.quantity()).withStyle(ChatFormatting.AQUA),
                    0xFF55FFFF, supplyTooltip(view), onPress);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            renderIcon(graphics, icon, null, getX() + 20, getY() + 6, 32);
            String displayName = Minecraft.getInstance().font.plainSubstrByWidth(name, TALL_CARD_WIDTH - 6);
            graphics.drawCenteredString(Minecraft.getInstance().font, Component.literal(displayName),
                    getX() + width / 2, getY() + 52, 0xFFFFFFFF);
            graphics.drawCenteredString(Minecraft.getInstance().font, detail,
                    getX() + width / 2, getY() + 68, detailColor);
        }
    }
}
