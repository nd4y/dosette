package icu.nd4y.dosette.wear.tile

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.link.WatchDose
import icu.nd4y.dosette.link.WatchDoseStatus
import icu.nd4y.dosette.link.WatchSnapshot
import org.junit.Test
import java.time.LocalDate

class TileModelTest {
    private val today = LocalDate.of(2026, 11, 13)

    @Test fun `no data and saved old day cannot claim today is complete`() {
        assertThat(tileModel(null, today).hasData).isFalse()
        assertThat(tileModel(WatchSnapshot(date = "2026-11-12"), today).stale).isTrue()
    }

    @Test fun `yesterday pending is first and taken medicine is excluded`() {
        val doses =
            listOf(
                WatchDose("today", "Today", timeMinutes = 10, status = WatchDoseStatus.PENDING),
                WatchDose("old", "Yesterday", timeMinutes = 1430, status = WatchDoseStatus.PENDING, carryover = true),
                WatchDose("taken", "Taken", timeMinutes = 0, status = WatchDoseStatus.TAKEN),
            )
        val model = tileModel(WatchSnapshot(date = today.toString(), doses = doses), today)
        assertThat(model.remaining).isEqualTo(2)
        assertThat(model.next?.id).isEqualTo("old")
        assertThat(model.stale).isFalse()
    }
}
