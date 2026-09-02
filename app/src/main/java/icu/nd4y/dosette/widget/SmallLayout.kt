package icu.nd4y.dosette.widget

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Height budgets for the compact (2x2) and medium (4x2) widgets, the same
 * idea as [LargeLayout]: Glance clips silently, and in a dense launcher
 * the nominal 110dp bucket is shorter than the content drawn at scale 1.
 */
object SmallLayout {
    /** Which of the optional lines fit under the ring row and above the button. */
    data class CompactPlan(
        val showName: Boolean,
        val showSubtitle: Boolean,
    )

    /** How many dose rows the medium widget lists; the rest goes to "+N more". */
    data class MediumPlan(
        val rows: Int,
        val hidden: Int,
    )

    fun compact(
        heightDp: Int,
        fontScale: Float = 1f,
    ): CompactPlan {
        val name = scaled(COMPACT_NAME_TEXT, fontScale)
        val subtitle = scaled(COMPACT_SUBTITLE_TEXT, fontScale)
        return when {
            heightDp >= COMPACT_BASE + name + subtitle -> CompactPlan(showName = true, showSubtitle = true)
            heightDp >= COMPACT_BASE + name -> CompactPlan(showName = true, showSubtitle = false)
            else -> CompactPlan(showName = false, showSubtitle = false)
        }
    }

    fun medium(
        heightDp: Int,
        doseCount: Int,
        fontScale: Float = 1f,
    ): MediumPlan {
        val header = scaled(MEDIUM_HEADER_TEXT, fontScale)
        val row = MEDIUM_ROW_FIXED + max(MEDIUM_ROW_CHIP, scaled(MEDIUM_ROW_TEXT, fontScale))
        val more = MEDIUM_MORE_FIXED + scaled(MEDIUM_MORE_TEXT, fontScale)
        var rows = min(doseCount, MAX_MEDIUM_ROWS)
        // The first row always stays: a medium widget without a dose row is
        // just a ring, and that is what the compact widget is for.
        while (rows > 1 && MEDIUM_PADDING + header + rows * row + moreLine(doseCount - rows, more) > heightDp) {
            rows--
        }
        return MediumPlan(rows = rows, hidden = doseCount - rows)
    }

    private fun moreLine(
        hidden: Int,
        cost: Int,
    ): Int = if (hidden > 0) cost else 0

    private fun scaled(
        dp: Int,
        fontScale: Float,
    ): Int = (dp * fontScale).roundToInt()

    // Compact: padding 24 + ring row 44 + spacer 8 + button 32.
    private const val COMPACT_BASE = 108
    private const val COMPACT_NAME_TEXT = 17
    private const val COMPACT_SUBTITLE_TEXT = 13

    // Medium: padding 24, a header line, rows of spacer 5 + padding 10 +
    // max(chip 28, two text lines), and the "+N more" line.
    private const val MAX_MEDIUM_ROWS = 2
    private const val MEDIUM_PADDING = 24
    private const val MEDIUM_HEADER_TEXT = 16
    private const val MEDIUM_ROW_FIXED = 15
    private const val MEDIUM_ROW_CHIP = 28
    private const val MEDIUM_ROW_TEXT = 28
    private const val MEDIUM_MORE_FIXED = 3
    private const val MEDIUM_MORE_TEXT = 16
}
