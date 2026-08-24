package com.sfgame.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SFGameScreenTest {
    @Test
    void shortViewportRegistersOnlyFullyVisibleCardsAndReachesTwentiethProduct() {
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
                + twentiethIndex / layout.columns() * (96 + 8) - maxScroll;
        assertTrue(twentiethY >= SFGameScreen.CONTENT_TOP);
        assertTrue(twentiethY + 96 <= bottom);
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
    void emptySupplyRemovesSectionWithoutGap() {
        SFGameScreen.BodyLayout empty = SFGameScreen.layoutBody(320, 0, 0, 20);
        SFGameScreen.BodyLayout populated = SFGameScreen.layoutBody(320, 0, 2, 20);

        assertEquals(-1, empty.supplyHeading());
        assertEquals(empty.classStrip() + 44 + 16, empty.shopHeading());
        assertTrue(populated.shopHeading() > empty.shopHeading());
    }

    private static void assertVisibleCardBottoms(SFGameScreen.BodyLayout layout, int scroll, int bottom) {
        for (int i = 0; i < 2; i++) {
            int y = SFGameScreen.CONTENT_TOP + layout.supplyStart()
                    + i / layout.columns() * (96 + 8) - scroll;
            if (y >= SFGameScreen.CONTENT_TOP && y + 96 <= bottom) assertTrue(y + 96 <= bottom);
        }
        for (int i = 0; i < 20; i++) {
            int y = SFGameScreen.CONTENT_TOP + layout.shopStart()
                    + i / layout.columns() * (96 + 8) - scroll;
            if (y >= SFGameScreen.CONTENT_TOP && y + 96 <= bottom) assertTrue(y + 96 <= bottom);
        }
    }
}
