package org.mavriksc.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.awt.Point

class AppSettingsTest {
    @Test
    fun `mapping to overlay config keeps persisted league inputs and app trims separate`() {
        val settings = AppSettings(
            features = FeatureSettings(
                spellPacingEnabled = false,
                minimapReminderEnabled = true,
                dodgeDirectionEnabled = true,
                showOnlyWhileForeground = false
            ),
            timing = TimingSettings(
                dodgeCueIntervalMs = 1400,
                minimapReminderIntervalMs = 6200,
                minimapFlashDurationMs = 450
            ),
            calibration = CalibrationSettings(
                spellHorizontalOffsetAdjustPx = 17,
                spellBottomOffsetAdjustPx = 24,
                spellWidthScaleAdjustPercent = 20,
                spellHeightScaleAdjustPercent = -10,
                spellSpacingScaleAdjustPercent = 15,
                dodgeInsetAdjustPx = 12,
                minimapPaddingAdjustPx = 18,
                minimapOffsetAdjustX = 7,
                minimapOffsetAdjustY = -9
            )
        )
        val persisted = PersistedGameSettings(
            mapOnLeft = true,
            minimapScale = 40f,
            minimapOffset = Point(11, 13),
            hudScale = 55f
        )

        val config = settings.toOverlayConfig(persisted)

        assertFalse(config.enableSpellPacing)
        assertFalse(config.showOnlyWhenGameForeground)
        assertEquals(1400, config.dodgeTimer)
        assertEquals(6200, config.mapTimer)
        assertEquals(450, config.mapFlashTime)
        assertTrue(config.mapOnLeft)
        assertEquals(40.0, config.mapScale)
        assertEquals(55.0, config.hudScale)
        assertEquals(Point(18, 4), config.minimapOffset)
        assertEquals(18, config.minimapPaddingAdjust)
        assertEquals(12, config.dodgeInsetAdjust)
        assertEquals(3, config.spellHorizontalOffsetAdjust)
        assertEquals(14, config.spellBottomOffsetAdjust)
        assertEquals(0.20, config.spellWidthScaleAdjust)
        assertEquals(-0.10, config.spellHeightScaleAdjust)
        assertEquals(0.15, config.spellSpacingScaleAdjust)
    }

    @Test
    fun `reset calibration leaves other sections untouched`() {
        val original = AppSettings(
            features = FeatureSettings(showOnlyWhileForeground = false),
            timing = TimingSettings(dodgeCueIntervalMs = 1500),
            appearance = AppearanceSettings(panelDensity = PanelDensity.COMFY),
            calibration = CalibrationSettings(spellBottomOffsetAdjustPx = 44, dodgeInsetAdjustPx = 18)
        )

        val reset = original.resetCalibration()

        assertFalse(reset.features.showOnlyWhileForeground)
        assertEquals(1500, reset.timing.dodgeCueIntervalMs)
        assertEquals(PanelDensity.COMFY, reset.appearance.panelDensity)
        assertEquals(CalibrationSettings(), reset.calibration)
    }
}
