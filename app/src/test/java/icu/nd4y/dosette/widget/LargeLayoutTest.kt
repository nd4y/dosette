package icu.nd4y.dosette.widget

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.ui.today.DaySlot
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.TodayDose
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

private fun dose(
    time: String,
    status: DoseUiStatus = DoseUiStatus.PENDING,
    name: String = "med-$time",
    date: LocalDate = LocalDate.parse("2026-09-02"),
): TodayDose =
    TodayDose(
        medicationId = name,
        date = date,
        time = LocalTime.parse(time),
        name = name,
        strengthText = null,
        amountText = "1",
        instructions = null,
        form = MedicationForm.TABLET,
        colorSeed = 0,
        status = status,
        actedTime = null,
    )

class LargeLayoutTest {
    // A full day: two at midnight (already taken), one mid-morning, two in
    // the afternoon — the shape that showed an empty section on a 4x3 widget.
    private val day =
        listOf(
            dose("00:00", DoseUiStatus.TAKEN, "n1"),
            dose("00:00", DoseUiStatus.TAKEN, "n2"),
            dose("10:00", name = "m1"),
            dose("14:00", name = "a1"),
            dose("14:00", name = "a2"),
        )

    private fun LargePlan.rowNames() = entries.filterIsInstance<LargeEntry.DoseRow>().map { it.dose.name }

    @Test
    fun `with room to spare every slot lists its rows`() {
        val plan = LargeLayout.plan(heightDp = 420, carryover = emptyList(), doses = day)

        val headers = plan.entries.filterIsInstance<LargeEntry.SlotHeader>()
        assertThat(headers.map { it.collapsed }).containsExactly(false, false, false).inOrder()
        assertThat(plan.rowNames()).containsExactly("n1", "n2", "m1", "a1", "a2").inOrder()
        assertThat(plan.hidden).isEqualTo(0)
    }

    @Test
    fun `an acted slot folds into its header only when the day does not fit`() {
        // 330dp: fully expanded the day overflows by a row; folding the
        // taken midnight slot (the oldest) is enough, the rest stays listed.
        val plan = LargeLayout.plan(heightDp = 330, carryover = emptyList(), doses = day)

        val headers = plan.entries.filterIsInstance<LargeEntry.SlotHeader>()
        assertThat(headers.map { it.collapsed }).containsExactly(true, false, false).inOrder()
        assertThat(plan.rowNames()).containsExactly("m1", "a1", "a2").inOrder()
        assertThat(plan.hidden).isEqualTo(0)
    }

    @Test
    fun `a fully acted day fills the widget instead of three bare headers`() {
        // The end-of-day picture: everything taken or missed. With room,
        // the rows (names + marks) are listed rather than collapsed away.
        val done = day.map { if (it.status == DoseUiStatus.PENDING) it.copy(status = DoseUiStatus.MISSED) else it }
        val plan = LargeLayout.plan(heightDp = 420, carryover = emptyList(), doses = done)

        assertThat(plan.entries.filterIsInstance<LargeEntry.SlotHeader>().none { it.collapsed }).isTrue()
        assertThat(plan.rowNames()).hasSize(5)
    }

    @Test
    fun `a section is cut after its first row when the rest does not fit`() {
        // 280dp: the afternoon header and its first row fit, the second
        // row does not — it goes to the "+1 more" line.
        val plan = LargeLayout.plan(heightDp = 280, carryover = emptyList(), doses = day)

        val headers = plan.entries.filterIsInstance<LargeEntry.SlotHeader>()
        assertThat(headers).hasSize(3)
        assertThat(plan.rowNames()).containsExactly("m1", "a1").inOrder()
        assertThat(plan.hidden).isEqualTo(1)
    }

    @Test
    fun `a header is never drawn without its first row`() {
        // 240dp (the base large bucket): title + collapsed night + morning
        // fit; the afternoon header alone must not appear with its rows
        // clipped below the widget's edge.
        val plan = LargeLayout.plan(heightDp = 240, carryover = emptyList(), doses = day)

        val headers = plan.entries.filterIsInstance<LargeEntry.SlotHeader>()
        assertThat(headers.map { it.doses.first().slot }).containsExactly(DaySlot.NIGHT, DaySlot.MORNING).inOrder()
        assertThat(plan.rowNames()).containsExactly("m1")
        assertThat(plan.hidden).isEqualTo(2)
        assertThat(plan.prnFits).isFalse()
    }

    @Test
    fun `everything after the first cut stays hidden to keep the day in order`() {
        val plan = LargeLayout.plan(heightDp = 160, carryover = emptyList(), doses = day)

        val headers = plan.entries.filterIsInstance<LargeEntry.SlotHeader>()
        assertThat(headers.map { it.doses.first().slot }).containsExactly(DaySlot.NIGHT)
        assertThat(plan.rowNames()).isEmpty()
        assertThat(plan.hidden).isEqualTo(3)
    }

    @Test
    fun `carryover comes first and counts toward the budget`() {
        val yesterday = listOf(dose("23:50", name = "y1", date = LocalDate.parse("2026-09-01")))
        val plan = LargeLayout.plan(heightDp = 240, carryover = yesterday, doses = day)

        assertThat(plan.entries.first()).isEqualTo(LargeEntry.CarryoverHeader)
        assertThat(plan.rowNames().first()).isEqualTo("y1")
        assertThat(plan.hidden).isGreaterThan(0)
    }

    @Test
    fun `nothing hidden leaves room for the as-needed row`() {
        val plan = LargeLayout.plan(heightDp = 420, carryover = emptyList(), doses = day)

        assertThat(plan.prnFits).isTrue()
    }

    @Test
    fun `text costs grow with the font scale`() {
        // 330dp fits the whole day at scale 1 (night folded); at 1.4 the
        // taller rows push the last afternoon dose out.
        assertThat(LargeLayout.plan(heightDp = 330, carryover = emptyList(), doses = day).hidden).isEqualTo(0)
        val large = LargeLayout.plan(heightDp = 330, carryover = emptyList(), doses = day, fontScale = 1.4f)

        assertThat(large.hidden).isEqualTo(1)
    }
}
