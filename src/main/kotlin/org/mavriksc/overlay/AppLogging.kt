package org.mavriksc.overlay

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

object AppLogging {
    private const val LOG_DIR_PROPERTY = "overlay.log.dir"
    private const val LOG_FILE_NAME = "overlay.log"
    private var initialized = false

    fun initialize() {
        if (initialized) return

        val logDir = resolveLogDir()
        Files.createDirectories(logDir)

        val rootLogger = Logger.getLogger("")
        rootLogger.handlers.forEach(rootLogger::removeHandler)

        val fileHandler = FileHandler(logDir.resolve(LOG_FILE_NAME).toString(), true).apply {
            formatter = SimpleFormatter()
            level = Level.ALL
        }

        rootLogger.addHandler(fileHandler)
        rootLogger.level = Level.INFO

        Logger.getLogger(AppLogging::class.java.name).info("Logging initialized at ${logDir.toAbsolutePath()}")
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.getLogger("uncaught").log(
                Level.SEVERE,
                "Unhandled exception on thread ${thread.name}",
                throwable
            )
        }

        initialized = true
    }

    private fun resolveLogDir(): Path {
        val configured = System.getProperty(LOG_DIR_PROPERTY)?.trim()
        if (!configured.isNullOrEmpty()) {
            return Paths.get(configured)
        }
        return Paths.get("logs")
    }
}
