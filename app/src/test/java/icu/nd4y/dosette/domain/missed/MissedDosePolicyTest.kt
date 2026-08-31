package icu.nd4y.dosette.domain.missed

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class MissedDosePolicyTest {
    private val scheduledAt = Instant.parse("2026-08-29T20:00:00Z")

    @Test
    fun `dose inside the grace window is not missed`() {
        val now = Instant.parse("2026-08-29T20:59:59Z")
        assertThat(MissedDosePolicy.isMissed(scheduledAt, now, graceMin = 60)).isFalse()
    }

    @Test
    fun `dose exactly at the cutoff is not missed yet`() {
        val now = Instant.parse("2026-08-29T21:00:00Z")
        assertThat(MissedDosePolicy.isMissed(scheduledAt, now, graceMin = 60)).isFalse()
    }

    @Test
    fun `dose past the cutoff is missed`() {
        val now = Instant.parse("2026-08-29T21:00:01Z")
        assertThat(MissedDosePolicy.isMissed(scheduledAt, now, graceMin = 60)).isTrue()
    }

    @Test
    fun `zero grace misses right after the scheduled time`() {
        assertThat(
            MissedDosePolicy.isMissed(scheduledAt, Instant.parse("2026-08-29T20:00:01Z"), graceMin = 0),
        ).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative grace is rejected`() {
        MissedDosePolicy.isMissed(scheduledAt, scheduledAt, graceMin = -1)
    }
}
