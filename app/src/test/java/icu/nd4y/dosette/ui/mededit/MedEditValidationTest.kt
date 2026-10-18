package icu.nd4y.dosette.ui.mededit

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.ScheduleType
import org.junit.Test
import java.time.LocalTime

class MedEditValidationTest {
    @Test
    fun `decimal commas are valid in doses and inventory`() {
        val doses = MedEditUiState(step = WizardStep.TIMES, times = listOf(TimeSlotDraft(LocalTime.NOON, "0,5")))
        val stock =
            MedEditUiState(
                step = WizardStep.STOCK,
                trackStock = true,
                variants = listOf(VariantDraft(stockText = "2,5")),
            )
        assertThat(doses.canProceed).isTrue()
        assertThat(stock.canProceed).isTrue()
    }

    @Test
    fun `nonfinite and negative amounts are blocked before saving`() {
        listOf("NaN", "Infinity", "1e999", "-1").forEach { invalid ->
            assertThat(MedEditUiState(name = "Test", strengthText = invalid).canProceed).isFalse()
            assertThat(
                MedEditUiState(
                    step = WizardStep.TIMES,
                    times = listOf(TimeSlotDraft(LocalTime.NOON, invalid)),
                ).canProceed,
            ).isFalse()
            assertThat(
                MedEditUiState(
                    step = WizardStep.STOCK,
                    trackStock = true,
                    variants = listOf(VariantDraft(stockText = invalid)),
                ).canProceed,
            ).isFalse()
        }
    }

    @Test
    fun `cycle off days must parse as a nonnegative integer`() {
        val state = MedEditUiState(step = WizardStep.SCHEDULE, scheduleType = ScheduleType.CYCLE)
        assertThat(state.copy(cycleOffText = "").canProceed).isFalse()
        assertThat(state.copy(cycleOffText = "wrong").canProceed).isFalse()
        assertThat(state.copy(cycleOffText = "0").canProceed).isTrue()
    }
}
