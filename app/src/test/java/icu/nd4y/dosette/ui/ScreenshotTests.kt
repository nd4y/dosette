package icu.nd4y.dosette.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.Profile
import icu.nd4y.dosette.domain.stats.AdherenceCalculator
import icu.nd4y.dosette.ui.appointments.AppointmentsContent
import icu.nd4y.dosette.ui.appointments.AppointmentsUiState
import icu.nd4y.dosette.ui.backup.BackupContent
import icu.nd4y.dosette.ui.backup.BackupUiState
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
import icu.nd4y.dosette.ui.more.MoreContent
import icu.nd4y.dosette.ui.onboarding.OnboardingContent
import icu.nd4y.dosette.ui.settings.SettingsContent
import icu.nd4y.dosette.ui.stats.MedStat
import icu.nd4y.dosette.ui.stats.StatsContent
import icu.nd4y.dosette.ui.stats.StatsUiState
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
        composeRule.onRoot().captureRoboImage("$SHOTS/cabinet_light.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7_NIGHT)
    fun cabinetDark() {
        cabinet(CabinetUiState(loading = false, active = cards))
        composeRule.onRoot().captureRoboImage("$SHOTS/cabinet_dark.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun cabinetEmpty() {
        cabinet(CabinetUiState(loading = false))
        composeRule.onRoot().captureRoboImage("$SHOTS/cabinet_empty_light.png", roborazziOptions = SHOT_OPTIONS)
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
        composeRule.onRoot().captureRoboImage("$SHOTS/wizard_basics_light.png", roborazziOptions = SHOT_OPTIONS)
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
        composeRule.onRoot().captureRoboImage("$SHOTS/wizard_schedule_light.png", roborazziOptions = SHOT_OPTIONS)
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
                        onShowMonth = {},
                        onSelect = {},
                        onMark = { _, _ -> },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$SHOTS/calendar_light.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun todayLight() {
        today(todayState)
        composeRule.onRoot().captureRoboImage("$SHOTS/today_light.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun todayBigFont() {
        // Large system font: the layout must survive fontScale 1.3 without clipping.
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        today(todayState)
        composeRule.onRoot().captureRoboImage("$SHOTS/today_bigfont.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7_NIGHT)
    fun todayDark() {
        today(todayState)
        composeRule.onRoot().captureRoboImage("$SHOTS/today_dark.png", roborazziOptions = SHOT_OPTIONS)
    }

    private val profileFixtures =
        listOf(
            Profile(
                id = "p1",
                name = "Андрей",
                colorSeed = 0,
                avatarKey = null,
                sortOrder = 0,
                createdAt = java.time.Instant.parse("2026-08-01T00:00:00Z"),
            ),
            Profile(
                id = "p2",
                name = "Мама",
                colorSeed = 2,
                avatarKey = null,
                sortOrder = 1,
                createdAt = java.time.Instant.parse("2026-08-02T00:00:00Z"),
            ),
        )

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun moreLight() {
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    MoreContent(
                        profiles = profileFixtures,
                        contentPadding = screenPadding,
                        onOpenProfiles = {},
                        onOpenSettings = {},
                        onOpenAppointments = {},
                        onOpenStats = {},
                        onOpenBackup = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$SHOTS/more_light.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun settingsLight() {
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    SettingsContent(
                        settings = AppSettings(),
                        batteryExempt = false,
                        contentPadding = screenPadding,
                        onBack = {},
                        onNagInterval = {},
                        onSnooze = {},
                        onGrace = {},
                        onTheme = {},
                        onDynamicColor = {},
                        onLanguage = {},
                        onRequestExemption = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$SHOTS/settings_light.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun backupLight() {
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    BackupContent(
                        state = BackupUiState(),
                        contentPadding = screenPadding,
                        onBack = {},
                        onExport = {},
                        onImport = {},
                        onConfirmImport = {},
                        onDismissImport = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$SHOTS/backup_light.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun onboardingLight() {
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    OnboardingContent(
                        notificationsGranted = false,
                        batteryExempt = false,
                        onRequestNotifications = {},
                        onRequestBattery = {},
                        onStart = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$SHOTS/onboarding_light.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun appointmentsLight() {
        val appointments =
            listOf(
                Appointment(
                    id = "a1",
                    profileId = "p1",
                    title = "Терапевт",
                    doctorName = "Иванова А. П.",
                    location = "Поликлиника №3",
                    date = LocalDate.parse("2026-09-02"),
                    time = LocalTime.of(9, 30),
                    notes = null,
                    reminderOffsetsMin = listOf(1440, 120),
                    createdAt = java.time.Instant.parse("2026-08-20T00:00:00Z"),
                ),
                Appointment(
                    id = "a2",
                    profileId = "p1",
                    title = "Кардиолог",
                    doctorName = null,
                    location = "МЦ «Здоровье»",
                    date = LocalDate.parse("2026-09-15"),
                    time = LocalTime.of(14, 0),
                    notes = null,
                    reminderOffsetsMin = listOf(120),
                    createdAt = java.time.Instant.parse("2026-08-25T00:00:00Z"),
                ),
            )
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    AppointmentsContent(
                        state = AppointmentsUiState(loading = false, upcoming = appointments),
                        contentPadding = screenPadding,
                        onBack = {},
                        onAdd = {},
                        onOpen = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$SHOTS/appointments_light.png", roborazziOptions = SHOT_OPTIONS)
    }

    @Test
    @Config(sdk = [34], qualifiers = RU_PIXEL7)
    fun statsLight() {
        val state =
            StatsUiState(
                loading = false,
                percent = 92,
                taken = 138,
                missed = 12,
                skipped = 4,
                streakDays = 6,
                meds =
                    listOf(
                        MedStat("m1", "Метформин", 0, taken = 56, missed = 2),
                        MedStat("m2", "Лизиноприл", 1, taken = 27, missed = 3),
                        MedStat("m3", "Аторвастатин", 2, taken = 24, missed = 5),
                        MedStat("m4", "Витамин D", 3, taken = 31, missed = 2),
                    ),
            )
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    StatsContent(
                        state = state,
                        contentPadding = screenPadding,
                        onBack = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$SHOTS/stats_light.png", roborazziOptions = SHOT_OPTIONS)
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
        composeRule.onRoot().captureRoboImage("$SHOTS/wizard_stock_light.png", roborazziOptions = SHOT_OPTIONS)
    }
}
