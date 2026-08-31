package icu.nd4y.dosette.data.backup

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.MedicationVariant
import icu.nd4y.dosette.domain.model.Profile
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleTime
import icu.nd4y.dosette.domain.model.ScheduleType
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class BackupCodecTest {
    private val instant = Instant.parse("2026-08-29T08:00:00Z")

    private val fullData =
        BackupData(
            settings =
                AppSettings(
                    activeProfileId = "p1",
                    nagIntervalMin = 5,
                    nagMaxCount = 4,
                    snoozeMin = 15,
                    missedGraceMin = 30,
                    theme = ThemeMode.DARK,
                    dynamicColor = false,
                    language = AppLanguage.RU,
                    lowStockNotifyEnabled = false,
                    onboardingDone = true,
                ),
            profiles =
                listOf(
                    Profile("p1", "Андрей", 0, null, 0, instant),
                    Profile("p2", "Мама", 2, null, 1, instant),
                ),
            medications =
                listOf(
                    Medication(
                        id = "m1",
                        profileId = "p1",
                        name = "Препарат X",
                        form = MedicationForm.CAPSULE,
                        strengthValue = 150.0,
                        strengthUnit = "мг",
                        instructions = "с едой",
                        colorSeed = 1,
                        iconKey = "capsule",
                        defaultVariantId = "v150",
                        archivedAt = null,
                        createdAt = instant,
                    ),
                    Medication(
                        id = "m2",
                        profileId = "p2",
                        name = "Ибупрофен",
                        form = MedicationForm.TABLET,
                        strengthValue = 400.0,
                        strengthUnit = "мг",
                        instructions = null,
                        colorSeed = 4,
                        iconKey = "tablet",
                        defaultVariantId = null,
                        archivedAt = instant,
                        createdAt = instant,
                    ),
                ),
            variants =
                listOf(
                    MedicationVariant("v150", "m1", null, 150.0, "мг", 0, true, 10.0, 5.0, 30.0, instant),
                    MedicationVariant("v75", "m1", "半", 75.0, "мг", 1, true, 20.0, null, null, null),
                ),
            schedules =
                listOf(
                    Schedule(
                        id = "s1",
                        medicationId = "m1",
                        type = ScheduleType.WEEKDAYS,
                        startDate = LocalDate.parse("2026-05-01"),
                        endDate = LocalDate.parse("2026-12-31"),
                        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                        intervalDays = null,
                        cycleDaysOn = null,
                        cycleDaysOff = null,
                        defaultDoseAmount = 1.0,
                        remindersEnabled = true,
                        createdAt = instant,
                        times =
                            listOf(
                                ScheduleTime("t1", "s1", LocalTime.of(8, 0), 1.0, 0),
                                ScheduleTime("t2", "s1", LocalTime.of(20, 30), 2.0, 1),
                            ),
                    ),
                    Schedule(
                        id = "s2",
                        medicationId = "m2",
                        type = ScheduleType.AS_NEEDED,
                        startDate = LocalDate.parse("2026-06-01"),
                        endDate = null,
                        weekdays = emptySet(),
                        intervalDays = null,
                        cycleDaysOn = null,
                        cycleDaysOff = null,
                        defaultDoseAmount = 1.0,
                        remindersEnabled = false,
                        createdAt = instant,
                        times = emptyList(),
                    ),
                ),
            doseLogs =
                listOf(
                    DoseLog(
                        id = "d1",
                        profileId = "p1",
                        medicationId = "m1",
                        scheduleId = "s1",
                        kind = DoseKind.SCHEDULED,
                        date = LocalDate.parse("2026-08-28"),
                        time = LocalTime.of(8, 0),
                        scheduledAt = instant,
                        status = DoseStatus.TAKEN,
                        actedAt = instant,
                        amount = 1.0,
                        variantId = "v75",
                        consumedUnits = 2.0,
                        note = "запил водой",
                        updatedAt = instant,
                    ),
                    DoseLog(
                        id = "d2",
                        profileId = "p2",
                        medicationId = "m2",
                        scheduleId = null,
                        kind = DoseKind.PRN,
                        date = LocalDate.parse("2026-08-27"),
                        time = null,
                        scheduledAt = null,
                        status = DoseStatus.TAKEN,
                        actedAt = instant,
                        amount = 1.0,
                        variantId = null,
                        consumedUnits = null,
                        note = null,
                        updatedAt = instant,
                    ),
                ),
            appointments =
                listOf(
                    Appointment(
                        id = "a1",
                        profileId = "p1",
                        title = "Терапевт",
                        doctorName = "Иванова",
                        location = "Поликлиника №3",
                        date = LocalDate.parse("2026-09-02"),
                        time = LocalTime.of(9, 30),
                        notes = "взять карту",
                        reminderOffsetsMin = listOf(1440, 120),
                        createdAt = instant,
                    ),
                ),
        )

    @Test
    fun `full round trip preserves everything`() {
        val yaml = BackupCodec.encode(BackupMapper.toSnapshot(fullData, instant))
        val restored = BackupMapper.fromSnapshot(BackupCodec.decode(yaml))
        assertThat(restored).isEqualTo(fullData)
    }

    @Test
    fun `golden v1 fixture parses`() {
        val data = BackupMapper.fromSnapshot(BackupCodec.decode(GOLDEN_V1_YAML))
        assertThat(data.profiles).hasSize(1)
        assertThat(data.medications.single().form).isEqualTo(MedicationForm.DROPS)
        assertThat(
            data.schedules
                .single()
                .times
                .single()
                .time,
        ).isEqualTo(LocalTime.of(9, 0))
        assertThat(data.settings.language).isEqualTo(AppLanguage.RU)
        assertThat(data.settings.onboardingDone).isTrue()
    }

    @Test
    fun `garbage is rejected`() {
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("hello: [broken")
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("just a string, not a backup")
        }
    }

    @Test
    fun `unknown keys are rejected in strict mode`() {
        val yaml = BackupCodec.encode(BackupMapper.toSnapshot(fullData, instant))
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(yaml.replaceFirst("schema_version:", "surprise: 1\nschema_version:"))
        }
    }

    @Test
    fun `newer schema version is rejected`() {
        val yaml =
            BackupCodec
                .encode(BackupMapper.toSnapshot(fullData, instant))
                .replaceFirst("schema_version: 1", "schema_version: 99")
        assertThrows(BackupFormatException::class.java) {
            BackupMapper.fromSnapshot(BackupCodec.decode(yaml))
        }
    }

    @Test
    fun `unknown enum value is rejected`() {
        val yaml =
            BackupCodec
                .encode(BackupMapper.toSnapshot(fullData, instant))
                .replaceFirst("\"CAPSULE\"", "\"POTION\"")
        assertThrows(BackupFormatException::class.java) {
            BackupMapper.fromSnapshot(BackupCodec.decode(yaml))
        }
    }

    @Test
    fun `dangling default variant is rejected`() {
        val broken =
            fullData.copy(
                medications =
                    fullData.medications.map {
                        if (it.id == "m1") it.copy(defaultVariantId = "ghost") else it
                    },
            )
        val yaml = BackupCodec.encode(BackupMapper.toSnapshot(broken, instant))
        assertThrows(BackupFormatException::class.java) {
            BackupMapper.fromSnapshot(BackupCodec.decode(yaml))
        }
    }

    @Test
    fun `dose log referencing missing schedule is rejected`() {
        val broken =
            fullData.copy(
                doseLogs =
                    fullData.doseLogs.map {
                        if (it.id == "d1") it.copy(scheduleId = "ghost") else it
                    },
            )
        val yaml = BackupCodec.encode(BackupMapper.toSnapshot(broken, instant))
        assertThrows(BackupFormatException::class.java) {
            BackupMapper.fromSnapshot(BackupCodec.decode(yaml))
        }
    }

    @Test
    fun `active profile pointing nowhere is rejected`() {
        val broken = fullData.copy(settings = fullData.settings.copy(activeProfileId = "ghost"))
        val yaml = BackupCodec.encode(BackupMapper.toSnapshot(broken, instant))
        assertThrows(BackupFormatException::class.java) {
            BackupMapper.fromSnapshot(BackupCodec.decode(yaml))
        }
    }

    @Test
    fun `scheduled log without a time is rejected`() {
        // A scheduled log needs its wall-clock identity; letting one through
        // would crash every reminder pass after the import.
        val broken =
            fullData.copy(
                doseLogs =
                    fullData.doseLogs.map {
                        if (it.kind == DoseKind.SCHEDULED) it.copy(time = null) else it
                    },
            )
        val yaml = BackupCodec.encode(BackupMapper.toSnapshot(broken, instant))
        assertThrows(BackupFormatException::class.java) {
            BackupMapper.fromSnapshot(BackupCodec.decode(yaml))
        }
    }

    @Test
    fun `duplicate schedule ids are rejected with a readable error`() {
        val broken = fullData.copy(schedules = fullData.schedules + fullData.schedules.first())
        val yaml = BackupCodec.encode(BackupMapper.toSnapshot(broken, instant))
        assertThrows(BackupFormatException::class.java) {
            BackupMapper.fromSnapshot(BackupCodec.decode(yaml))
        }
    }
}

private val GOLDEN_V1_YAML =
    """
    schema_version: 1
    exported_at: "2026-08-29T08:00:00Z"
    settings:
      active_profile_id: "p1"
      nag_interval_min: 10
      nag_max_count: 6
      snooze_min: 10
      missed_grace_min: 60
      theme: "SYSTEM"
      dynamic_color: true
      language: "RU"
      low_stock_notify: true
    profiles:
    - id: "p1"
      name: "Тест"
      color_seed: 3
      avatar_key: null
      sort_order: 0
      created_at: "2026-08-01T00:00:00Z"
      medications:
      - id: "m1"
        name: "Витамин D"
        form: "DROPS"
        strength_value: 2000.0
        strength_unit: "МЕ"
        instructions: null
        color_seed: 3
        icon_key: "drops"
        default_variant_id: null
        archived_at: null
        created_at: "2026-08-01T00:00:00Z"
        variants: []
        schedules:
        - id: "s1"
          type: "FIXED_TIMES"
          start_date: "2026-08-01"
          end_date: null
          weekdays: []
          interval_days: null
          cycle_days_on: null
          cycle_days_off: null
          default_dose_amount: 1.0
          reminders_enabled: true
          created_at: "2026-08-01T00:00:00Z"
          times:
          - id: "t1"
            time: "09:00"
            dose_amount: 1.0
            sort_index: 0
      dose_logs: []
      appointments: []
    """.trimIndent()
