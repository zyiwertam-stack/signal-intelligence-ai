package com.signalintelligence.pocketsignalassistant.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkBlueColor = Color(0xFF0A1220)
private val PanelColor = Color(0xFF101D2D)
private val CardColor = Color(0xFF16253A)
private val AccentColor = Color(0xFF3C82F6)
private val AccentSoftColor = Color(0xFF67B7FF)
private val SuccessColor = Color(0xFF20C997)
private val DangerColor = Color(0xFFEF5A5A)
private val NeutralColor = Color(0xFF6C7A8C)
private val TextPrimaryColor = Color(0xFFEAF3FF)
private val TextSecondaryColor = Color(0xFFB0C5DD)

private val PocketSignalColorScheme: ColorScheme = darkColorScheme(
    primary = AccentColor,
    secondary = AccentSoftColor,
    background = DarkBlueColor,
    surface = PanelColor,
    onPrimary = TextPrimaryColor,
    onSecondary = TextPrimaryColor,
    onBackground = TextPrimaryColor,
    onSurface = TextPrimaryColor,
    error = DangerColor,
    onError = TextPrimaryColor,
)

@Composable
fun PocketSignalAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PocketSignalColorScheme,
        typography = Typography(),
        content = content,
    )
}

object PocketColors {
    val DarkBackground: Color = DarkBlueColor
    val PanelBackground: Color = PanelColor
    val CardBackground: Color = CardColor
    val Accent: Color = AccentColor
    val AccentSoft: Color = AccentSoftColor
    val Success: Color = SuccessColor
    val Danger: Color = DangerColor
    val Neutral: Color = NeutralColor
    val TextPrimary: Color = TextPrimaryColor
    val TextSecondary: Color = TextSecondaryColor
}
