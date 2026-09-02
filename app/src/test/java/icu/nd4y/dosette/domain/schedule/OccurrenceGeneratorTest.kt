package icu.nd4y.dosette.domain.schedule

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator.isActiveOn
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator.nextOccurrenceAfter
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator.occurrencesInRange
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator.occurrencesOn
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import icu.nd4y.dosette.domain.schedule as fixture

class OccurrenceGeneratorTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `fixed times fires every day with all slots sorted`() {
        val s = fixture(times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0)))
        val occurrences = occurrencesOn(listOf(s), LocalDate.parse("2026-08-29"))
        assertThat(occurrences.map { it.time }).containsExactly(LocalTime.of(8, 0), LocalTime.of(20, 0)).inOrder()
    }

    @Test
    fun `start and end bounds are inclusive`() {
        val s =
            fixture(
                startDate = LocalDate.parse("2026-08-10"),
                endDate = LocalDate.parse("2026-08-20"),
            )
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-09"))).isFalse()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-10"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-20"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-21"))).isFalse()
    }

    @Test
    fun `weekdays fires only on selected days`() {
        val s =
            fixture(
                type = ScheduleType.WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            )
        // 2026-08-24 is a Monday.
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-24"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-25"))).isFalse()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-26"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-27"))).isFalse()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-28"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-29"))).isFalse()
    }

    @Test
    fun `every n days anchors to start date`() {
        val s =
            fixture(
                type = ScheduleType.EVERY_N_DAYS,
                startDate = LocalDate.parse("2026-08-01"),
                intervalDays = 2,
            )
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-01"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-02"))).isFalse()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-03"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-31"))).isTrue()
    }

    @Test
    fun `every n days with invalid interval never fires`() {
        val s = fixture(type = ScheduleType.EVERY_N_DAYS, intervalDays = 0)
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-01"))).isFalse()
    }

    @Test
    fun `cycle runs on days then off days`() {
        val s =
            fixture(
                type = ScheduleType.CYCLE,
                startDate = LocalDate.parse("2026-08-01"),
                cycleDaysOn = 21,
                cycleDaysOff = 7,
            )
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-01"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-21"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-22"))).isFalse()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-28"))).isFalse()
        // Day 28 = start of the second cycle.
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-29"))).isTrue()
    }

    @Test
    fun `cycle with zero off days is continuous`() {
        val s = fixture(type = ScheduleType.CYCLE, cycleDaysOn = 5, cycleDaysOff = 0)
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-06"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-09-15"))).isTrue()
    }

    @Test
    fun `as needed never generates occurrences`() {
        val s = fixture(type = ScheduleType.AS_NEEDED)
        assertThat(occurrencesOn(listOf(s), LocalDate.parse("2026-08-29"))).isEmpty()
    }

    @Test
    fun `range aggregates all days in order`() {
        val s = fixture(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val occurrences =
            occurrencesInRange(listOf(s), LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"))
        assertThat(occurrences).hasSize(6)
        assertThat(occurrences.first().date).isEqualTo(LocalDate.parse("2026-08-01"))
        assertThat(occurrences.last().date).isEqualTo(LocalDate.parse("2026-08-03"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inverted range throws`() {
        occurrencesInRange(listOf(fixture()), LocalDate.parse("2026-08-02"), LocalDate.parse("2026-08-01"))
    }

    @Test
    fun `versioned schedules do not overlap on the boundary`() {
        val old =
            fixture(
                id = "s1",
                times = listOf(LocalTime.of(8, 0)),
                endDate = LocalDate.parse("2026-08-28"),
            )
        val replacement =
            fixture(
                id = "s2",
                times = listOf(LocalTime.of(9, 0)),
                startDate = LocalDate.parse("2026-08-29"),
            )

        val boundaryEve = occurrencesOn(listOf(old, replacement), LocalDate.parse("2026-08-28"))
        val boundaryDay = occurrencesOn(listOf(old, replacement), LocalDate.parse("2026-08-29"))

        assertThat(boundaryEve.map { it.time }).containsExactly(LocalTime.of(8, 0))
        assertThat(boundaryDay.map { it.time }).containsExactly(LocalTime.of(9, 0))
    }

    @Test
    fun `next occurrence prefers a later slot the same day`() {
        val s = fixture(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val after =
            LocalDate
                .parse("2026-08-29")
                .atTime(10, 0)
                .atZone(zone)
                .toInstant()

        val next = nextOccurrenceAfter(listOf(s), after, zone)

        assertThat(next?.date).isEqualTo(LocalDate.parse("2026-08-29"))
        assertThat(next?.time).isEqualTo(LocalTime.of(20, 0))
    }

    @Test
    fun `next occurrence rolls to the next active day`() {
        val s =
            fixture(
                type = ScheduleType.WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY),
                times = listOf(LocalTime.of(8, 0)),
            )
        // Saturday evening -> Monday morning.
        val after =
            LocalDate
                .parse("2026-08-29")
                .atTime(21, 0)
                .atZone(zone)
                .toInstant()

        val next = nextOccurrenceAfter(listOf(s), after, zone)

        assertThat(next?.date).isEqualTo(LocalDate.parse("2026-08-31"))
    }

    @Test
    fun `next occurrence is strictly after the reference instant`() {
        val s = fixture(times = listOf(LocalTime.of(8, 0)))
        val exactlyAtSlot =
            LocalDate
                .parse("2026-08-29")
                .atTime(8, 0)
                .atZone(zone)
                .toInstant()

        val next = nextOccurrenceAfter(listOf(s), exactlyAtSlot, zone)

        assertThat(next?.date).isEqualTo(LocalDate.parse("2026-08-30"))
    }

    @Test
    fun `next occurrence is null when the schedule has ended`() {
        val s = fixture(endDate = LocalDate.parse("2026-08-28"))
        val after =
            LocalDate
                .parse("2026-08-29")
                .atTime(0, 0)
                .atZone(zone)
                .toInstant()

        assertThat(nextOccurrenceAfter(listOf(s), after, zone)).isNull()
    }

    @Test
    fun `spring forward gap shifts the dose forward`() {
        // Europe/Berlin 2026-03-29: 02:00 -> 03:00, 02:30 does not exist.
        val s = fixture(startDate = LocalDate.parse("2026-03-01"), times = listOf(LocalTime.of(2, 30)))
        val occurrence = occurrencesOn(listOf(s), LocalDate.parse("2026-03-29")).single()

        val resolved = occurrence.instantAt(zone).atZone(zone)

        assertThat(resolved.toLocalTime()).isEqualTo(LocalTime.of(3, 30))
        assertThat(resolved.offset.id).isEqualTo("+02:00")
    }

    @Test
    fun `fall back overlap takes the earlier offset`() {
        // Europe/Berlin 2026-10-25: 03:00 -> 02:00, 02:30 happens twice.
        val s = fixture(startDate = LocalDate.parse("2026-10-01"), times = listOf(LocalTime.of(2, 30)))
        val occurrence = occurrencesOn(listOf(s), LocalDate.parse("2026-10-25")).single()

        val resolved = occurrence.instantAt(zone).atZone(zone)

        assertThat(resolved.offset.id).isEqualTo("+02:00")
    }

    @Test
    fun `occurrences from different medications interleave by time`() {
        val a = fixture(id = "s1", medicationId = "mA", times = listOf(LocalTime.of(9, 0)))
        val b = fixture(id = "s2", medicationId = "mB", times = listOf(LocalTime.of(8, 0)))

        val occurrences = occurrencesOn(listOf(a, b), LocalDate.parse("2026-08-29"))

        assertThat(occurrences.map { it.medicationId }).containsExactly("mB", "mA").inOrder()
    }

    @Test
    fun `occurrence key carries wall clock identity`() {
        val s = fixture()
        val occurrence = occurrencesOn(listOf(s), LocalDate.parse("2026-08-29")).single()
        assertThat(occurrence.key.encode()).isEqualTo("m1|2026-08-29|08:00")
    }

    @Test
    fun `every n days counts from the anchor when one is set`() {
        // An edited version starting on the 4th keeps the rhythm of the
        // original that started on the 1st: doses stay on odd days.
        val s =
            fixture(
                type = ScheduleType.EVERY_N_DAYS,
                startDate = LocalDate.parse("2026-08-04"),
                anchorDate = LocalDate.parse("2026-08-01"),
                intervalDays = 2,
            )
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-04"))).isFalse()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-05"))).isTrue()
        // The anchor does not widen the window: before the start is still off.
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-01"))).isFalse()
    }

    @Test
    fun `an anchor after the start still gives a stable rhythm`() {
        val s =
            fixture(
                type = ScheduleType.EVERY_N_DAYS,
                startDate = LocalDate.parse("2026-08-01"),
                anchorDate = LocalDate.parse("2026-08-10"),
                intervalDays = 3,
            )
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-01"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-02"))).isFalse()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-07"))).isTrue()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-10"))).isTrue()
    }

    @Test
    fun `cycle counts from the anchor when one is set`() {
        // 21 on / 7 off from the 1st; the version replaced on day 25 (an
        // off day) stays off until the 29th instead of restarting the cycle.
        val s =
            fixture(
                type = ScheduleType.CYCLE,
                startDate = LocalDate.parse("2026-08-25"),
                anchorDate = LocalDate.parse("2026-08-01"),
                cycleDaysOn = 21,
                cycleDaysOff = 7,
            )
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-25"))).isFalse()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-28"))).isFalse()
        assertThat(isActiveOn(s, LocalDate.parse("2026-08-29"))).isTrue()
    }

    @Test
    fun `a slot before its version was created is not planned`() {
        // Medication added at 21:00 with an 08:00 slot: that morning's dose
        // was never in the plan, so it must not turn up as missed.
        val day = LocalDate.parse("2026-09-02")
        val s =
            fixture(
                times = listOf(LocalTime.of(8, 0), LocalTime.of(22, 0)),
                startDate = day,
                createdAt = day.atTime(21, 0).atZone(zone).toInstant(),
            )

        val planned = OccurrenceGenerator.plannedOccurrencesOn(listOf(s), day, zone)

        assertThat(planned.map { it.time }).containsExactly(LocalTime.of(22, 0))
        // From the next day on every slot is planned.
        assertThat(OccurrenceGenerator.plannedOccurrencesOn(listOf(s), day.plusDays(1), zone)).hasSize(2)
    }

    @Test
    fun `a slot the replaced version already had keeps its identity across the edit`() {
        val editDay = LocalDate.parse("2026-09-02")
        val old =
            fixture(
                id = "old",
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                endDate = editDay.minusDays(1),
            )
        val edited =
            fixture(
                id = "new",
                times = listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0)),
                startDate = editDay,
                createdAt = editDay.atTime(21, 0).atZone(zone).toInstant(),
            )

        val planned = OccurrenceGenerator.plannedOccurrencesOn(listOf(old, edited), editDay, zone)

        // 08:00 and 20:00 existed before the edit; 14:00 was added after its time had passed.
        assertThat(planned.map { it.time }).containsExactly(LocalTime.of(8, 0), LocalTime.of(20, 0)).inOrder()
        assertThat(planned.map { it.scheduleId }.toSet()).containsExactly("new")
    }

    @Test
    fun `a one-off dose is planned even in the past`() {
        val day = LocalDate.parse("2026-09-02")
        val oneOff =
            fixture(
                id = "once",
                startDate = day,
                endDate = day,
                times = listOf(LocalTime.of(14, 0)),
                createdAt = day.atTime(16, 0).atZone(zone).toInstant(),
                oneOff = true,
            )

        assertThat(OccurrenceGenerator.plannedOccurrencesOn(listOf(oneOff), day, zone)).hasSize(1)
    }
}
