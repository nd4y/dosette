package icu.nd4y.dosette.ui.designsystem

import androidx.compose.ui.graphics.Color

data class MedColor(
    val container: Color,
    val onContainer: Color,
)

/**
 * Fixed pastel pairs from the design mockups; a medication keeps its color
 * via colorSeed regardless of the dynamic theme, so it stays recognizable.
 */
object MedPalette {
    private val light =
        listOf(
            MedColor(Color(0xFFFFE0B0), Color(0xFF5A3C00)), // amber
            MedColor(Color(0xFFC9E2FF), Color(0xFF143B63)), // blue
            MedColor(Color(0xFFFAD3E4), Color(0xFF6B2C4E)), // rose
            MedColor(Color(0xFFD8F0BC), Color(0xFF315200)), // green
            MedColor(Color(0xFFE5DEFF), Color(0xFF423B77)), // violet
            MedColor(Color(0xFFFFD9CC), Color(0xFF7A2E0E)), // coral
        )

    private val dark = light.map { MedColor(container = it.onContainer, onContainer = it.container) }

    val size: Int get() = light.size

    fun resolve(
        colorSeed: Int,
        darkTheme: Boolean,
    ): MedColor {
        val palette = if (darkTheme) dark else light
        return palette[((colorSeed % palette.size) + palette.size) % palette.size]
    }
}
