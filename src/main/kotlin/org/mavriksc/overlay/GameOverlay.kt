package org.mavriksc.overlay

import java.awt.*
import javax.swing.JFrame
import javax.swing.Timer
import kotlin.random.Random
import kotlin.math.roundToInt


//this is for my ui. will need to get scale of hud and map from client and resolution in the future
//spell top left locations["1079,1325","1137,1325","1196,1325","1255,1325"] 20wx10h
//map 372 × 373 @ (2188, 1067) 370x370 in from bottom right 350x350 in size

data class OverlayConfig(
    var enableSpellPacing: Boolean = true,
    var enableMapLookTimer: Boolean = true,
    var enableDodgeDirection: Boolean = true,
    var mapRect: Rectangle,
    var mapFlashColor: Color = Color.WHITE,
    var northColor: Color = Color.RED,
    var southColor: Color = Color.BLUE,
    var eastColor: Color = Color.GREEN,
    var westColor: Color = Color.YELLOW,
    var dodgeTimer: Int = 1_000,
    var mapTimer: Int = 5_000,
    var mapFlashTime: Int = 250,
    var mapOnLeft: Boolean = false,
    // Percent 0-100
    var mapScale: Double = 33.0,
    var hudScale: Double = 0.0
)

class GameOverlay : JFrame() {
    val fullScreenBounds: Rectangle? =
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    var config = OverlayConfig(
        mapRect = run {
            val scaleFactor = 1.0 + (33.0 / 100.0)
            val mapFull = 280.0 * scaleFactor
            Rectangle(
                (fullScreenBounds!!.width - mapFull).roundToInt(),
                (fullScreenBounds.height - mapFull).roundToInt(),
                (mapFull - 20.0).roundToInt(),
                (mapFull - 20.0).roundToInt()
            )
        },
        northColor = Color.GREEN,
        southColor = Color.GREEN,
        eastColor = Color.GREEN,
        westColor = Color.GREEN,
        mapFlashColor = Color.WHITE,
        dodgeTimer = 1_000,
        mapTimer = 5_000
    )
    var spellStates: List<Pair<Color, Boolean>>? = null
    private var dodgeDir = Random.nextBoolean()
    private var flashMap = false
    private val p1 = Pair(20, 20)
    private val p2 = Pair(fullScreenBounds!!.width - 20, 20)
    private val p3 = Pair(fullScreenBounds!!.width - 20, fullScreenBounds.height - 20)
    private val p4 = Pair(20, fullScreenBounds!!.height - 20)
    private val topLeftsScale0 = listOf(
        Point(1079, 1325),
        Point(1137, 1325),
        Point(1196, 1325),
        Point(1255, 1325)
    )
    private val topLeftsScale100 = listOf(
        Point(974, 1265),
        Point(1063, 1264),
        Point(1152, 1265),
        Point(1241, 1265)
    )
    private val spellSizeScale0 = Pair(20, 10)
    private val spellSizeScale100 = Pair(spellSizeScale0.x() * 2, spellSizeScale0.y() * 2)


    init {
        isUndecorated = true
        isAlwaysOnTop = true
        background = Color(0, 0, 0, 0)
        focusableWindowState = false
        //start timers
        Timer(config.dodgeTimer) {
            val next = Random.nextBoolean()
            if (next != dodgeDir) {
                dodgeDir = next
                repaint()
            }
        }.start()

        Timer(config.mapTimer) {
            flashMap = true
            repaint()
            Timer(config.mapFlashTime) {
                flashMap = false
                repaint()
            }.apply { isRepeats = false }.start()
        }.start()
    }

    fun updateWindowBounds(b: Rectangle) {
        bounds = b
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
        g2d.color = config.mapFlashColor
        val x = if (config.mapOnLeft) 0 else config.mapRect.x
        g2d.fillRect(x, config.mapRect.y, config.mapRect.width, config.mapRect.height)
    }

    private fun drawDodgeDirection(g2d: Graphics2D) {
        //True is up or left, and false is down or right
        g2d.stroke = BasicStroke(2f)
        if (dodgeDir) {
            //draw top
            g2d.color = config.northColor
            g2d.drawLine(p1.x(), p1.y(), p2.x(), p2.y())

            //draw left
            g2d.color = config.westColor
            g2d.drawLine(p1.x(), p1.y(), p4.x(), p4.y())
        } else {
            //draw bottom
            g2d.color = config.southColor
            g2d.drawLine(p4.x(), p4.y(), p3.x(), p3.y())

            //draw right
            g2d.color = config.eastColor
            g2d.drawLine(p2.x(), p2.y(), p3.x(), p3.y())
        }
    }

    private fun drawSpellPacing(g2d: Graphics2D) {
        val percent = config.hudScale.roundToInt().coerceIn(0, 100)
        val spellWidth = intBetween(spellSizeScale0.x(), spellSizeScale100.x(), percent)
        val spellHeight = intBetween(spellSizeScale0.y(), spellSizeScale100.y(), percent)
        spellStates?.forEachIndexed { i, state ->
            if (state.second) {
                g2d.color = state.first
                val topLeft = pointBetween(topLeftsScale0[i], topLeftsScale100[i], percent)
                g2d.fillRect(topLeft.x, topLeft.y, spellWidth, spellHeight)
            }
        }
    }
}
