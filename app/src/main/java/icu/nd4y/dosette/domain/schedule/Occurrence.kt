package icu.nd4y.dosette.domain.schedule

import icu.nd4y.dosette.domain.model.OccurrenceKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One planned intake of one medication, computed from schedules — never stored. */
data class Occurrence(
    val medicationId: String,
    val scheduleId: String,
    val date: LocalDate,
    val time: LocalTime,
    val amount: Double,
) {
    val key: OccurrenceKey get() = OccurrenceKey(medicationId, date, time)

    /**
     * Wall clock resolved against a zone at the moment of use.
     * DST gap shifts forward (02:30 -> 03:30), DST overlap takes the earlier offset —
     * both are java.time defaults and both are covered by tests.
     */
    fun instantAt(zone: ZoneId): Instant = date.atTime(time).atZone(zone).toInstant()
}
