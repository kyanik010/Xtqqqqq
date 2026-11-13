package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ThreadGuardDarkColorScheme =
  darkColorScheme(
    primary = CyanAccent,
    onPrimary = MidnightBg,
    primaryContainer = CyanContainer,
    onPrimaryContainer = TextPrimary,
    secondary = EmeraldSafe,
    onSecondary = MidnightBg,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberWarn,
    onTertiary = MidnightBg,
    background = MidnightBg,
    onBackground = TextPrimary,
    surface = SurfaceNavy,
    onSurface = TextPrimary,
    surfaceVariant = CardSlate,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = CrimsonCritical,
    onError = TextPrimary,
    errorContainer = CrimsonContainer,
    onErrorContainer = TextPrimary,
  )

private val ThreadGuardShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
  )

@Composable
fun ThreadGuardTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = ThreadGuardDarkColorScheme,
    typography = ThreadGuardTypography,
    shapes = ThreadGuardShapes,
    content = content,
  )
}
