package icu.nd4y.dosette.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OccurrenceKeyTest {
    @Test
    fun `encode decode round trip`() {
        val key = OccurrenceKey("med-42", LocalDate.parse("2026-08-29"), LocalTime.of(8, 0))
        assertThat(OccurrenceKey.decode(key.encode())).isEqualTo(key)
    }

    @Test
    fun `encoded form is stable`() {
        val key = OccurrenceKey("m1", LocalDate.parse("2026-01-05"), LocalTime.of(20, 30))
        assertThat(key.encode()).isEqualTo("m1|2026-01-05|20:30")
    }

    @Test
    fun `midnight encodes as zeros`() {
        val key = OccurrenceKey("m1", LocalDate.parse("2026-01-05"), LocalTime.MIDNIGHT)
        assertThat(OccurrenceKey.decode(key.encode())).isEqualTo(key)
    }
}
