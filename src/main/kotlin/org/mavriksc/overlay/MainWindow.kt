package org.mavriksc.overlay

import java.awt.GraphicsEnvironment
import javax.swing.JFrame

class MainWindow : JFrame() {
    val overlay = GameOverlay()

    init {
        title = "Overlay Settings"
        setSize(400, 300)
        setLocationRelativeTo(null)
        defaultCloseOperation = EXIT_ON_CLOSE
        overlay.updateWindowBounds(GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.bounds)

    }
    fun toggleOverlay() {
        overlay.isVisible = !overlay.isVisible
    }

}