package icu.nd4y.dosette.widget

import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.TodayDose
import icu.nd4y.dosette.ui.today.slotSections

/** One line of the large widget's list, in display order. */
sealed interface LargeEntry {
    data object CarryoverHeader : LargeEntry

    data class DoseRow(
        val dose: TodayDose,
    ) : LargeEntry

    /**
     * A slot header. [collapsed] = every dose of the slot was already
     * acted on, so the header alone (with status marks) tells the story
     * and the rows are not spent on it.
     */
    data class SlotHeader(
        val doses: List<TodayDose>,
        val collapsed: Boolean,
    ) : LargeEntry
}

data class LargePlan(
    val entries: List<LargeEntry>,
    /** Doses that did not fit; rendered as a "+N more" line. */
    val hidden: Int,
    /** Room is left for the as-needed row after everything else. */
    val prnFits: Boolean,
)

/**
 * Fits the day list into the widget's height. A Glance Column does not
 * scroll and clips silently, so without a budget a slot header could be
 * drawn with its rows lost below the edge — exactly the "empty section"
 * a user sees on a 4x3 widget with a full day.
 */
object LargeLayout {
    // dp costs mirroring the composables in WidgetUi.
    const val OUTER_PADDING = 28
    const val TITLE_BLOCK = 44
    const val MORE_LINE = 18
    const val SECTION_HEADER = 22
    const val PENDING_ROW = 44
    const val ACTED_ROW = 36
    const val PRN_ROW = 38

    fun plan(
        heightDp: Int,
        carryover: List<TodayDose>,
        doses: List<TodayDose>,
    ): LargePlan {
        val cursor = Cursor(heightDp - OUTER_PADDING - TITLE_BLOCK - MORE_LINE)
        if (carryover.isNotEmpty()) cursor.placeSection(LargeEntry.CarryoverHeader, carryover, collapsed = false)
        slotSections(doses).forEach { slot ->
            val collapsed = slot.none { it.status == DoseUiStatus.PENDING }
            cursor.placeSection(LargeEntry.SlotHeader(slot, collapsed), slot, collapsed)
        }
        return LargePlan(
            entries = cursor.entries,
            hidden = cursor.hidden,
            prnFits = cursor.hidden == 0 && cursor.budget + MORE_LINE >= PRN_ROW,
        )
    }

    private fun rowCost(dose: TodayDose): Int = if (dose.status == DoseUiStatus.PENDING) PENDING_ROW else ACTED_ROW

    /** Walks the sections top-down, spending height until it runs out. */
    private class Cursor(
        var budget: Int,
    ) {
        val entries = mutableListOf<LargeEntry>()
        var hidden = 0

        fun placeSection(
            header: LargeEntry,
            rows: List<TodayDose>,
            collapsed: Boolean,
        ) {
            when {
                // Once something is cut, everything after it is cut too: a
                // later section drawn above a hidden one would reorder the day.
                hidden > 0 -> {
                    hidden += rows.size
                }

                collapsed -> {
                    placeCollapsed(header, rows)
                }

                // A header is only worth drawing together with its first row.
                budget < SECTION_HEADER + rowCost(rows.first()) -> {
                    hidden += rows.size
                }

                else -> {
                    take(header, SECTION_HEADER)
                    rows.forEach(::placeRow)
                }
            }
        }

        private fun placeCollapsed(
            header: LargeEntry,
            rows: List<TodayDose>,
        ) {
            if (budget >= SECTION_HEADER) take(header, SECTION_HEADER) else hidden += rows.size
        }

        private fun placeRow(dose: TodayDose) {
            val cost = rowCost(dose)
            if (hidden == 0 && budget >= cost) take(LargeEntry.DoseRow(dose), cost) else hidden++
        }

        private fun take(
            entry: LargeEntry,
            cost: Int,
        ) {
            entries += entry
            budget -= cost
        }
    }
}
