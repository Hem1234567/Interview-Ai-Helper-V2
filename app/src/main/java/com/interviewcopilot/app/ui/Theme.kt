package com.interviewcopilot.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE one place where colours are defined. No other file may contain a colour literal
 * (enforced by HardcodedColorTest and by the CI "colours" check).
 */
object Pastel {
    // Light palette: exactly the product spec.
    val Background = Color(0xFFF5F5FA)
    val Surface = Color(0xFFFFFFFF)
    val Primary = Color(0xFFA5B4FC)
    val Secondary = Color(0xFFC4B5FD)
    val Accent = Color(0xFFFBCFE8)
    val Success = Color(0xFFBBF7D0)
    val Warning = Color(0xFFFDE68A)
    val TextPrimary = Color(0xFF1C1C1E)
    val TextSecondary = Color(0xFF8E8E93)

    // Same family, tuned for dark mode.
    val DarkBackground = Color(0xFF111118)
    val DarkSurface = Color(0xFF1C1C26)
    val DarkTextPrimary = Color(0xFFF2F2F7)
    val DarkTextSecondary = Color(0xFF9A9AA6)

    // Supporting tones derived from the palette.
    val Danger = Color(0xFFFCA5A5)
    val Scrim = Color(0x66000000)
    val Hairline = Color(0x1F3C3C43)
    val HairlineDark = Color(0x33EBEBF5)
    val GlassLight = Color(0xCCFFFFFF)
    val GlassDark = Color(0xCC1C1C26)
    val IconBase = Color(0xFF000000)

    /** Window colour for the splash/theme XML. Kept equal to [Background] by a unit test. */
    const val BACKGROUND_HEX = "#FFF5F5FA"
    const val DARK_BACKGROUND_HEX = "#FF111118"
}

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val text: Color,
    val text2: Color,
    val onPastel: Color,
    val hairline: Color,
    val glass: Color,
    val scrim: Color,
    val dark: Boolean,
)

val LightColors = AppColors(
    background = Pastel.Background,
    surface = Pastel.Surface,
    surfaceAlt = Pastel.Background,
    primary = Pastel.Primary,
    secondary = Pastel.Secondary,
    accent = Pastel.Accent,
    success = Pastel.Success,
    warning = Pastel.Warning,
    danger = Pastel.Danger,
    text = Pastel.TextPrimary,
    text2 = Pastel.TextSecondary,
    onPastel = Pastel.TextPrimary,
    hairline = Pastel.Hairline,
    glass = Pastel.GlassLight,
    scrim = Pastel.Scrim,
    dark = false,
)

val DarkColors = AppColors(
    background = Pastel.DarkBackground,
    surface = Pastel.DarkSurface,
    surfaceAlt = Pastel.DarkBackground,
    primary = Pastel.Primary,
    secondary = Pastel.Secondary,
    accent = Pastel.Accent,
    success = Pastel.Success,
    warning = Pastel.Warning,
    danger = Pastel.Danger,
    text = Pastel.DarkTextPrimary,
    text2 = Pastel.DarkTextSecondary,
    onPastel = Pastel.TextPrimary,
    hairline = Pastel.HairlineDark,
    glass = Pastel.GlassDark,
    scrim = Pastel.Scrim,
    dark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** Apple Human Interface Guidelines type scale on the system font. */
object Type {
    private val font = FontFamily.Default
    val largeTitle = TextStyle(fontFamily = font, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    val title1 = TextStyle(fontFamily = font, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val title2 = TextStyle(fontFamily = font, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val title3 = TextStyle(fontFamily = font, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontFamily = font, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = font, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val callout = TextStyle(fontFamily = font, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val subhead = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val footnote = TextStyle(fontFamily = font, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
}

/** Corner radii, all within the 12 to 20 dp range of the design spec. */
object Radius {
    val small = 12.dp
    val medium = 16.dp
    val large = 20.dp
}

@Composable
fun AppTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkColors else LightColors, content = content)
}
