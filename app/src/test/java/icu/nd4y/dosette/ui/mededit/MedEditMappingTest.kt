package icu.nd4y.dosette.ui.mededit

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.MedicationVariant
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.domain.schedule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private fun medication(defaultVariantId: String? = "v1"): Medication =
    Medication(
        id = "m1",
        profileId = "p1",
        name = "Метформин",
        form = MedicationForm.TABLET,
        strengthValue = 500.0,
        strengthUnit = "мг",
        instructions = "после еды",
        colorSeed = 2,
        iconKey = "tablet",
        defaultVariantId = defaultVariantId,
        archivedAt = null,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

private fun variant(
    id: String = "v1",
    tracked: Boolean = true,
    stock: Double = 42.0,
): MedicationVariant =
    MedicationVariant(
        id = id,
        medicationId = "m1",
        label = null,
        strengthValue = 500.0,
        strengthUnit = "мг",
        sortOrder = 0,
        trackingEnabled = tracked,
        currentStock = stock,
        lowStockThreshold = 10.0,
        defaultRefillAmount = 30.0,
        lastRefillAt = null,
    )

class MedEditMappingTest {
    @Test
    fun `prefill copies basics schedule and tracked variants`() {
        val details =
            MedicationDetails(
                medication = medication(),
                schedules =
                    listOf(
                        schedule(
                            id = "s1",
                            type = ScheduleType.WEEKDAYS,
                            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                            times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0)),
                            amount = 2.0,
                        ),
                    ),
                variants = listOf(variant()),
            )

        val state = prefillFrom(details)

        assertThat(state.editing).isTrue()
        assertThat(state.name).isEqualTo("Метформин")
        assertThat(state.strengthText).isEqualTo("500")
        assertThat(state.strengthUnit).isEqualTo("мг")
        assertThat(state.colorSeed).isEqualTo(2)
        assertThat(state.scheduleType).isEqualTo(ScheduleType.WEEKDAYS)
        assertThat(state.weekdays).containsExactly(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        // Sorted by time, amounts preserved.
        assertThat(state.times.map { it.time }).containsExactly(LocalTime.of(8, 0), LocalTime.of(20, 0)).inOrder()
        assertThat(state.times.map { it.amountText }).containsExactly("2", "2")
        assertThat(state.trackStock).isTrue()
        val draft = state.variants.single()
        assertThat(draft.id).isEqualTo("v1")
        assertThat(draft.stockText).isEqualTo("42")
        assertThat(draft.thresholdText).isEqualTo("10")
        assertThat(draft.refillText).isEqualTo("30")
    }

    @Test
    fun `prefill with untracked variant keeps stock off`() {
        val details =
            MedicationDetails(
                medication = medication(),
                schedules = listOf(schedule()),
                variants = listOf(variant(tracked = false)),
            )

        val state = prefillFrom(details)

        assertThat(state.trackStock).isFalse()
    }

    @Test
    fun `main schedule skips one-offs and closed versions`() {
        val oneOff =
            schedule(
                id = "one-off",
                startDate = LocalDate.parse("2026-08-20"),
                endDate = LocalDate.parse("2026-08-20"),
            )
        val closed = schedule(id = "old", endDate = LocalDate.parse("2026-08-15"))
        val current = schedule(id = "current")

        assertThat(mainScheduleOf(listOf(oneOff, closed, current))?.id).isEqualTo("current")
        assertThat(mainScheduleOf(listOf(oneOff, closed))).isNull()
    }

    @Test
    fun `schedule matches ignores dates and ids but not slots`() {
        val existing = schedule(id = "a", startDate = LocalDate.parse("2026-08-01"))
        val sameSlots = schedule(id = "b", startDate = LocalDate.parse("2026-09-01"))
        val movedTime = schedule(id = "c", times = listOf(LocalTime.of(9, 0)))
        val changedAmount = schedule(id = "d", amount = 2.0)

        assertThat(scheduleMatches(existing, sameSlots)).isTrue()
        assertThat(scheduleMatches(existing, movedTime)).isFalse()
        assertThat(scheduleMatches(existing, changedAmount)).isFalse()
    }

    @Test
    fun `schedule matches respects type-specific fields`() {
        val everyTwo = schedule(type = ScheduleType.EVERY_N_DAYS, intervalDays = 2)
        val everyThree = schedule(type = ScheduleType.EVERY_N_DAYS, intervalDays = 3)
        val cycle = schedule(type = ScheduleType.CYCLE, cycleDaysOn = 21, cycleDaysOff = 7, times = emptyList())
        val cycleShifted = schedule(type = ScheduleType.CYCLE, cycleDaysOn = 21, cycleDaysOff = 3, times = emptyList())
        val prnA = schedule(type = ScheduleType.AS_NEEDED, times = emptyList(), amount = 1.0)
        val prnB = schedule(type = ScheduleType.AS_NEEDED, times = emptyList(), amount = 2.0)

        assertThat(scheduleMatches(everyTwo, everyThree)).isFalse()
        assertThat(scheduleMatches(everyTwo, everyTwo.copy(id = "x"))).isTrue()
        assertThat(scheduleMatches(cycle, cycleShifted)).isFalse()
        assertThat(scheduleMatches(everyTwo, cycle)).isFalse()
        // PRN has no configurable slots — always the same plan.
        assertThat(scheduleMatches(prnA, prnB)).isTrue()
    }
}
