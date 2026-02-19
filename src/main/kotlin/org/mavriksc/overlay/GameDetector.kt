package org.mavriksc.overlay

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.WinEventProc
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mavriksc.overlay.lolservice.LiveGameEvent
import java.util.concurrent.TimeUnit

class GameDetector {
    private val EVENT_SYSTEM_FOREGROUND = 0x0003
    private val WIN_EVENT_OUT_OF_CONTEXT = 0x0000
    private val SKIP_OWN_PROCESS = 0x0002
    private val EVENT_OBJECT_CREATE = 0x8000
    private val EVENT_OBJECT_DESTROY = 0x8001
    private val GAME_EXECUTABLE_NAME = "League of Legends.exe"
    private val client = getOkHttpClientForGameClient(5, TimeUnit.SECONDS)
    private val eventDataURL = "https://localhost:2999/liveclientdata/eventdata"

    private val _isGameForeground = MutableStateFlow(false)
    val isGameForeground: StateFlow<Boolean> = _isGameForeground.asStateFlow()

    private val _currentGameState = MutableStateFlow(GameStatus.NOT_RUNNING)
    val currentGameState: StateFlow<GameStatus> = _currentGameState.asStateFlow()

    fun detectGame() {

        val foregroundEventProc = WinEventProc { _, _, hwnd, _, _, _, _ ->
            _isGameForeground.value = exeNameFromHwnd(hwnd) == GAME_EXECUTABLE_NAME
        }
        val hook = User32.INSTANCE.SetWinEventHook(
            EVENT_SYSTEM_FOREGROUND,
            EVENT_SYSTEM_FOREGROUND,
            null,
            foregroundEventProc,
            0,
            0,
            WIN_EVENT_OUT_OF_CONTEXT or SKIP_OWN_PROCESS
        )

        val allExeStartStopEventProc = WinEventProc { _, event, hwnd, _, _, _, _ ->
            processExeStartStopEvent(event, hwnd)
        }
        val hHook = User32.INSTANCE.SetWinEventHook(
            EVENT_OBJECT_CREATE,
            EVENT_OBJECT_DESTROY,
            null,
            allExeStartStopEventProc,
            0, 0,
            WIN_EVENT_OUT_OF_CONTEXT
        )
        Runtime.getRuntime().addShutdownHook(Thread {
            User32.INSTANCE.UnhookWinEvent(hook)
            User32.INSTANCE.UnhookWinEvent(hHook)
        })

        val msg = WinUser.MSG()
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg)
            User32.INSTANCE.DispatchMessage(msg)
        }
    }

    private fun processExeStartStopEvent(event: DWORD, hwnd: HWND?) {
        if (exeNameFromHwnd(hwnd) != GAME_EXECUTABLE_NAME) return
        when (event.toInt()) {
            EVENT_OBJECT_CREATE -> when (_currentGameState.value) {
                GameStatus.NOT_RUNNING -> _currentGameState.value = GameStatus.FALSE_START
                GameStatus.FALSE_START -> _currentGameState.value = GameStatus.LOADING
                else -> {}
            }

            EVENT_OBJECT_DESTROY -> when (_currentGameState.value) {
                GameStatus.IN_PROGRESS -> _currentGameState.value = GameStatus.NOT_RUNNING
                else -> {}
            }
        }
    }

    private fun exeNameFromHwnd(hwnd: HWND?): String {
        if (hwnd == null) {
            return "<none>"
        }

        val pidRef = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
        val pid = pidRef.value
        if (pid == 0) {
            return "<unknown>"
        }

        val path = pidToExeFileName(pid)
        return path?.substringAfterLast('\\') ?: "<unknown>"
    }

    private fun pidToExeFileName(pid: Int): String? {
        val process = Kernel32.INSTANCE.OpenProcess(
            WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
            false,
            pid
        ) ?: return null

        return try {
            val buffer = CharArray(1024)
            val size = IntByReference(buffer.size)
            val ok = Kernel32.INSTANCE.QueryFullProcessImageName(
                process,
                0,
                buffer,
                size
            )
            if (!ok) return null
            String(buffer, 0, size.value)
        } finally {
            Kernel32.INSTANCE.CloseHandle(process)
        }
    }

    suspend fun startEventsFlow() {
        var lastEventId = 0
        while (currentGameState.value == GameStatus.LOADING || currentGameState.value == GameStatus.IN_PROGRESS) {
            //println("Fetching events with lastEventId: $lastEventId")
            val events = fetchEvents(lastEventId)
            //println("Fetched ${events.size} events")
            when (currentGameState.value) {
                GameStatus.IN_PROGRESS -> if (lookForGameOver(events)) _currentGameState.value = GameStatus.GAME_OVER
                else -> if (lookForGameStart(events)) _currentGameState.value = GameStatus.IN_PROGRESS
            }
            lastEventId = events.lastOrNull()?.eventId ?: lastEventId
            delay(1000)
        }
        println("Events flow stopped")
    }

    private fun lookForGameStart(events: List<LiveGameEvent>): Boolean {
        //println("Looking for game start:")
        return events.any { it.eventName == "GameStart" }
    }

    private fun lookForGameOver(events: List<LiveGameEvent>): Boolean {
        //println("Looking for game over:")
        return events.any { it.eventName == "GameEnd" }
    }


    private fun fetchEvents(lastEventId: Int): List<LiveGameEvent> {
        try {
            client.newCall(("$eventDataURL?eventID=$lastEventId").toRequest()).execute().use { response ->
                val body = response.body.string()
                //println("Fetched events response: $body")
                val bodyObject = Json.parseToJsonElement(body).jsonObject
                val eventsArray = bodyObject["Events"]?.jsonArray ?: return emptyList()
                return eventsArray.mapNotNull { eventElement ->
                    val eventObject = eventElement.jsonObject
                    val eventId = eventObject["EventID"]?.jsonPrimitive?.int ?: return@mapNotNull null
                    val eventName = eventObject["EventName"]?.jsonPrimitive?.content ?: "Unknown"
                    //println(eventName)
                    val eventTime = eventObject["EventTime"]?.jsonPrimitive?.floatOrNull
                    LiveGameEvent(eventId, eventName, eventTime)
                }
            }
        } catch (e: Exception) {
            println("Failed to fetch events: ${e.message}")
            return emptyList()
        }
    }

}

enum class GameStatus {
    NOT_RUNNING,
    FALSE_START,
    LOADING,
    IN_PROGRESS,
    GAME_OVER
}