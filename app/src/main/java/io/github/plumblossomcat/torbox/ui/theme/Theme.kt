package io.github.plumblossomcat.torbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.plumblossomcat.torbox.core.model.TransferState

/**
 * "Ink & Signal": warm paper neutrals, a near-black ink, and a single chartreuse signal colour
 * reserved for things that are live or need the eye. Everything else is quiet.
 */
@Immutable
data class TbColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val sunken: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    /** Fill colour for primary actions and live progress. */
    val signal: Color,
    val onSignal: Color,
    /** Signal tuned for text and thin strokes on [background]. */
    val signalInk: Color,
    val seeding: Color,
    val caution: Color,
    val danger: Color,
    val dangerSoft: Color,
    val scrim: Color,
) {
    fun forState(state: TransferState): Color = when (state) {
        TransferState.DOWNLOADING, TransferState.PREPARING -> signalInk
        TransferState.SEEDING -> seeding
        TransferState.READY -> ink
        TransferState.PAUSED -> inkFaint
        TransferState.STALLED, TransferState.QUEUED -> caution
        TransferState.FAILED -> danger
    }

    /** Fill for progress meters, where thin bars need more chroma than text does. */
    fun meterFor(state: TransferState): Color = when (state) {
        TransferState.DOWNLOADING, TransferState.PREPARING -> if (isDark) signal else signalInk
        else -> forState(state)
    }
}

val LightColors = TbColors(
    isDark = false,
    background = Color(0xFFF2F0E9),
    surface = Color(0xFFF8F7F2),
    raised = Color(0xFFFFFFFF),
    sunken = Color(0xFFE8E5DB),
    ink = Color(0xFF16150F),
    inkMuted = Color(0xFF5E5A4F),
    inkFaint = Color(0xFF8C877A),
    hairline = Color(0xFFDDD9CD),
    hairlineStrong = Color(0xFFC7C2B3),
    signal = Color(0xFFCCF547),
    onSignal = Color(0xFF141A00),
    signalInk = Color(0xFF4A6500),
    seeding = Color(0xFF2B57CF),
    caution = Color(0xFFA06A00),
    danger = Color(0xFFC1361A),
    dangerSoft = Color(0xFFF7E1DA),
    scrim = Color(0x6616150F),
)

val DarkColors = TbColors(
    isDark = true,
    background = Color(0xFF0D0D0B),
    surface = Color(0xFF151512),
    raised = Color(0xFF1C1C18),
    sunken = Color(0xFF080807),
    ink = Color(0xFFEEECE4),
    inkMuted = Color(0xFFA19D91),
    inkFaint = Color(0xFF6C685E),
    hairline = Color(0xFF25241F),
    hairlineStrong = Color(0xFF36352E),
    signal = Color(0xFFCCF547),
    onSignal = Color(0xFF141A00),
    signalInk = Color(0xFFCCF547),
    seeding = Color(0xFF86A8FF),
    caution = Color(0xFFF0C04F),
    danger = Color(0xFFFF7A5C),
    dangerSoft = Color(0xFF3A1A12),
    scrim = Color(0x99000000),
)

private val LocalTbColors = staticCompositionLocalOf { LightColors }

object Tb {
    val colors: TbColors
        @Composable get() = LocalTbColors.current
    val type: TbType
        @Composable get() = LocalTbType.current
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private fun TbColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = if (isDark) signal else ink,
        onPrimary = if (isDark) onSignal else background,
        primaryContainer = signal,
        onPrimaryContainer = onSignal,
        secondary = inkMuted,
        onSecondary = background,
        secondaryContainer = sunken,
        onSecondaryContainer = ink,
        tertiary = seeding,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = sunken,
        onSurfaceVariant = inkMuted,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        surfaceBright = raised,
        surfaceDim = sunken,
        inverseSurface = ink,
        inverseOnSurface = background,
        inversePrimary = signal,
        outline = hairlineStrong,
        outlineVariant = hairline,
        error = danger,
        onError = background,
        errorContainer = dangerSoft,
        onErrorContainer = danger,
        scrim = scrim,
    )
}

val TbShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun TorBoxTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val type = tbType(colors)
    CompositionLocalProvider(LocalTbColors provides colors, LocalTbType provides type) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = type.toMaterial(),
            shapes = TbShapes,
            content = content,
        )
    }
}
