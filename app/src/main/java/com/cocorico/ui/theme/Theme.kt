package com.cocorico.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Nuit = Color(0xFF101638)
val NuitHaute = Color(0xFF1B2154)
val Crete = Color(0xFFE3372B)
val Bec = Color(0xFFF7B32B)
val Craie = Color(0xFFFAF7F2)

private val DarkScheme = darkColorScheme(
    primary = Bec,
    onPrimary = Nuit,
    secondary = Crete,
    onSecondary = Craie,
    background = Nuit,
    onBackground = Craie,
    surface = NuitHaute,
    onSurface = Craie,
    error = Crete,
    onError = Craie,
)

private val LightScheme = lightColorScheme(
    primary = Crete,
    onPrimary = Craie,
    secondary = Bec,
    onSecondary = Nuit,
    background = Craie,
    onBackground = Nuit,
    surface = Color(0xFFFFFFFF),
    onSurface = Nuit,
    error = Crete,
    onError = Craie,
)

/** Chiffres : chasse fixe obligatoire, ils ne doivent pas sauter quand ils changent. */
val ChiffresStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 64.sp,
)

private val CocoricoTypography = Typography(
    displayLarge = ChiffresStyle,
    titleLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
)

@Composable
fun CocoricoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = CocoricoTypography,
        content = content,
    )
}
