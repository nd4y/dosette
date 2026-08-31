package icu.nd4y.dosette.domain.alarm

import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.domain.model.ReminderState
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.nag.NagSettings
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class AlarmReason { DOSE, NAG, SNOOZE, GRACE, APPOINTMENT, HOUSEKEEPING, PLACE_POLL }

data class AlarmPlan(
    val at: Instant,
    val reason: AlarmReason,
)

/** Everything the planner weighs when picking the single next alarm. */
data class AlarmObligations(
    val schedules: List<Schedule>,
    val states: List<ReminderState>,
    val appointments: List<Appointment>,
)

/**
 * Computes the single next alarm: exactly one system alarm exists at any
 * moment, so the plan is the minimum over every pending obligation. Entries
 * already in the past are returned as-is — the engine fires them immediately.
 */
object AlarmPlanner {
    /** Housekeeping runs nightly to finalize missed doses of the previous day. */
    private val HOUSEKEEPING_TIME: LocalTime = LocalTime.of(0, 5)

    /** Wi-Fi fallback cadence while any reminder waits for a place. */
    private const val PLACE_POLL_MINUTES = 15L

    fun nextAlarm(
        now: Instant,
        zone: ZoneId,
        obligations: AlarmObligations,
        settings: NagSettings,
    ): AlarmPlan {
        val candidates = mutableListOf<AlarmPlan>()

        nextDose(obligations.schedules, now, zone)?.let(candidates::add)
        obligations.states.forEach { state -> candidates += stateCandidates(state, settings, now) }
        candidates += appointmentCandidates(obligations.appointments, now, zone)
        candidates += housekeeping(now, zone)

        return candidates.minBy { it.at }
    }

    private fun nextDose(
        schedules: List<Schedule>,
        now: Instant,
        zone: ZoneId,
    ): AlarmPlan? {
        val reminded = schedules.filter { it.remindersEnabled }
        val next = OccurrenceGenerator.nextOccurrenceAfter(reminded, now, zone) ?: return null
        return AlarmPlan(next.instantAt(zone), AlarmReason.DOSE)
    }

    private fun stateCandidates(
        state: ReminderState,
        settings: NagSettings,
        now: Instant,
    ): List<AlarmPlan> {
        val graceEnd =
            state.graceAnchor.plus(settings.missedGraceMin.toLong(), ChronoUnit.MINUTES)
        return when (state.phase) {
            ReminderPhase.ACTIVE -> {
                buildList {
                    add(AlarmPlan(graceEnd, AlarmReason.GRACE))
                    if (settings.nagIntervalMin > 0 && state.nagCount + 1 < settings.nagMaxCount) {
                        val tick =
                            state.lastAlertAt.plus(settings.nagIntervalMin.toLong(), ChronoUnit.MINUTES)
                        if (tick.isBefore(graceEnd)) add(AlarmPlan(tick, AlarmReason.NAG))
                    }
                }
            }

            ReminderPhase.SNOOZED -> {
                buildList {
                    if (state.snoozedUntilPlace != null) {
                        // No time expiry while waiting for a place: the geofence is
                        // the primary wake-up, this poll is the Wi-Fi fallback.
                        add(
                            AlarmPlan(
                                now.plus(PLACE_POLL_MINUTES, ChronoUnit.MINUTES),
                                AlarmReason.PLACE_POLL,
                            ),
                        )
                    } else {
                        // No GRACE alarm while snoozed: waking from a snooze
                        // restarts the grace window, so grace cannot end first.
                        state.snoozedUntil?.let { add(AlarmPlan(it, AlarmReason.SNOOZE)) }
                    }
                }
            }
        }
    }

    private fun appointmentCandidates(
        appointments: List<Appointment>,
        now: Instant,
        zone: ZoneId,
    ): List<AlarmPlan> =
        appointments.flatMap { appointment ->
            val startsAt =
                appointment.date
                    .atTime(appointment.time)
                    .atZone(zone)
                    .toInstant()
            appointment.reminderOffsetsMin
                .map { offset -> startsAt.minus(offset.toLong(), ChronoUnit.MINUTES) }
                .filter { it.isAfter(now) }
                .map { AlarmPlan(it, AlarmReason.APPOINTMENT) }
        }

    private fun housekeeping(
        now: Instant,
        zone: ZoneId,
    ): AlarmPlan {
        val today = now.atZone(zone).toLocalDate()
        val todayRun = today.atTime(HOUSEKEEPING_TIME).atZone(zone).toInstant()
        val next =
            if (todayRun.isAfter(
                    now,
                )
            ) {
                todayRun
            } else {
                today
                    .plusDays(1)
                    .atTime(HOUSEKEEPING_TIME)
                    .atZone(zone)
                    .toInstant()
            }
        return AlarmPlan(next, AlarmReason.HOUSEKEEPING)
    }
}
