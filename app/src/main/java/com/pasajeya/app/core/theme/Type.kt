package com.pasajeya.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pasajeya.app.R

/** Poppins, la misma tipografia del pitch. */
val Poppins = FontFamily(
    Font(R.font.poppins_400, FontWeight.Normal),
    Font(R.font.poppins_500, FontWeight.Medium),
    Font(R.font.poppins_600, FontWeight.SemiBold),
    Font(R.font.poppins_700, FontWeight.Bold)
)

private fun poppins(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0
) = TextStyle(
    fontFamily = Poppins,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

val PasajeYaTypography = Typography(
    displayLarge = poppins(40, 46, FontWeight.Bold, -0.5),
    displayMedium = poppins(34, 40, FontWeight.Bold, -0.5),
    displaySmall = poppins(28, 34, FontWeight.Bold, -0.25),
    headlineLarge = poppins(28, 34, FontWeight.Bold, -0.25),
    headlineMedium = poppins(24, 30, FontWeight.Bold),
    headlineSmall = poppins(20, 26, FontWeight.SemiBold),
    titleLarge = poppins(20, 26, FontWeight.SemiBold),
    titleMedium = poppins(16, 22, FontWeight.SemiBold),
    titleSmall = poppins(14, 20, FontWeight.SemiBold),
    bodyLarge = poppins(16, 24),
    bodyMedium = poppins(14, 21),
    bodySmall = poppins(13, 19),
    labelLarge = poppins(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = poppins(12, 16, FontWeight.Medium, 0.2),
    labelSmall = poppins(11, 15, FontWeight.Medium, 0.3)
)
