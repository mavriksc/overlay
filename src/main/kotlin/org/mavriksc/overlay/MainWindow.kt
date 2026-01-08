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
    // CONTROLS:
    //  to start/stop polling for champion data
    //  to show the status of the game
    //  for activation of the 3 tools
    //  for setting the time limit for the spell burn down
    //  Dodge dir and map look timer color and rates

    private val overlay = GameOverlay()
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
        scope.launch {
            while (isActive) {
                gd.detectGame()
                overlay.isVisible = gd.isForeground()
                delay(5_000)
            }
        }
    }

    override fun dispose() {
        job.cancel()
        super.dispose()
    }


}