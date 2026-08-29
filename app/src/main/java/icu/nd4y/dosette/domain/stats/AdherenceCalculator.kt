package icu.nd4y.dosette.domain.stats

import kotlin.math.roundToInt

/**
 * Adherence semantics:
 * - Skipped doses are excluded from the percentage: skipping is a deliberate,
 *   recorded decision, not non-adherence.
 * - "Today" must be finalized (past its grace window) before it enters the
 *   counts; callers feed finalized ranges only.
 */
object AdherenceCalculator {
    enum class DayStatus {
        /** Nothing was scheduled. */
        NONE,

        /** Every scheduled dose was acted on (taken or deliberately skipped). */
        COMPLETE,

        /** Some doses acted on, some missed. */
        PARTIAL,

        /** Every scheduled dose was missed. */
        ALL_MISSED,
    }

    /** Percent of taken among taken+missed, rounded; null when there is no data to judge. */
    fun percent(
        taken: Int,
        missed: Int,
    ): Int? {
        require(taken >= 0 && missed >= 0) { "negative counts" }
        val denominator = taken + missed
        if (denominator == 0) return null
        return (taken * 100.0 / denominator).roundToInt()
    }

    fun dayStatus(
        taken: Int,
        skipped: Int,
        missed: Int,
    ): DayStatus {
        require(taken >= 0 && skipped >= 0 && missed >= 0) { "negative counts" }
        val acted = taken + skipped
        return when {
            acted == 0 && missed == 0 -> DayStatus.NONE
            missed == 0 -> DayStatus.COMPLETE
            acted == 0 -> DayStatus.ALL_MISSED
            else -> DayStatus.PARTIAL
        }
    }
}
