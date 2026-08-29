package icu.nd4y.dosette.data.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `instant round trip`() {
        val instant = Instant.parse("2026-08-29T20:00:00Z")
        assertThat(converters.longToInstant(converters.instantToLong(instant))).isEqualTo(instant)
    }

    @Test
    fun `null instant stays null`() {
        assertThat(converters.instantToLong(null)).isNull()
        assertThat(converters.longToInstant(null)).isNull()
    }

    @Test
    fun `local date round trip`() {
        val date = LocalDate.parse("2026-02-28")
        assertThat(converters.stringToLocalDate(converters.localDateToString(date))).isEqualTo(date)
    }

    @Test
    fun `iso dates sort lexicographically like chronologically`() {
        val earlier = converters.localDateToString(LocalDate.parse("2026-09-30"))!!
        val later = converters.localDateToString(LocalDate.parse("2026-10-01"))!!
        assertThat(earlier < later).isTrue()
    }
}
