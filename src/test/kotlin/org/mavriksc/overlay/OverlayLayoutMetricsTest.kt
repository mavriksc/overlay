package org.mavriksc.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.awt.Rectangle

class OverlayLayoutMetricsTest {
    @Test
    fun `reference window keeps r spell offset from center by default trim`() {
        val layout = OverlayLayoutMetrics.from(
            windowBounds = Rectangle(100, 200, 2560, 1440),
            hudScale = 0.0,
            minimapScale = 33.0,
            mapOnLeft = false
        )

        assertEquals(352, layout.mapRect.width)
        assertEquals(352, layout.mapRect.height)
        assertEquals(2208, layout.mapRect.x)
        assertEquals(1068, layout.mapRect.y)

        val rCenter = layout.spellTopLefts.last().x + layout.spellSize.first / 2.0
        assertEquals(1266.0, rCenter)
        assertEquals(1301, layout.spellTopLefts.last().x)
    }

    @Test
    fun `smaller window shrinks layout and preserves edge anchoring`() {
        val layout = OverlayLayoutMetrics.from(
            windowBounds = Rectangle(0, 0, 1920, 1080),
            hudScale = 0.0,
            minimapScale = 33.0,
            mapOnLeft = false
        )

        assertTrue(layout.mapRect.width < 352)
        assertEquals(801, layout.mapRect.x)
        assertEquals(801, layout.mapRect.y)
        assertTrue(layout.spellSize.first < 20)
        val rCenter = layout.spellTopLefts.last().x + layout.spellSize.first / 2.0
        assertEquals(946.0, rCenter)
    }

    @Test
    fun `larger window and hud scale increase spell size and spacing`() {
        val base = OverlayLayoutMetrics.from(
            windowBounds = Rectangle(0, 0, 2560, 1440),
            hudScale = 0.0,
            minimapScale = 33.0,
            mapOnLeft = false
        )
        val scaled = OverlayLayoutMetrics.from(
            windowBounds = Rectangle(0, 0, 3440, 1440),
            hudScale = 100.0,
            minimapScale = 33.0,
            mapOnLeft = false
        )

        val baseStride = base.spellTopLefts[1].x - base.spellTopLefts[0].x
        val scaledStride = scaled.spellTopLefts[1].x - scaled.spellTopLefts[0].x

        assertTrue(scaled.spellSize.first > base.spellSize.first)
        assertTrue(scaled.spellSize.second > base.spellSize.second)
        assertTrue(scaledStride > baseStride)
    }

    @Test
    fun `non widescreen window and flipped map stay in bounds`() {
        val layout = OverlayLayoutMetrics.from(
            windowBounds = Rectangle(0, 0, 1600, 1200),
            hudScale = 50.0,
            minimapScale = 50.0,
            mapOnLeft = true
        )

        assertEquals(0, layout.mapRect.x)
        assertTrue(layout.mapRect.x + layout.mapRect.width <= 1600)
        assertTrue(layout.mapRect.y + layout.mapRect.height <= 1200)
        assertTrue(layout.spellTopLefts.all { it.x >= 0 && it.y >= 0 })
        assertTrue(layout.spellTopLefts.last().x + layout.spellSize.first <= 1600)
    }

    @Test
    fun `calibration trims affect derived layout`() {
        val base = OverlayLayoutMetrics.from(
            windowBounds = Rectangle(0, 0, 2560, 1440),
            hudScale = 25.0,
            minimapScale = 33.0,
            mapOnLeft = false
        )
        val tuned = OverlayLayoutMetrics.from(
            windowBounds = Rectangle(0, 0, 2560, 1440),
            hudScale = 25.0,
            minimapScale = 33.0,
            mapOnLeft = false,
            spellHorizontalOffsetAdjust = 17,
            minimapPaddingAdjust = 20,
            dodgeInsetAdjust = 12,
            spellBottomOffsetAdjust = 30,
            spellWidthScaleAdjust = 0.25,
            spellHeightScaleAdjust = 0.10,
            spellSpacingScaleAdjust = 0.20
        )

        assertTrue(tuned.dodgeInset > base.dodgeInset)
        assertTrue(tuned.mapRect.width < base.mapRect.width)
        assertTrue(tuned.spellSize.first > base.spellSize.first)
        assertTrue(tuned.spellSize.second > base.spellSize.second)
        assertTrue(tuned.spellTopLefts[1].x - tuned.spellTopLefts[0].x > base.spellTopLefts[1].x - base.spellTopLefts[0].x)
        assertTrue(tuned.spellTopLefts.last().x > base.spellTopLefts.last().x)
        assertTrue(tuned.spellTopLefts.first().y < base.spellTopLefts.first().y)
    }
}
