package icu.nd4y.dosette.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Schedules are immutable versions: an edit closes the current row
 * (sets [endDate]) and inserts a new one effective from the edit date.
 * Past occurrences therefore always recompute identically.
 */
data class Schedule(
    val id: String,
    val medicationId: String,
    val type: ScheduleType,
    /** First day of the schedule, inclusive. */
    val startDate: LocalDate,
    /** Last day of the schedule, inclusive. Null = open-ended. */
    val endDate: LocalDate?,
    /**
     * Day the every-N / cycle count runs from; null = [startDate]. Set when
     * an edit keeps the rhythm of the version it replaces.
     */
    val anchorDate: LocalDate? = null,
    /**
     * Single-day dose added from the calendar: deletable as a whole, and
     * planned explicitly even for a time already in the past.
     */
    val oneOff: Boolean = false,
    /** Days of week for [ScheduleType.WEEKDAYS]. */
    val weekdays: Set<DayOfWeek>,
    /** Interval for [ScheduleType.EVERY_N_DAYS], anchored to [anchorDate], falling back to [startDate]. */
    val intervalDays: Int?,
    /** Days on for [ScheduleType.CYCLE]. */
    val cycleDaysOn: Int?,
    /** Days off for [ScheduleType.CYCLE]. */
    val cycleDaysOff: Int?,
    /** Dose amount for as-needed intake. */
    val defaultDoseAmount: Double,
    val remindersEnabled: Boolean,
    val createdAt: Instant,
    val times: List<ScheduleTime>,
)

data class ScheduleTime(
    val id: String,
    val scheduleId: String,
    val time: LocalTime,
    val doseAmount: Double,
    val sortIndex: Int,
)

enum class ScheduleType {
    FIXED_TIMES,
    WEEKDAYS,
    EVERY_N_DAYS,
    CYCLE,
    AS_NEEDED,
}
