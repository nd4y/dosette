package icu.nd4y.dosette.domain.inventory

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.ScheduleType
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import icu.nd4y.dosette.domain.schedule as fixture

class InventoryPolicyTest {
    @Test
    fun `crossing the threshold downward fires`() {
        assertThat(InventoryPolicy.crossedLowThreshold(before = 11.0, after = 10.0, threshold = 10.0)).isTrue()
        assertThat(InventoryPolicy.crossedLowThreshold(before = 10.5, after = 9.5, threshold = 10.0)).isTrue()
    }

    @Test
    fun `already at or below the threshold does not fire again`() {
        assertThat(InventoryPolicy.crossedLowThreshold(before = 10.0, after = 9.0, threshold = 10.0)).isFalse()
        assertThat(InventoryPolicy.crossedLowThreshold(before = 5.0, after = 4.0, threshold = 10.0)).isFalse()
    }

    @Test
    fun `no threshold never fires`() {
        assertThat(InventoryPolicy.crossedLowThreshold(before = 11.0, after = 0.0, threshold = null)).isFalse()
    }

    @Test
    fun `fixed times consume the sum of slots daily`() {
        val s = fixture(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        assertThat(InventoryPolicy.dailyConsumption(s)).isEqualTo(2.0)
    }

    @Test
    fun `weekday schedules average over the week`() {
        val s =
            fixture(
                type = ScheduleType.WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            )
        assertThat(InventoryPolicy.dailyConsumption(s)).isWithin(1e-9).of(3.0 / 7.0)
    }

    @Test
    fun `every n days divides by the interval`() {
        val s = fixture(type = ScheduleType.EVERY_N_DAYS, intervalDays = 2)
        assertThat(InventoryPolicy.dailyConsumption(s)).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `cycle averages over on plus off`() {
        val s =
            fixture(
                type = ScheduleType.CYCLE,
                cycleDaysOn = 21,
                cycleDaysOff = 7,
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            )
        assertThat(InventoryPolicy.dailyConsumption(s)).isWithin(1e-9).of(1.5)
    }

    @Test
    fun `prn consumes nothing on schedule`() {
        assertThat(InventoryPolicy.dailyConsumption(fixture(type = ScheduleType.AS_NEEDED))).isEqualTo(0.0)
    }

    @Test
    fun `invalid interval consumes nothing`() {
        assertThat(
            InventoryPolicy.dailyConsumption(fixture(type = ScheduleType.EVERY_N_DAYS, intervalDays = 0)),
        ).isEqualTo(0.0)
    }

    @Test
    fun `days of supply floors partial days`() {
        assertThat(InventoryPolicy.daysOfSupply(stock = 42.0, dailyConsumption = 2.0)).isEqualTo(21)
        assertThat(InventoryPolicy.daysOfSupply(stock = 10.0, dailyConsumption = 3.0)).isEqualTo(3)
    }

    @Test
    fun `zero consumption has no supply horizon`() {
        assertThat(InventoryPolicy.daysOfSupply(stock = 42.0, dailyConsumption = 0.0)).isNull()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative stock is rejected`() {
        InventoryPolicy.daysOfSupply(stock = -1.0, dailyConsumption = 1.0)
    }
}
