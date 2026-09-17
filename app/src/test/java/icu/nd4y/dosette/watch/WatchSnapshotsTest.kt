package icu.nd4y.dosette.watch

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.link.WatchDoseStatus
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.PrnMed
import icu.nd4y.dosette.ui.today.TodayDose
import icu.nd4y.dosette.widget.WidgetState
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WatchSnapshotsTest {
    private val today: LocalDate = LocalDate.parse("2026-09-17")

    private fun dose(
        id: String,
        date: LocalDate,
        time: LocalTime,
        status: DoseUiStatus,
        actedTime: LocalTime? = null,
        strength: String? = "500 мг",
    ) = TodayDose(
        medicationId = id,
        date = date,
        time = time,
        name = "Метформин",
        strengthText = strength,
        amountText = "2",
        instructions = null,
        form = MedicationForm.TABLET,
        colorSeed = 0,
        status = status,
        actedTime = actedTime,
    )

    private val state =
        WidgetState(
            date = today,
            doses =
                listOf(
                    dose("m1", today, LocalTime.of(8, 0), DoseUiStatus.TAKEN, actedTime = LocalTime.of(8, 3)),
                    dose("m1", today, LocalTime.of(20, 0), DoseUiStatus.PENDING),
                ),
            carryover =
                listOf(
                    dose("m2", today.minusDays(1), LocalTime.of(22, 0), DoseUiStatus.PENDING, strength = null),
                ),
            prn = listOf(PrnMed("m3", "Ибупрофен", "400 мг", MedicationForm.TABLET, 0)),
        )

    @Test
    fun `carryover comes first and is flagged, doses keep their order`() {
        val snapshot = buildWatchSnapshot(state, activeReminders = emptySet(), profileName = null)

        assertThat(snapshot.date).isEqualTo("2026-09-17")
        assertThat(snapshot.doses.map { it.id })
            .containsExactly(
                "m2|2026-09-16|22:00",
                "m1|2026-09-17|08:00",
                "m1|2026-09-17|20:00",
            ).inOrder()
        assertThat(snapshot.doses.map { it.carryover }).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun `status, times and amount cross the wire`() {
        val snapshot = buildWatchSnapshot(state, activeReminders = emptySet(), profileName = null)

        val taken = snapshot.doses[1]
        assertThat(taken.status).isEqualTo(WatchDoseStatus.TAKEN)
        assertThat(taken.timeMinutes).isEqualTo(8 * 60)
        assertThat(taken.actedTimeMinutes).isEqualTo(8 * 60 + 3)
        assertThat(taken.amount).isEqualTo("2")
        val pending = snapshot.doses[2]
        assertThat(pending.status).isEqualTo(WatchDoseStatus.PENDING)
        assertThat(pending.actedTimeMinutes).isNull()
    }

    @Test
    fun `titles join name and strength, with or without one`() {
        val snapshot = buildWatchSnapshot(state, activeReminders = emptySet(), profileName = "Анна")

        assertThat(snapshot.doses[0].title).isEqualTo("Метформин")
        assertThat(snapshot.doses[1].title).isEqualTo("Метформин 500 мг")
        assertThat(snapshot.prn.single().title).isEqualTo("Ибупрофен 400 мг")
        assertThat(snapshot.profileName).isEqualTo("Анна")
    }

    @Test
    fun `a ringing reminder marks its dose`() {
        val ringing = OccurrenceKey("m1", today, LocalTime.of(20, 0))

        val snapshot = buildWatchSnapshot(state, activeReminders = setOf(ringing), profileName = null)

        assertThat(snapshot.doses.map { it.reminderActive }).containsExactly(false, false, true).inOrder()
    }
}
