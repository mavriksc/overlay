package org.mavriksc.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.mavriksc.overlay.lolservice.LiveClientService
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Rectangle
import javax.imageio.ImageIO
import javax.swing.*

class MainWindow : JFrame() {
    // CONTROLS:
    //  for activation of the 3 tools - DONE
    //  actual burndown calculations - DONE
    //  Dodge dir and map look color - DONE
    //  and rates
    //  Label to show the status of the game

    // TODO
    // - settings for timers
    // - option for full map flash or surrounding rect. if rect the thickness can be set
    //    - only enable for mana champions

    // known issues
    // after the game ends and into a new game it will not have reset things to start back up correctly
    // ---Look into restarting the jobs

    private val overlay = GameOverlay()
    private val gameDetector = GameDetector()
    private val gameIsForeground = gameDetector.isGameForeground
    private val gameState = gameDetector.currentGameState
    private var currentGameService: LiveClientService? = null
    private var burndownCalculator: BurndownCalculator? = null
    private val settingsReader = PersistedSettingsReader()
    private var persistentGameSettings = settingsReader.read()

    init {
        title = "Overlay Settings"
        applyAppIcon()
        setSize(400, 300)
        setLocationRelativeTo(null)
        defaultCloseOperation = EXIT_ON_CLOSE
        overlay.isVisible = false

        contentPane = buildControlsPanel()
        overlay.updateWindowBounds(
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
        )
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch { gameDetector.detectGame() }
        scope.launch {
            settingsReader.settingsFlow().collect { settings ->
                val displayBounds = overlay.fullScreenBounds
                persistentGameSettings = settings
                overlay.config.mapOnLeft = settings.mapOnLeft
                overlay.config.mapScale = settings.minimapScale.toDouble()
                overlay.config.hudScale = settings.hudScale.toDouble()
                val scaleFactor = 1.0 + (overlay.config.mapScale / 100.0)
                println("Scale Factor: $scaleFactor")
                val mapFull = 280.0 * scaleFactor
                overlay.config.mapRect = Rectangle(
                    (displayBounds!!.width - mapFull).toInt(),
                    (displayBounds.height - mapFull).toInt(),
                    (mapFull - 20.0).toInt(),
                    (mapFull - 20.0).toInt()
                )
                overlay.repaint()
            }
        }
        scope.launch {
            gameIsForeground.collect { isForeground ->
                println("Game is foreground: $isForeground")
                overlay.isVisible = isForeground && gameState.value == GameStatus.IN_PROGRESS
            }
        }
        scope.launch {
            gameState.collect { state ->
                println("Game state: $state")
                when (state) {
                    GameStatus.LOADING -> {
                        scope.launch {
                            delay(5000)
                            println("Starting events flow")
                            gameDetector.startEventsFlow()
                        }
                    }
                    GameStatus.IN_PROGRESS -> {
                        overlay.isVisible = gameIsForeground.value
                        currentGameService = LiveClientService()
                        burndownCalculator = BurndownCalculator(overlay, currentGameService!!.activePlayerData)
                    }
                    GameStatus.GAME_OVER -> {
                        currentGameService?.close()
                        currentGameService = null
                        overlay.isVisible = false
                    }
                    GameStatus.NOT_RUNNING -> {
                        currentGameService?.close()
                        currentGameService = null
                        overlay.isVisible = false
                    }
                    else -> {}
                }
            }
        }
    }

    private fun applyAppIcon() {
        val iconUrl = javaClass.getResource("/icon.png") ?: return
        val iconImage = ImageIO.read(iconUrl) ?: return
        setIconImage(iconImage)
        iconImages = listOf(iconImage)
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
                this, "Map Flash Color", overlay.config.mapFlashColor
            )
            if (chosen != null) {
                overlay.config.mapFlashColor = chosen
                overlay.repaint()
            }
        }

        val dodgeColorsButton = JButton("Dodge direction color...")
        dodgeColorsButton.addActionListener {
            val chosen = JColorChooser.showDialog(
                this, "Dodge Direction Color", overlay.config.northColor
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
}
