package icu.nd4y.dosette.domain.schedule

import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The single source of truth for when doses happen. Pure: occurrences are
 * always computed from schedule rules, never materialized ahead of time.
 */
object OccurrenceGenerator {
    /** How far [nextOccurrenceAfter] searches before giving up. */
    private const val DEFAULT_HORIZON_DAYS = 366L

    fun isActiveOn(
        schedule: Schedule,
        date: LocalDate,
    ): Boolean =
        isInWindow(schedule, date) &&
            when (schedule.type) {
                ScheduleType.FIXED_TIMES -> true
                ScheduleType.WEEKDAYS -> date.dayOfWeek in schedule.weekdays
                ScheduleType.EVERY_N_DAYS -> matchesEveryNDays(schedule, date)
                ScheduleType.CYCLE -> matchesCycle(schedule, date)
                ScheduleType.AS_NEEDED -> false
            }

    private fun isInWindow(
        schedule: Schedule,
        date: LocalDate,
    ): Boolean {
        if (date.isBefore(schedule.startDate)) return false
        val end = schedule.endDate
        return end == null || !date.isAfter(end)
    }

    private fun matchesEveryNDays(
        schedule: Schedule,
        date: LocalDate,
    ): Boolean {
        val interval = schedule.intervalDays
        return interval != null &&
            interval >= 1 &&
            ChronoUnit.DAYS.between(schedule.startDate, date) % interval == 0L
    }

    private fun matchesCycle(
        schedule: Schedule,
        date: LocalDate,
    ): Boolean {
        val on = schedule.cycleDaysOn
        val off = schedule.cycleDaysOff
        return on != null &&
            off != null &&
            on >= 1 &&
            off >= 0 &&
            ChronoUnit.DAYS.between(schedule.startDate, date) % (on + off) < on
    }

    fun occurrencesOn(
        schedules: List<Schedule>,
        date: LocalDate,
    ): List<Occurrence> =
        schedules
            .filter { isActiveOn(it, date) }
            .flatMap { schedule ->
                schedule.times.map { slot ->
                    Occurrence(
                        medicationId = schedule.medicationId,
                        scheduleId = schedule.id,
                        date = date,
                        time = slot.time,
                        amount = slot.doseAmount,
                    )
                }
            }.sortedWith(compareBy({ it.time }, { it.medicationId }))

    fun occurrencesInRange(
        schedules: List<Schedule>,
        from: LocalDate,
        to: LocalDate,
    ): List<Occurrence> {
        require(!to.isBefore(from)) { "range end $to is before start $from" }
        return generateSequence(from) { prev -> prev.plusDays(1).takeIf { !it.isAfter(to) } }
            .flatMap { occurrencesOn(schedules, it) }
            .toList()
    }

    /** Earliest occurrence strictly after [after]; null when nothing is planned within the horizon. */
    fun nextOccurrenceAfter(
        schedules: List<Schedule>,
        after: Instant,
        zone: ZoneId,
        horizonDays: Long = DEFAULT_HORIZON_DAYS,
    ): Occurrence? {
        val firstDate = after.atZone(zone).toLocalDate()
        for (offset in 0..horizonDays) {
            val date = firstDate.plusDays(offset)
            val candidate =
                occurrencesOn(schedules, date)
                    .filter { it.instantAt(zone).isAfter(after) }
                    .minByOrNull { it.instantAt(zone) }
            if (candidate != null) return candidate
        }
        return null
    }
}
