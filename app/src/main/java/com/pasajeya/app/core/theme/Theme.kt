package com.pasajeya.app.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PasajeYaColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = BlueSoft,
    onPrimaryContainer = DeepBlue,
    secondary = AccentOrange,
    onSecondary = Navy,
    secondaryContainer = AccentOrangeSoft,
    onSecondaryContainer = Navy,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = TextGray,
    outline = BorderLight,
    error = ErrorRed,
    onError = Color.White
)

private val PasajeYaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PasajeYaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PasajeYaColorScheme,
        typography = PasajeYaTypography,
        shapes = PasajeYaShapes,
        content = content
    )
}
