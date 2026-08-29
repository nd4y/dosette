package icu.nd4y.dosette.domain.stats

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.stats.AdherenceCalculator.DayStatus
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    private val today = LocalDate.parse("2026-08-29")

    private fun day(offset: Long) = today.minusDays(offset)

    @Test
    fun `empty history is zero`() {
        assertThat(StreakCalculator.currentStreak(emptyMap(), today)).isEqualTo(0)
    }

    @Test
    fun `unbroken run counts every complete day`() {
        val days = (0L..4L).associate { day(it) to DayStatus.COMPLETE }
        assertThat(StreakCalculator.currentStreak(days, today)).isEqualTo(5)
    }

    @Test
    fun `partial day breaks the run`() {
        val days =
            mapOf(
                day(0) to DayStatus.COMPLETE,
                day(1) to DayStatus.COMPLETE,
                day(2) to DayStatus.PARTIAL,
                day(3) to DayStatus.COMPLETE,
            )
        assertThat(StreakCalculator.currentStreak(days, today)).isEqualTo(2)
    }

    @Test
    fun `all-missed day breaks the run immediately`() {
        val days =
            mapOf(
                day(0) to DayStatus.ALL_MISSED,
                day(1) to DayStatus.COMPLETE,
            )
        assertThat(StreakCalculator.currentStreak(days, today)).isEqualTo(0)
    }

    @Test
    fun `none days are skipped without breaking`() {
        val days =
            mapOf(
                day(0) to DayStatus.COMPLETE,
                day(1) to DayStatus.NONE,
                day(2) to DayStatus.COMPLETE,
                day(3) to DayStatus.COMPLETE,
            )
        assertThat(StreakCalculator.currentStreak(days, today)).isEqualTo(3)
    }

    @Test
    fun `missing map entries behave like none`() {
        val days =
            mapOf(
                day(0) to DayStatus.COMPLETE,
                day(3) to DayStatus.COMPLETE,
            )
        assertThat(StreakCalculator.currentStreak(days, today)).isEqualTo(2)
    }

    @Test
    fun `today without data does not break yesterday's run`() {
        val days =
            mapOf(
                day(1) to DayStatus.COMPLETE,
                day(2) to DayStatus.COMPLETE,
            )
        assertThat(StreakCalculator.currentStreak(days, today)).isEqualTo(2)
    }

    @Test
    fun `walk stops at the earliest recorded day`() {
        val days = mapOf(day(1) to DayStatus.COMPLETE)
        assertThat(StreakCalculator.currentStreak(days, today)).isEqualTo(1)
    }
}
