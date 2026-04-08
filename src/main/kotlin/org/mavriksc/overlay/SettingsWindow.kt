package org.mavriksc.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import java.awt.Rectangle
import kotlin.math.roundToInt

private val PanelBg = Color(0xFF10161D)
private val CardBg = Color(0xFF171F29)
private val CardBorder = Color(0xFF293545)
private val Accent = Color(0xFF4FD1A5)
private val AccentMuted = Color(0xFF294C45)
private val TextPrimary = Color(0xFFF3F7FB)
private val TextSecondary = Color(0xFF93A2B6)
private val Danger = Color(0xFFFF6B6B)
private val WindowBg = Color(0xE610161D)

@Composable
fun WindowScope.SettingsWindow(
    controller: MainWindow,
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onQuit: () -> Unit
) {
    val appSettings by controller.appSettings.collectAsState()
    val persistedSettings by controller.persistedGameSettings.collectAsState()
    val gameBounds by controller.currentGameBounds.collectAsState()
    val gameState by controller.currentGameState.collectAsState()
    val isForeground by controller.isGameForeground.collectAsState()
    val scrollState = rememberScrollState()
    val compact = appSettings.appearance.panelDensity == PanelDensity.COMPACT
    val outerPadding = if (compact) 20.dp else 28.dp
    val sectionGap = if (compact) 16.dp else 22.dp

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(WindowBg)
                .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0C1117), Color(0xFF111823), Color(0xFF0B1017))
                        )
                    )
            ) {
                WindowDraggableArea {
                    TitleBar(
                        isMaximized = isMaximized,
                        onMinimize = onMinimize,
                        onToggleMaximize = onToggleMaximize,
                        onQuit = onQuit
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = outerPadding, end = outerPadding, bottom = outerPadding)
                        .verticalScroll(scrollState)
                ) {
                    Text("Overlay Control", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Compose settings panel with live calibration controls, previews, and runtime diagnostics.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(if (compact) 18.dp else 24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(sectionGap), modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(sectionGap), modifier = Modifier.weight(1f)) {
                            SectionCard("Features", "Runtime visibility and overlay systems") {
                                OverlayToggle(
                                    label = "Spell pacing",
                                    description = "Bottom-center pacing bars for rotation pressure.",
                                    checked = appSettings.features.spellPacingEnabled,
                                    onCheckedChange = { controller.updateFeatures { f -> f.copy(spellPacingEnabled = it) } }
                                )
                                OverlayToggle(
                                    label = "Minimap reminder",
                                    description = "Flashes the minimap area on a repeating timer.",
                                    checked = appSettings.features.minimapReminderEnabled,
                                    onCheckedChange = { controller.updateFeatures { f -> f.copy(minimapReminderEnabled = it) } }
                                )
                                OverlayToggle(
                                    label = "Dodge direction",
                                    description = "Shows randomized edge cues around the game window.",
                                    checked = appSettings.features.dodgeDirectionEnabled,
                                    onCheckedChange = { controller.updateFeatures { f -> f.copy(dodgeDirectionEnabled = it) } }
                                )
                                OverlayToggle(
                                    label = "Show only while foreground",
                                    description = "Keep enabled to prefer active-game sessions over strict hwnd focus.",
                                    checked = appSettings.features.showOnlyWhileForeground,
                                    onCheckedChange = { controller.updateFeatures { f -> f.copy(showOnlyWhileForeground = it) } }
                                )
                            }

                            SectionCard("Timing", "Tune reminder cadence and preview responsiveness") {
                                IntSliderRow(
                                    label = "Dodge interval",
                                    value = appSettings.timing.dodgeCueIntervalMs,
                                    range = 500..4000,
                                    step = 100,
                                    suffix = "ms",
                                    onValueChange = { controller.updateTiming { t -> t.copy(dodgeCueIntervalMs = it) } }
                                )
                                IntSliderRow(
                                    label = "Minimap interval",
                                    value = appSettings.timing.minimapReminderIntervalMs,
                                    range = 1000..15000,
                                    step = 250,
                                    suffix = "ms",
                                    onValueChange = { controller.updateTiming { t -> t.copy(minimapReminderIntervalMs = it) } }
                                )
                                IntSliderRow(
                                    label = "Flash duration",
                                    value = appSettings.timing.minimapFlashDurationMs,
                                    range = 100..1500,
                                    step = 50,
                                    suffix = "ms",
                                    onValueChange = { controller.updateTiming { t -> t.copy(minimapFlashDurationMs = it) } }
                                )
                            }

                            SectionCard("Appearance", "Color, density, and panel feel") {
                                Text("Map flash", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                                ColorPaletteRow(
                                    selectedArgb = appSettings.appearance.mapFlashColorArgb,
                                    onSelect = { controller.updateAppearance { a -> a.copy(mapFlashColorArgb = it) } }
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Dodge cue", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                                ColorPaletteRow(
                                    selectedArgb = appSettings.appearance.dodgeCueColorArgb,
                                    onSelect = { controller.updateAppearance { a -> a.copy(dodgeCueColorArgb = it) } }
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Panel density", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                                SegmentedControl(
                                    options = listOf("Compact", "Comfort"),
                                    selectedIndex = if (appSettings.appearance.panelDensity == PanelDensity.COMPACT) 0 else 1,
                                    onSelect = {
                                        controller.updateAppearance { a ->
                                            a.copy(panelDensity = if (it == 0) PanelDensity.COMPACT else PanelDensity.COMFY)
                                        }
                                    }
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(sectionGap), modifier = Modifier.weight(1f)) {
                            SectionCard("Calibration", "Fine trim values layered on top of detected League settings") {
                                IntSliderRow(
                                    label = "Spell horizontal offset",
                                    value = appSettings.calibration.spellHorizontalOffsetAdjustPx,
                                    range = -80..80,
                                    step = 2,
                                    suffix = "px",
                                    onValueChange = { controller.updateCalibration { c -> c.copy(spellHorizontalOffsetAdjustPx = it) } }
                                )
                                IntSliderRow(
                                    label = "Spell bottom offset",
                                    value = appSettings.calibration.spellBottomOffsetAdjustPx,
                                    range = -120..120,
                                    step = 2,
                                    suffix = "px",
                                    onValueChange = { controller.updateCalibration { c -> c.copy(spellBottomOffsetAdjustPx = it) } }
                                )
                                IntSliderRow(
                                    label = "Spell width trim",
                                    value = appSettings.calibration.spellWidthScaleAdjustPercent,
                                    range = -50..100,
                                    step = 5,
                                    suffix = "%",
                                    onValueChange = { controller.updateCalibration { c -> c.copy(spellWidthScaleAdjustPercent = it) } }
                                )
                                IntSliderRow(
                                    label = "Spell height trim",
                                    value = appSettings.calibration.spellHeightScaleAdjustPercent,
                                    range = -50..100,
                                    step = 5,
                                    suffix = "%",
                                    onValueChange = { controller.updateCalibration { c -> c.copy(spellHeightScaleAdjustPercent = it) } }
                                )
                                IntSliderRow(
                                    label = "Spell spacing trim",
                                    value = appSettings.calibration.spellSpacingScaleAdjustPercent,
                                    range = -50..100,
                                    step = 5,
                                    suffix = "%",
                                    onValueChange = { controller.updateCalibration { c -> c.copy(spellSpacingScaleAdjustPercent = it) } }
                                )
                                IntSliderRow(
                                    label = "Dodge inset trim",
                                    value = appSettings.calibration.dodgeInsetAdjustPx,
                                    range = -40..120,
                                    step = 4,
                                    suffix = "px",
                                    onValueChange = { controller.updateCalibration { c -> c.copy(dodgeInsetAdjustPx = it) } }
                                )
                                IntSliderRow(
                                    label = "Minimap padding trim",
                                    value = appSettings.calibration.minimapPaddingAdjustPx,
                                    range = -60..120,
                                    step = 4,
                                    suffix = "px",
                                    onValueChange = { controller.updateCalibration { c -> c.copy(minimapPaddingAdjustPx = it) } }
                                )
                                IntSliderRow(
                                    label = "Minimap offset X",
                                    value = appSettings.calibration.minimapOffsetAdjustX,
                                    range = -120..120,
                                    step = 4,
                                    suffix = "px",
                                    onValueChange = { controller.updateCalibration { c -> c.copy(minimapOffsetAdjustX = it) } }
                                )
                                IntSliderRow(
                                    label = "Minimap offset Y",
                                    value = appSettings.calibration.minimapOffsetAdjustY,
                                    range = -120..120,
                                    step = 4,
                                    suffix = "px",
                                    onValueChange = { controller.updateCalibration { c -> c.copy(minimapOffsetAdjustY = it) } }
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ActionButton("Reset Calibration", onClick = controller::resetCalibration)
                                    ActionButton("Reset All", onClick = controller::resetAllSettings, background = Danger)
                                }
                            }

                            SectionCard("Live Preview", "Preview card uses current settings and detected game bounds when available") {
                                PreviewCard(
                                    settings = appSettings,
                                    persistedSettings = persistedSettings,
                                    gameBounds = gameBounds
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ActionButton("Flash Minimap", onClick = controller::previewMapFlash)
                                    ActionButton("Flip Dodge Cue", onClick = controller::previewDodgeCue)
                                }
                            }

                            SectionCard("Diagnostics", "Read-only values from runtime detection and League persisted settings") {
                                DiagnosticRow("Game state", gameState.name)
                                DiagnosticRow("League in foreground", if (isForeground) "Yes" else "No")
                                DiagnosticRow("Detected bounds", gameBounds?.let { "${it.width}x${it.height} @ ${it.x}, ${it.y}" } ?: "Not detected")
                                DiagnosticRow("Persisted HUD scale", persistedSettings?.hudScale?.roundToInt()?.toString()?.plus("%") ?: "Unavailable")
                                DiagnosticRow("Persisted minimap scale", persistedSettings?.minimapScale?.roundToInt()?.toString()?.plus("%") ?: "Unavailable")
                                DiagnosticRow("Persisted minimap side", persistedSettings?.let { if (it.mapOnLeft) "Left" else "Right" } ?: "Unavailable")
                                DiagnosticRow(
                                    "Persisted minimap offset",
                                    persistedSettings?.minimapOffset?.let { "${it.x}, ${it.y}" } ?: "Unavailable"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleBar(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onQuit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Overlay Settings", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Custom shell, tray-aware minimize, replay-tolerant runtime controls.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TitleBarButton("—", onClick = onMinimize)
            TitleBarButton(if (isMaximized) "❐" else "□", onClick = onToggleMaximize)
            TitleBarButton("×", onClick = onQuit, background = Danger)
        }
    }
}

@Composable
private fun TitleBarButton(label: String, onClick: () -> Unit, background: Color = CardBorder) {
    val normalizedLabel = label.trim()
    val isMinimize = normalizedLabel == "-" || normalizedLabel == "—" || normalizedLabel == "â€”"
    val isQuit = background == Danger
    val buttonWidth = when {
        isMinimize -> 34.dp
        else -> 42.dp
    }
    val buttonHeight = when {
        isMinimize -> 28.dp
        else -> 34.dp
    }
    val cornerRadius = when {
        isMinimize -> 8.dp
        else -> 9.dp
    }
    val textStyle = when {
        isMinimize -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }
    val textOffsetY = when {
        isMinimize -> 0.dp
        isQuit -> (-1).dp
        else -> (-2).dp
    }

    Box(
        modifier = Modifier
            .size(width = buttonWidth, height = buttonHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = TextPrimary,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = textOffsetY)
        )
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 420.dp, max = 560.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun OverlayToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBg)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 30.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) AccentMuted else Color(0xFF23303F))
                .padding(4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = if (checked) Accent else Color(0xFF4B5B72),
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                )
                drawCircle(
                    color = Color.White,
                    radius = size.height / 2.4f,
                    center = Offset(
                        if (checked) size.width - size.height / 2.1f else size.height / 2.1f,
                        size.height / 2
                    )
                )
            }
        }
    }
}

@Composable
private fun IntSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBg)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StepButton("-") { onValueChange((value - step).coerceIn(range.first, range.last)) }
            Spacer(Modifier.width(8.dp))
            ValueBadge("$value$suffix")
            Spacer(Modifier.width(8.dp))
            StepButton("+") { onValueChange((value + step).coerceIn(range.first, range.last)) }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color(0xFF243344)
            )
        )
    }
}

@Composable
private fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PanelBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) AccentMuted else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(label, color = if (selected) Accent else TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ColorPaletteRow(selectedArgb: Int, onSelect: (Int) -> Unit) {
    val palette = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFF6B6B),
        Color(0xFFFFC857),
        Color(0xFF4FD1A5),
        Color(0xFF5AC8FA),
        Color(0xFFC792EA),
        Color(0xFFFF8C42),
        Color(0xFF90A4AE)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        palette.forEach { color ->
            val selected = color.toArgb() == selectedArgb
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
                    .border(if (selected) 3.dp else 1.dp, if (selected) Accent else CardBorder, RoundedCornerShape(999.dp))
                    .clickable { onSelect(color.toArgb()) }
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PanelBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, background: Color = Accent) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color(0xFF071015), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(CardBorder)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ValueBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CardBorder)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = TextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PreviewCard(settings: AppSettings, persistedSettings: PersistedGameSettings?, gameBounds: Rectangle?) {
    val previewBounds = Rectangle(0, 0, gameBounds?.width ?: 1600, gameBounds?.height ?: 900)
    val overlayConfig = settings.toOverlayConfig(persistedSettings)
    val layout = OverlayLayoutMetrics.from(
        windowBounds = previewBounds,
        hudScale = overlayConfig.hudScale,
        minimapScale = overlayConfig.mapScale,
        mapOnLeft = overlayConfig.mapOnLeft,
        minimapOffset = overlayConfig.minimapOffset,
        minimapPaddingAdjust = overlayConfig.minimapPaddingAdjust,
        dodgeInsetAdjust = overlayConfig.dodgeInsetAdjust,
        spellHorizontalOffsetAdjust = overlayConfig.spellHorizontalOffsetAdjust,
        spellBottomOffsetAdjust = overlayConfig.spellBottomOffsetAdjust,
        spellWidthScaleAdjust = overlayConfig.spellWidthScaleAdjust,
        spellHeightScaleAdjust = overlayConfig.spellHeightScaleAdjust,
        spellSpacingScaleAdjust = overlayConfig.spellSpacingScaleAdjust
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PanelBg)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val scaleX = size.width / previewBounds.width
            val scaleY = size.height / previewBounds.height

            fun sx(x: Int) = x * scaleX
            fun sy(y: Int) = y * scaleY
            fun sw(w: Int) = w * scaleX
            fun sh(h: Int) = h * scaleY

            drawRoundRect(
                color = Color(0xFF0E141B),
                cornerRadius = CornerRadius(24f, 24f),
                size = size
            )

            if (settings.features.minimapReminderEnabled) {
                drawRoundRect(
                    color = Color(settings.appearance.mapFlashColorArgb).copy(alpha = 0.8f),
                    topLeft = Offset(sx(layout.mapRect.x), sy(layout.mapRect.y)),
                    size = Size(sw(layout.mapRect.width), sh(layout.mapRect.height)),
                    cornerRadius = CornerRadius(14f, 14f)
                )
            }

            if (settings.features.dodgeDirectionEnabled) {
                val dodgeColor = Color(settings.appearance.dodgeCueColorArgb)
                val insetX = sx(layout.dodgeInset)
                val insetY = sy(layout.dodgeInset)
                drawLine(dodgeColor, Offset(insetX, insetY), Offset(size.width - insetX, insetY), 4f, StrokeCap.Round)
                drawLine(dodgeColor, Offset(insetX, insetY), Offset(insetX, size.height - insetY), 4f, StrokeCap.Round)
                drawLine(
                    dodgeColor.copy(alpha = 0.25f),
                    Offset(insetX, size.height - insetY),
                    Offset(size.width - insetX, size.height - insetY),
                    4f,
                    StrokeCap.Round
                )
                drawLine(
                    dodgeColor.copy(alpha = 0.25f),
                    Offset(size.width - insetX, insetY),
                    Offset(size.width - insetX, size.height - insetY),
                    4f,
                    StrokeCap.Round
                )
            }

            if (settings.features.spellPacingEnabled) {
                val colors = listOf(Color(0xFF4FD1A5), Color(0xFFFFC857), Color(0xFFFF6B6B), Color(0xFF4FD1A5))
                layout.spellTopLefts.forEachIndexed { index, point ->
                    drawRoundRect(
                        color = colors[index],
                        topLeft = Offset(sx(point.x), sy(point.y)),
                        size = Size(sw(layout.spellSize.first), sh(layout.spellSize.second)),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }

            drawRoundRect(
                color = CardBorder,
                cornerRadius = CornerRadius(24f, 24f),
                size = size,
                style = Stroke(width = 2f)
            )
        }
    }
}
