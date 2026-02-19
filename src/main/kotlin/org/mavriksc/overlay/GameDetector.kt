package org.mavriksc.overlay

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.*
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.WinEventProc
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
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
        ifGameIsRunningAlreadySetState()

        val msg = WinUser.MSG()
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg)
            User32.INSTANCE.DispatchMessage(msg)
        }
    }

    private fun ifGameIsRunningAlreadySetState() {
        val list = mutableListOf<String>()
        val kernel = Kernel32.INSTANCE

        val TH32CS_SNAPPROCESS = DWORD(Tlhelp32.TH32CS_SNAPPROCESS.toLong())
        val hSnapshot: WinNT.HANDLE = kernel.CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, WinDef.DWORD(0))
        if (WinBase.INVALID_HANDLE_VALUE(hSnapshot)) {
            return
        }
        try {
            val pe32 = Tlhelp32.PROCESSENTRY32()
            pe32.dwSize = DWORD(pe32.size().toLong())

            if (!kernel.Process32First(hSnapshot, pe32)) {
                return
            }
            do {
                val exe = charArrayToString(pe32.szExeFile)
                println(exe)
                list.add(exe)
            } while (kernel.Process32Next(hSnapshot, pe32))
        } finally {
            kernel.CloseHandle(hSnapshot)
        }
        if (list.contains(GAME_EXECUTABLE_NAME)) _currentGameState.value = GameStatus.IN_PROGRESS
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

object WinBase {
    fun INVALID_HANDLE_VALUE(handle: WinNT.HANDLE?): Boolean {
        if (handle == null) return true
        val pointer: Pointer = handle.pointer ?: return true
        // INVALID_HANDLE_VALUE is (HANDLE) -1, which is pointer value of -1
        return Pointer.nativeValue(pointer) == -1L
    }
}