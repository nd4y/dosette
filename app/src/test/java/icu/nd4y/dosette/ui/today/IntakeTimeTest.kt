package icu.nd4y.dosette.ui.today

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.OccurrenceKey
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IntakeTimeTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val day: LocalDate = LocalDate.parse("2026-09-08")

    private fun at(
        date: LocalDate,
        time: LocalTime,
    ): Instant = date.atTime(time).atZone(zone).toInstant()

    private fun key(time: LocalTime) = OccurrenceKey("m1", day, time)

    @Test
    fun `now leaves the stamp to the engine`() {
        assertThat(IntakeTime.Now.resolve(key(LocalTime.of(8, 0)), zone)).isNull()
    }

    @Test
    fun `on time is the planned instant`() {
        assertThat(IntakeTime.OnTime.resolve(key(LocalTime.of(8, 0)), zone))
            .isEqualTo(at(day, LocalTime.of(8, 0)))
    }

    @Test
    fun `a picked time lands on the planned day`() {
        val key = key(LocalTime.of(8, 0))
        assertThat(IntakeTime.At(LocalTime.of(7, 30)).resolve(key, zone)).isEqualTo(at(day, LocalTime.of(7, 30)))
        assertThat(IntakeTime.At(LocalTime.of(19, 0)).resolve(key, zone)).isEqualTo(at(day, LocalTime.of(19, 0)))
    }

    @Test
    fun `a small-hours time for a late-evening dose is the next morning`() {
        assertThat(IntakeTime.At(LocalTime.of(0, 30)).resolve(key(LocalTime.of(23, 0)), zone))
            .isEqualTo(at(day.plusDays(1), LocalTime.of(0, 30)))
    }

    @Test
    fun `a late-evening time for a small-hours dose is the night before`() {
        assertThat(IntakeTime.At(LocalTime.of(23, 40)).resolve(key(LocalTime.of(1, 0)), zone))
            .isEqualTo(at(day.minusDays(1), LocalTime.of(23, 40)))
    }
}
