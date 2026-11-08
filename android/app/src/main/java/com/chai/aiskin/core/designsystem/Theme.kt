package com.chai.aiskin.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val LightScheme = lightColorScheme(
    primary = AiSkinColors.Charcoal,
    onPrimary = AiSkinColors.OffWhite,
    primaryContainer = AiSkinColors.ChampagneLight,
    onPrimaryContainer = AiSkinColors.Charcoal,
    secondary = AiSkinColors.Champagne,
    onSecondary = AiSkinColors.OffWhite,
    secondaryContainer = AiSkinColors.SoftBeige,
    onSecondaryContainer = AiSkinColors.Charcoal,
    tertiary = AiSkinColors.Sage,
    background = AiSkinColors.Ivory,
    onBackground = AiSkinColors.Charcoal,
    surface = AiSkinColors.OffWhite,
    onSurface = AiSkinColors.Charcoal,
    surfaceVariant = AiSkinColors.SoftBeige,
    onSurfaceVariant = AiSkinColors.Graphite,
    surfaceContainerLowest = AiSkinColors.OffWhite,
    surfaceContainerLow = AiSkinColors.OffWhite,
    surfaceContainer = AiSkinColors.SoftBeige,
    surfaceContainerHigh = AiSkinColors.Beige,
    outline = AiSkinColors.Sand,
    outlineVariant = AiSkinColors.Hairline,
    error = AiSkinColors.Rosewood,
    onError = AiSkinColors.OffWhite,
    errorContainer = AiSkinColors.RosewoodSoft,
    onErrorContainer = AiSkinColors.Rosewood,
)

private val DarkScheme = darkColorScheme(
    primary = AiSkinColors.ChampagneOnDark,
    onPrimary = AiSkinColors.Night,
    primaryContainer = AiSkinColors.NightSurfaceHigh,
    onPrimaryContainer = AiSkinColors.NightText,
    secondary = AiSkinColors.ChampagneOnDark,
    onSecondary = AiSkinColors.Night,
    secondaryContainer = AiSkinColors.NightSurfaceHigh,
    onSecondaryContainer = AiSkinColors.NightText,
    tertiary = Color(0xFF9FB9A2),
    background = AiSkinColors.Night,
    onBackground = AiSkinColors.NightText,
    surface = AiSkinColors.NightSurface,
    onSurface = AiSkinColors.NightText,
    surfaceVariant = AiSkinColors.NightSurfaceHigh,
    onSurfaceVariant = AiSkinColors.NightTextMuted,
    surfaceContainerLowest = AiSkinColors.Night,
    surfaceContainerLow = AiSkinColors.NightSurface,
    surfaceContainer = AiSkinColors.NightSurface,
    surfaceContainerHigh = AiSkinColors.NightSurfaceHigh,
    outline = AiSkinColors.NightHairline,
    outlineVariant = AiSkinColors.NightHairline,
    error = Color(0xFFE5A39A),
    onError = AiSkinColors.Night,
    errorContainer = Color(0xFF4A2A26),
    onErrorContainer = Color(0xFFF4D3CE),
)

val AiSkinShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Accent colours outside Material's scheme (status tones, champagne accent). */
@Immutable
data class AiSkinExtendedColors(
    val accent: Color,
    val accentSoft: Color,
    val positive: Color,
    val positiveSoft: Color,
    val caution: Color,
    val cautionSoft: Color,
    val alert: Color,
    val alertSoft: Color,
    val hairline: Color,
)

private val LightExtended = AiSkinExtendedColors(
    accent = AiSkinColors.Champagne,
    accentSoft = AiSkinColors.ChampagneLight,
    positive = AiSkinColors.Sage,
    positiveSoft = AiSkinColors.SageSoft,
    caution = AiSkinColors.Amber,
    cautionSoft = AiSkinColors.AmberSoft,
    alert = AiSkinColors.Rosewood,
    alertSoft = AiSkinColors.RosewoodSoft,
    hairline = AiSkinColors.Hairline,
)

private val DarkExtended = AiSkinExtendedColors(
    accent = AiSkinColors.ChampagneOnDark,
    accentSoft = Color(0xFF3B3328),
    positive = Color(0xFF9FB9A2),
    positiveSoft = Color(0xFF2A3329),
    caution = Color(0xFFE0B66F),
    cautionSoft = Color(0xFF3A3122),
    alert = Color(0xFFE5A39A),
    alertSoft = Color(0xFF4A2A26),
    hairline = AiSkinColors.NightHairline,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

object AiSkinTheme {
    val extended: AiSkinExtendedColors
        @Composable get() = LocalExtendedColors.current
}

@Composable
fun AiSkinTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AiSkinTypography,
            shapes = AiSkinShapes,
            content = content,
        )
    }
}
