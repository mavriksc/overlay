package org.mavriksc.overlay

import java.awt.*
import java.awt.geom.Ellipse2D
import javax.swing.JFrame

class GameOverlay: JFrame() {
    init {
        isUndecorated = true
        isAlwaysOnTop = true
        background = Color(0, 0, 0, 0)
        focusableWindowState = false
    }

    fun updateWindowBounds(b: Rectangle) {
        bounds = b
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f) // 50% transparent
        g2d.color = Color.RED
        g2d.fill(Ellipse2D.Double(50.0, 50.0, 100.0, 100.0))
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f) // Fully opaque
        g2d.color = Color.WHITE
        g2d.font = Font("Arial", Font.BOLD, 24)
        g2d.drawString("My Java Overlay!", 200, 100)
        // draw a blue line 1 alpha 20 px in from bounds
        val offset = 20
        g2d.color = Color.BLUE
        g2d.stroke = BasicStroke(2f)
        g2d.drawLine(offset, offset, bounds.width - offset, 20)
        g2d.drawLine(bounds.width - offset, 20, bounds.width - offset, bounds.height - offset)
        g2d.drawLine(bounds.width - offset, bounds.height - offset, offset, bounds.height - offset)
        g2d.drawLine(offset, bounds.height - offset, offset, offset)
    }

    fun drawUI(g2d: Graphics2D) {

    }
}