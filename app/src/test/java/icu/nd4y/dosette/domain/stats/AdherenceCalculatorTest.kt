package icu.nd4y.dosette.domain.stats

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.stats.AdherenceCalculator.DayStatus
import org.junit.Test

class AdherenceCalculatorTest {
    @Test
    fun `all taken is one hundred percent`() {
        assertThat(AdherenceCalculator.percent(taken = 10, missed = 0)).isEqualTo(100)
    }

    @Test
    fun `all missed is zero percent`() {
        assertThat(AdherenceCalculator.percent(taken = 0, missed = 5)).isEqualTo(0)
    }

    @Test
    fun `percent rounds to the nearest integer`() {
        assertThat(AdherenceCalculator.percent(taken = 2, missed = 1)).isEqualTo(67)
        assertThat(AdherenceCalculator.percent(taken = 1, missed = 2)).isEqualTo(33)
    }

    @Test
    fun `no data means no percentage`() {
        assertThat(AdherenceCalculator.percent(taken = 0, missed = 0)).isNull()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative counts are rejected`() {
        AdherenceCalculator.percent(taken = -1, missed = 0)
    }

    @Test
    fun `empty day has no status`() {
        assertThat(AdherenceCalculator.dayStatus(0, 0, 0)).isEqualTo(DayStatus.NONE)
    }

    @Test
    fun `taken only day is complete`() {
        assertThat(AdherenceCalculator.dayStatus(3, 0, 0)).isEqualTo(DayStatus.COMPLETE)
    }

    @Test
    fun `deliberately skipped day still counts as complete`() {
        assertThat(AdherenceCalculator.dayStatus(0, 2, 0)).isEqualTo(DayStatus.COMPLETE)
    }

    @Test
    fun `mixed day is partial`() {
        assertThat(AdherenceCalculator.dayStatus(2, 0, 1)).isEqualTo(DayStatus.PARTIAL)
        assertThat(AdherenceCalculator.dayStatus(0, 1, 1)).isEqualTo(DayStatus.PARTIAL)
    }

    @Test
    fun `fully missed day is all missed`() {
        assertThat(AdherenceCalculator.dayStatus(0, 0, 4)).isEqualTo(DayStatus.ALL_MISSED)
    }
}
