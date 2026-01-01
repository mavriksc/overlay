package org.mavriksc.overlay

import java.awt.Color
import java.awt.Rectangle

class Application {
    fun start() {
        MainWindow().isVisible = true
    }
}

data class OverlayConfig(
    var enableSpellPacing: Boolean = true,
    var enableMapLookTimer: Boolean = true,
    var enableDodgeDirection: Boolean = true,
    var mapRect: Rectangle,
    var northColor: Color = Color.RED,
    var southColor: Color = Color.BLUE,
    var eastColor: Color = Color.GREEN,
    var westColor: Color = Color.YELLOW
)
fun main() {
    Application().start()
}