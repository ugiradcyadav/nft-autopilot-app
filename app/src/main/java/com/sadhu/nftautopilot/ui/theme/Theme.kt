package com.sadhu.nftautopilot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CyberGreen   = Color(0xFF00E5A0)
val CyberBlue    = Color(0xFF4FA0E8)
val DeepBg       = Color(0xFF0B0D12)
val SurfaceDark  = Color(0xFF13171F)
val Surface2Dark = Color(0xFF1B2030)
val BorderDark   = Color(0xFF252D3A)
val TextPrimary  = Color(0xFFC4CDD8)
val TextMuted    = Color(0xFF556070)
val ErrorRed     = Color(0xFFE55C4A)
val WarningYellow= Color(0xFFD4A020)

private val DarkColors = darkColorScheme(
    primary          = CyberGreen,
    onPrimary        = Color(0xFF003320),
    secondary        = CyberBlue,
    onSecondary      = Color(0xFF001A2A),
    background       = DeepBg,
    onBackground     = TextPrimary,
    surface          = SurfaceDark,
    onSurface        = TextPrimary,
    surfaceVariant   = Surface2Dark,
    onSurfaceVariant = TextMuted,
    error            = ErrorRed,
    outline          = BorderDark
)

private val LightColors = lightColorScheme(
    primary      = Color(0xFF006B3A),
    secondary    = Color(0xFF1565C0),
    background   = Color(0xFFF4F6F9),
    surface      = Color(0xFFFFFFFF),
    error        = Color(0xFFB71C1C)
)

@Composable
fun NFTAutopilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
