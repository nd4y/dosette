package icu.nd4y.dosette.ui.today

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.schedule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val zone: ZoneId = ZoneId.of("Europe/Moscow")
private val editDay: LocalDate = LocalDate.parse("2026-09-02")

private fun medication(): Medication =
    Medication(
        id = "m1",
        profileId = "p1",
        name = "Метформин",
        form = MedicationForm.TABLET,
        strengthValue = 500.0,
        strengthUnit = "мг",
        instructions = null,
        colorSeed = 0,
        iconKey = "tablet",
        defaultVariantId = null,
        archivedAt = null,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

private fun takenLog(time: LocalTime): DoseLog {
    val at = editDay.atTime(time).atZone(zone).toInstant()
    return DoseLog(
        id = "log-$time",
        profileId = "p1",
        medicationId = "m1",
        scheduleId = "old",
        kind = DoseKind.SCHEDULED,
        date = editDay,
        time = time,
        scheduledAt = at,
        status = DoseStatus.TAKEN,
        actedAt = at,
        amount = 1.0,
        variantId = null,
        consumedUnits = null,
        note = null,
        updatedAt = at,
    )
}

private fun details(vararg schedules: Schedule) =
    MedicationDetails(medication = medication(), schedules = schedules.toList(), variants = emptyList())

class DayDosesTest {
    // Version "old" (08:00, 20:00) replaced at 21:00 on the edit day by
    // "new" (08:00, 14:00, 20:00).
    private val old =
        schedule(
            id = "old",
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            endDate = editDay.minusDays(1),
        )
    private val edited =
        schedule(
            id = "new",
            times = listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0)),
            startDate = editDay,
            createdAt = editDay.atTime(21, 0).atZone(zone).toInstant(),
        )

    @Test
    fun `a slot added after its time is not a dose of the day`() {
        val doses = buildDayDoses(editDay, listOf(details(old, edited)), emptyList(), zone)

        assertThat(doses.map { it.time }).containsExactly(LocalTime.of(8, 0), LocalTime.of(20, 0)).inOrder()
        assertThat(doses.none { it.oneOff }).isTrue()
    }

    @Test
    fun `a logged slot stays even when only a later version remains`() {
        // The same-day swap deletes the version the dose was taken under.
        val doses = buildDayDoses(editDay, listOf(details(edited)), listOf(takenLog(LocalTime.of(8, 0))), zone)

        assertThat(doses.map { it.time to it.status })
            .containsExactly(LocalTime.of(8, 0) to DoseUiStatus.TAKEN)
    }

    @Test
    fun `a medication added in the evening starts the next day`() {
        assertThat(buildDayDoses(editDay, listOf(details(edited)), emptyList(), zone)).isEmpty()
        assertThat(buildDayDoses(editDay.plusDays(1), listOf(details(edited)), emptyList(), zone)).hasSize(3)
    }

    @Test
    fun `the one-off flag comes from the schedule, not from its date window`() {
        // A regular version closed on the day it started is not a one-off.
        val closedSameDay = schedule(id = "closed", startDate = editDay, endDate = editDay)
        val once =
            schedule(
                id = "once",
                startDate = editDay,
                endDate = editDay,
                times = listOf(LocalTime.of(14, 0)),
                createdAt = editDay.atTime(16, 0).atZone(zone).toInstant(),
                oneOff = true,
            )

        val doses = buildDayDoses(editDay, listOf(details(closedSameDay, once)), emptyList(), zone)

        assertThat(doses.associate { it.time to it.oneOff })
            .containsExactly(LocalTime.of(8, 0), false, LocalTime.of(14, 0), true)
    }

    @Test
    fun `a mark whose slot moved away stays as history`() {
        // 08:00 was taken, then a same-day edit moved the slot to 09:00 and
        // swapped the version out.
        val moved =
            schedule(
                id = "moved",
                times = listOf(LocalTime.of(9, 0)),
                startDate = editDay,
                createdAt = editDay.atTime(8, 30).atZone(zone).toInstant(),
            )

        val doses = buildDayDoses(editDay, listOf(details(moved)), listOf(takenLog(LocalTime.of(8, 0))), zone)

        assertThat(doses.map { it.time to it.status })
            .containsExactly(LocalTime.of(8, 0) to DoseUiStatus.TAKEN, LocalTime.of(9, 0) to DoseUiStatus.PENDING)
            .inOrder()
    }
}
