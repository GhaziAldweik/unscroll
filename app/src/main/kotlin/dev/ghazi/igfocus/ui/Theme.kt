package dev.ghazi.igfocus.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Taken from the "Night" icon: a mint-to-teal accent on Instagram's dark blue-grey (#0C1014).
// Dynamic (wallpaper) colour is deliberately not used, so the app and its icon match.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6B5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEDE5),
    onPrimaryContainer = Color(0xFF00201B),
    // Set explicitly: Material's defaults are purple, and show in the selected tab label.
    secondary = Color(0xFF2F6A60),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF3E6374),
    onTertiary = Color(0xFFFFFFFF),
    outline = Color(0xFF6F7A78),
    secondaryContainer = Color(0xFFDCE7E4),
    onSecondaryContainer = Color(0xFF15211F),
    background = Color(0xFFF5FAF8),
    onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF5FAF8),
    onSurface = Color(0xFF161D1C),
    onSurfaceVariant = Color(0xFF4F5E5B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9F0EE),
    surfaceContainerHigh = Color(0xFFE3EBE8),
    surfaceContainerHighest = Color(0xFFDDE5E2),
    outlineVariant = Color(0xFFC3CECB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DDBC6),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1B4A42),
    onPrimaryContainer = Color(0xFFCDEDE5),
    secondary = Color(0xFF7DDBC6),
    onSecondary = Color(0xFF00382F),
    tertiary = Color(0xFFA6CCE0),
    onTertiary = Color(0xFF0B3445),
    outline = Color(0xFF6E7D85),
    secondaryContainer = Color(0xFF26343A),
    onSecondaryContainer = Color(0xFFD8E4E8),
    background = Color(0xFF0C1014),
    onBackground = Color(0xFFE1E6E8),
    surface = Color(0xFF0C1014),
    onSurface = Color(0xFFE1E6E8),
    onSurfaceVariant = Color(0xFFA3B0B5),
    surfaceContainerLowest = Color(0xFF080B0E),
    surfaceContainerLow = Color(0xFF12181D),
    surfaceContainer = Color(0xFF161E24),
    surfaceContainerHigh = Color(0xFF1C2733),
    surfaceContainerHighest = Color(0xFF26323E),
    outlineVariant = Color(0xFF34424C),
)

private val FocusShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = FocusShapes,
        content = content,
    )
}
