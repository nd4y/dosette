package icu.nd4y.dosette.widget

import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.TodayDose
import icu.nd4y.dosette.ui.today.slotSections
import kotlin.math.max
import kotlin.math.roundToInt

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
    /**
     * Collapsing is a last resort, not a style: with room to spare every
     * slot lists its rows (names and marks), and only when the day does not
     * fit are acted-on slots folded into their header — top-down, one at a
     * time, so the most recent slots stay readable the longest.
     */
    fun plan(
        heightDp: Int,
        carryover: List<TodayDose>,
        doses: List<TodayDose>,
        fontScale: Float = 1f,
    ): LargePlan {
        val costs = Costs(fontScale)
        val slots = slotSections(doses)
        val actedSlots = slots.count { slot -> slot.none { it.status == DoseUiStatus.PENDING } }
        var plan = layout(heightDp, carryover, slots, collapseFirst = 0, costs)
        var collapseFirst = 0
        while (plan.hidden > 0 && collapseFirst < actedSlots) {
            collapseFirst++
            plan = layout(heightDp, carryover, slots, collapseFirst, costs)
        }
        return plan
    }

    /** One layout pass with the first [collapseFirst] acted-on slots folded. */
    private fun layout(
        heightDp: Int,
        carryover: List<TodayDose>,
        slots: List<List<TodayDose>>,
        collapseFirst: Int,
        costs: Costs,
    ): LargePlan {
        val cursor = Cursor(heightDp - OUTER_PADDING - costs.titleBlock - costs.moreLine, costs)
        if (carryover.isNotEmpty()) cursor.placeSection(LargeEntry.CarryoverHeader, carryover, collapsed = false)
        var foldsLeft = collapseFirst
        slots.forEach { slot ->
            val acted = slot.none { it.status == DoseUiStatus.PENDING }
            val collapsed = acted && foldsLeft > 0
            if (collapsed) foldsLeft--
            cursor.placeSection(LargeEntry.SlotHeader(slot, collapsed), slot, collapsed)
        }
        return LargePlan(
            entries = cursor.entries,
            hidden = cursor.hidden,
            prnFits = cursor.hidden == 0 && cursor.budget + costs.moreLine >= costs.prnRow,
        )
    }

    /**
     * dp costs mirroring the composables in WidgetUi. Text grows with the
     * font size setting; chips, circles and paddings do not, so each cost
     * is a fixed part plus a scaled text part (the sums equal the measured
     * values at scale 1).
     */
    private class Costs(
        private val fontScale: Float,
    ) {
        /** Title + date lines. */
        val titleBlock: Int = TITLE_PADDING + text(TITLE_TEXT)

        /** The "+N more" line, reserved up front. */
        val moreLine: Int = MORE_PADDING + text(MORE_TEXT)

        val sectionHeader: Int = HEADER_PADDING + text(HEADER_TEXT)

        /** Header carrying status circles instead of rows. */
        val collapsedHeader: Int = HEADER_PADDING + max(STATUS_CIRCLE, text(HEADER_TEXT))

        val pendingRow: Int = PENDING_FIXED + text(PENDING_TEXT)

        val actedRow: Int = ACTED_FIXED + max(ACTED_CHIP, text(ACTED_TEXT))

        val prnRow: Int = PRN_FIXED + max(PRN_BUTTON, text(PRN_TEXT))

        fun row(dose: TodayDose): Int = if (dose.status == DoseUiStatus.PENDING) pendingRow else actedRow

        private fun text(dp: Int): Int = (dp * fontScale).roundToInt()
    }

    /** Walks the sections top-down, spending height until it runs out. */
    private class Cursor(
        var budget: Int,
        private val costs: Costs,
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
                budget < costs.sectionHeader + costs.row(rows.first()) -> {
                    hidden += rows.size
                }

                else -> {
                    take(header, costs.sectionHeader)
                    rows.forEach(::placeRow)
                }
            }
        }

        private fun placeCollapsed(
            header: LargeEntry,
            rows: List<TodayDose>,
        ) {
            if (budget >= costs.collapsedHeader) take(header, costs.collapsedHeader) else hidden += rows.size
        }

        private fun placeRow(dose: TodayDose) {
            val cost = costs.row(dose)
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

    private const val OUTER_PADDING = 28
    private const val TITLE_PADDING = 12
    private const val TITLE_TEXT = 32
    private const val MORE_PADDING = 4
    private const val MORE_TEXT = 14
    private const val HEADER_PADDING = 6
    private const val HEADER_TEXT = 16
    private const val STATUS_CIRCLE = 20
    private const val PENDING_FIXED = 14
    private const val PENDING_TEXT = 30
    private const val ACTED_FIXED = 12
    private const val ACTED_CHIP = 24
    private const val ACTED_TEXT = 16
    private const val PRN_FIXED = 14
    private const val PRN_BUTTON = 26
    private const val PRN_TEXT = 16
}
