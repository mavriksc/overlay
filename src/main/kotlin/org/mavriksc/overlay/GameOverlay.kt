package org.mavriksc.overlay

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JFrame
import javax.swing.Timer
import kotlin.random.Random

data class OverlayConfig(
    var enableSpellPacing: Boolean = true,
    var enableMapLookTimer: Boolean = true,
    var enableDodgeDirection: Boolean = true,
    var mapFlashColor: Color = Color.WHITE,
    var northColor: Color = Color.RED,
    var southColor: Color = Color.BLUE,
    var eastColor: Color = Color.GREEN,
    var westColor: Color = Color.YELLOW,
    var dodgeTimer: Int = 1_000,
    var mapTimer: Int = 5_000,
    var mapFlashTime: Int = 250,
    var showOnlyWhenGameForeground: Boolean = true,
    var mapOnLeft: Boolean = false,
    var mapScale: Double = 33.0,
    var hudScale: Double = 0.0,
    var minimapOffset: Point = Point(),
    var minimapPaddingAdjust: Int = 0,
    var dodgeInsetAdjust: Int = 0,
    var spellHorizontalOffsetAdjust: Int = 0,
    var spellBottomOffsetAdjust: Int = 0,
    var spellWidthScaleAdjust: Double = 0.0,
    var spellHeightScaleAdjust: Double = 0.0,
    var spellSpacingScaleAdjust: Double = 0.0
)

class GameOverlay : JFrame() {
    var config = OverlayConfig(
        northColor = Color(0x3D, 0xE0, 0x7A),
        southColor = Color(0x3D, 0xE0, 0x7A),
        eastColor = Color(0x3D, 0xE0, 0x7A),
        westColor = Color(0x3D, 0xE0, 0x7A),
        mapFlashColor = Color.WHITE
    )
        private set

    var spellStates: List<Pair<Color, Boolean>>? = null
    private var dodgeDir = Random.nextBoolean()
    private var flashMap = false
    private var dodgeTimerRef: Timer? = null
    private var mapTimerRef: Timer? = null
    private var mapFlashResetTimer: Timer? = null

    init {
        isUndecorated = true
        isAlwaysOnTop = true
        background = Color(0, 0, 0, 0)
        focusableWindowState = false
        restartTimers()
    }

    fun updateConfig(newConfig: OverlayConfig) {
        val restartTimers =
            newConfig.dodgeTimer != config.dodgeTimer ||
                    newConfig.mapTimer != config.mapTimer ||
                    newConfig.mapFlashTime != config.mapFlashTime
        config = newConfig
        if (restartTimers) {
            restartTimers()
        }
        repaint()
    }

    fun updateWindowBounds(windowBounds: Rectangle) {
        bounds = windowBounds
    }

    fun previewMapFlash() {
        flashMap = true
        repaint()
        mapFlashResetTimer?.stop()
        mapFlashResetTimer = Timer(config.mapFlashTime) {
            flashMap = false
            repaint()
        }.apply {
            isRepeats = false
            start()
        }
    }

    fun previewDodgeFlip() {
        dodgeDir = !dodgeDir
        repaint()
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        drawUI(g as Graphics2D)
    }

    private fun drawUI(g2d: Graphics2D) {
        if (config.enableMapLookTimer && flashMap) drawMapLook(g2d)
        if (config.enableSpellPacing) drawSpellPacing(g2d)
        if (config.enableDodgeDirection) drawDodgeDirection(g2d)
    }

    private fun drawMapLook(g2d: Graphics2D) {
        val layout = currentLayout()
        g2d.color = config.mapFlashColor
        g2d.fillRect(layout.mapRect.x, layout.mapRect.y, layout.mapRect.width, layout.mapRect.height)
    }

    private fun drawDodgeDirection(g2d: Graphics2D) {
        val layout = currentLayout()
        val minX = layout.dodgeInset
        val minY = layout.dodgeInset
        val maxX = width - layout.dodgeInset
        val maxY = height - layout.dodgeInset

        g2d.stroke = BasicStroke(2f)
        if (dodgeDir) {
            g2d.color = config.northColor
            g2d.drawLine(minX, minY, maxX, minY)

            g2d.color = config.westColor
            g2d.drawLine(minX, minY, minX, maxY)
        } else {
            g2d.color = config.southColor
            g2d.drawLine(minX, maxY, maxX, maxY)

            g2d.color = config.eastColor
            g2d.drawLine(maxX, minY, maxX, maxY)
        }
    }

    private fun drawSpellPacing(g2d: Graphics2D) {
        val layout = currentLayout()
        val spellWidth = layout.spellSize.x()
        val spellHeight = layout.spellSize.y()
        spellStates?.forEachIndexed { i, state ->
            if (state.second) {
                g2d.color = state.first
                val topLeft = layout.spellTopLefts[i]
                g2d.fillRect(topLeft.x, topLeft.y, spellWidth, spellHeight)
            }
        }
    }

    private fun currentLayout(): OverlayLayoutMetrics =
        OverlayLayoutMetrics.from(
            windowBounds = bounds,
            hudScale = config.hudScale,
            minimapScale = config.mapScale,
            mapOnLeft = config.mapOnLeft,
            minimapOffset = config.minimapOffset,
            minimapPaddingAdjust = config.minimapPaddingAdjust,
            dodgeInsetAdjust = config.dodgeInsetAdjust,
            spellHorizontalOffsetAdjust = config.spellHorizontalOffsetAdjust,
            spellBottomOffsetAdjust = config.spellBottomOffsetAdjust,
            spellWidthScaleAdjust = config.spellWidthScaleAdjust,
            spellHeightScaleAdjust = config.spellHeightScaleAdjust,
            spellSpacingScaleAdjust = config.spellSpacingScaleAdjust
        )

    private fun restartTimers() {
        dodgeTimerRef?.stop()
        mapTimerRef?.stop()
        mapFlashResetTimer?.stop()

        dodgeTimerRef = Timer(config.dodgeTimer) {
            val next = Random.nextBoolean()
            if (next != dodgeDir) {
                dodgeDir = next
                repaint()
            }
        }.apply { start() }

        mapTimerRef = Timer(config.mapTimer) {
            flashMap = true
            repaint()
            mapFlashResetTimer?.stop()
            mapFlashResetTimer = Timer(config.mapFlashTime) {
                flashMap = false
                repaint()
            }.apply {
                isRepeats = false
                start()
            }
        }.apply { start() }
    }
}
