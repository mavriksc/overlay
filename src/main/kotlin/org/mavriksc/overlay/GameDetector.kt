package org.mavriksc.overlay

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Tlhelp32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.WinEventProc
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.delay
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
import java.awt.Rectangle
import java.util.concurrent.TimeUnit

data class TrackedGameWindow(
    val handleId: Long,
    val bounds: Rectangle
)

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

    private val _currentGameBounds = MutableStateFlow<Rectangle?>(null)
    val currentGameBounds: StateFlow<Rectangle?> = _currentGameBounds.asStateFlow()

    private val _currentGameWindowId = MutableStateFlow<Long?>(null)
    val currentGameWindowId: StateFlow<Long?> = _currentGameWindowId.asStateFlow()

    fun detectGame() {
        val foregroundEventProc = WinEventProc { _, _, _, _, _, _, _ ->
            refreshWindowTracking()
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
            0,
            0,
            WIN_EVENT_OUT_OF_CONTEXT
        )
        Runtime.getRuntime().addShutdownHook(Thread {
            User32.INSTANCE.UnhookWinEvent(hook)
            User32.INSTANCE.UnhookWinEvent(hHook)
        })
        initializeOnAppStart()

        val msg = WinUser.MSG()
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg)
            User32.INSTANCE.DispatchMessage(msg)
        }
    }

    fun initializeOnAppStart() {
        refreshWindowTracking()
        val isRunning = isGameProcessRunning()
        if (!isRunning || _currentGameBounds.value == null) {
            _currentGameState.value = GameStatus.NOT_RUNNING
            return
        }
        val events = try {
            fetchEvents(0)
        } catch (_: Exception) {
            emptyList()
        }
        _currentGameState.value = if (events.isNotEmpty()) GameStatus.IN_PROGRESS else GameStatus.LOADING
    }

    suspend fun monitorGameLifecycle() {
        while (true) {
            refreshWindowTracking()
            val running = isGameProcessRunning()
            val hasWindow = _currentGameBounds.value != null

            when {
                !running || !hasWindow -> {
                    if (_currentGameState.value != GameStatus.NOT_RUNNING) {
                        _currentGameState.value = GameStatus.NOT_RUNNING
                    }
                }

                _currentGameState.value == GameStatus.NOT_RUNNING ||
                        _currentGameState.value == GameStatus.FALSE_START ||
                        _currentGameState.value == GameStatus.GAME_OVER -> {
                    val events = fetchEvents(0)
                    _currentGameState.value = if (events.isNotEmpty()) GameStatus.IN_PROGRESS else GameStatus.LOADING
                }
            }

            delay(1000)
        }
    }

    private fun refreshWindowTracking() {
        val tracked = findPrimaryGameWindow()
        _currentGameBounds.value = tracked?.bounds
        _currentGameWindowId.value = tracked?.handleId
        val foregroundIsGame = exeNameFromHwnd(User32.INSTANCE.GetForegroundWindow()) == GAME_EXECUTABLE_NAME
        _isGameForeground.value = foregroundIsGame || tracked != null
    }

    private fun isGameProcessRunning(): Boolean {
        val list = mutableListOf<String>()
        val kernel = Kernel32.INSTANCE

        val snapshotFlag = DWORD(Tlhelp32.TH32CS_SNAPPROCESS.toLong())
        val snapshot = kernel.CreateToolhelp32Snapshot(snapshotFlag, WinDef.DWORD(0))
        if (LocalWinBase.INVALID_HANDLE_VALUE(snapshot)) {
            return false
        }
        try {
            val processEntry = Tlhelp32.PROCESSENTRY32()
            processEntry.dwSize = DWORD(processEntry.size().toLong())

            if (!kernel.Process32First(snapshot, processEntry)) {
                return false
            }
            do {
                list.add(charArrayToString(processEntry.szExeFile))
            } while (kernel.Process32Next(snapshot, processEntry))
        } finally {
            kernel.CloseHandle(snapshot)
        }
        return list.contains(GAME_EXECUTABLE_NAME)
    }

    private fun findPrimaryGameWindow(): TrackedGameWindow? {
        var bestWindow: TrackedGameWindow? = null
        User32.INSTANCE.EnumWindows({ hwnd, _ ->
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                return@EnumWindows true
            }
            if (exeNameFromHwnd(hwnd) != GAME_EXECUTABLE_NAME) {
                return@EnumWindows true
            }
            val rect = RECT()
            if (!User32.INSTANCE.GetWindowRect(hwnd, rect)) {
                return@EnumWindows true
            }
            val bounds = Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top)
            if (bounds.width <= 0 || bounds.height <= 0) {
                return@EnumWindows true
            }
            val handleId = hwnd.pointer?.let(Pointer::nativeValue) ?: return@EnumWindows true
            val candidate = TrackedGameWindow(handleId = handleId, bounds = bounds)
            if (bestWindow == null || bounds.width * bounds.height > bestWindow!!.bounds.width * bestWindow!!.bounds.height) {
                bestWindow = candidate
            }
            true
        }, null)
        return bestWindow
    }

    private fun charArrayToString(chars: CharArray): String {
        val sb = StringBuilder()
        for (c in chars) {
            if (c.code == 0) break
            sb.append(c)
        }
        return sb.toString()
    }

    private fun processExeStartStopEvent(event: DWORD, hwnd: HWND?) {
        if (exeNameFromHwnd(hwnd) != GAME_EXECUTABLE_NAME) return
        when (event.toInt()) {
            EVENT_OBJECT_CREATE -> {
                if (_currentGameState.value == GameStatus.NOT_RUNNING) {
                    _currentGameState.value = GameStatus.LOADING
                }
            }

            EVENT_OBJECT_DESTROY -> {
                if (_currentGameWindowId.value != null && _currentGameWindowId.value == hwnd?.pointer?.let(Pointer::nativeValue)) {
                    _currentGameState.value = GameStatus.NOT_RUNNING
                }
            }
        }
        refreshWindowTracking()
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
        while (_currentGameState.value == GameStatus.LOADING || _currentGameState.value == GameStatus.IN_PROGRESS) {
            refreshWindowTracking()
            val events = fetchEvents(lastEventId)
            when (_currentGameState.value) {
                GameStatus.IN_PROGRESS -> if (lookForGameOver(events)) _currentGameState.value = GameStatus.GAME_OVER
                else -> if (lookForGameStart(events)) _currentGameState.value = GameStatus.IN_PROGRESS
            }
            lastEventId = events.lastOrNull()?.eventId ?: lastEventId
            delay(1000)
        }
    }

    private fun lookForGameStart(events: List<LiveGameEvent>): Boolean = events.any { it.eventName == "GameStart" }

    private fun lookForGameOver(events: List<LiveGameEvent>): Boolean = events.any { it.eventName == "GameEnd" }

    private fun fetchEvents(lastEventId: Int): List<LiveGameEvent> {
        try {
            client.newCall(("$eventDataURL?eventID=$lastEventId").toRequest()).execute().use { response ->
                val body = response.body.string()
                val bodyObject = Json.parseToJsonElement(body).jsonObject
                val eventsArray = bodyObject["Events"]?.jsonArray ?: return emptyList()
                return eventsArray.mapNotNull { eventElement ->
                    val eventObject = eventElement.jsonObject
                    val eventId = eventObject["EventID"]?.jsonPrimitive?.int ?: return@mapNotNull null
                    val eventName = eventObject["EventName"]?.jsonPrimitive?.content ?: "Unknown"
                    val eventTime = eventObject["EventTime"]?.jsonPrimitive?.floatOrNull
                    LiveGameEvent(eventId, eventName, eventTime)
                }
            }
        } catch (_: Exception) {
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

object LocalWinBase {
    fun INVALID_HANDLE_VALUE(handle: WinNT.HANDLE?): Boolean {
        if (handle == null) return true
        val pointer: Pointer = handle.pointer ?: return true
        return Pointer.nativeValue(pointer) == -1L
    }
}
