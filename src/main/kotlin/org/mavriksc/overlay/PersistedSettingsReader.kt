package org.mavriksc.overlay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Dimension
import java.awt.Point
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class PersistedDisplaySettings(
    val width: Int?,
    val height: Int?,
    val windowModeRaw: String?,
    val isFullscreen: Boolean?
)

data class PersistedMinimapSettings(
    val positionRaw: String?,
    val size: Dimension?,
    val scale: Float?,
    val offset: Point?
)

data class PersistedGameSettings(
    val sourcePath: Path,
    val display: PersistedDisplaySettings,
    val minimap: PersistedMinimapSettings,
    val hudScale: Float?
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
        val persisted = parseSettings(root, path)
        val gameCfg = readGameCfg(resolveGameCfgPath(path))
        return mergeSettings(persisted, gameCfg)
    }

    private fun parseSettings(root: JsonElement, sourcePath: Path): PersistedGameSettings {
        val values = extractSettingValues(root, TARGET_KEYS)

        val width = values.firstInt(DISPLAY_WIDTH_KEYS)
        val height = values.firstInt(DISPLAY_HEIGHT_KEYS)

        val windowModeRaw = values.firstString(WINDOW_MODE_KEYS)
        val fullscreenFlag = values.firstBoolean(FULLSCREEN_KEYS)
        val fullscreenFromWindowMode = windowModeRaw?.let { mode ->
            when (mode.lowercase()) {
                "fullscreen", "borderless", "true", "1" -> true
                "windowed", "window", "false", "0" -> false
                else -> null
            }
        }

        val mapScale = values.firstFloat(MAP_SCALE_KEYS)
        val mapWidth = values.firstInt(MAP_WIDTH_KEYS)
        val mapHeight = values.firstInt(MAP_HEIGHT_KEYS)
        val mapOffsetX = values.firstInt(MAP_OFFSET_X_KEYS)
        val mapOffsetY = values.firstInt(MAP_OFFSET_Y_KEYS)
        val mapPositionRaw = values.firstString(MAP_POSITION_KEYS)
        val mapSize = if (mapWidth != null && mapHeight != null) Dimension(mapWidth, mapHeight) else null
        val mapOffset = if (mapOffsetX != null && mapOffsetY != null) Point(mapOffsetX, mapOffsetY) else null

        val display = PersistedDisplaySettings(
            width = width,
            height = height,
            windowModeRaw = windowModeRaw,
            isFullscreen = fullscreenFlag ?: fullscreenFromWindowMode
        )
        val minimap = PersistedMinimapSettings(
            positionRaw = mapPositionRaw,
            size = mapSize,
            scale = mapScale,
            offset = mapOffset
        )
        val hudScale = values.firstFloat(HUD_SCALE_KEYS)

        return PersistedGameSettings(
            sourcePath = sourcePath,
            display = display,
            minimap = minimap,
            hudScale = hudScale
        )
    }

    private fun resolveGameCfgPath(persistedSettingsPath: Path): Path? {
        val candidates = mutableListOf<Path>()
        candidates.add(persistedSettingsPath.parent.resolve("Game.cfg"))
        val root = persistedSettingsPath.parent.parent
        if (root != null) {
            candidates.add(root.resolve("Config").resolve("Game.cfg"))
            candidates.add(root.resolve("Game").resolve("Config").resolve("Game.cfg"))
        }
        return candidates.firstOrNull { Files.isRegularFile(it) }
    }

    private fun readGameCfg(path: Path?): GameCfgSettings? {
        if (path == null || !Files.isRegularFile(path)) return null
        val lines = try {
            Files.readAllLines(path)
        } catch (_: Exception) {
            return null
        }

        var section = ""
        val values = mutableMapOf<Pair<String, String>, String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim()
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            values[section to key] = value
        }

        val width = values["General" to "Width"]?.toIntOrNull()
        val height = values["General" to "Height"]?.toIntOrNull()
        val windowModeRaw = values["General" to "WindowMode"]
        val minimapScale = values["HUD" to "MinimapScale"]?.toFloatOrNull()
        val minimapScaleSpectator = values["HUD" to "MinimapScaleSpectator"]?.toFloatOrNull()

        return GameCfgSettings(
            sourcePath = path,
            width = width,
            height = height,
            windowModeRaw = windowModeRaw,
            minimapScale = minimapScale ?: minimapScaleSpectator
        )
    }

    private fun mergeSettings(
        persisted: PersistedGameSettings,
        gameCfg: GameCfgSettings?
    ): PersistedGameSettings {
        if (gameCfg == null) return persisted

        val display = persisted.display.copy(
            width = persisted.display.width ?: gameCfg.width,
            height = persisted.display.height ?: gameCfg.height,
            windowModeRaw = persisted.display.windowModeRaw ?: gameCfg.windowModeRaw,
            isFullscreen = persisted.display.isFullscreen ?: windowModeToFullscreen(gameCfg.windowModeRaw)
        )

        val minimap = persisted.minimap.copy(
            scale = persisted.minimap.scale ?: gameCfg.minimapScale
        )

        return persisted.copy(display = display, minimap = minimap)
    }

    private fun windowModeToFullscreen(windowModeRaw: String?): Boolean? {
        val modeInt = windowModeRaw?.toIntOrNull()
        return when (modeInt) {
            0 -> false
            1 -> false
            2 -> true
            else -> null
        }
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

    private fun extractSettingValues(root: JsonElement, targetKeys: Set<String>): Map<String, JsonPrimitive> {
        val found = mutableMapOf<String, JsonPrimitive>()

        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val name = element["name"]?.jsonPrimitive?.contentOrNull
                    if (name != null && name in targetKeys) {
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

    private fun Map<String, JsonPrimitive>.firstString(keys: List<String>): String? =
        keys.firstNotNullOfOrNull { this[it]?.contentOrNull }

    private fun Map<String, JsonPrimitive>.firstInt(keys: List<String>): Int? =
        keys.firstNotNullOfOrNull { this[it]?.intOrNull ?: this[it]?.contentOrNull?.toIntOrNull() }

    private fun Map<String, JsonPrimitive>.firstFloat(keys: List<String>): Float? =
        keys.firstNotNullOfOrNull { this[it]?.floatOrNull ?: this[it]?.contentOrNull?.toFloatOrNull() }

    private fun Map<String, JsonPrimitive>.firstBoolean(keys: List<String>): Boolean? =
        keys.firstNotNullOfOrNull { key ->
            val value = this[key] ?: return@firstNotNullOfOrNull null
            value.booleanOrNull ?: when (value.contentOrNull?.lowercase()) {
                "1", "true", "yes" -> true
                "0", "false", "no" -> false
                else -> null
            }
        }

    companion object {
        private const val SETTINGS_FILE = "PersistedSettings.json"

        private val DISPLAY_WIDTH_KEYS = listOf(
            "Width",
            "ResolutionWidth",
            "ScreenWidth",
            "WindowWidth"
        )

        private val DISPLAY_HEIGHT_KEYS = listOf(
            "Height",
            "ResolutionHeight",
            "ScreenHeight",
            "WindowHeight"
        )

        private val WINDOW_MODE_KEYS = listOf(
            "WindowMode",
            "DisplayMode",
            "FullscreenMode"
        )

        private val FULLSCREEN_KEYS = listOf(
            "Fullscreen",
            "FullScreen"
        )

        private val MAP_SCALE_KEYS = listOf(
            "MinimapScale",
            "MinimapScaleSpectator",
            "MapScale"
        )

        private val MAP_WIDTH_KEYS = listOf(
            "MinimapWidth",
            "MapWidth"
        )

        private val MAP_HEIGHT_KEYS = listOf(
            "MinimapHeight",
            "MapHeight"
        )

        private val MAP_OFFSET_X_KEYS = listOf(
            "MinimapOffsetX",
            "MapOffsetX"
        )

        private val MAP_OFFSET_Y_KEYS = listOf(
            "MinimapOffsetY",
            "MapOffsetY"
        )

        private val MAP_POSITION_KEYS = listOf(
            "MinimapPosition",
            "MapPosition"
        )

        private val HUD_SCALE_KEYS = listOf(
            "GlobalScale",
            "HUDScale",
            "HudScale"
        )

        private val TARGET_KEYS = (
            DISPLAY_WIDTH_KEYS +
                DISPLAY_HEIGHT_KEYS +
                WINDOW_MODE_KEYS +
                FULLSCREEN_KEYS +
                MAP_SCALE_KEYS +
                MAP_WIDTH_KEYS +
                MAP_HEIGHT_KEYS +
                MAP_OFFSET_X_KEYS +
                MAP_OFFSET_Y_KEYS +
                MAP_POSITION_KEYS +
                HUD_SCALE_KEYS
            ).toSet()
    }
}

private data class GameCfgSettings(
    val sourcePath: Path,
    val width: Int?,
    val height: Int?,
    val windowModeRaw: String?,
    val minimapScale: Float?
)
 fun main() {
     println(PersistedSettingsReader().read())
 }