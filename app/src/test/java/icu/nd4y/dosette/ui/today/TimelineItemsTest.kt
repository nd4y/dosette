package icu.nd4y.dosette.ui.today

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.MedicationForm
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimelineItemsTest {
    private val today = LocalDate.parse("2026-09-01")

    private fun dose(
        date: LocalDate,
        time: LocalTime,
        status: DoseUiStatus = DoseUiStatus.PENDING,
    ) = TodayDose(
        medicationId = "m1",
        date = date,
        time = time,
        name = "Med",
        strengthText = null,
        amountText = "1",
        instructions = null,
        form = MedicationForm.TABLET,
        colorSeed = 0,
        status = status,
        actedTime = null,
    )

    private fun state(
        days: List<TimelineDay>,
        anchorDate: LocalDate = today,
        prn: List<PrnMed> = emptyList(),
    ) = TodayUiState(loading = false, date = today, days = days, anchorDate = anchorDate, prn = prn)

    @Test
    fun `past days come before the today block and future days after`() {
        val items =
            timelineItems(
                state(
                    days =
                        listOf(
                            TimelineDay(today.minusDays(1), listOf(dose(today.minusDays(1), LocalTime.of(23, 50)))),
                            TimelineDay(today, listOf(dose(today, LocalTime.of(8, 0)))),
                            TimelineDay(today.plusDays(1), listOf(dose(today.plusDays(1), LocalTime.of(8, 0)))),
                        ),
                ),
            )

        val keys = items.map { it.key }
        assertThat(keys.indexOf("day-${today.minusDays(1)}")).isLessThan(keys.indexOf("header"))
        assertThat(keys.indexOf("header")).isLessThan(keys.indexOf("hero"))
        assertThat(keys.indexOf("hero")).isLessThan(keys.indexOf("day-${today.plusDays(1)}"))
        // Today's own block has no day header of its own.
        assertThat(keys).doesNotContain("day-$today")
    }

    @Test
    fun `future doses are read-only and past ones are not`() {
        val items =
            timelineItems(
                state(
                    days =
                        listOf(
                            TimelineDay(today.minusDays(1), listOf(dose(today.minusDays(1), LocalTime.of(23, 50)))),
                            TimelineDay(today, emptyList()),
                            TimelineDay(today.plusDays(1), listOf(dose(today.plusDays(1), LocalTime.of(8, 0)))),
                        ),
                ),
            )

        val doses = items.filterIsInstance<TimelineItem.Dose>()
        assertThat(doses.single { it.dose.date < today }.readOnly).isFalse()
        assertThat(doses.single { it.dose.date > today }.readOnly).isTrue()
    }

    @Test
    fun `prn block follows the today block and precedes future days`() {
        val items =
            timelineItems(
                state(
                    days =
                        listOf(
                            TimelineDay(today, listOf(dose(today, LocalTime.of(8, 0)))),
                            TimelineDay(today.plusDays(2), listOf(dose(today.plusDays(2), LocalTime.of(8, 0)))),
                        ),
                    prn = listOf(PrnMed("m9", "Prn", null, MedicationForm.TABLET, 1)),
                ),
            )

        val keys = items.map { it.key }
        assertThat(keys.indexOf("prn-header")).isGreaterThan(keys.indexOf("hero"))
        assertThat(keys.indexOf("prn-header")).isLessThan(keys.indexOf("day-${today.plusDays(2)}"))
    }

    @Test
    fun `empty today renders its placeholder inside the today block`() {
        val items = timelineItems(state(days = listOf(TimelineDay(today, emptyList()))))

        assertThat(items.map { it.key }).containsExactly("header", "hero", "today-empty").inOrder()
    }

    @Test
    fun `anchor lands on today by default and on the unresolved past day when set`() {
        val days =
            listOf(
                TimelineDay(today.minusDays(1), listOf(dose(today.minusDays(1), LocalTime.of(23, 50)))),
                TimelineDay(today, listOf(dose(today, LocalTime.of(8, 0)))),
            )
        val plain = state(days)
        assertThat(timelineItems(plain)[anchorIndex(timelineItems(plain), plain)].key).isEqualTo("header")

        val carryover = state(days, anchorDate = today.minusDays(1))
        assertThat(timelineItems(carryover)[anchorIndex(timelineItems(carryover), carryover)].key)
            .isEqualTo("day-${today.minusDays(1)}")
    }
}
