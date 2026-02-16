package org.mavriksc.overlay

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class GameDetector {
    private val client = getOkHttpClientForGameClient()
    private val gameUrl = "https://127.0.0.1:2999/liveclientdata/eventdata"
    private val game = "League of Legends.exe"
    private val pollingJob = Job()
    private val scope = CoroutineScope(Dispatchers.Default + pollingJob)
    private var gamePid: Int? = null
    private var foregroundPid: Int? = null
    private var gameStarted = false
    private var gameStartedJob: Job? = null

    fun isRunning() = gamePid != null
    fun isForeground() = gamePid != null && gamePid == foregroundPid
    fun gameStarted() = gameStarted

    fun detectGame() {
        try {
            isGameRunning()
            if (isRunning()) {
                getForegroundPid()
                startGameStartedCheck()
            } else {
                gameStarted = false
                gameStartedJob?.cancel()
                gameStartedJob = null
            }
        } catch (t: Throwable) {
            System.err.println("GameDetector failed to enumerate processes: ${t.message}")
        }
    }

    private fun startGameStartedCheck() {
        if (gameStartedJob?.isActive == true) return
        gameStartedJob = scope.launch {
            checkGameStarted()
        }
    }

    private fun checkGameStarted() {
        if (!isRunning()) {
            gameStarted = false
            return
        }
        if (gameStarted) return
        try {
            client.newCall(gameUrl.toRequest()).execute().use {
                gameStarted = it.isSuccessful
            }

        } catch (t: Throwable) {
            println("GameDetector failed to check if game started: ${t.message}")
        }
    }

    private fun getForegroundPid() {
        val foregroundWindow = User32.INSTANCE.GetForegroundWindow()
        val fp = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(foregroundWindow, fp)
        foregroundPid = fp.value
    }

    private fun isGameRunning() {
        val processes = enumerateProcesses()
        gamePid = processes.find { it.second.contains(game, ignoreCase = true) }?.first
    }

    /**
     * Enumerate processes via ToolHelp snapshot APIs.
     */
    private fun enumerateProcesses(): List<Pair<Int, String>> {
        val list = mutableListOf<Pair<Int, String>>()
        val kernel = Kernel32.INSTANCE

        val TH32CS_SNAPPROCESS = WinDef.DWORD(Tlhelp32.TH32CS_SNAPPROCESS.toLong())
        val hSnapshot: WinNT.HANDLE = kernel.CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, WinDef.DWORD(0))
        if (WinBase.INVALID_HANDLE_VALUE(hSnapshot)) {
            return list
        }
        try {
            val pe32 = Tlhelp32.PROCESSENTRY32()
            pe32.dwSize = WinDef.DWORD(pe32.size().toLong())

            if (!kernel.Process32First(hSnapshot, pe32)) {
                return list
            }
            do {
                val exe = charArrayToString(pe32.szExeFile)
                val pid = pe32.th32ProcessID.toInt()
                list.add(pid to exe)
            } while (kernel.Process32Next(hSnapshot, pe32))
        } finally {
            kernel.CloseHandle(hSnapshot)
        }
        return list
    }

    private fun charArrayToString(chars: CharArray): String {
        val sb = StringBuilder()
        for (c in chars) {
            if (c.code == 0) break
            sb.append(c)
        }
        return sb.toString()
    }
}

// Helper to detect INVALID_HANDLE_VALUE since JNA doesn't expose a direct Kotlin-friendly check
object WinBase {
    fun INVALID_HANDLE_VALUE(handle: WinNT.HANDLE?): Boolean {
        if (handle == null) return true
        val pointer: Pointer = handle.pointer ?: return true
        // INVALID_HANDLE_VALUE is (HANDLE) -1, which is pointer value of -1
        return Pointer.nativeValue(pointer) == -1L
    }
}
