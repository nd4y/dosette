package icu.nd4y.dosette.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.ui.designsystem.SegmentedRing
import icu.nd4y.dosette.ui.mededit.MedEditContent
import icu.nd4y.dosette.ui.mededit.MedEditUiState
import icu.nd4y.dosette.ui.mededit.WizardStep
import icu.nd4y.dosette.ui.theme.DosetteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val RU_PIXEL7 = "ru-rRU-" + RobolectricDeviceQualifiers.Pixel7
private const val SHOTS = "src/test/screenshots"

/**
 * Frame-by-frame motion snapshots: the main clock is frozen and advanced by
 * hand, so mid-transition frames land in PNGs that can be reviewed by eye.
 * This is the only way to see motion without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MotionTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun wizardStepTransitionFrames() {
        var state by mutableStateOf(
            MedEditUiState(
                step = WizardStep.BASICS,
                name = "Омега-3",
                form = MedicationForm.CAPSULE,
                strengthText = "1000",
                strengthUnit = "мг",
                colorSeed = 5,
            ),
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MedEditContent(
                        state = state,
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                        onUpdate = {},
                        onNext = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)

        state = state.copy(step = WizardStep.SCHEDULE)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onRoot().captureRoboImage("$SHOTS/motion_wizard_080ms.png")
        composeRule.mainClock.advanceTimeBy(120)
        composeRule.onRoot().captureRoboImage("$SHOTS/motion_wizard_200ms.png")
        composeRule.mainClock.advanceTimeBy(1_800)
        composeRule.onRoot().captureRoboImage("$SHOTS/motion_wizard_end.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun ringFillFrames() {
        var done by mutableIntStateOf(2)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SegmentedRing(
                        total = 5,
                        done = done,
                        doneColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        size = 160.dp,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)

        done = 3
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onRoot().captureRoboImage("$SHOTS/motion_ring_080ms.png")
        composeRule.mainClock.advanceTimeBy(120)
        composeRule.onRoot().captureRoboImage("$SHOTS/motion_ring_200ms.png")
        composeRule.mainClock.advanceTimeBy(1_800)
        composeRule.onRoot().captureRoboImage("$SHOTS/motion_ring_end.png")
    }
}
