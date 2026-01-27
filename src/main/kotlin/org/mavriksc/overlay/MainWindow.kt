package org.mavriksc.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mavriksc.overlay.lolservice.LiveClientService
import java.awt.GraphicsEnvironment
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JColorChooser
import javax.swing.JFrame
import javax.swing.JPanel

class MainWindow : JFrame() {
    // CONTROLS:
    //  to start/stop polling for champion data
    //  to show the status of the game
    //  for activation of the 3 tools
    //  for setting the time limit for the spell burn down
    //  Dodge dir and map look timer color and rates

    private val overlay = GameOverlay()
    private val gd = GameDetector()
    private var lcs = LiveClientService()
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)


    init {
        title = "Overlay Settings"
        setSize(400, 300)
        setLocationRelativeTo(null)
        defaultCloseOperation = EXIT_ON_CLOSE
        contentPane = buildControlsPanel()
        overlay.updateWindowBounds(
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration.bounds
        )
        overlay.isVisible = false
        scope.launch {
            while (isActive) {
                gd.detectGame()
                // if is gd.isRunning() start lcs polling
                //if (gd.isRunning()) lcs.startPolling()
                overlay.isVisible = gd.isForeground()
                delay(1_000)
            }
        }
    }

    private fun buildControlsPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val mapLookCheck = JCheckBox("Enable map look timer", overlay.config.enableMapLookTimer)
        mapLookCheck.addActionListener {
            overlay.config.enableMapLookTimer = mapLookCheck.isSelected
            overlay.repaint()
        }

        val spellPacingCheck = JCheckBox("Enable spell pacing", overlay.config.enableSpellPacing)
        spellPacingCheck.addActionListener {
            overlay.config.enableSpellPacing = spellPacingCheck.isSelected
            overlay.repaint()
        }

        val dodgeDirCheck = JCheckBox("Enable dodge direction", overlay.config.enableDodgeDirection)
        dodgeDirCheck.addActionListener {
            overlay.config.enableDodgeDirection = dodgeDirCheck.isSelected
            overlay.repaint()
        }

        val mapFlashColorButton = JButton("Map flash color...")
        mapFlashColorButton.addActionListener {
            val chosen = JColorChooser.showDialog(
                this,
                "Map Flash Color",
                overlay.config.mapFlashColor
            )
            if (chosen != null) {
                overlay.config.mapFlashColor = chosen
                overlay.repaint()
            }
        }

        val dodgeColorsButton = JButton("Dodge direction color...")
        dodgeColorsButton.addActionListener {
            val chosen = JColorChooser.showDialog(
                this,
                "Dodge Direction Color",
                overlay.config.northColor
            )
            if (chosen != null) {
                overlay.config.northColor = chosen
                overlay.config.southColor = chosen
                overlay.config.eastColor = chosen
                overlay.config.westColor = chosen
                overlay.repaint()
            }
        }

        panel.add(mapLookCheck)
        panel.add(spellPacingCheck)
        panel.add(dodgeDirCheck)
        panel.add(mapFlashColorButton)
        panel.add(dodgeColorsButton)
        return panel
    }

    override fun dispose() {
        job.cancel()
        super.dispose()
    }


}
