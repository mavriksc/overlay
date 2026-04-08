package org.mavriksc.overlay

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO

class Application {
    fun start() = application {
        val controller = remember { MainWindow() }
        val traySupported = SystemTray.isSupported()
        val windowState = rememberWindowState(width = 980.dp, height = 860.dp)
        var settingsVisible by remember { mutableStateOf(true) }
        val trayIcon = remember {
            if (!traySupported) null else {
                val image = ImageIO.read(Application::class.java.getResource("/icon.png"))
                val popup = PopupMenu()
                val openItem = MenuItem("Open Settings")
                val quitItem = MenuItem("Quit")
                popup.add(openItem)
                popup.add(quitItem)
                TrayIcon(image, "Overlay", popup).apply {
                    isImageAutoSize = true
                    openItem.addActionListener {
                        settingsVisible = true
                        windowState.isMinimized = false
                    }
                    quitItem.addActionListener {
                        controller.close()
                        exitApplication()
                    }
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1) {
                                settingsVisible = true
                                windowState.isMinimized = false
                            }
                        }
                    })
                }
            }
        }

        fun hideToTray() {
            if (traySupported) {
                settingsVisible = false
            } else {
                windowState.isMinimized = true
            }
        }

        DisposableEffect(Unit) {
            if (trayIcon != null) {
                SystemTray.getSystemTray().add(trayIcon)
            }
            onDispose {
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon)
                }
                controller.close()
            }
        }

        Window(
            onCloseRequest = {
                controller.close()
                exitApplication()
            },
            title = "Overlay Settings",
            state = windowState,
            icon = androidx.compose.ui.res.painterResource("icon.png"),
            undecorated = true,
            transparent = true,
            visible = settingsVisible
        ) {
            if (windowState.isMinimized && traySupported) {
                settingsVisible = false
                windowState.isMinimized = false
            }

            SettingsWindow(
                controller = controller,
                isMaximized = windowState.placement == WindowPlacement.Maximized,
                onMinimize = ::hideToTray,
                onToggleMaximize = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                },
                onQuit = {
                    controller.close()
                    exitApplication()
                }
            )
        }
    }
}

fun main() {
    Application().start()
}
