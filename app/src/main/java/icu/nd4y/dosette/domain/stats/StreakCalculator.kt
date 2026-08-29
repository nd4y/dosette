package icu.nd4y.dosette.domain.stats

import java.time.LocalDate

/**
 * Streak semantics:
 * - COMPLETE extends the streak.
 * - NONE (or a day with no data at all) is neutral: nothing was scheduled,
 *   so it neither extends nor breaks the run.
 * - PARTIAL and ALL_MISSED break it.
 * - Counting walks backwards from [today] and stops at the earliest recorded
 *   day, so an empty history yields zero instead of an unbounded walk.
 */
object StreakCalculator {
    fun currentStreak(
        days: Map<LocalDate, AdherenceCalculator.DayStatus>,
        today: LocalDate,
    ): Int {
        val earliest = days.keys.minOrNull() ?: return 0
        var streak = 0
        var date = today
        while (date >= earliest) {
            val status = days[date] ?: AdherenceCalculator.DayStatus.NONE
            if (status == AdherenceCalculator.DayStatus.PARTIAL ||
                status == AdherenceCalculator.DayStatus.ALL_MISSED
            ) {
                break
            }
            if (status == AdherenceCalculator.DayStatus.COMPLETE) streak++
            date = date.minusDays(1)
        }
        return streak
    }
}
