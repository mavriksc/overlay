package org.mavriksc.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment
import javax.swing.JCheckBox
import javax.swing.JFrame

class MainWindow : JFrame() {
    private val overlay = GameOverlay()
    private val overlayToggle = JCheckBox("Show Overlay")
    private val gd = GameDetector()
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)


    init {
        title = "Overlay Settings"
        setSize(400, 300)
        setLocationRelativeTo(null)
        defaultCloseOperation = EXIT_ON_CLOSE
        overlay.updateWindowBounds(
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration.bounds
        )
        overlay.isVisible = false

        overlayToggle.isSelected = overlay.isVisible
        overlayToggle.addItemListener {
            toggleOverlay()
        }

        add(overlayToggle)
        scope.launch {
            while (isActive) {
                gd.detectGame()
                //overlay.isVisible = gd.isForeground()
                //overlayToggle.isSelected = overlay.isVisible
                delay(5_000)
            }
        }
    }

    override fun dispose() {
        job.cancel()
        super.dispose()
    }

    private fun toggleOverlay() {
        overlay.isVisible = !overlay.isVisible
        overlayToggle.isSelected = overlay.isVisible
    }

}