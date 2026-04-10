package org.mavriksc.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MainWindowTest {
    @Test
    fun `runtime only starts for in progress playable sessions`() {
        assertTrueCase(GameStatus.IN_PROGRESS, GameSessionKind.PLAYABLE, expected = true)
        assertTrueCase(GameStatus.IN_PROGRESS, GameSessionKind.SPECTATOR, expected = false)
        assertTrueCase(GameStatus.IN_PROGRESS, GameSessionKind.UNKNOWN, expected = false)
        assertTrueCase(GameStatus.LOADING, GameSessionKind.PLAYABLE, expected = false)
    }

    @Test
    fun `spectator sessions never instantiate overlay`() {
        val detector = FakeGameRuntimeDetector(
            initialState = GameStatus.IN_PROGRESS,
            initialSessionKind = GameSessionKind.SPECTATOR
        )
        val overlayCreations = AtomicInteger(0)

        val window = MainWindow(
            gameDetector = detector,
            overlayFactory = {
                overlayCreations.incrementAndGet()
                error("overlay should not be created for spectator sessions")
            }
        )

        Thread.sleep(150)

        assertFalse(window.hasOverlayInstance())
        assertEquals(0, overlayCreations.get())

        window.close()
    }

    private fun assertTrueCase(state: GameStatus, kind: GameSessionKind, expected: Boolean) {
        assertEquals(expected, MainWindow.shouldStartRuntime(state, kind))
    }
}

private class FakeGameRuntimeDetector(
    initialForeground: Boolean = false,
    initialState: GameStatus = GameStatus.NOT_RUNNING,
    initialBounds: Rectangle? = null,
    initialWindowId: Long? = null,
    initialSessionKind: GameSessionKind = GameSessionKind.UNKNOWN
) : GameRuntimeDetector {
    override val isGameForeground: StateFlow<Boolean> = MutableStateFlow(initialForeground)
    override val currentGameState: StateFlow<GameStatus> = MutableStateFlow(initialState)
    override val currentGameBounds: StateFlow<Rectangle?> = MutableStateFlow(initialBounds)
    override val currentGameWindowId: StateFlow<Long?> = MutableStateFlow(initialWindowId)
    override val currentGameSessionKind: StateFlow<GameSessionKind> = MutableStateFlow(initialSessionKind)

    override fun detectGame() = Unit

    override fun initializeOnAppStart() = Unit

    override fun setTrackedExecutableName(executableName: String) = Unit

    override suspend fun monitorGameLifecycle() = Unit

    override suspend fun startEventsFlow() = Unit
}
