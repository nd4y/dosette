package icu.nd4y.dosette.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import icu.nd4y.dosette.link.WatchActionKind
import icu.nd4y.dosette.link.WatchDose
import icu.nd4y.dosette.link.WatchDoseStatus
import icu.nd4y.dosette.link.WatchPrn
import icu.nd4y.dosette.wear.theme.WatchTheme
import icu.nd4y.dosette.wear.today.DoseScreen
import icu.nd4y.dosette.wear.today.WatchDoseUi
import icu.nd4y.dosette.wear.today.WatchPrnUi
import icu.nd4y.dosette.wear.today.WatchUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

private const val RU_WATCH = "ru-rRU-" + RobolectricDeviceQualifiers.WearOSLargeRound
private const val SHOTS = "src/test/screenshots"

private val SHOT_OPTIONS =
    RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.02f),
    )

/** A wall clock that never moves, so the time text is the same on every run. */
private object FixedTime : TimeSource {
    @Composable
    override fun currentTime(): String = "10:00"
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchScreenshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun dose(
        id: String,
        title: String,
        minutes: Int,
        status: WatchDoseStatus,
        acted: Int? = null,
        amount: String? = "1",
    ) = WatchDose(
        id = id,
        title = title,
        amount = amount,
        timeMinutes = minutes,
        status = status,
        actedTimeMinutes = acted,
    )

    private val day =
        WatchUiState(
            loaded = true,
            hasSnapshot = true,
            date = LocalDate.parse("2026-09-17"),
            doses =
                listOf(
                    WatchDoseUi(
                        dose("d1", "Метформин 500 мг", 8 * 60, WatchDoseStatus.TAKEN, acted = 8 * 60 + 3),
                        due = true,
                    ),
                    WatchDoseUi(dose("d2", "Лизиноприл 10 мг", 8 * 60, WatchDoseStatus.SKIPPED), due = true),
                    WatchDoseUi(dose("d3", "Витамин D", 13 * 60, WatchDoseStatus.PENDING, amount = "2"), due = true),
                    WatchDoseUi(
                        dose("d4", "Омега-3 1000 мг", 13 * 60, WatchDoseStatus.PENDING),
                        inFlight = WatchActionKind.TAKE,
                    ),
                    WatchDoseUi(dose("d5", "Аторвастатин 20 мг", 20 * 60, WatchDoseStatus.PENDING)),
                ),
            prn = listOf(WatchPrnUi(WatchPrn("m6", "Ибупрофен 400 мг"))),
            phoneReachable = true,
        )

    private fun shoot(name: String) {
        composeRule.onRoot().captureRoboImage("$SHOTS/$name.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_WATCH)
    fun today() {
        composeRule.setContent { WatchContent(day, WatchActions(), timeSource = FixedTime, dynamicColor = false) }
        shoot("watch_today")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_WATCH)
    fun todayEmpty() {
        val state =
            WatchUiState(loaded = true, hasSnapshot = true, date = LocalDate.parse("2026-09-17"), phoneReachable = true)
        composeRule.setContent { WatchContent(state, WatchActions(), timeSource = FixedTime, dynamicColor = false) }
        shoot("watch_today_empty")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_WATCH)
    fun noPhone() {
        val state = WatchUiState(loaded = true, phoneReachable = false)
        composeRule.setContent { WatchContent(state, WatchActions(), timeSource = FixedTime, dynamicColor = false) }
        shoot("watch_no_phone")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_WATCH)
    fun dosePending() {
        val dose =
            WatchDoseUi(
                dose("d3", "Витамин D", 13 * 60, WatchDoseStatus.PENDING, amount = "2").copy(reminderActive = true),
                due = true,
            )
        composeRule.setContent { DoseShot(dose) }
        shoot("watch_dose_pending")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_WATCH)
    fun doseTaken() {
        val dose =
            WatchDoseUi(dose("d1", "Метформин 500 мг", 8 * 60, WatchDoseStatus.TAKEN, acted = 8 * 60 + 3), due = true)
        composeRule.setContent { DoseShot(dose) }
        shoot("watch_dose_taken")
    }

    @Composable
    private fun DoseShot(dose: WatchDoseUi) {
        WatchTheme(dynamicColor = false) {
            AppScaffold(timeText = { TimeText(timeSource = FixedTime) }) {
                DoseScreen(dose = dose, onTake = {}, onSkip = {}, onSnooze = {})
            }
        }
    }
}
