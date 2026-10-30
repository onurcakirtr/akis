package com.akis.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Nothing tarzı (siyah-beyaz + kırmızı, nokta ızgarası, mono yazı) açık mı? */
val LocalNothingStyle = staticCompositionLocalOf { false }

val NothingRed = Color(0xFFD71921)

private val NothingDark = darkColorScheme(
    primary = NothingRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B0B0C),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFEDEDED),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFBDBDBD),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF1F1F1F),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFA3A3A3),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF232323),
    outline = Color(0xFF3C3C3C),
    outlineVariant = Color(0xFF262626),
    error = Color(0xFFFF6B5F),
)

private val NothingLight = lightColorScheme(
    primary = NothingRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF1A1A1A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF555555),
    background = Color(0xFFF2F2F2),
    onBackground = Color.Black,
    surface = Color(0xFFF2F2F2),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE4E4E4),
    onSurfaceVariant = Color(0xFF5C5C5C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFDDDDDD),
)

// Material You kapalı / Android 11 ve altı için yedek renkler
private val FallbackDark = darkColorScheme(primary = Color(0xFFB4C5FF), secondary = Color(0xFFC1C6DD), tertiary = Color(0xFFE2BBDB))
private val FallbackLight = lightColorScheme(primary = Color(0xFF3E5BA9), secondary = Color(0xFF585E71), tertiary = Color(0xFF735470))

// Expressive: daha yuvarlak, daha "yumuşak" şekil ölçeği
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

private fun typography(nothing: Boolean): Typography {
    val base = Typography()
    if (!nothing) {
        return base.copy(
            displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    val mono = FontFamily.Monospace
    fun TextStyle.m(w: FontWeight? = null, ls: Float? = null) =
        copy(fontFamily = mono, fontWeight = w ?: fontWeight, letterSpacing = ls?.sp ?: letterSpacing)
    return base.copy(
        displayLarge = base.displayLarge.m(FontWeight.Bold),
        displayMedium = base.displayMedium.m(FontWeight.Bold),
        displaySmall = base.displaySmall.m(FontWeight.Bold),
        headlineLarge = base.headlineLarge.m(FontWeight.Bold, 1f),
        headlineMedium = base.headlineMedium.m(FontWeight.Bold, 0.5f),
        headlineSmall = base.headlineSmall.m(FontWeight.Medium),
        titleLarge = base.titleLarge.m(FontWeight.Medium),
        titleMedium = base.titleMedium.m(FontWeight.Medium),
        titleSmall = base.titleSmall.m(),
        labelLarge = base.labelLarge.m(FontWeight.Medium, 1.2f),
        labelMedium = base.labelMedium.m(ls = 1.2f),
        labelSmall = base.labelSmall.m(ls = 1.2f),
    )
}

@Composable
fun AkisTheme(theme: String, dark: Boolean, content: @Composable () -> Unit) {
    val nothing = theme == "nothing"
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        nothing -> if (dark) NothingDark else NothingLight
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> if (dark) FallbackDark else FallbackLight
    }
    CompositionLocalProvider(LocalNothingStyle provides nothing) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(nothing),
            shapes = ExpressiveShapes,
            content = content,
        )
    }
}
