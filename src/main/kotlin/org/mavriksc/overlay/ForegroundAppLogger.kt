package org.mavriksc.overlay

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.*
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.WinEventProc
import com.sun.jna.ptr.IntByReference

object ForegroundAppLogger {
    private const val EVENT_SYSTEM_FOREGROUND = 0x0003
    private const val WIN_EVENT_OUT_OF_CONTEXT = 0x0000
    private const val SKIP_OWN_PROCESS = 0x0002
    private const val EVENT_OBJECT_CREATE = 0x8000
    private const val EVENT_OBJECT_DESTROY = 0x8001

    private const val game = "League of Legends.exe"

    @JvmStatic
    fun main(args: Array<String>) {
        val foregroundEventProc = WinEventProc { _, _, hwnd, _, _, _, _ ->
            println("League is foreground:${exeNameFromHwnd(hwnd) == game}")
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


        // Example structure (Conceptual JNA)
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


        if (hook == null || Pointer.nativeValue(hook.pointer) == 0L) {
            System.err.println("Failed to set foreground window hook.")
            return
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            User32.INSTANCE.UnhookWinEvent(hook)
            User32.INSTANCE.UnhookWinEvent(hHook)
        })

        println("Foreground app logger running. Press Ctrl+C to exit.")

        val msg = WinUser.MSG()
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg)
            User32.INSTANCE.DispatchMessage(msg)
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

    private fun processExeStartStopEvent(event: DWORD, hwnd: HWND?) {
        if (exeNameFromHwnd(hwnd) != game) return
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
