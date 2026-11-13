package icu.nd4y.dosette.ui.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.ui.theme.DosetteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class MissedDoseActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `missed dose takes now with one click without opening the menu`() {
        val actions = mutableListOf<IntakeTime>()
        val dose =
            TodayDose(
                medicationId = "m",
                date = LocalDate.of(2026, 11, 12),
                time = LocalTime.of(23, 50),
                name = "Night medicine",
                strengthText = null,
                amountText = "1",
                instructions = null,
                form = MedicationForm.TABLET,
                colorSeed = 0,
                status = DoseUiStatus.MISSED,
                actedTime = null,
            )
        compose.setContent {
            DosetteTheme {
                ActedDoseRow(dose, onTake = actions::add, onSkip = {}, onUndo = {})
            }
        }
        compose.onNodeWithText("Take").performClick()
        assertThat(actions).containsExactly(IntakeTime.Now)
    }
}
