package org.mavriksc.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.mavriksc.overlay.lolservice.LiveClientService
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle

class MainWindow(
    private val settingsStore: AppSettingsStore = AppSettingsStore(),
    private val gameDetector: GameRuntimeDetector = GameDetector(),
    private val overlayFactory: () -> GameOverlay = { GameOverlay() },
    private val liveClientServiceFactory: () -> LiveClientService = { LiveClientService() }
) {
    private var overlay: GameOverlay? = null
    private val gameIsForeground = gameDetector.isGameForeground
    private val gameState = gameDetector.currentGameState
    private val gameBounds = gameDetector.currentGameBounds
    private val gameWindowId = gameDetector.currentGameWindowId
    private val gameSessionKind = gameDetector.currentGameSessionKind
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentGameService: LiveClientService? = null
    private var burndownCalculator: BurndownCalculator? = null
    private var eventsJob: kotlinx.coroutines.Job? = null
    private var boundRuntimeWindowId: Long? = null
    private val settingsReader = PersistedSettingsReader()
    private val _persistedGameSettings = MutableStateFlow(settingsReader.read())
    val persistedGameSettings: StateFlow<PersistedGameSettings?> = _persistedGameSettings.asStateFlow()
    val appSettings: StateFlow<AppSettings> = settingsStore.settings
    val currentGameBounds: StateFlow<Rectangle?> = gameBounds
    val currentGameState: StateFlow<GameStatus> = gameState
    val isGameForeground: StateFlow<Boolean> = gameIsForeground

    private val defaultDisplayBounds: Rectangle = safeDefaultDisplayBounds()

    init {
        applyRuntimeConfig()
        startRuntime()
    }

    fun updateFeatures(transform: (FeatureSettings) -> FeatureSettings) {
        settingsStore.update { it.copy(features = transform(it.features)) }
    }

    fun updateTiming(transform: (TimingSettings) -> TimingSettings) {
        settingsStore.update { it.copy(timing = transform(it.timing)) }
    }

    fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
        settingsStore.update { it.copy(appearance = transform(it.appearance)) }
    }

    fun updateCalibration(transform: (CalibrationSettings) -> CalibrationSettings) {
        settingsStore.update { it.copy(calibration = transform(it.calibration)) }
    }

    fun resetCalibration() {
        settingsStore.resetCalibration()
    }

    fun resetAllSettings() {
        settingsStore.resetAll()
    }

    fun previewMapFlash() {
        overlay?.previewMapFlash()
    }

    fun previewDodgeCue() {
        overlay?.previewDodgeFlip()
    }

    fun close() {
        scope.cancel()
        currentGameService?.close()
        currentGameService = null
        eventsJob?.cancel()
        eventsJob = null
        disposeOverlay()
    }

    private fun startRuntime() {
        scope.launch { gameDetector.detectGame() }
        scope.launch { gameDetector.monitorGameLifecycle() }
        scope.launch {
            appSettings.collect {
                gameDetector.setTrackedExecutableName(it.features.effectiveGameExecutableName())
                applyRuntimeConfig()
                updateOverlayVisibility()
            }
        }
        scope.launch {
            settingsReader.settingsFlow().collect { settings ->
                _persistedGameSettings.value = settings
                applyRuntimeConfig()
            }
        }
        scope.launch {
            gameBounds.collect { bounds ->
                overlay?.let {
                    it.updateWindowBounds(bounds ?: defaultDisplayBounds)
                    it.repaint()
                }
            }
        }
        scope.launch {
            gameWindowId.collect { windowId ->
                if (windowId != null && boundRuntimeWindowId != null && boundRuntimeWindowId != windowId) {
                    rebindGameRuntime()
                }
            }
        }
        scope.launch {
            gameIsForeground.collect {
                updateOverlayVisibility()
            }
        }
        scope.launch {
            gameSessionKind.collect { kind ->
                if (kind != GameSessionKind.PLAYABLE) {
                    tearDownRuntime()
                } else if (gameState.value == GameStatus.IN_PROGRESS) {
                    ensureLiveClientBound()
                }
                updateOverlayVisibility()
            }
        }
        scope.launch {
            gameState.collect { state ->
                when (state) {
                    GameStatus.LOADING -> {
                        if (eventsJob?.isActive != true) {
                            eventsJob = scope.launch {
                                delay(5000)
                                gameDetector.startEventsFlow()
                            }
                        }
                    }

                    GameStatus.IN_PROGRESS -> {
                        if (eventsJob?.isActive != true) {
                            eventsJob = scope.launch {
                                gameDetector.startEventsFlow()
                            }
                        }
                        if (shouldStartRuntime(state, gameSessionKind.value)) {
                            ensureLiveClientBound()
                        } else {
                            tearDownRuntime()
                        }
                    }

                    GameStatus.GAME_OVER,
                    GameStatus.NOT_RUNNING -> {
                        tearDownRuntime()
                        eventsJob?.cancel()
                        eventsJob = null
                    }

                    else -> {}
                }
                updateOverlayVisibility()
            }
        }
    }

    private fun applyRuntimeConfig() {
        overlay?.updateConfig(appSettings.value.toOverlayConfig(_persistedGameSettings.value))
    }

    private fun ensureLiveClientBound() {
        val overlay = ensureOverlayCreated()
        val currentWindowId = gameWindowId.value
        if (currentGameService == null || (currentWindowId != null && boundRuntimeWindowId != currentWindowId)) {
            currentGameService?.close()
            currentGameService = liveClientServiceFactory()
            burndownCalculator = BurndownCalculator(overlay, currentGameService!!.activePlayerData)
            boundRuntimeWindowId = currentWindowId
        }
    }

    private fun rebindGameRuntime() {
        tearDownRuntime()
        if (gameState.value == GameStatus.IN_PROGRESS && gameSessionKind.value == GameSessionKind.PLAYABLE) {
            ensureLiveClientBound()
        }
    }

    private fun updateOverlayVisibility() {
        val settings = appSettings.value
        val visibleByState = shouldStartRuntime(gameState.value, gameSessionKind.value)
        val visibleByForeground = !settings.features.showOnlyWhileForeground || gameIsForeground.value
        overlay?.isVisible = visibleByState && visibleByForeground
    }

    private fun ensureOverlayCreated(): GameOverlay {
        overlay?.let { return it }
        val created = overlayFactory()
        created.isVisible = false
        created.updateWindowBounds(gameBounds.value ?: defaultDisplayBounds)
        created.updateConfig(appSettings.value.toOverlayConfig(_persistedGameSettings.value))
        overlay = created
        return created
    }

    private fun tearDownRuntime() {
        currentGameService?.close()
        currentGameService = null
        burndownCalculator = null
        boundRuntimeWindowId = null
        disposeOverlay()
    }

    private fun disposeOverlay() {
        overlay?.let {
            it.isVisible = false
            it.dispose()
        }
        overlay = null
    }

    internal fun hasOverlayInstance(): Boolean = overlay != null

    internal fun currentSessionKindForTest(): GameSessionKind = gameSessionKind.value

    private fun safeDefaultDisplayBounds(): Rectangle =
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
        } catch (_: HeadlessException) {
            Rectangle(0, 0, 1920, 1080)
        }

    internal companion object {
        fun shouldStartRuntime(state: GameStatus, sessionKind: GameSessionKind): Boolean =
            state == GameStatus.IN_PROGRESS && sessionKind == GameSessionKind.PLAYABLE
    }
}
