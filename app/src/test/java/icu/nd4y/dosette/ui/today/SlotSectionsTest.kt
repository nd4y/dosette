package icu.nd4y.dosette.ui.today

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.MedicationForm
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SlotSectionsTest {
    private fun dose(time: LocalTime) =
        TodayDose(
            medicationId = "m1",
            date = LocalDate.parse("2026-08-31"),
            time = time,
            name = "Testin",
            strengthText = null,
            amountText = "1",
            instructions = null,
            form = MedicationForm.TABLET,
            colorSeed = 0,
            status = DoseUiStatus.PENDING,
            actedTime = null,
        )

    @Test
    fun `midnight doses open the day and late night closes it`() {
        val doses =
            listOf(
                dose(LocalTime.of(0, 0)),
                dose(LocalTime.of(10, 0)),
                dose(LocalTime.of(20, 0)),
                dose(LocalTime.of(23, 30)),
            )

        val sections = slotSections(doses)

        assertThat(sections.map { it.first().slot })
            .containsExactly(DaySlot.NIGHT, DaySlot.MORNING, DaySlot.EVENING, DaySlot.NIGHT)
            .inOrder()
        assertThat(sections.first().single().time).isEqualTo(LocalTime.of(0, 0))
        assertThat(sections.last().single().time).isEqualTo(LocalTime.of(23, 30))
    }

    @Test
    fun `same slot doses stay in one section`() {
        val doses = listOf(dose(LocalTime.of(8, 0)), dose(LocalTime.of(9, 0)), dose(LocalTime.of(14, 0)))

        val sections = slotSections(doses)

        assertThat(sections).hasSize(2)
        assertThat(sections.first()).hasSize(2)
    }

    @Test
    fun `empty list gives no sections`() {
        assertThat(slotSections(emptyList())).isEmpty()
    }
}
