package icu.nd4y.dosette.ui.meddetail

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.stats.AdherenceCalculator.DayStatus
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private fun log(
    date: String,
    status: DoseStatus,
    medicationId: String = "m1",
    kind: DoseKind = DoseKind.SCHEDULED,
): DoseLog =
    DoseLog(
        id = "$medicationId-$date-$status-${System.nanoTime()}",
        profileId = "p1",
        medicationId = medicationId,
        scheduleId = "s1",
        kind = kind,
        date = LocalDate.parse(date),
        time = LocalTime.of(8, 0),
        scheduledAt = null,
        status = status,
        actedAt = null,
        amount = 1.0,
        variantId = null,
        consumedUnits = null,
        note = null,
        updatedAt = Instant.parse("2026-08-30T00:00:00Z"),
    )

class AdherenceDaysTest {
    private val from = LocalDate.parse("2026-08-01")
    private val to = LocalDate.parse("2026-08-05")

    @Test
    fun `days cover the whole range with per-day statuses`() {
        val logs =
            listOf(
                log("2026-08-01", DoseStatus.TAKEN),
                log("2026-08-02", DoseStatus.TAKEN),
                log("2026-08-02", DoseStatus.MISSED),
                log("2026-08-03", DoseStatus.MISSED),
                log("2026-08-04", DoseStatus.SKIPPED),
            )

        val days = adherenceDays(logs, "m1", from, to)

        assertThat(days.map { it.date })
            .containsExactly(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-02"),
                LocalDate.parse("2026-08-03"),
                LocalDate.parse("2026-08-04"),
                LocalDate.parse("2026-08-05"),
            ).inOrder()
        assertThat(days.map { it.status })
            .containsExactly(
                DayStatus.COMPLETE,
                DayStatus.PARTIAL,
                DayStatus.ALL_MISSED,
                DayStatus.COMPLETE,
                DayStatus.NONE,
            ).inOrder()
    }

    @Test
    fun `other medications and prn intakes are excluded`() {
        val logs =
            listOf(
                log("2026-08-01", DoseStatus.MISSED, medicationId = "other"),
                log("2026-08-01", DoseStatus.MISSED, kind = DoseKind.PRN),
            )

        val days = adherenceDays(logs, "m1", from, to)

        assertThat(days.all { it.status == DayStatus.NONE }).isTrue()
        assertThat(adherencePercent(logs, "m1")).isNull()
    }

    @Test
    fun `percent counts taken against missed and ignores skipped`() {
        val logs =
            listOf(
                log("2026-08-01", DoseStatus.TAKEN),
                log("2026-08-02", DoseStatus.TAKEN),
                log("2026-08-03", DoseStatus.TAKEN),
                log("2026-08-04", DoseStatus.MISSED),
                log("2026-08-05", DoseStatus.SKIPPED),
            )

        assertThat(adherencePercent(logs, "m1")).isEqualTo(75)
    }
}
