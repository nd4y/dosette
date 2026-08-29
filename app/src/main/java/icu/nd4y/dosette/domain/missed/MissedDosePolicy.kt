package icu.nd4y.dosette.domain.missed

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A dose becomes MISSED once its grace window has fully passed. Finalization
 * itself is done by callers via the idempotent dose-log write, so running
 * this check twice can never double-write.
 */
object MissedDosePolicy {
    fun isMissed(
        scheduledAt: Instant,
        now: Instant,
        graceMin: Int,
    ): Boolean {
        require(graceMin >= 0) { "negative grace" }
        return now.isAfter(scheduledAt.plus(graceMin.toLong(), ChronoUnit.MINUTES))
    }

    /** Occurrences scheduled at or before this instant are already missed. */
    fun missedCutoff(
        now: Instant,
        graceMin: Int,
    ): Instant {
        require(graceMin >= 0) { "negative grace" }
        return now.minus(graceMin.toLong(), ChronoUnit.MINUTES)
    }
}
