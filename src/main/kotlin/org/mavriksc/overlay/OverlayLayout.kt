package org.mavriksc.overlay

import java.awt.Point
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class OverlayLayoutMetrics(
    val windowBounds: Rectangle,
    val mapRect: Rectangle,
    val dodgeInset: Int,
    val spellTopLefts: List<Point>,
    val spellSize: Pair<Int, Int>
) {
    companion object {
        private const val REFERENCE_WIDTH = 2560.0
        private const val REFERENCE_HEIGHT = 1440.0

        private const val BASE_MAP_FULL = 280.0
        private const val BASE_MAP_PADDING = 20.0
        private const val BASE_DODGE_INSET = 20.0

        private const val BASE_SPELL_WIDTH = 20.0
        private const val BASE_SPELL_HEIGHT = 10.0
        private const val MAX_HUD_SIZE_MULTIPLIER = 2.0
        private const val BASE_SPELL_STRIDE = 59.0
        private const val MAX_HUD_SPELL_STRIDE = 89.0
        private const val BASE_SPELL_BOTTOM_OFFSET = 115.0
        private const val MAX_HUD_SPELL_BOTTOM_OFFSET = 175.0
        private const val SPELL_SLOT_COUNT = 4

        fun from(
            windowBounds: Rectangle,
            hudScale: Double,
            minimapScale: Double,
            mapOnLeft: Boolean,
            minimapOffset: Point = Point(),
            minimapPaddingAdjust: Int = 0,
            dodgeInsetAdjust: Int = 0,
            spellHorizontalOffsetAdjust: Int = 0,
            spellBottomOffsetAdjust: Int = 0,
            spellWidthScaleAdjust: Double = 0.0,
            spellHeightScaleAdjust: Double = 0.0,
            spellSpacingScaleAdjust: Double = 0.0
        ): OverlayLayoutMetrics {
            val width = max(windowBounds.width, 1)
            val height = max(windowBounds.height, 1)
            val windowScale = min(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT)
            val hudPercent = (hudScale / 100.0).coerceIn(0.0, 1.0)

            val mapFull = BASE_MAP_FULL * (1.0 + minimapScale / 100.0) * windowScale
            val mapPadding = BASE_MAP_PADDING * windowScale + minimapPaddingAdjust
            val mapSize = max((mapFull - mapPadding).roundToInt(), 1)
            val rawMapY = max((height - mapFull + minimapOffset.y).roundToInt(), 0)
            val rawMapX = if (mapOnLeft) {
                max(minimapOffset.x, 0)
            } else {
                max((width - mapFull - minimapOffset.x).roundToInt(), 0)
            }
            val mapX = rawMapX.coerceIn(0, max(width - mapSize, 0))
            val mapY = rawMapY.coerceIn(0, max(height - mapSize, 0))

            val spellSizeMultiplier = 1.0 + hudPercent * (MAX_HUD_SIZE_MULTIPLIER - 1.0)
            val spellWidth = max(
                (BASE_SPELL_WIDTH * windowScale * spellSizeMultiplier * (1.0 + spellWidthScaleAdjust)).roundToInt(),
                1
            )
            val spellHeight = max(
                (BASE_SPELL_HEIGHT * windowScale * spellSizeMultiplier * (1.0 + spellHeightScaleAdjust)).roundToInt(),
                1
            )

            val spellStride = lerp(BASE_SPELL_STRIDE, MAX_HUD_SPELL_STRIDE, hudPercent) *
                    windowScale *
                    (1.0 + spellSpacingScaleAdjust)
            val spellBottomOffset = lerp(BASE_SPELL_BOTTOM_OFFSET, MAX_HUD_SPELL_BOTTOM_OFFSET, hudPercent) *
                    windowScale +
                    spellBottomOffsetAdjust
            val rCenterX = width / 2.0 + spellHorizontalOffsetAdjust
            val rLeftX = (rCenterX - spellWidth / 2.0).roundToInt()
            val startX = (rLeftX - spellStride * (SPELL_SLOT_COUNT - 1)).roundToInt()
            val spellY = max((height - spellBottomOffset - spellHeight).roundToInt(), 0)
            val spellTopLefts = List(SPELL_SLOT_COUNT) { index ->
                Point((startX + index * spellStride).roundToInt(), spellY)
            }

            return OverlayLayoutMetrics(
                windowBounds = Rectangle(windowBounds),
                mapRect = Rectangle(mapX, mapY, mapSize, mapSize),
                dodgeInset = max((BASE_DODGE_INSET * windowScale).roundToInt() + dodgeInsetAdjust, 8),
                spellTopLefts = spellTopLefts,
                spellSize = spellWidth to spellHeight
            )
        }

        private fun lerp(start: Double, end: Double, t: Double): Double = start + (end - start) * t
    }
}
