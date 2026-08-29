package icu.nd4y.dosette.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.stats.AdherenceCalculator
import icu.nd4y.dosette.ui.cabinet.CabinetContent
import icu.nd4y.dosette.ui.cabinet.CabinetUiState
import icu.nd4y.dosette.ui.cabinet.MedCard
import icu.nd4y.dosette.ui.cabinet.ScheduleBrief
import icu.nd4y.dosette.ui.calendar.CalendarContent
import icu.nd4y.dosette.ui.calendar.CalendarDay
import icu.nd4y.dosette.ui.calendar.CalendarUiState
import icu.nd4y.dosette.ui.mededit.MedEditContent
import icu.nd4y.dosette.ui.mededit.MedEditUiState
import icu.nd4y.dosette.ui.mededit.VariantDraft
import icu.nd4y.dosette.ui.mededit.WizardStep
import icu.nd4y.dosette.ui.theme.DosetteTheme
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.PrnMed
import icu.nd4y.dosette.ui.today.TodayContent
import icu.nd4y.dosette.ui.today.TodayDose
import icu.nd4y.dosette.ui.today.TodayUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
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

    private fun todayDose(
        medicationId: String,
        name: String,
        strength: String,
        time: LocalTime,
        form: MedicationForm,
        colorSeed: Int,
        status: DoseUiStatus,
        actedTime: LocalTime? = null,
        instructions: String? = null,
    ) = TodayDose(
        medicationId = medicationId,
        date = LocalDate.parse("2026-08-29"),
        time = time,
        name = name,
        strengthText = strength,
        amountText = "1",
        instructions = instructions,
        form = form,
        colorSeed = colorSeed,
        status = status,
        actedTime = actedTime,
    )

    private val todayState =
        TodayUiState(
            loading = false,
            date = LocalDate.parse("2026-08-29"),
            doses =
                listOf(
                    todayDose(
                        "m1",
                        "Метформин",
                        "500 мг",
                        LocalTime.of(8, 0),
                        MedicationForm.TABLET,
                        0,
                        DoseUiStatus.TAKEN,
                        LocalTime.of(8, 4),
                    ),
                    todayDose(
                        "m2",
                        "Лизиноприл",
                        "10 мг",
                        LocalTime.of(8, 0),
                        MedicationForm.TABLET,
                        1,
                        DoseUiStatus.TAKEN,
                        LocalTime.of(8, 4),
                    ),
                    todayDose(
                        "m4",
                        "Витамин D",
                        "2000 МЕ",
                        LocalTime.of(8, 0),
                        MedicationForm.DROPS,
                        3,
                        DoseUiStatus.TAKEN,
                        LocalTime.of(8, 5),
                    ),
                    todayDose(
                        "m1",
                        "Метформин",
                        "500 мг",
                        LocalTime.of(20, 0),
                        MedicationForm.TABLET,
                        0,
                        DoseUiStatus.PENDING,
                        instructions = "с едой",
                    ),
                    todayDose(
                        "m3",
                        "Аторвастатин",
                        "20 мг",
                        LocalTime.of(20, 0),
                        MedicationForm.CAPSULE,
                        2,
                        DoseUiStatus.PENDING,
                    ),
                ),
            prn = listOf(PrnMed("m5", "Ибупрофен", "400 мг", MedicationForm.TABLET, 4)),
            takenCount = 3,
            plannedCount = 5,
            nextDoseTime = LocalTime.of(20, 0),
        )

    private fun today(state: TodayUiState) {
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    TodayContent(
                        state = state,
                        contentPadding = screenPadding,
                        onTake = {},
                        onSkip = {},
                        onTakePrn = {},
                    )
                }
            }
        }
    }

    private val calendarState: CalendarUiState =
        run {
            val month = java.time.YearMonth.of(2026, 8)
            val today = LocalDate.parse("2026-08-29")
            val gridStart = LocalDate.parse("2026-07-27")
            val partialDays = setOf(5, 15, 26)
            val days =
                (0 until 42).map { offset ->
                    val date = gridStart.plusDays(offset.toLong())
                    val inMonth = java.time.YearMonth.from(date) == month
                    val status =
                        when {
                            !inMonth || date >= today -> null
                            date.dayOfMonth == 11 -> AdherenceCalculator.DayStatus.ALL_MISSED
                            date.dayOfMonth in partialDays -> AdherenceCalculator.DayStatus.PARTIAL
                            else -> AdherenceCalculator.DayStatus.COMPLETE
                        }
                    CalendarDay(date = date, inMonth = inMonth, isToday = date == today, status = status)
                }
            CalendarUiState(
                loading = false,
                month = month,
                days = days,
                monthAdherencePercent = 92,
            )
        }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun calendarLight() {
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    CalendarContent(
                        state = calendarState,
                        contentPadding = screenPadding,
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelect = {},
                        onMark = { _, _ -> },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$SHOTS/calendar_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun todayLight() {
        today(todayState)
        composeRule.onRoot().captureRoboImage("$SHOTS/today_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7_NIGHT)
    fun todayDark() {
        today(todayState)
        composeRule.onRoot().captureRoboImage("$SHOTS/today_dark.png")
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
