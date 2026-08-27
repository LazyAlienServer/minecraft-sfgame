package com.sfgame.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SFGameScreenTest {
    @Test
    void shortViewportKeepsPartialCardsAndReachesTwentiethProduct() {
        int width = 320;
        int height = 240;
        SFGameScreen.BodyLayout layout = SFGameScreen.layoutBody(width, 0, 2, 20);
        int bottom = height - SFGameScreen.CONTENT_BOTTOM_MARGIN;
        int maxScroll = SFGameScreen.maxContentScroll(layout.contentHeight(), height);

        for (int scroll = 0; scroll <= maxScroll; scroll += 24) {
            assertVisibleCardBottoms(layout, scroll, bottom);
        }
        assertVisibleCardBottoms(layout, maxScroll, bottom);

        int twentiethIndex = 19;
        int twentiethY = SFGameScreen.CONTENT_TOP + layout.shopStart()
                + twentiethIndex / layout.shopColumns() * (96 + 8) - maxScroll;
        assertTrue(twentiethY >= SFGameScreen.CONTENT_TOP);
        assertTrue(twentiethY + 96 <= bottom);
    }
    @Test
    void respawnLabelsUseSourceTypeAndDirectBeaconName() {
        var squad = SFGameScreen.respawnLabel(new com.sfgame.network.MatchSnapshot.RespawnOption(
                "squad:id", "squad", "Alpha")).getContents();
        var beacon = SFGameScreen.respawnLabel(new com.sfgame.network.MatchSnapshot.RespawnOption(
                "beacon", "beacon", "red")).getContents();
        assertTrue(squad instanceof net.minecraft.network.chat.contents.TranslatableContents);
        assertTrue(beacon instanceof net.minecraft.network.chat.contents.TranslatableContents);
        assertEquals("sfgame.respawn.squad",
                ((net.minecraft.network.chat.contents.TranslatableContents) squad).getKey());
        assertEquals("item.sfgame.respawn_beacon",
                ((net.minecraft.network.chat.contents.TranslatableContents) beacon).getKey());
    }

    @Test
    void multipleBeaconLabelsCarryTheirDisplayNumber() {
        var first = (net.minecraft.network.chat.contents.TranslatableContents) SFGameScreen.respawnLabel(
                new com.sfgame.network.MatchSnapshot.RespawnOption("beacon:1", "beacon", "red"), 1, 2)
                .getContents();
        var second = (net.minecraft.network.chat.contents.TranslatableContents) SFGameScreen.respawnLabel(
                new com.sfgame.network.MatchSnapshot.RespawnOption("beacon:2", "beacon", "red"), 2, 2)
                .getContents();
        assertEquals("sfgame.respawn.beacon_numbered", first.getKey());
        assertEquals("sfgame.respawn.beacon_numbered", second.getKey());
        assertEquals(1, first.getArgs()[0]);
        assertEquals(2, second.getArgs()[0]);
    }
    @Test
    void shopUsesHorizontalRowsWhileSupplyKeepsResponsiveColumns() {
        SFGameScreen.BodyLayout layout = SFGameScreen.layoutBody(320, 0, 2, 20);

        assertEquals(3, layout.columns());
        assertEquals(3, layout.shopColumns());
        assertEquals(7, layout.shopRows());
    }
    @Test
    void supplySectionDoesNotDependOnShopAvailability() {
        SFGameScreen.BodyLayout layout = SFGameScreen.layoutBody(320, 0, 2, 0);

        assertTrue(layout.supplyHeading() >= 0);
        assertEquals(-1, layout.shopHeading());
    }

    @Test
    void scrollbarThumbShowsContentProgress() {
        int width = 320;
        int height = 240;
        SFGameScreen.BodyLayout layout = SFGameScreen.layoutBody(width, 0, 0, 20);
        int maximum = SFGameScreen.maxContentScroll(layout.contentHeight(), height);

        SFGameScreen.ScrollbarGeometry start = SFGameScreen.contentScrollbar(
                width, height, layout.contentHeight(), 0);
        SFGameScreen.ScrollbarGeometry middle = SFGameScreen.contentScrollbar(
                width, height, layout.contentHeight(), maximum / 2);
        SFGameScreen.ScrollbarGeometry end = SFGameScreen.contentScrollbar(
                width, height, layout.contentHeight(), maximum);

        assertTrue(start != null && middle != null && end != null);
        assertEquals(0, start.percent());
        assertEquals(100, end.percent());
        assertEquals(start.top(), start.thumbTop());
        assertEquals(end.bottom(), end.thumbBottom());
        assertTrue(middle.thumbTop() > start.thumbTop());
        assertTrue(middle.thumbBottom() < end.thumbBottom());
    }
    @Test
    void scrollbarDragMapsThumbTrackToContentOffset() {
        int width = 320;
        int height = 240;
        SFGameScreen.BodyLayout layout = SFGameScreen.layoutBody(width, 0, 0, 20);
        int maximum = SFGameScreen.maxContentScroll(layout.contentHeight(), height);
        SFGameScreen.ScrollbarGeometry scrollbar = SFGameScreen.contentScrollbar(
                width, height, layout.contentHeight(), 0);
        int thumbHeight = scrollbar.thumbBottom() - scrollbar.thumbTop();

        assertEquals(0, SFGameScreen.contentOffsetForScrollbarPointer(
                width, height, layout.contentHeight(), scrollbar.top(), 0));
        assertEquals(maximum, SFGameScreen.contentOffsetForScrollbarPointer(
                width, height, layout.contentHeight(), scrollbar.bottom(), thumbHeight));
    }

    @Test
    void partialCardsIntersectingViewportRemainEligibleForRendering() {
        int bottom = 240 - SFGameScreen.CONTENT_BOTTOM_MARGIN;

        assertTrue(SFGameScreen.intersectsContentViewport(
                SFGameScreen.CONTENT_TOP - 48, 96, bottom));
        assertTrue(SFGameScreen.intersectsContentViewport(bottom - 48, 96, bottom));
        assertTrue(!SFGameScreen.intersectsContentViewport(bottom, 96, bottom));
    }

    @Test
    void classWheelChangesOnlyHorizontalOffset() {
        SFGameScreen.ScrollResult classScroll = SFGameScreen.scrollOffsets(
                true, 8, 4, 0, 48, 500, -1);
        assertEquals(1, classScroll.classOffset());
        assertEquals(48, classScroll.contentOffset());

        SFGameScreen.ScrollResult bodyScroll = SFGameScreen.scrollOffsets(
                false, 8, 4, 1, 48, 500, -1);
        assertEquals(1, bodyScroll.classOffset());
        assertEquals(72, bodyScroll.contentOffset());
    }

    @Test
    void emptySupplyReservesSectionBeforeShop() {
        SFGameScreen.BodyLayout empty = SFGameScreen.layoutBody(320, 0, 0, 20);
        SFGameScreen.BodyLayout populated = SFGameScreen.layoutBody(320, 0, 2, 20);

        assertTrue(empty.supplyHeading() >= 0);
        assertTrue(empty.shopHeading() > empty.supplyHeading());
        assertEquals(populated.shopHeading(), empty.shopHeading());
    }

    private static void assertVisibleCardBottoms(SFGameScreen.BodyLayout layout, int scroll, int bottom) {
        for (int i = 0; i < 2; i++) {
            int y = SFGameScreen.CONTENT_TOP + layout.supplyStart()
                    + i / layout.columns() * (96 + 8) - scroll;
            if (y >= SFGameScreen.CONTENT_TOP && y + 96 <= bottom) assertTrue(y + 96 <= bottom);
        }
        for (int i = 0; i < 20; i++) {
            int y = SFGameScreen.CONTENT_TOP + layout.shopStart()
                    + i / layout.shopColumns() * (96 + 8) - scroll;
            if (y >= SFGameScreen.CONTENT_TOP && y + 96 <= bottom) assertTrue(y + 96 <= bottom);
        }
    }
}
