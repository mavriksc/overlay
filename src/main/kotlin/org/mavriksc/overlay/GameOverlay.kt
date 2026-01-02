package org.mavriksc.overlay

import java.awt.*
import java.awt.geom.Ellipse2D
import javax.swing.JFrame
import javax.swing.Timer
import kotlin.Pair
import kotlin.random.Random


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
    var mapFlashTime: Int = 250
)

class GameOverlay : JFrame() {
    private val fullScreenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    var config = OverlayConfig(
        mapRect = Rectangle(fullScreenBounds.width - 370, fullScreenBounds.height - 370, 350, 350),
        northColor = Color.GREEN,
        southColor = Color.GREEN,
        eastColor = Color.GREEN,
        westColor = Color.GREEN,
        mapFlashColor = Color.WHITE,
        dodgeTimer = 1_000,
        mapTimer = 5_000
    )
    private var dodgeDir = Random.nextBoolean()
    private var flashMap = false
    private val p1 = Pair(20, 20)
    private val p2 = Pair(fullScreenBounds.width - 20, 20)
    private val p3 = Pair(fullScreenBounds.width - 20, fullScreenBounds.height - 20)
    private val p4 = Pair(20, fullScreenBounds.height - 20)

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
        g2d.drawRect(config.mapRect.x, config.mapRect.y, config.mapRect.width, config.mapRect.height)
        g2d.fillRect(config.mapRect.x, config.mapRect.y, config.mapRect.width, config.mapRect.height)
    }

    private fun drawDodgeDirection(g2d: Graphics2D) {
        //True is up or left, and false is down or right
        g2d.stroke = BasicStroke(2f)
        if (dodgeDir) {
            //draw top
            g2d.color = config.northColor
            g2d.drawLine(p1.first, p1.second, p2.first, p2.second)

            //draw left
            g2d.color = config.westColor
            g2d.drawLine(p1.first, p1.second, p4.first, p4.second)
        } else {
            //draw bottom
            g2d.color = config.southColor
            g2d.drawLine(p4.first, p4.second, p3.first, p3.second)

            //draw right
            g2d.color = config.eastColor
            g2d.drawLine(p2.first, p2.second, p3.first, p3.second)
        }
    }

    private fun drawSpellPacing(g2d: Graphics2D) {}
}