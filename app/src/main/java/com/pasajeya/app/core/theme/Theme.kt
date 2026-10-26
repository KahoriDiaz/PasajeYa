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
    secondary = PrimaryBlue,
    onSecondary = Color.White,
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
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp)
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
