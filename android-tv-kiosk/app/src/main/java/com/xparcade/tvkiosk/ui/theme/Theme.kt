package com.xparcade.tvkiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KioskColorScheme = darkColorScheme(
    primary = XpYellow,
    secondary = XpMagenta,
    background = XpBlack,
    surface = XpDarkGray,
    onPrimary = XpBlack,
    onSecondary = XpWhite,
    onBackground = XpWhite,
    onSurface = XpWhite
)

@Composable
fun XPArcadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KioskColorScheme,
        content = content
    )
}
