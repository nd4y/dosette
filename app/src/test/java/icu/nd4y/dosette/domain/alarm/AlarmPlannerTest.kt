package icu.nd4y.dosette.domain.alarm

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.domain.model.ReminderState
import icu.nd4y.dosette.domain.nag.NagSettings
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import icu.nd4y.dosette.domain.schedule as fixture

class AlarmPlannerTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val settings = NagSettings(nagIntervalMin = 10, nagMaxCount = 6, snoozeMin = 10, missedGraceMin = 60)

    // Saturday 2026-08-29 12:00 Moscow.
    private val now: Instant =
        LocalDate
            .parse("2026-08-29")
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()

    private fun state(
        phase: ReminderPhase = ReminderPhase.ACTIVE,
        scheduledAt: Instant = now.minusSeconds(300),
        lastAlertAt: Instant = now.minusSeconds(300),
        nagCount: Int = 0,
        snoozedUntil: Instant? = null,
    ) = ReminderState(
        occurrenceKey = OccurrenceKey("m1", LocalDate.parse("2026-08-29"), LocalTime.of(8, 0)),
        medicationId = "m1",
        profileId = "p1",
        scheduledAt = scheduledAt,
        phase = phase,
        snoozedUntil = snoozedUntil,
        nagCount = nagCount,
        firstNotifiedAt = scheduledAt,
        lastAlertAt = lastAlertAt,
    )

    @Test
    fun `with nothing pending the housekeeping alarm remains`() {
        val plan = AlarmPlanner.nextAlarm(now, zone, AlarmObligations(emptyList(), emptyList(), emptyList()), settings)

        assertThat(plan.reason).isEqualTo(AlarmReason.HOUSEKEEPING)
        assertThat(plan.at.atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(0, 5))
        assertThat(plan.at.atZone(zone).toLocalDate()).isEqualTo(LocalDate.parse("2026-08-30"))
    }

    @Test
    fun `upcoming dose wins over housekeeping`() {
        val schedule = fixture(times = listOf(LocalTime.of(20, 0)))

        val plan =
            AlarmPlanner.nextAlarm(
                now,
                zone,
                AlarmObligations(listOf(schedule), emptyList(), emptyList()),
                settings,
            )

        assertThat(plan.reason).isEqualTo(AlarmReason.DOSE)
        assertThat(plan.at.atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(20, 0))
    }

    @Test
    fun `schedules with reminders disabled produce no dose alarms`() {
        val silent = fixture(times = listOf(LocalTime.of(20, 0))).copy(remindersEnabled = false)

        val plan =
            AlarmPlanner.nextAlarm(
                now,
                zone,
                AlarmObligations(listOf(silent), emptyList(), emptyList()),
                settings,
            )

        assertThat(plan.reason).isEqualTo(AlarmReason.HOUSEKEEPING)
    }

    @Test
    fun `active reminder schedules the next nag tick`() {
        val plan =
            AlarmPlanner.nextAlarm(now, zone, AlarmObligations(emptyList(), listOf(state()), emptyList()), settings)

        assertThat(plan.reason).isEqualTo(AlarmReason.NAG)
        assertThat(plan.at).isEqualTo(now.minusSeconds(300).plusSeconds(600))
    }

    @Test
    fun `nag interval off leaves only the grace deadline`() {
        val noNag = settings.copy(nagIntervalMin = 0)

        val plan = AlarmPlanner.nextAlarm(now, zone, AlarmObligations(emptyList(), listOf(state()), emptyList()), noNag)

        assertThat(plan.reason).isEqualTo(AlarmReason.GRACE)
    }

    @Test
    fun `exhausted nag counter stops ticking before the grace deadline`() {
        val plan =
            AlarmPlanner.nextAlarm(
                now,
                zone,
                AlarmObligations(emptyList(), listOf(state(nagCount = 5)), emptyList()),
                settings,
            )

        assertThat(plan.reason).isEqualTo(AlarmReason.GRACE)
    }

    @Test
    fun `snoozed reminder wakes at snoozedUntil`() {
        val snoozed =
            state(
                phase = ReminderPhase.SNOOZED,
                snoozedUntil = now.plusSeconds(300),
            )

        val plan =
            AlarmPlanner.nextAlarm(
                now,
                zone,
                AlarmObligations(emptyList(), listOf(snoozed), emptyList()),
                settings,
            )

        assertThat(plan.reason).isEqualTo(AlarmReason.SNOOZE)
        assertThat(plan.at).isEqualTo(now.plusSeconds(300))
    }

    @Test
    fun `appointment reminders expand offsets and skip past ones`() {
        val appointment =
            Appointment(
                id = "a1",
                profileId = "p1",
                title = "Кардиолог",
                doctorName = null,
                location = null,
                date = LocalDate.parse("2026-08-29"),
                time = LocalTime.of(14, 0),
                notes = null,
                // 24h before is already past; 90 min before is upcoming.
                reminderOffsetsMin = listOf(1440, 90),
                createdAt = now,
            )

        val plan =
            AlarmPlanner.nextAlarm(
                now,
                zone,
                AlarmObligations(emptyList(), emptyList(), listOf(appointment)),
                settings,
            )

        assertThat(plan.reason).isEqualTo(AlarmReason.APPOINTMENT)
        assertThat(plan.at.atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(12, 30))
    }

    @Test
    fun `earliest obligation wins across kinds`() {
        val schedule = fixture(times = listOf(LocalTime.of(20, 0)))
        val snoozed = state(phase = ReminderPhase.SNOOZED, snoozedUntil = now.plusSeconds(120))

        val plan =
            AlarmPlanner.nextAlarm(
                now,
                zone,
                AlarmObligations(listOf(schedule), listOf(snoozed), emptyList()),
                settings,
            )

        assertThat(plan.reason).isEqualTo(AlarmReason.SNOOZE)
    }

    @Test
    fun `overdue obligations are returned as-is for immediate processing`() {
        val overdue = state(scheduledAt = now.minusSeconds(7200), lastAlertAt = now.minusSeconds(7200))

        val plan =
            AlarmPlanner.nextAlarm(
                now,
                zone,
                AlarmObligations(emptyList(), listOf(overdue), emptyList()),
                settings,
            )

        assertThat(plan.at).isLessThan(now)
    }
}
