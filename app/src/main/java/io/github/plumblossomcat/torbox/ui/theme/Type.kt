package io.github.plumblossomcat.torbox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.plumblossomcat.torbox.R

val Geist = FontFamily(
    Font(R.font.geist_400, FontWeight.Normal),
    Font(R.font.geist_500, FontWeight.Medium),
    Font(R.font.geist_600, FontWeight.SemiBold),
    Font(R.font.geist_700, FontWeight.Bold),
)

val GeistMono = FontFamily(
    Font(R.font.geistmono_400, FontWeight.Normal),
    Font(R.font.geistmono_500, FontWeight.Medium),
    Font(R.font.geistmono_600, FontWeight.SemiBold),
)

val InstrumentSerif = FontFamily(
    Font(R.font.instrumentserif_regular, FontWeight.Normal),
    Font(R.font.instrumentserif_italic, FontWeight.Normal, FontStyle.Italic),
)

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * Three voices: an editorial serif for titles and moments, a neutral grotesk for reading,
 * and a mono with tabular figures for every number, size, hash and timestamp.
 */
@Immutable
data class TbType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val eyebrow: TextStyle,
    val mono: TextStyle,
    val monoSmall: TextStyle,
    val figure: TextStyle,
)

internal val LocalTbType = staticCompositionLocalOf { tbType(LightColors) }

internal fun tbType(colors: TbColors): TbType {
    val base = TextStyle(color = colors.ink, lineHeightStyle = Trim)
    return TbType(
        display = base.copy(fontFamily = InstrumentSerif, fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-0.01).em),
        headline = base.copy(fontFamily = InstrumentSerif, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.005).em),
        title = base.copy(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em),
        subtitle = base.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.005).em),
        body = base.copy(fontFamily = Geist, fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = base.copy(fontFamily = Geist, fontSize = 13.sp, lineHeight = 18.sp, color = colors.inkMuted),
        label = base.copy(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
        button = base.copy(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = (-0.005).em),
        eyebrow = base.copy(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em, color = colors.inkMuted),
        mono = base.copy(fontFamily = GeistMono, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum"),
        monoSmall = base.copy(fontFamily = GeistMono, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum", color = colors.inkMuted),
        figure = base.copy(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum"),
    )
}

internal fun TbType.toMaterial() = Typography(
    displayLarge = display,
    displayMedium = display.copy(fontSize = 40.sp, lineHeight = 44.sp),
    displaySmall = headline,
    headlineLarge = headline,
    headlineMedium = headline.copy(fontSize = 30.sp, lineHeight = 34.sp),
    headlineSmall = headline.copy(fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = title.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = title,
    titleSmall = subtitle,
    bodyLarge = body,
    bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = bodySmall,
    labelLarge = button,
    labelMedium = label,
    labelSmall = label.copy(fontSize = 11.sp),
)
