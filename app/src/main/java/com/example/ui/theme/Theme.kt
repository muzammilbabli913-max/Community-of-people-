package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryLight,
    secondary = TerracottaLight,
    tertiary = MintAccent,
    background = Gray900Background,
    surface = Gray800Surface,
    onPrimary = Color(0xFF111111),
    onSecondary = Color(0xFF111111),
    onBackground = Color(0xFFF7FAFC),
    onSurface = Color(0xFFF7FAFC)
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    secondary = TerracottaSecondary,
    tertiary = MintAccent,
    background = SoftCreamBackground,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = CharcoalText,
    onSurface = CharcoalText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
