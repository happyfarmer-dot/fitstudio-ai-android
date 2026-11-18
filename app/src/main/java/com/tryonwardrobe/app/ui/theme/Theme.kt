package com.tryonwardrobe.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ObsidianInk = Color(0xFF141518)
private val IvoryCanvas = Color(0xFFFAF8F5)
private val WarmStone = Color(0xFFF0ECE4)
private val WarmStoneVariant = Color(0xFFE5DFD3)
private val TerracottaAccent = Color(0xFFA85D3B)
private val DeepSlate = Color(0xFF23394D)
private val SoftError = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = ObsidianInk,
    onPrimary = IvoryCanvas,
    primaryContainer = WarmStoneVariant,
    onPrimaryContainer = ObsidianInk,
    secondary = TerracottaAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E1D7),
    onSecondaryContainer = Color(0xFF471F0E),
    tertiary = DeepSlate,
    onTertiary = Color.White,
    background = IvoryCanvas,
    onBackground = ObsidianInk,
    surface = Color.White,
    onSurface = ObsidianInk,
    surfaceVariant = WarmStone,
    onSurfaceVariant = Color(0xFF54514B),
    outline = Color(0xFFD6D0C4),
    error = SoftError
)

private val DarkColors = darkColorScheme(
    primary = IvoryCanvas,
    onPrimary = ObsidianInk,
    primaryContainer = Color(0xFF2D2E34),
    onPrimaryContainer = IvoryCanvas,
    secondary = Color(0xFFE39E7D),
    onSecondary = Color(0xFF2E1407),
    secondaryContainer = Color(0xFF532A17),
    onSecondaryContainer = Color(0xFFFFDFD1),
    background = Color(0xFF101114),
    onBackground = IvoryCanvas,
    surface = Color(0xFF191A1F),
    onSurface = IvoryCanvas,
    surfaceVariant = Color(0xFF25262D),
    onSurfaceVariant = Color(0xFFB7B3AA),
    outline = Color(0xFF3E404A)
)

@Composable
fun FitStudioTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
