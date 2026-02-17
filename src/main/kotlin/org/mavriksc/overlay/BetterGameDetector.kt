package org.mavriksc.overlay

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser.WinEventProc
import com.sun.jna.ptr.IntByReference
import java.util.concurrent.CountDownLatch
//TODO publish to flow and get to properly run in the background
class BetterGameDetector {
    private val EVENT_SYSTEM_FOREGROUND = 0x0003
    private val WIN_EVENT_OUT_OF_CONTEXT = 0x0000
    private val SKIP_OWN_PROCESS = 0x0002
    private val EVENT_OBJECT_CREATE = 0x8000
    private val EVENT_OBJECT_DESTROY = 0x8001
    private val GAME_EXECUTABLE_NAME = "League of Legends.exe"


    init {

        val foregroundEventProc = WinEventProc { _, _, hwnd, _, _, _, _ ->
            println("League is foreground:${exeNameFromHwnd(hwnd) == GAME_EXECUTABLE_NAME}")
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

        val proc =
            WinEventProc { _, event, hwnd, _, _, _, _ ->
                processExeStartStopEvent(event, hwnd)
            }
        val hHook = User32.INSTANCE.SetWinEventHook(
            EVENT_OBJECT_CREATE,
            EVENT_OBJECT_DESTROY,
            null,
            proc,
            0, 0,
            WIN_EVENT_OUT_OF_CONTEXT
        )
        Runtime.getRuntime().addShutdownHook(Thread {
            User32.INSTANCE.UnhookWinEvent(hook)
            User32.INSTANCE.UnhookWinEvent(hHook)
        })

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

    private fun processExeStartStopEvent(event: DWORD, hwnd: HWND?) {
        if (exeNameFromHwnd(hwnd) != GAME_EXECUTABLE_NAME) return
        if (DWORD(EVENT_OBJECT_CREATE.toLong()).compareTo(event) == 0) {
            println("League is starting: ${exeNameFromHwnd(hwnd)}")
        } else if (DWORD(EVENT_OBJECT_DESTROY.toLong()).compareTo(event) == 0) {
            println("League is stopping: ${exeNameFromHwnd(hwnd)}")
        }
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
}

fun main() {
    val shutdownLatch = CountDownLatch(1)

    val loggerThread = Thread {
        BetterGameDetector()
        try {
            shutdownLatch.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
    loggerThread.name = "foreground-app-logger"
    loggerThread.isDaemon = true
    loggerThread.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        shutdownLatch.countDown()
    })

    println("Foreground app logger running in background. Type 'q' and press Enter to exit.")
    while (true) {
        val line = readLine() ?: break
        if (line.trim().equals("q", ignoreCase = true)) {
            break
        }
    }
    shutdownLatch.countDown()
}
