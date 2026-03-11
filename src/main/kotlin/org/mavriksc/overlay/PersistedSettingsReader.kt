package org.mavriksc.overlay

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.awt.Point
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds

data class PersistedGameSettings(
    val mapOnLeft: Boolean,
    val minimapScale: Float,
    val minimapOffset: Point,
    val hudScale: Float
)

class PersistedSettingsReader(
    private val settingsPath: Path? = null,
    private val installRoot: Path? = null
) {
    fun resolveSettingsPath(): Path? {
        settingsPath?.let { if (Files.isRegularFile(it)) return it }
        val roots = mutableListOf<Path>()
        installRoot?.let { roots.add(it) }
        roots.addAll(candidateInstallRoots())
        return roots
            .distinct()
            .asSequence()
            .flatMap { root ->
                sequenceOf(
                    root.resolve("Config").resolve(SETTINGS_FILE),
                    root.resolve("Game").resolve("Config").resolve(SETTINGS_FILE)
                )
            }
            .firstOrNull { Files.isRegularFile(it) }
    }

    fun read(): PersistedGameSettings? {
        val path = resolveSettingsPath() ?: return null
        val text = try {
            Files.readString(path)
        } catch (_: Exception) {
            return null
        }
        val root = try {
            Json.parseToJsonElement(text)
        } catch (_: Exception) {
            return null
        }
        val persisted = parseSettings(root)
        return persisted
    }

    fun settingsFlow(): Flow<PersistedGameSettings> = callbackFlow {
        val settingsPath = resolveSettingsPath()
        if (settingsPath == null) {
            close()
            return@callbackFlow
        }

        val initial = read()
        if (initial != null) {
            trySend(initial)
        }

        val watchService = FileSystems.getDefault().newWatchService()
        val watchedFiles = mutableSetOf(settingsPath.fileName.toString())
        val watchedDirs = mutableSetOf<Path>()

        fun registerDir(dir: Path) {
            if (watchedDirs.add(dir)) {
                dir.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )
            }
        }

        registerDir(settingsPath.parent)

        val watchJob = launch {
            while (true) {
                val key = try {
                    watchService.take()
                } catch (_: Exception) {
                    break
                }
                val hasRelevantChange = key.pollEvents().any { event ->
                    val context = event.context() as? Path ?: return@any false
                    watchedFiles.contains(context.fileName.toString())
                }
                if (hasRelevantChange) {
                    val updated = read()
                    println("updated Settings $updated")
                    if (updated != null) {
                        trySend(updated)
                    }
                }
                if (!key.reset()) break
            }
        }

        awaitClose {
            watchJob.cancel()
            try {
                watchService.close()
            } catch (_: Exception) {
            }
        }
    }
        .flowOn(Dispatchers.IO)

    private fun parseSettings(root: JsonElement): PersistedGameSettings {
        val values = extractSettingValues(root)

        val mapOnLeft = values.firstBoolean(MAP_ON_LEFT_KEYS) ?: false
        val mapScale = values.firstFloat(MAP_SCALE_KEYS)?.div(3.0f)?.times(100) ?: 33.0f
        val mapOffsetX = values.firstInt(MAP_OFFSET_X_KEYS)
        val mapOffsetY = values.firstInt(MAP_OFFSET_Y_KEYS)
        val mapOffset = if (mapOffsetX != null && mapOffsetY != null) Point(mapOffsetX, mapOffsetY) else Point(0, 0)

        val hudScale = values.firstFloat(HUD_SCALE_KEYS)?.times(100) ?: 0.0f

        return PersistedGameSettings(
            mapOnLeft = mapOnLeft,
            minimapScale = mapScale,
            minimapOffset = mapOffset,
            hudScale = hudScale
        )
    }

    private fun candidateInstallRoots(): List<Path> {
        val roots = mutableListOf<Path>()

        val riotClientInstall = System.getenv("RiotClientInstallFolder")
        if (!riotClientInstall.isNullOrBlank()) {
            roots.add(Paths.get(riotClientInstall).resolve("League of Legends"))
        }

        val systemDrive = System.getenv("SystemDrive")
        if (!systemDrive.isNullOrBlank()) {
            roots.add(Paths.get("$systemDrive\\", "Riot Games", "League of Legends"))
        }

        val programFiles = System.getenv("ProgramFiles")
        if (!programFiles.isNullOrBlank()) {
            roots.add(Paths.get(programFiles, "Riot Games", "League of Legends"))
        }

        val programFilesX86 = System.getenv("ProgramFiles(x86)")
        if (!programFilesX86.isNullOrBlank()) {
            roots.add(Paths.get(programFilesX86, "Riot Games", "League of Legends"))
        }

        val localAppData = System.getenv("LOCALAPPDATA")
        if (!localAppData.isNullOrBlank()) {
            roots.add(Paths.get(localAppData, "Riot Games", "League of Legends"))
        }

        return roots
    }

    private fun extractSettingValues(root: JsonElement): Map<String, JsonPrimitive> {
        val found = mutableMapOf<String, JsonPrimitive>()

        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val name = element["name"]?.jsonPrimitive?.contentOrNull
                    if (name != null && name in TARGET_KEYS) {
                        val value = element["value"]?.jsonPrimitive ?: element["Value"]?.jsonPrimitive
                        if (value != null) {
                            found[name] = value
                        }
                    }
                    element.values.forEach { visit(it) }
                }

                is JsonArray -> element.forEach { visit(it) }
                else -> {}
            }
        }

        visit(root)
        return found
    }

    private fun Map<String, JsonPrimitive>.firstInt(keys: List<String>): Int? =
        keys.firstNotNullOfOrNull { this[it]?.intOrNull ?: this[it]?.contentOrNull?.toIntOrNull() }

    private fun Map<String, JsonPrimitive>.firstFloat(keys: List<String>): Float? =
        keys.firstNotNullOfOrNull { this[it]?.floatOrNull ?: this[it]?.contentOrNull?.toFloatOrNull() }

    private fun Map<String, JsonPrimitive>.firstBoolean(keys: List<String>): Boolean? =
        keys.firstNotNullOfOrNull { key ->
            val value = this[key] ?: return@firstNotNullOfOrNull null
            when (value.contentOrNull?.lowercase()) {
                "1", "true", "yes" -> true
                "0", "false", "no" -> false
                else -> null
            }
        }

    companion object {
        private const val SETTINGS_FILE = "PersistedSettings.json"

        private val MAP_SCALE_KEYS = listOf(
            "MinimapScale",
            "MinimapScaleSpectator",
            "MapScale"
        )

        private val MAP_OFFSET_X_KEYS = listOf(
            "MinimapOffsetX",
            "MapOffsetX"
        )

        private val MAP_OFFSET_Y_KEYS = listOf(
            "MinimapOffsetY",
            "MapOffsetY"
        )

        private val MAP_ON_LEFT_KEYS = listOf(
            "FlipMiniMap",
            "FlipMinimap"
        )

        private val HUD_SCALE_KEYS = listOf(
            "GlobalScale",
            "HUDScale",
            "HudScale"
        )

        private val TARGET_KEYS = (
                MAP_ON_LEFT_KEYS +
                        MAP_SCALE_KEYS +
                        MAP_OFFSET_X_KEYS +
                        MAP_OFFSET_Y_KEYS +
                        HUD_SCALE_KEYS
                ).toSet()
    }
}

fun main() {
    println(PersistedSettingsReader().read())
}
