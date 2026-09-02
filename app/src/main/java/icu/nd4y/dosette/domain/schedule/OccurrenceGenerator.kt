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
    ): Boolean = schedule.isInWindow(date) && schedule.matchesPattern(date)

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

    /**
     * [occurrencesOn] minus the slots that were not part of the plan when
     * their time came. A version inserted at 21:00 with an 08:00 slot did
     * not plan that morning's dose, so a medication added in the evening
     * (or a slot added by an edit) must not start with a missed dose. A
     * slot an earlier version already had that day — ignoring the close
     * date the edit gave it — keeps its identity across the edit. One-off
     * doses are planned explicitly, in the past too, and are never dropped.
     */
    fun plannedOccurrencesOn(
        schedules: List<Schedule>,
        date: LocalDate,
        zone: ZoneId,
    ): List<Occurrence> {
        val byId = schedules.associateBy { it.id }
        return occurrencesOn(schedules, date).filter { occurrence ->
            val version = byId.getValue(occurrence.scheduleId)
            version.oneOff || wasPlanned(schedules, version, occurrence, zone)
        }
    }

    fun plannedOccurrencesInRange(
        schedules: List<Schedule>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): List<Occurrence> {
        require(!to.isBefore(from)) { "range end $to is before start $from" }
        return generateSequence(from) { prev -> prev.plusDays(1).takeIf { !it.isAfter(to) } }
            .flatMap { plannedOccurrencesOn(schedules, it, zone) }
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

private fun wasPlanned(
    schedules: List<Schedule>,
    version: Schedule,
    occurrence: Occurrence,
    zone: ZoneId,
): Boolean {
    val at = occurrence.instantAt(zone)
    if (!at.isBefore(version.createdAt)) return true
    return schedules.any { earlier ->
        earlier.id != version.id &&
            earlier.medicationId == version.medicationId &&
            !earlier.oneOff &&
            !earlier.createdAt.isAfter(at) &&
            !occurrence.date.isBefore(earlier.startDate) &&
            earlier.matchesPattern(occurrence.date) &&
            earlier.times.any { it.time == occurrence.time }
    }
}

private fun Schedule.isInWindow(date: LocalDate): Boolean {
    if (date.isBefore(startDate)) return false
    val end = endDate
    return end == null || !date.isAfter(end)
}

/** The day pattern alone, regardless of the version's date window. */
private fun Schedule.matchesPattern(date: LocalDate): Boolean =
    when (type) {
        ScheduleType.FIXED_TIMES -> true
        ScheduleType.WEEKDAYS -> date.dayOfWeek in weekdays
        ScheduleType.EVERY_N_DAYS -> matchesEveryNDays(date)
        ScheduleType.CYCLE -> matchesCycle(date)
        ScheduleType.AS_NEEDED -> false
    }

/** Day the every-N / cycle count runs from: the anchor an edit carried over, else the version start. */
private val Schedule.anchor: LocalDate
    get() = anchorDate ?: startDate

private fun Schedule.matchesEveryNDays(date: LocalDate): Boolean {
    val interval = intervalDays
    return interval != null &&
        interval >= 1 &&
        ChronoUnit.DAYS.between(anchor, date).mod(interval.toLong()) == 0L
}

private fun Schedule.matchesCycle(date: LocalDate): Boolean {
    val on = cycleDaysOn
    val off = cycleDaysOff
    return on != null &&
        off != null &&
        on >= 1 &&
        off >= 0 &&
        ChronoUnit.DAYS.between(anchor, date).mod((on + off).toLong()) < on
}
