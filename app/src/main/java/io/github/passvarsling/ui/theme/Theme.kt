package io.github.passvarsling.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF2E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF456179),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001E31),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FBF8),
    onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF4FBF8),
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFECF2EF),
    inversePrimary = Color(0xFF82D5C8),
    surfaceDim = Color(0xFFD5DBD9),
    surfaceBright = Color(0xFFF4FBF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9EF2E4),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFADCAE5),
    onTertiary = Color(0xFF153349),
    tertiaryContainer = Color(0xFF2D4960),
    onTertiaryContainer = Color(0xFFCCE5FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    inverseSurface = Color(0xFFDDE4E1),
    inverseOnSurface = Color(0xFF2B3230),
    inversePrimary = Color(0xFF006A60),
    surfaceDim = Color(0xFF0E1513),
    surfaceBright = Color(0xFF343B39),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF2F3635),
)

private val Base = Typography()

/** Bolder headings and labels, in the spirit of Material 3 Expressive. */
private val AppTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Strong colours for small marks (map dots), the same in light and dark mode, like a traffic light. */
object WaitColors {
    val Short = Color(0xFF2E7D32)
    val Medium = Color(0xFFF9A825)
    val Long = Color(0xFFEF6C00)
    val VeryLong = Color(0xFFC62828)
    val NoneFree = Color(0xFF757575)
}

/** Softer container and content colours for wait-time badges. */
@Immutable
data class WaitTone(val container: Color, val content: Color)

@Immutable
data class WaitPalette(
    val short: WaitTone,
    val medium: WaitTone,
    val long: WaitTone,
    val veryLong: WaitTone,
    val noneFree: WaitTone,
)

private val LightWait = WaitPalette(
    short = WaitTone(Color(0xFFCDEFD1), Color(0xFF0F5223)),
    medium = WaitTone(Color(0xFFFFE9A8), Color(0xFF5C4300)),
    long = WaitTone(Color(0xFFFFD8B5), Color(0xFF7A3300)),
    veryLong = WaitTone(Color(0xFFFFD6D3), Color(0xFF8C1D18)),
    noneFree = WaitTone(Color(0xFFE2E4E3), Color(0xFF4A4F4D)),
)

private val DarkWait = WaitPalette(
    short = WaitTone(Color(0xFF1E4D2B), Color(0xFFB6F2C0)),
    medium = WaitTone(Color(0xFF4D3B00), Color(0xFFFFE08A)),
    long = WaitTone(Color(0xFF5C2B00), Color(0xFFFFC08A)),
    veryLong = WaitTone(Color(0xFF6B1A16), Color(0xFFFFB4AB)),
    noneFree = WaitTone(Color(0xFF353B39), Color(0xFFCDD3D1)),
)

val LocalDarkTheme = staticCompositionLocalOf { false }
val LocalWaitPalette = staticCompositionLocalOf { LightWait }

@Composable
fun PassvarslingTheme(dark: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalDarkTheme provides dark,
        LocalWaitPalette provides if (dark) DarkWait else LightWait,
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
            // Text drawn straight on the screen background (not inside a Surface) would
            // otherwise default to black, which disappears in dark mode.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    }
}
