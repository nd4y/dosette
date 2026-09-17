package icu.nd4y.dosette.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * The phone app's teal palette on a watch: Material You when the watch
 * offers it, otherwise the same dark tones the phone falls back to, over
 * pure black for the always-on display.
 */
internal val DosetteWatchColors =
    ColorScheme(
        primary = Color(0xFF80D4D6),
        primaryDim = Color(0xFF4FB6B8),
        primaryContainer = Color(0xFF004F52),
        onPrimary = Color(0xFF003739),
        onPrimaryContainer = Color(0xFF9BF0F2),
        secondary = Color(0xFFB0CCCC),
        secondaryDim = Color(0xFF8DAAAA),
        secondaryContainer = Color(0xFF324B4C),
        onSecondary = Color(0xFF1B3435),
        onSecondaryContainer = Color(0xFFCBE8E8),
        tertiary = Color(0xFFFFB877),
        tertiaryDim = Color(0xFFDE9A5C),
        tertiaryContainer = Color(0xFF6A3C00),
        onTertiary = Color(0xFF4A2800),
        onTertiaryContainer = Color(0xFFFFDCC0),
        surfaceContainerLow = Color(0xFF161D1D),
        surfaceContainer = Color(0xFF1B2222),
        surfaceContainerHigh = Color(0xFF232B2B),
        onSurface = Color(0xFFDDE4E4),
        onSurfaceVariant = Color(0xFFBEC8C8),
        outline = Color(0xFF889392),
        outlineVariant = Color(0xFF3F4948),
        background = Color(0xFF000000),
        onBackground = Color(0xFFDDE4E4),
        error = Color(0xFFFFB4AB),
        errorDim = Color(0xFFE08B82),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
    )

@Composable
fun WatchTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        remember(dynamicColor) { if (dynamicColor) dynamicColorScheme(context) else null } ?: DosetteWatchColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
