package com.sfgame.client;

import com.sfgame.game.MatchPhase;
import com.sfgame.game.TeamSide;
import com.sfgame.network.ClientActionPacket;
import com.sfgame.network.MatchSnapshot;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SFGameScreen extends Screen {
    private static final int CLASS_CARD_SIZE = 44;
    private static final int CLASS_CARD_GAP = 8;
    private static final int SHOP_CARD_WIDTH = 72;
    private static final int SHOP_CARD_HEIGHT = 96;
    private static final int SHOP_CARD_GAP = 8;

    private boolean rebuilding;
    private int classScrollOffset;
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
    private int shopHeadingX;
    private int shopHeadingY = -1;
    private int shopStripLeft;
    private int shopStripRight;
    private int shopStripY = -1;
    private int shopStripBottom;
    private int shopScrollOffset;
    private int visibleShopColumns;
    private int visibleShopRows;
    private int totalShopRows;
    private int totalShopCount;
    private final List<ShopCardButton> shopCards = new ArrayList<>();

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
        resetLayoutMarkers();

        MatchSnapshot snapshot = ClientMatchState.snapshot();
        int center = width / 2;
        int y = 70;
        if (snapshot == null) {
            addRenderableWidget(new DarkButton(center - 60, y, 120, 22, Component.literal("Refresh"),
                    button -> requestRefresh()));
            rebuilding = false;
            return;
        }

        boolean activeMatch = snapshot.phase() == MatchPhase.PREPARING
                || snapshot.phase() == MatchPhase.COUNTDOWN
                || snapshot.phase() == MatchPhase.RUNNING
                || snapshot.phase() == MatchPhase.RESULT;
        boolean attackerElectionLocked = snapshot.phase() == MatchPhase.PREPARING
                && snapshot.electionSeconds() > 0 && snapshot.side() == snapshot.attacker();
        boolean showJoin = !snapshot.participating() && !attackerElectionLocked;
        boolean showLeave = !activeMatch;
        // In the lobby, the bound vanilla team is the server-side membership
        // selection made by the Join Game action.  Make the two choices
        // mutually exclusive: the selected action is disabled while the
        // opposite action remains available.
        boolean joinedLobby = showLeave && snapshot.side() != TeamSide.NONE;
        if (showJoin) {
            Component joinLabel = snapshot.queued()
                    ? Component.translatable("sfgame.menu.queued")
                    : Component.translatable("sfgame.menu.join");
            DarkButton join = new DarkButton(showLeave ? center - 125 : center - 60, y, 120, 22,
                    joinLabel, button -> action(ClientActionPacket.Action.JOIN, ""));
            join.active = !snapshot.queued() && !joinedLobby;
            addRenderableWidget(join);
        }
        if (showLeave) {
            DarkButton spectate = new DarkButton(showJoin ? center + 5 : center - 60, y, 120, 22,
                    Component.translatable("sfgame.menu.leave"),
                    button -> action(ClientActionPacket.Action.LEAVE, ""));
            spectate.active = joinedLobby;
            addRenderableWidget(spectate);
        }
        if (showJoin || showLeave) y += 34;

        if (snapshot.awaitingRespawnSelection()) {
            respawnHeadingX = center - 150;
            respawnHeadingY = y;
            y += 16;
            for (int i = 0; i < snapshot.respawnOptions().size(); i++) {
                MatchSnapshot.RespawnOption option = snapshot.respawnOptions().get(i);
                Component label = option.pointId().isEmpty()
                        ? Component.translatable("sfgame.respawn.base")
                        : Component.translatable("sfgame.respawn.point", option.pointId().toUpperCase(Locale.ROOT));
                int column = i % 2;
                int row = i / 2;
                addRenderableWidget(new DarkButton(center - 150 + column * 152, y + row * 26, 148, 22,
                        label, button -> action(ClientActionPacket.Action.SELECT_RESPAWN, option.id())));
            }
            y += ((snapshot.respawnOptions().size() + 1) / 2) * 26 + 12;
        }

        if (snapshot.electionSeconds() > 0 && snapshot.side() == snapshot.attacker()) {
            captainVoteHeadingX = center - 150;
            captainVoteHeadingY = y;
            y += 16;
            for (int i = 0; i < snapshot.captainCandidates().size(); i++) {
                MatchSnapshot.CaptainCandidate candidate = snapshot.captainCandidates().get(i);
                int column = i % 2;
                int row = i / 2;
                addRenderableWidget(new DarkButton(center - 150 + column * 152, y + row * 26, 148, 22,
                        Component.literal(candidate.name()),
                        button -> action(ClientActionPacket.Action.CAPTAIN_VOTE, candidate.uuid())));
            }
            y += ((snapshot.captainCandidates().size() + 1) / 2) * 26;
            addRenderableWidget(new DarkButton(center - 75, y, 150, 22,
                    Component.translatable("sfgame.menu.captain.abstain"),
                    button -> action(ClientActionPacket.Action.CAPTAIN_ABSTAIN, "")));
            y += 32;
        }

        List<MatchSnapshot.ClassView> classPool = snapshot.captain()
                ? snapshot.captainClasses() : snapshot.classes();
        String pendingClass = snapshot.captain()
                ? snapshot.pendingCaptainClass() : snapshot.pendingClass();
        ClientActionPacket.Action classAction = snapshot.captain()
                ? ClientActionPacket.Action.SELECT_CAPTAIN_CLASS : ClientActionPacket.Action.SELECT_CLASS;
        addClassCards(classPool, pendingClass, classAction, center, y);
        if ("ctf".equals(snapshot.modeId()) && snapshot.phase() == MatchPhase.RUNNING && !snapshot.ctfShopItems().isEmpty()) {
            int shopY = classStripY >= 0 ? classStripY + CLASS_CARD_SIZE + 24 : y;
            addShopCards(snapshot, center, shopY);
        }
        rebuilding = false;
    }

    private void addShopCards(MatchSnapshot snapshot, int center, int y) {
        List<MatchSnapshot.ShopView> shopItems = snapshot.ctfShopItems();
        totalShopCount = shopItems.size();
        if (shopItems.isEmpty()) {
            shopScrollOffset = 0;
            return;
        }

        int availableWidth = Math.max(SHOP_CARD_WIDTH, Math.min(480, width - 40));
        visibleShopColumns = Math.max(1,
                Math.min(totalShopCount, (availableWidth + SHOP_CARD_GAP) / (SHOP_CARD_WIDTH + SHOP_CARD_GAP)));
        int availableHeight = Math.max(SHOP_CARD_HEIGHT,
                height - (y + 16) - 12);
        visibleShopRows = Math.max(1,
                Math.min(2, (availableHeight + SHOP_CARD_GAP) / (SHOP_CARD_HEIGHT + SHOP_CARD_GAP)));
        totalShopRows = (totalShopCount + visibleShopColumns - 1) / visibleShopColumns;
        int maxOffset = Math.max(0, totalShopRows - visibleShopRows);
        shopScrollOffset = Math.max(0, Math.min(shopScrollOffset, maxOffset));
        int contentWidth = visibleShopColumns * SHOP_CARD_WIDTH + (visibleShopColumns - 1) * SHOP_CARD_GAP;

        shopStripLeft = center - contentWidth / 2;
        shopStripRight = shopStripLeft + contentWidth;
        shopHeadingX = shopStripLeft;
        shopHeadingY = y;
        shopStripY = y + 16;
        shopStripBottom = shopStripY + visibleShopRows * SHOP_CARD_HEIGHT
                + (visibleShopRows - 1) * SHOP_CARD_GAP;
        for (int row = 0; row < visibleShopRows; row++) {
            for (int column = 0; column < visibleShopColumns; column++) {
                int itemIndex = (shopScrollOffset + row) * visibleShopColumns + column;
                if (itemIndex >= shopItems.size()) continue;
                MatchSnapshot.ShopView item = shopItems.get(itemIndex);
                int x = shopStripLeft + column * (SHOP_CARD_WIDTH + SHOP_CARD_GAP);
                int cardY = shopStripY + row * (SHOP_CARD_HEIGHT + SHOP_CARD_GAP);
                ShopCardButton card = new ShopCardButton(x, cardY, item,
                        button -> action(ClientActionPacket.Action.SHOP_BUY, item.id()));
                shopCards.add(card);
                addRenderableWidget(card);
            }
        }
    }

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
        classHeadingX = classStripLeft;
        classHeadingY = y;
        classStripY = y + 16;

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
        shopHeadingY = -1;
        shopStripY = -1;
        shopStripBottom = 0;
        visibleShopColumns = 0;
        visibleShopRows = 0;
        totalShopRows = 0;
        totalShopCount = 0;
        shopCards.clear();
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
        if (totalClassCount > visibleClassCount && classStripY >= 0
                && mouseX >= classStripLeft - 8 && mouseX <= classStripRight + 8
                && mouseY >= classStripY - 4 && mouseY <= classStripY + CLASS_CARD_SIZE + 8) {
            int maxOffset = totalClassCount - visibleClassCount;
            int nextOffset = Math.max(0,
                    Math.min(maxOffset, classScrollOffset + (delta < 0 ? 1 : -1)));
            if (nextOffset != classScrollOffset) {
                classScrollOffset = nextOffset;
                rebuild();
            }
            return true;
        }
        if (totalShopRows > visibleShopRows && shopStripY >= 0
                && mouseX >= shopStripLeft - 8 && mouseX <= shopStripRight + 8
                && mouseY >= shopStripY - 4 && mouseY <= shopStripBottom + 8) {
            int maxOffset = totalShopRows - visibleShopRows;
            int nextOffset = Math.max(0,
                    Math.min(maxOffset, shopScrollOffset + (delta < 0 ? 1 : -1)));
            if (nextOffset != shopScrollOffset) {
                shopScrollOffset = nextOffset;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

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
            if ("ctf".equals(snapshot.modeId())) {
                graphics.drawCenteredString(font, Component.literal("货币 " + snapshot.ctfCurrency()).withStyle(ChatFormatting.GOLD),
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
            if (totalClassCount > visibleClassCount) {
                if (classScrollOffset > 0) {
                    graphics.drawCenteredString(font, "‹", classStripLeft - 10,
                            classStripY + CLASS_CARD_SIZE / 2 - 4, 0xFFFFFF);
                }
                if (classScrollOffset + visibleClassCount < totalClassCount) {
                    graphics.drawCenteredString(font, "›", classStripRight + 10,
                            classStripY + CLASS_CARD_SIZE / 2 - 4, 0xFFFFFF);
                }
            }
        }
        if (shopHeadingY >= 0) {
            graphics.drawString(font, Component.translatable("sfgame.menu.shop"), shopHeadingX, shopHeadingY, 0xFFFFFF, true);
            if (totalShopRows > visibleShopRows) {
                if (shopScrollOffset > 0) {
                    graphics.drawCenteredString(font, "↑", width / 2,
                            shopStripY - 12, 0xFFFFFF);
                }
                if (shopScrollOffset + visibleShopRows < totalShopRows) {
                    graphics.drawCenteredString(font, "↓", width / 2,
                            shopStripBottom + 2, 0xFFFFFF);
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderClassTooltip(graphics, mouseX, mouseY);
        renderShopTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ItemStack iconStack(String iconId) {
        ResourceLocation id = ResourceLocation.tryParse(iconId);
        if (id == null) return new ItemStack(Items.BARRIER);
        return BuiltInRegistries.ITEM.getOptional(id)
                .filter(item -> item != Items.AIR)
                .map(ItemStack::new)
                .orElseGet(() -> new ItemStack(Items.BARRIER));
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

    private void renderShopTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (ShopCardButton card : shopCards) {
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
        return List.of(
                Component.literal(view.name()).withStyle(ChatFormatting.YELLOW),
                Component.literal("价格：" + view.price()).withStyle(ChatFormatting.GOLD),
                Component.literal("点击购买").withStyle(ChatFormatting.GRAY));
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
        private final boolean selected;
        private final List<Component> tooltipLines;

        private ClassCardButton(int x, int y, MatchSnapshot.ClassView view, boolean selected, OnPress onPress) {
            super(x, y, CLASS_CARD_SIZE, CLASS_CARD_SIZE, Component.empty(), onPress);
            this.icon = iconStack(view.icon());
            this.selected = selected;
            this.tooltipLines = classTooltip(view);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + 6, getY() + 6, 100.0F);
            graphics.pose().scale(2.0F, 2.0F, 1.0F);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();
            if (selected) {
                int color = 0xFFFFD54F;
                graphics.fill(getX(), getY(), getX() + width, getY() + 2, color);
                graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height, color);
                graphics.fill(getX(), getY(), getX() + 2, getY() + height, color);
                graphics.fill(getX() + width - 2, getY(), getX() + width, getY() + height, color);
            }
        }
    }

    private static final class ShopCardButton extends DarkButton {
        private final ItemStack icon;
        private final String name;
        private final int price;
        private final List<Component> tooltipLines;

        private ShopCardButton(int x, int y, MatchSnapshot.ShopView view, OnPress onPress) {
            super(x, y, SHOP_CARD_WIDTH, SHOP_CARD_HEIGHT, Component.empty(), onPress);
            this.icon = iconStack(view.icon());
            this.name = view.name();
            this.price = view.price();
            this.tooltipLines = shopTooltip(view);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + 20, getY() + 6, 100.0F);
            graphics.pose().scale(2.0F, 2.0F, 1.0F);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();

            String displayName = Minecraft.getInstance().font.plainSubstrByWidth(name, SHOP_CARD_WIDTH - 6);
            graphics.drawCenteredString(Minecraft.getInstance().font, Component.literal(displayName),
                    getX() + width / 2, getY() + 52, 0xFFFFFFFF);
            graphics.drawCenteredString(Minecraft.getInstance().font,
                    Component.literal("价格 " + price).withStyle(ChatFormatting.GOLD),
                    getX() + width / 2, getY() + 68, 0xFFFFD54F);
        }
    }
}
