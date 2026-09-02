package icu.nd4y.dosette.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A [Clock] the test moves by hand; the engine reads it afresh on every pass. */
class MutableClock(
    var current: Instant,
    private val zoneId: ZoneId,
) : Clock() {
    override fun instant(): Instant = current

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    fun advance(duration: Duration) {
        current += duration
    }
}
