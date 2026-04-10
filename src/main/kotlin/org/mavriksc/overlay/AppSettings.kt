package org.mavriksc.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Color
import java.awt.Point
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
data class AppSettings(
    val features: FeatureSettings = FeatureSettings(),
    val timing: TimingSettings = TimingSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val calibration: CalibrationSettings = CalibrationSettings()
) {
    fun resetCalibration(): AppSettings = copy(calibration = CalibrationSettings())
}

@Serializable
data class FeatureSettings(
    val spellPacingEnabled: Boolean = true,
    val minimapReminderEnabled: Boolean = true,
    val dodgeDirectionEnabled: Boolean = true,
    val showOnlyWhileForeground: Boolean = true,
    val overrideExeNameEnabled: Boolean = false,
    val overrideExeName: String = ""
)

@Serializable
data class TimingSettings(
    val dodgeCueIntervalMs: Int = 1_000,
    val minimapReminderIntervalMs: Int = 5_000,
    val minimapFlashDurationMs: Int = 250
)

@Serializable
data class AppearanceSettings(
    val mapFlashColorArgb: Int = Color.WHITE.rgb,
    val dodgeCueColorArgb: Int = Color(0x3D, 0xE0, 0x7A).rgb,
    val panelDensity: PanelDensity = PanelDensity.COMPACT
)

@Serializable
data class CalibrationSettings(
    val spellHorizontalOffsetAdjustPx: Int = 0,
    val spellBottomOffsetAdjustPx: Int = 0,
    val spellWidthScaleAdjustPercent: Int = 0,
    val spellHeightScaleAdjustPercent: Int = 0,
    val spellSpacingScaleAdjustPercent: Int = 0,
    val dodgeInsetAdjustPx: Int = 0,
    val minimapPaddingAdjustPx: Int = 0,
    val minimapOffsetAdjustX: Int = 0,
    val minimapOffsetAdjustY: Int = 0
)

@Serializable
enum class PanelDensity {
    COMPACT,
    COMFY
}

private const val DEFAULT_SPELL_HORIZONTAL_OFFSET_PX = -14
private const val DEFAULT_SPELL_BOTTOM_OFFSET_PX = -10
const val DEFAULT_GAME_EXECUTABLE_NAME = "League of Legends.exe"

class AppSettingsStore(
    private val settingsPath: Path = defaultSettingsPath()
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        persist(updated)
    }

    fun resetAll() {
        _settings.value = AppSettings()
        persist(_settings.value)
    }

    fun resetCalibration() {
        update { it.resetCalibration() }
    }

    private fun load(): AppSettings {
        if (!Files.isRegularFile(settingsPath)) return AppSettings()
        return try {
            json.decodeFromString<AppSettings>(Files.readString(settingsPath))
        } catch (_: Exception) {
            AppSettings()
        }
    }

    private fun persist(settings: AppSettings) {
        try {
            Files.createDirectories(settingsPath.parent)
            Files.writeString(settingsPath, json.encodeToString(settings))
        } catch (_: Exception) {
        }
    }

    companion object {
        private fun defaultSettingsPath(): Path {
            val appData = System.getenv("APPDATA")
            return if (!appData.isNullOrBlank()) {
                Paths.get(appData, "Overlay", "app-settings.json")
            } else {
                Paths.get(System.getProperty("user.home"), ".overlay", "app-settings.json")
            }
        }
    }
}

fun AppSettings.toOverlayConfig(persisted: PersistedGameSettings?): OverlayConfig {
    val minimapOffset = Point(
        (persisted?.minimapOffset?.x ?: 0) + calibration.minimapOffsetAdjustX,
        (persisted?.minimapOffset?.y ?: 0) + calibration.minimapOffsetAdjustY
    )
    val dodgeColor = Color(appearance.dodgeCueColorArgb, true)
    return OverlayConfig(
        enableSpellPacing = features.spellPacingEnabled,
        enableMapLookTimer = features.minimapReminderEnabled,
        enableDodgeDirection = features.dodgeDirectionEnabled,
        mapFlashColor = Color(appearance.mapFlashColorArgb, true),
        northColor = dodgeColor,
        southColor = dodgeColor,
        eastColor = dodgeColor,
        westColor = dodgeColor,
        dodgeTimer = timing.dodgeCueIntervalMs,
        mapTimer = timing.minimapReminderIntervalMs,
        mapFlashTime = timing.minimapFlashDurationMs,
        showOnlyWhenGameForeground = features.showOnlyWhileForeground,
        mapOnLeft = persisted?.mapOnLeft ?: false,
        mapScale = persisted?.minimapScale?.toDouble() ?: 33.0,
        hudScale = persisted?.hudScale?.toDouble() ?: 0.0,
        minimapOffset = minimapOffset,
        minimapPaddingAdjust = calibration.minimapPaddingAdjustPx,
        dodgeInsetAdjust = calibration.dodgeInsetAdjustPx,
        spellHorizontalOffsetAdjust = calibration.spellHorizontalOffsetAdjustPx + DEFAULT_SPELL_HORIZONTAL_OFFSET_PX,
        spellBottomOffsetAdjust = calibration.spellBottomOffsetAdjustPx + DEFAULT_SPELL_BOTTOM_OFFSET_PX,
        spellWidthScaleAdjust = calibration.spellWidthScaleAdjustPercent / 100.0,
        spellHeightScaleAdjust = calibration.spellHeightScaleAdjustPercent / 100.0,
        spellSpacingScaleAdjust = calibration.spellSpacingScaleAdjustPercent / 100.0
    )
}

fun FeatureSettings.effectiveGameExecutableName(): String =
    if (overrideExeNameEnabled) {
        overrideExeName.trim().ifBlank { DEFAULT_GAME_EXECUTABLE_NAME }
    } else {
        DEFAULT_GAME_EXECUTABLE_NAME
    }
