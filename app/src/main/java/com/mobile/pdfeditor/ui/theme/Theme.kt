package com.mobile.pdfeditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens taken from the design canvas.
object Brand {
    val Blue = Color(0xFF2457D6)
    val BlueDeep = Color(0xFF1E47B0)
    val BlueSoft = Color(0xFFE8EEFC)
    val BlueContainer = Color(0xFFDCE4FA)
    val Ink = Color(0xFF15171A)
    val InkMuted = Color(0xFF5B6270)
    val InkSubtle = Color(0xFF3D434D)
    val Line = Color(0xFFE3E6EB)
    val Ground = Color(0xFFF5F6F8)
    val Canvas = Color(0xFFE9EBEF)
    val Danger = Color(0xFFB42318)
    val PdfRed = Color(0xFFC8352B)
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    primaryContainer = Brand.BlueSoft,
    onPrimaryContainer = Brand.BlueDeep,
    secondaryContainer = Brand.BlueContainer,
    onSecondaryContainer = Brand.BlueDeep,
    background = Brand.Ground,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.Canvas,
    onSurfaceVariant = Brand.InkMuted,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F2F5),
    outline = Color(0xFFD5D9E0),
    outlineVariant = Brand.Line,
    error = Brand.Danger,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    onPrimary = Color(0xFF0B2A73),
    primaryContainer = Color(0xFF1B3A8F),
    onPrimaryContainer = Color(0xFFDCE4FA),
    secondaryContainer = Color(0xFF233259),
    onSecondaryContainer = Color(0xFFDCE4FA),
    background = Color(0xFF111317),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF1A1C21),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF0B0C0F),
    onSurfaceVariant = Color(0xFFA9B0BC),
    surfaceContainer = Color(0xFF1A1C21),
    surfaceContainerHigh = Color(0xFF24272D),
    outline = Color(0xFF3A3F47),
    outlineVariant = Color(0xFF2A2E35),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val SectionLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)

@Composable
fun PdfEditorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
