package icu.nd4y.dosette.ui.today

import icu.nd4y.dosette.domain.model.OccurrenceKey
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** When a dose marked taken was actually taken. */
sealed interface IntakeTime {
    /** The moment of the tap — what a plain Take records. */
    data object Now : IntakeTime

    /** Exactly the planned time. */
    data object OnTime : IntakeTime

    /** A wall-clock time the user picked, read against the planned day. */
    data class At(
        val time: LocalTime,
    ) : IntakeTime
}

/**
 * The instant a stated intake time means for [key]; null for [IntakeTime.Now],
 * which the engine stamps itself. A picked time lands on whichever day puts
 * it closest to the plan: 00:30 for a 23:00 dose is half past midnight after
 * it, not the small hours before.
 */
fun IntakeTime.resolve(
    key: OccurrenceKey,
    zone: ZoneId,
): Instant? {
    val planned =
        key.date
            .atTime(key.time)
            .atZone(zone)
            .toInstant()
    return when (this) {
        IntakeTime.Now -> {
            null
        }

        IntakeTime.OnTime -> {
            planned
        }

        is IntakeTime.At -> {
            (-1L..1L)
                .map {
                    key.date
                        .plusDays(it)
                        .atTime(time)
                        .atZone(zone)
                        .toInstant()
                }.minBy { Duration.between(planned, it).abs() }
        }
    }
}
