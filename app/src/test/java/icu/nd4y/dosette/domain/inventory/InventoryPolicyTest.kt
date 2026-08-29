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
    fun `dose converts to variant units by strength ratio`() {
        // 150 mg dose (1 reference unit) taken as 75 mg capsules -> 2 units.
        assertThat(InventoryPolicy.unitsForDose(1.0, medicationStrength = 150.0, variantStrength = 75.0))
            .isEqualTo(2.0)
        // Same variant as the reference -> 1:1.
        assertThat(InventoryPolicy.unitsForDose(1.0, medicationStrength = 150.0, variantStrength = 150.0))
            .isEqualTo(1.0)
        // 225 mg (1.5 reference units) as 75 mg capsules -> 3 units.
        assertThat(InventoryPolicy.unitsForDose(1.5, medicationStrength = 150.0, variantStrength = 75.0))
            .isEqualTo(3.0)
        // Half a pill of a stronger variant.
        assertThat(InventoryPolicy.unitsForDose(1.0, medicationStrength = 75.0, variantStrength = 150.0))
            .isEqualTo(0.5)
    }

    @Test
    fun `dose conversion falls back to plain units without strengths`() {
        assertThat(InventoryPolicy.unitsForDose(2.0, medicationStrength = null, variantStrength = 75.0))
            .isEqualTo(2.0)
        assertThat(InventoryPolicy.unitsForDose(2.0, medicationStrength = 150.0, variantStrength = null))
            .isEqualTo(2.0)
        assertThat(InventoryPolicy.unitsForDose(2.0, medicationStrength = 0.0, variantStrength = 75.0))
            .isEqualTo(2.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative dose is rejected`() {
        InventoryPolicy.unitsForDose(-1.0, medicationStrength = 150.0, variantStrength = 75.0)
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
