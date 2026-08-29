package icu.nd4y.dosette.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.ui.cabinet.CabinetContent
import icu.nd4y.dosette.ui.cabinet.CabinetUiState
import icu.nd4y.dosette.ui.cabinet.MedCard
import icu.nd4y.dosette.ui.cabinet.ScheduleBrief
import icu.nd4y.dosette.ui.mededit.MedEditContent
import icu.nd4y.dosette.ui.mededit.MedEditUiState
import icu.nd4y.dosette.ui.mededit.VariantDraft
import icu.nd4y.dosette.ui.mededit.WizardStep
import icu.nd4y.dosette.ui.theme.DosetteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime

private const val RU_PIXEL7 = "ru-rRU-" + RobolectricDeviceQualifiers.Pixel7

// Resource qualifier order matters: night sits before density.
private const val RU_PIXEL7_NIGHT = "ru-rRU-w411dp-h914dp-normal-long-notround-any-night-420dpi-keyshidden-nonav"
private const val SHOTS = "src/test/screenshots"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private val cards =
        listOf(
            MedCard(
                id = "m1",
                name = "Метформин",
                strengthText = "500 мг",
                form = MedicationForm.TABLET,
                colorSeed = 0,
                schedule = ScheduleBrief.FixedTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
                stockUnits = "42",
                daysOfSupply = 21,
                lowStock = false,
            ),
            MedCard(
                id = "m2",
                name = "Лизиноприл",
                strengthText = "10 мг",
                form = MedicationForm.TABLET,
                colorSeed = 1,
                schedule = ScheduleBrief.FixedTimes(listOf(LocalTime.of(8, 0))),
                stockUnits = "6",
                daysOfSupply = 6,
                lowStock = true,
            ),
            MedCard(
                id = "m3",
                name = "Аторвастатин",
                strengthText = "20 мг",
                form = MedicationForm.CAPSULE,
                colorSeed = 2,
                schedule = ScheduleBrief.FixedTimes(listOf(LocalTime.of(20, 0))),
                stockUnits = "27",
                daysOfSupply = 27,
                lowStock = false,
            ),
            MedCard(
                id = "m4",
                name = "Витамин D",
                strengthText = "2000 МЕ",
                form = MedicationForm.DROPS,
                colorSeed = 3,
                schedule = ScheduleBrief.FixedTimes(listOf(LocalTime.of(8, 0))),
                stockUnits = null,
                daysOfSupply = null,
                lowStock = false,
            ),
            MedCard(
                id = "m5",
                name = "Ибупрофен",
                strengthText = "400 мг",
                form = MedicationForm.TABLET,
                colorSeed = 4,
                schedule = ScheduleBrief.AsNeeded,
                stockUnits = "18",
                daysOfSupply = null,
                lowStock = false,
            ),
        )

    private val screenPadding = PaddingValues(top = 24.dp, bottom = 88.dp)

    private fun cabinet(state: CabinetUiState) {
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    CabinetContent(
                        state = state,
                        contentPadding = screenPadding,
                        onAddMedication = {},
                        onOpenMedication = {},
                    )
                }
            }
        }
    }

    private fun wizard(state: MedEditUiState) {
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
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
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun cabinetLight() {
        cabinet(CabinetUiState(loading = false, active = cards))
        composeRule.onRoot().captureRoboImage("$SHOTS/cabinet_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7_NIGHT)
    fun cabinetDark() {
        cabinet(CabinetUiState(loading = false, active = cards))
        composeRule.onRoot().captureRoboImage("$SHOTS/cabinet_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun cabinetEmpty() {
        cabinet(CabinetUiState(loading = false))
        composeRule.onRoot().captureRoboImage("$SHOTS/cabinet_empty_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun wizardBasics() {
        wizard(
            MedEditUiState(
                step = WizardStep.BASICS,
                name = "Омега-3",
                form = MedicationForm.CAPSULE,
                strengthText = "1000",
                strengthUnit = "мг",
                colorSeed = 5,
            ),
        )
        composeRule.onRoot().captureRoboImage("$SHOTS/wizard_basics_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun wizardSchedule() {
        wizard(
            MedEditUiState(
                step = WizardStep.SCHEDULE,
                name = "Омега-3",
                form = MedicationForm.CAPSULE,
                strengthText = "1000",
                strengthUnit = "мг",
                colorSeed = 5,
                scheduleType = icu.nd4y.dosette.domain.model.ScheduleType.WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
        )
        composeRule.onRoot().captureRoboImage("$SHOTS/wizard_schedule_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun wizardStockVariants() {
        wizard(
            MedEditUiState(
                step = WizardStep.STOCK,
                name = "Препарат X",
                form = MedicationForm.CAPSULE,
                strengthText = "150",
                strengthUnit = "мг",
                colorSeed = 1,
                trackStock = true,
                variants =
                    listOf(
                        VariantDraft(strengthText = "150", stockText = "10"),
                        VariantDraft(strengthText = "75", stockText = "20"),
                    ),
            ),
        )
        composeRule.onRoot().captureRoboImage("$SHOTS/wizard_stock_light.png")
    }
}
