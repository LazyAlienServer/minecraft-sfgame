package com.sfgame.client;

import com.sfgame.game.AdminRuleCatalog;
import com.sfgame.game.MatchPhase;
import com.sfgame.network.AdminActionPacket;
import com.sfgame.network.AdminSnapshot;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game administrator control panel.  The visual language intentionally
 * mirrors the existing class cards: opaque dark surfaces, strong contrast and
 * immediate hover/press feedback, with square corners throughout.
 */
public final class SFGameAdminScreen extends Screen {
    private static final int BACKGROUND = 0xF0101114;
    private static final int SURFACE = 0xF01A1C20;
    private static final int SURFACE_ALT = 0xF022252A;
    private static final int SUB_NAV_SURFACE = 0xF017191D;
    private static final int BORDER = 0xFF555A62;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF9A9FA8;
    private static final int ACCENT = 0xFFFFD54F;
    private static final int HOT = 0xFF62D98B;
    private static final int COLD = 0xFFA6ABB3;
    private static final int DANGER = 0xFFE06C75;
    private static final int RULE_ROW_HEIGHT = 38;
    private static final int TOP_ACTION_MARGIN = 16;
    private static final int TOP_ACTION_TOP = 6;
    private static final int TOP_ACTION_WIDTH = 72;
    private static final int TOP_ACTION_HEIGHT = 20;

    private enum Page { STATUS, RULES }

    private Page page = Page.STATUS;
    private boolean rebuilding;
    private int panelLeft;
    private int panelRight;
    private int contentTop;
    private int contentBottom;
    private int ruleScroll;
    private int maxRuleScroll;
    private int statusScroll;
    private int maxStatusScroll;
    private int mapScroll;
    private int visibleMapCount;
    private int totalMapCount;
    private int statusPollTicks;
    private final List<RuleControl> ruleControls = new ArrayList<>();

    public SFGameAdminScreen() {
        super(Component.translatable("sfgame.admin.title"));
    }

    @Override
    protected void init() {
        rebuild();
        SFGameNetwork.sendToServer(AdminActionPacket.request(false));
    }

    public void refresh() {
        if (minecraft != null && minecraft.screen == this) rebuild();
    }

    private void rebuild() {
        if (rebuilding) return;
        rebuilding = true;
        clearWidgets();
        ruleControls.clear();
        AdminSnapshot snapshot = ClientAdminState.snapshot();
        calculateLayout();

        addRenderableWidget(new SquareButton(width - TOP_ACTION_MARGIN - TOP_ACTION_WIDTH, TOP_ACTION_TOP,
                TOP_ACTION_WIDTH, TOP_ACTION_HEIGHT,
                Component.translatable("sfgame.admin.back"), false,
                button -> minecraft.setScreen(new SFGameScreen())));

        if (snapshot == null) {
            addRenderableWidget(new SquareButton(width / 2 - 60, height / 2 - 11, 120, 22,
                    Component.translatable("sfgame.admin.refresh"), false,
                    button -> SFGameNetwork.sendToServer(AdminActionPacket.request(false))));
            rebuilding = false;
            return;
        }

        boolean arenaEditable = snapshot.phase() == MatchPhase.LOBBY || snapshot.phase() == MatchPhase.UNCONFIGURED;
        addModeTabs(snapshot, arenaEditable);
        addMapTabs(snapshot, arenaEditable);

        int pageWidth = 98;
        addRenderableWidget(new SquareButton(panelLeft, 74, pageWidth, 22,
                Component.translatable("sfgame.admin.page.status"), page == Page.STATUS,
                button -> changePage(Page.STATUS)));
        addRenderableWidget(new SquareButton(panelLeft + pageWidth + 6, 74, pageWidth, 22,
                Component.translatable("sfgame.admin.page.rules"), page == Page.RULES,
                button -> changePage(Page.RULES)));
        if (page == Page.RULES) {
            addRenderableWidget(new SquareButton(panelRight - 72, 74, 72, 22,
                    Component.translatable("sfgame.admin.refresh"), false,
                    button -> SFGameNetwork.sendToServer(AdminActionPacket.request(false))));
        }

        if (page == Page.RULES) addRuleControls(snapshot);
        rebuilding = false;
    }

    private void calculateLayout() {
        int panelWidth = Math.min(760, Math.max(320, width - 32));
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        contentTop = 102;
        contentBottom = height - 18;
    }

    private void addModeTabs(AdminSnapshot snapshot, boolean editable) {
        int count = Math.max(1, snapshot.modes().size());
        int gap = 5;
        int tabWidth = Math.max(58, (panelRight - panelLeft - gap * (count - 1)) / count);
        for (int i = 0; i < snapshot.modes().size(); i++) {
            AdminSnapshot.ModeView mode = snapshot.modes().get(i);
            boolean selected = mode.id().equals(snapshot.selectedMode());
            SquareButton button = new SquareButton(panelLeft + i * (tabWidth + gap), 30, tabWidth, 20,
                    modeLabel(mode.id()), selected,
                    ignored -> SFGameNetwork.sendToServer(AdminActionPacket.selectMode(mode.id())));
            button.active = editable && !selected;
            addRenderableWidget(button);
        }
    }

    private void addMapTabs(AdminSnapshot snapshot, boolean editable) {
        AdminSnapshot.ModeView selectedMode = snapshot.modes().stream()
                .filter(mode -> mode.id().equals(snapshot.selectedMode())).findFirst().orElse(null);
        if (selectedMode == null) return;
        totalMapCount = selectedMode.maps().size();
        int tabWidth = 92;
        int gap = 5;
        visibleMapCount = Math.max(1, Math.min(totalMapCount,
                (panelRight - panelLeft + gap) / (tabWidth + gap)));
        int maxScroll = Math.max(0, totalMapCount - visibleMapCount);
        mapScroll = Math.max(0, Math.min(mapScroll, maxScroll));
        for (int visible = 0; visible < visibleMapCount; visible++) {
            int index = mapScroll + visible;
            if (index >= selectedMode.maps().size()) break;
            AdminSnapshot.MapView map = selectedMode.maps().get(index);
            boolean selected = map.id().equals(snapshot.selectedMap());
            SquareButton button = new SquareButton(panelLeft + visible * (tabWidth + gap), 53, tabWidth, 18,
                    Component.literal(map.id()), selected,
                    ignored -> SFGameNetwork.sendToServer(AdminActionPacket.selectMap(
                            snapshot.selectedMode(), map.id())));
            button.active = editable && !selected;
            addRenderableWidget(button);
        }
    }

    private void changePage(Page target) {
        if (page == target) return;
        page = target;
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        if (page != Page.STATUS) {
            statusPollTicks = 0;
            return;
        }
        if (++statusPollTicks >= 20) {
            statusPollTicks = 0;
            SFGameNetwork.sendToServer(AdminActionPacket.request(false));
        }
    }

    private void addRuleControls(AdminSnapshot snapshot) {
        List<AdminSnapshot.RuleView> hot = snapshot.rules().stream().filter(AdminSnapshot.RuleView::hotReload).toList();
        List<AdminSnapshot.RuleView> cold = snapshot.rules().stream().filter(rule -> !rule.hotReload()).toList();
        int totalHeight = 24 + hot.size() * RULE_ROW_HEIGHT + 26 + cold.size() * RULE_ROW_HEIGHT;
        maxRuleScroll = Math.max(0, totalHeight - Math.max(1, contentBottom - contentTop));
        ruleScroll = Math.max(0, Math.min(ruleScroll, maxRuleScroll));
        boolean activeMatch = isActiveMatch(snapshot.phase());
        int y = contentTop + 24 - ruleScroll;
        y = addRuleSection(snapshot, hot, y, activeMatch);
        y += 26;
        addRuleSection(snapshot, cold, y, activeMatch);
    }

    private int addRuleSection(AdminSnapshot snapshot, List<AdminSnapshot.RuleView> rules, int y,
                               boolean activeMatch) {
        for (AdminSnapshot.RuleView rule : rules) {
            int rowY = y;
            y += RULE_ROW_HEIGHT;
            // Keep partially visible rows and clip them during rendering. The
            // previous full-row check discarded them, producing a changing
            // blank band because the 30 px wheel step is not divisible by the
            // 38 px row height.
            if (rowY + RULE_ROW_HEIGHT <= contentTop || rowY >= contentBottom) continue;
            boolean enabled = rule.hotReload() || !activeMatch;
            int controlX = panelRight - 202;
            if (rule.type() == AdminRuleCatalog.ValueType.BOOLEAN) {
                boolean current = Boolean.parseBoolean(rule.value());
                SquareButton toggle = new SquareButton(controlX, rowY + 8, 122, 22,
                        Component.translatable(current ? "sfgame.admin.enabled" : "sfgame.admin.disabled"),
                        current, button -> setRule(snapshot, rule, Boolean.toString(!current)));
                toggle.clipTo(panelLeft, contentTop, panelRight, contentBottom);
                toggle.active = enabled;
                addRenderableWidget(toggle);
                ruleControls.add(new RuleControl(rule, rowY, null, toggle, null));
            } else {
                RuleValueBox editor = new RuleValueBox(font, controlX, rowY + 8, 122, 22, rule);
                editor.clipTo(panelLeft, contentTop, panelRight, contentBottom);
                editor.setValue(rule.value());
                editor.active = enabled;
                addRenderableWidget(editor);
                SquareButton apply = new SquareButton(panelRight - 72, rowY + 8, 62, 22,
                        Component.translatable("sfgame.admin.apply"), false,
                        button -> setRule(snapshot, rule, editor.getValue()));
                apply.clipTo(panelLeft, contentTop, panelRight, contentBottom);
                apply.active = enabled;
                addRenderableWidget(apply);
                ruleControls.add(new RuleControl(rule, rowY, editor, apply, snapshot));
            }
        }
        return y;
    }

    private void setRule(AdminSnapshot snapshot, AdminSnapshot.RuleView rule, String value) {
        SFGameNetwork.sendToServer(AdminActionPacket.setRule(
                snapshot.selectedMode(), snapshot.selectedMap(), rule.key(), value));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= 52 && mouseY <= 72 && totalMapCount > visibleMapCount) {
            int next = Math.max(0, Math.min(totalMapCount - visibleMapCount,
                    mapScroll + (delta < 0 ? 1 : -1)));
            if (next != mapScroll) {
                mapScroll = next;
                rebuild();
            }
            return true;
        }
        if (page == Page.RULES && mouseX >= panelLeft && mouseX <= panelRight
                && mouseY >= contentTop && mouseY <= contentBottom && maxRuleScroll > 0) {
            int next = Math.max(0, Math.min(maxRuleScroll, ruleScroll + (delta < 0 ? 30 : -30)));
            if (next != ruleScroll) {
                ruleScroll = next;
                rebuild();
            }
            return true;
        }
        if (page == Page.STATUS && mouseX >= panelLeft && mouseX <= panelRight
                && mouseY >= contentTop && mouseY <= contentBottom && maxStatusScroll > 0) {
            int next = Math.max(0, Math.min(maxStatusScroll, statusScroll + (delta < 0 ? 30 : -30)));
            if (next != statusScroll) statusScroll = next;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            for (RuleControl control : ruleControls) {
                if (control.editor != null && control.editor.isFocused() && control.editor.active
                        && control.snapshot != null) {
                    setRule(control.snapshot, control.rule, control.editor.getValue());
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.drawString(font, title, panelLeft, 11, TEXT, false);
        AdminSnapshot snapshot = ClientAdminState.snapshot();
        if (snapshot != null) {
            // The map selector is a child navigation row of the mode selector.
            // Giving the whole row its own surface makes that relationship
            // visible even when the selected mode only contains one map.
            graphics.fill(panelLeft, 51, panelRight, 73, SUB_NAV_SURFACE);
            outline(graphics, panelLeft, 51, panelRight, 73, BORDER);
            graphics.fill(panelLeft, contentTop - 2, panelRight, contentBottom, SURFACE);
            outline(graphics, panelLeft, contentTop - 2, panelRight, contentBottom, BORDER);
            if (page == Page.STATUS) renderStatus(graphics, snapshot);
            else renderRules(graphics, snapshot);
            renderMapScrollHints(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderStatus(GuiGraphics graphics, AdminSnapshot snapshot) {
        int gap = 8;
        int availableWidth = panelRight - panelLeft - 24;
        int columns = availableWidth >= 600 ? 3 : availableWidth >= 330 ? 2 : 1;
        int cardWidth = (availableWidth - gap * (columns - 1)) / columns;
        int x0 = panelLeft + 12;
        List<StatusCard> cards = List.of(
                new StatusCard("sfgame.admin.status.phase", phaseLabel(snapshot.phase()), ACCENT),
                new StatusCard("sfgame.admin.status.selection", snapshot.selectedMode() + " / " + snapshot.selectedMap(), TEXT),
                new StatusCard("sfgame.admin.status.players", snapshot.onlinePlayers() + " / "
                        + snapshot.participatingPlayers() + " / " + snapshot.queuedPlayers(), TEXT),
                new StatusCard("sfgame.admin.status.time", formatSeconds(snapshot.remainingSeconds()), TEXT),
                new StatusCard("sfgame.admin.status.map", Component.translatable(snapshot.mapConfigured()
                        ? "sfgame.admin.configured" : "sfgame.admin.incomplete").getString(),
                        snapshot.mapConfigured() ? HOT : DANGER),
                new StatusCard("sfgame.admin.status.inheritance", snapshot.ruleParent(), TEXT),
                new StatusCard("sfgame.admin.status.dev", Component.translatable(snapshot.devMode()
                        ? "sfgame.admin.enabled" : "sfgame.admin.disabled").getString(),
                        snapshot.devMode() ? HOT : MUTED),
                new StatusCard("sfgame.admin.status.scores", "R " + snapshot.redScore() + "  B " + snapshot.blueScore()
                        + "  Y " + snapshot.yellowScore() + "  G " + snapshot.greenScore(), TEXT)
        );
        int cardHeight = 54;
        int rows = (cards.size() + columns - 1) / columns;
        int naturalHeight = 12 + rows * (cardHeight + gap) + 4 + 60 + 12;
        maxStatusScroll = Math.max(0, naturalHeight - Math.max(1, contentBottom - contentTop));
        statusScroll = Math.max(0, Math.min(statusScroll, maxStatusScroll));
        int y0 = contentTop + 12 - statusScroll;

        graphics.enableScissor(panelLeft, contentTop, panelRight, contentBottom);
        for (int i = 0; i < cards.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            drawCard(graphics, x0 + column * (cardWidth + gap), y0 + row * (cardHeight + gap),
                    cardWidth, cardHeight, cards.get(i));
        }
        int restoreY = y0 + rows * (cardHeight + gap) + 4;
        renderRestoreStatus(graphics, snapshot, x0, restoreY, panelRight - panelLeft - 24, 60);
        graphics.disableScissor();

        if (maxStatusScroll > 0) renderScrollBar(graphics, statusScroll, maxStatusScroll);
    }

    private void renderRestoreStatus(GuiGraphics graphics, AdminSnapshot snapshot, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SURFACE_ALT);
        outline(graphics, x, y, x + width, y + height, BORDER);
        graphics.drawString(font, Component.translatable("sfgame.admin.status.restore"), x + 10, y + 8, MUTED, false);
        String state = snapshot.restoringMap()
                ? Component.translatable("sfgame.admin.restore.running").getString()
                : Component.translatable("sfgame.admin.restore.idle").getString();
        String partitions = snapshot.totalPartitions() > 0
                ? "  " + snapshot.restoredPartitions() + "/" + snapshot.totalPartitions() : "";
        graphics.drawString(font, state + partitions + "  " + snapshot.restoreElapsedMillis() + " ms",
                x + 10, y + 23, TEXT, false);
        int barX = x + 10;
        int barY = y + 41;
        int barWidth = width - 20;
        graphics.fill(barX, barY, barX + barWidth, barY + 8, 0xFF0D0E10);
        int fill = (int) Math.round(barWidth * snapshot.restoreProgress());
        graphics.fill(barX, barY, barX + fill, barY + 8, ACCENT);
    }

    private void renderRules(GuiGraphics graphics, AdminSnapshot snapshot) {
        int hotY = contentTop + 6 - ruleScroll;
        graphics.enableScissor(panelLeft, contentTop, panelRight, contentBottom);
        graphics.drawString(font, Component.translatable("sfgame.admin.rules.hot"),
                panelLeft + 10, hotY, HOT, false);
        int hotCount = (int) snapshot.rules().stream().filter(AdminSnapshot.RuleView::hotReload).count();
        int coldY = hotY + 24 + hotCount * RULE_ROW_HEIGHT;
        graphics.drawString(font, Component.translatable("sfgame.admin.rules.cold"),
                panelLeft + 10, coldY, COLD, false);
        for (RuleControl control : ruleControls) renderRuleLabel(graphics, control);
        graphics.disableScissor();
        if (maxRuleScroll > 0) {
            renderScrollBar(graphics, ruleScroll, maxRuleScroll);
        }
    }

    private void renderScrollBar(GuiGraphics graphics, int scroll, int maximum) {
        int trackTop = contentTop + 8;
        int trackBottom = contentBottom - 8;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(18, trackHeight * trackHeight / (trackHeight + maximum));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / Math.max(1, maximum);
        graphics.fill(panelRight - 4, trackTop, panelRight - 2, trackBottom, 0xFF33363B);
        graphics.fill(panelRight - 5, thumbY, panelRight - 1, thumbY + thumbHeight, ACCENT);
    }

    private void renderRuleLabel(GuiGraphics graphics, RuleControl control) {
        int y = control.y;
        graphics.fill(panelLeft + 8, y + 3, panelRight - 8, y + RULE_ROW_HEIGHT - 3, SURFACE_ALT);
        String translationKey = "sfgame.admin.rule." + control.rule.key();
        Component translated = Component.translatable(translationKey);
        String label = translated.getString().equals(translationKey) ? control.rule.key() : translated.getString();
        graphics.drawString(font, label, panelLeft + 16, y + 8, TEXT, false);
        String range = control.rule.type() == AdminRuleCatalog.ValueType.BOOLEAN
                ? control.rule.key()
                : control.rule.key() + "  [" + compact(control.rule.minimum()) + ".." + compact(control.rule.maximum()) + "]";
        graphics.drawString(font, range, panelLeft + 16, y + 21, MUTED, false);
        Component badge = Component.translatable(control.rule.hotReload()
                ? "sfgame.admin.badge.hot" : "sfgame.admin.badge.cold");
        int badgeX = panelRight - 266;
        graphics.drawString(font, badge, badgeX, y + 14, control.rule.hotReload() ? HOT : COLD, false);
    }

    private void renderMapScrollHints(GuiGraphics graphics) {
        if (totalMapCount <= visibleMapCount) return;
        if (mapScroll > 0) graphics.drawString(font, "‹", panelLeft - 10, 58, TEXT, false);
        if (mapScroll + visibleMapCount < totalMapCount) graphics.drawString(font, "›", panelRight + 4, 58, TEXT, false);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int width, int height, StatusCard card) {
        graphics.fill(x, y, x + width, y + height, SURFACE_ALT);
        outline(graphics, x, y, x + width, y + height, BORDER);
        graphics.drawString(font, Component.translatable(card.titleKey), x + 9, y + 9, MUTED, false);
        String value = font.plainSubstrByWidth(card.value, width - 18);
        graphics.drawString(font, value, x + 9, y + 29, card.color, false);
    }

    private static void outline(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }

    private static boolean isActiveMatch(MatchPhase phase) {
        return phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN
                || phase == MatchPhase.RUNNING || phase == MatchPhase.RESULT;
    }

    private static String phaseLabel(MatchPhase phase) {
        return Component.translatable("sfgame.phase." + phase.name().toLowerCase(Locale.ROOT)).getString();
    }

    private static Component modeLabel(String id) {
        String key = "sfgame.mode." + id;
        Component translated = Component.translatable(key);
        return translated.getString().equals(key) ? Component.literal(id) : translated;
    }

    private static String formatSeconds(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", safe / 60, safe % 60);
    }

    private static String compact(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record StatusCard(String titleKey, String value, int color) { }

    private record RuleControl(AdminSnapshot.RuleView rule, int y, RuleValueBox editor,
                               Button action, AdminSnapshot snapshot) { }

    private static class SquareButton extends Button {
        private final boolean selected;
        private long pressedUntil;
        private boolean clipped;
        private int clipLeft;
        private int clipTop;
        private int clipRight;
        private int clipBottom;

        SquareButton(int x, int y, int width, int height, Component message, boolean selected, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.selected = selected;
        }

        SquareButton clipTo(int left, int top, int right, int bottom) {
            clipped = true;
            clipLeft = left;
            clipTop = top;
            clipRight = right;
            clipBottom = bottom;
            return this;
        }

        @Override
        public void onPress() {
            // Keep an observable pressed state for a few frames. The raw GLFW
            // state alone can disappear between two rendered frames on a
            // quick click, which made working buttons look unresponsive.
            pressedUntil = Util.getMillis() + 140L;
            super.onPress();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (clipped) graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
            boolean hovered = isHoveredOrFocused();
            boolean pressed = Util.getMillis() < pressedUntil || isHovered && GLFW.glfwGetMouseButton(
                    Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            int background = !active ? 0xE0202124 : pressed ? 0xFF101114
                    : hovered ? 0xFF34373D : selected ? 0xFF2A2D32 : 0xF01A1C20;
            int border = selected ? ACCENT : hovered && active ? 0xFFFFFFFF : BORDER;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            outline(graphics, getX(), getY(), getX() + width, getY() + height, border);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2, active ? TEXT : MUTED);
            if (clipped) graphics.disableScissor();
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return super.isMouseOver(mouseX, mouseY) && (!clipped
                    || mouseX >= clipLeft && mouseX < clipRight
                    && mouseY >= clipTop && mouseY < clipBottom);
        }
    }

    private static final class RuleValueBox extends EditBox {
        private static final int HORIZONTAL_PADDING = 6;
        private final int frameX;
        private final int frameY;
        private final int frameWidth;
        private final int frameHeight;
        private boolean clipped;
        private int clipLeft;
        private int clipTop;
        private int clipRight;
        private int clipBottom;

        RuleValueBox(Font font, int x, int y, int width, int height, AdminSnapshot.RuleView rule) {
            // An unbordered vanilla EditBox renders text at its widget origin.
            // Keep the editable text widget on the centered baseline and draw
            // the larger SFGame frame around it.
            super(font, x + HORIZONTAL_PADDING, y + (height - 8) / 2,
                    width - HORIZONTAL_PADDING * 2, 8, Component.literal(rule.key()));
            this.frameX = x;
            this.frameY = y;
            this.frameWidth = width;
            this.frameHeight = height;
            setBordered(false);
            setMaxLength(32);
            setTextColor(TEXT);
            setTextColorUneditable(MUTED);
            if (rule.type() == AdminRuleCatalog.ValueType.INTEGER) {
                setFilter(value -> value.matches("[0-9]*"));
            } else {
                setFilter(value -> value.matches("[0-9]*\\.?[0-9]*"));
            }
        }

        RuleValueBox clipTo(int left, int top, int right, int bottom) {
            clipped = true;
            clipLeft = left;
            clipTop = top;
            clipRight = right;
            clipBottom = bottom;
            return this;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (clipped) graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
            int background = active ? 0xFF111215 : 0xFF202124;
            int border = isFocused() && active ? ACCENT : BORDER;
            graphics.fill(frameX, frameY, frameX + frameWidth, frameY + frameHeight, background);
            outline(graphics, frameX, frameY, frameX + frameWidth, frameY + frameHeight, border);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (clipped) graphics.disableScissor();
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return visible && mouseX >= frameX && mouseX < frameX + frameWidth
                    && mouseY >= frameY && mouseY < frameY + frameHeight
                    && (!clipped || mouseX >= clipLeft && mouseX < clipRight
                    && mouseY >= clipTop && mouseY < clipBottom);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            // Clicking the visual padding should focus the editor too. Clamp
            // to the inner text area so vanilla cursor placement remains sane.
            super.onClick(Mth.clamp(mouseX, getX(), getX() + width), getY() + 1);
        }
    }
}
