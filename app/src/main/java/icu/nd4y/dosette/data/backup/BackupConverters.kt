package icu.nd4y.dosette.data.backup

import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.domain.backup.AppointmentBackup
import icu.nd4y.dosette.domain.backup.DoseLogBackup
import icu.nd4y.dosette.domain.backup.MedicationBackup
import icu.nd4y.dosette.domain.backup.ProfileBackup
import icu.nd4y.dosette.domain.backup.ScheduleBackup
import icu.nd4y.dosette.domain.backup.ScheduleTimeBackup
import icu.nd4y.dosette.domain.backup.SettingsBackup
import icu.nd4y.dosette.domain.backup.VariantBackup
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal val BACKUP_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal inline fun <reified E : Enum<E>> parseEnum(
    value: String,
    field: String,
): E =
    runCatching { enumValueOf<E>(value) }
        .getOrElse { throw BackupFormatException("unknown $field: $value") }

internal fun parseInstant(
    value: String,
    field: String,
): Instant =
    runCatching { Instant.parse(value) }
        .getOrElse { throw BackupFormatException("bad timestamp in $field: $value", it) }

internal fun parseDate(
    value: String,
    field: String,
): LocalDate =
    runCatching { LocalDate.parse(value) }
        .getOrElse { throw BackupFormatException("bad date in $field: $value", it) }

internal fun parseTime(
    value: String,
    field: String,
): LocalTime =
    runCatching { LocalTime.parse(value, BACKUP_TIME_FORMAT) }
        .getOrElse { throw BackupFormatException("bad time in $field: $value", it) }

internal fun SettingsBackup.toDomain(): AppSettings =
    AppSettings(
        activeProfileId = activeProfileId,
        nagIntervalMin = nagIntervalMin,
        nagMaxCount = nagMaxCount,
        snoozeMin = snoozeMin,
        missedGraceMin = missedGraceMin,
        theme = parseEnum<ThemeMode>(theme, "theme"),
        dynamicColor = dynamicColor,
        language = parseEnum<AppLanguage>(language, "language"),
        lowStockNotifyEnabled = lowStockNotifyEnabled,
        // Import must never re-open onboarding.
        onboardingDone = true,
    )

internal fun ProfileBackup.toDomain(): Profile =
    Profile(
        id = id,
        name = name,
        colorSeed = colorSeed,
        avatarKey = avatarKey,
        sortOrder = sortOrder,
        createdAt = parseInstant(createdAt, "profile.created_at"),
    )

internal fun Medication.toBackup(data: BackupData): MedicationBackup =
    MedicationBackup(
        id = id,
        name = name,
        form = form.name,
        strengthValue = strengthValue,
        strengthUnit = strengthUnit,
        instructions = instructions,
        colorSeed = colorSeed,
        iconKey = iconKey,
        defaultVariantId = defaultVariantId,
        archivedAt = archivedAt?.toString(),
        createdAt = createdAt.toString(),
        variants =
            data.variants
                .filter { it.medicationId == id }
                .map { it.toBackup() },
        schedules =
            data.schedules
                .filter { it.medicationId == id }
                .map { it.toBackup() },
    )

internal fun MedicationBackup.toDomain(profileId: String): Medication =
    Medication(
        id = id,
        profileId = profileId,
        name = name,
        form = parseEnum<MedicationForm>(form, "medication.form"),
        strengthValue = strengthValue,
        strengthUnit = strengthUnit,
        instructions = instructions,
        colorSeed = colorSeed,
        iconKey = iconKey,
        defaultVariantId = defaultVariantId,
        archivedAt = archivedAt?.let { parseInstant(it, "medication.archived_at") },
        createdAt = parseInstant(createdAt, "medication.created_at"),
    )

internal fun MedicationVariant.toBackup(): VariantBackup =
    VariantBackup(
        id = id,
        label = label,
        strengthValue = strengthValue,
        strengthUnit = strengthUnit,
        sortOrder = sortOrder,
        trackingEnabled = trackingEnabled,
        currentStock = currentStock,
        lowStockThreshold = lowStockThreshold,
        defaultRefillAmount = defaultRefillAmount,
        lastRefillAt = lastRefillAt?.toString(),
    )

internal fun VariantBackup.toDomain(medicationId: String): MedicationVariant =
    MedicationVariant(
        id = id,
        medicationId = medicationId,
        label = label,
        strengthValue = strengthValue,
        strengthUnit = strengthUnit,
        sortOrder = sortOrder,
        trackingEnabled = trackingEnabled,
        currentStock = currentStock,
        lowStockThreshold = lowStockThreshold,
        defaultRefillAmount = defaultRefillAmount,
        lastRefillAt = lastRefillAt?.let { parseInstant(it, "variant.last_refill_at") },
    )

internal fun Schedule.toBackup(): ScheduleBackup =
    ScheduleBackup(
        id = id,
        type = type.name,
        startDate = startDate.toString(),
        endDate = endDate?.toString(),
        weekdays = weekdays.sortedBy { it.value }.map { it.name },
        intervalDays = intervalDays,
        cycleDaysOn = cycleDaysOn,
        cycleDaysOff = cycleDaysOff,
        defaultDoseAmount = defaultDoseAmount,
        remindersEnabled = remindersEnabled,
        createdAt = createdAt.toString(),
        times =
            times.map {
                ScheduleTimeBackup(
                    id = it.id,
                    time = it.time.format(BACKUP_TIME_FORMAT),
                    doseAmount = it.doseAmount,
                    sortIndex = it.sortIndex,
                )
            },
    )

internal fun ScheduleBackup.toDomain(medicationId: String): Schedule =
    Schedule(
        id = id,
        medicationId = medicationId,
        type = parseEnum<ScheduleType>(type, "schedule.type"),
        startDate = parseDate(startDate, "schedule.start_date"),
        endDate = endDate?.let { parseDate(it, "schedule.end_date") },
        weekdays = weekdays.mapTo(mutableSetOf()) { parseEnum<DayOfWeek>(it, "schedule.weekdays") },
        intervalDays = intervalDays,
        cycleDaysOn = cycleDaysOn,
        cycleDaysOff = cycleDaysOff,
        defaultDoseAmount = defaultDoseAmount,
        remindersEnabled = remindersEnabled,
        createdAt = parseInstant(createdAt, "schedule.created_at"),
        times =
            times.map {
                ScheduleTime(
                    id = it.id,
                    scheduleId = id,
                    time = parseTime(it.time, "schedule.times.time"),
                    doseAmount = it.doseAmount,
                    sortIndex = it.sortIndex,
                )
            },
    )

internal fun DoseLog.toBackup(): DoseLogBackup =
    DoseLogBackup(
        id = id,
        medicationId = medicationId,
        scheduleId = scheduleId,
        kind = kind.name,
        date = date.toString(),
        time = time?.format(BACKUP_TIME_FORMAT),
        scheduledAt = scheduledAt?.toString(),
        status = status.name,
        actedAt = actedAt?.toString(),
        amount = amount,
        variantId = variantId,
        consumedUnits = consumedUnits,
        note = note,
        updatedAt = updatedAt.toString(),
    )

internal fun DoseLogBackup.toDomain(profileId: String): DoseLog =
    DoseLog(
        id = id,
        profileId = profileId,
        medicationId = medicationId,
        scheduleId = scheduleId,
        kind = parseEnum<DoseKind>(kind, "dose_log.kind"),
        date = parseDate(date, "dose_log.date"),
        time = time?.let { parseTime(it, "dose_log.time") },
        scheduledAt = scheduledAt?.let { parseInstant(it, "dose_log.scheduled_at") },
        status = parseEnum<DoseStatus>(status, "dose_log.status"),
        actedAt = actedAt?.let { parseInstant(it, "dose_log.acted_at") },
        amount = amount,
        variantId = variantId,
        consumedUnits = consumedUnits,
        note = note,
        updatedAt = parseInstant(updatedAt, "dose_log.updated_at"),
    )

internal fun Appointment.toBackup(): AppointmentBackup =
    AppointmentBackup(
        id = id,
        title = title,
        doctorName = doctorName,
        location = location,
        date = date.toString(),
        time = time.format(BACKUP_TIME_FORMAT),
        notes = notes,
        reminderOffsetsMin = reminderOffsetsMin,
        createdAt = createdAt.toString(),
    )

internal fun AppointmentBackup.toDomain(profileId: String): Appointment =
    Appointment(
        id = id,
        profileId = profileId,
        title = title,
        doctorName = doctorName,
        location = location,
        date = parseDate(date, "appointment.date"),
        time = parseTime(time, "appointment.time"),
        notes = notes,
        reminderOffsetsMin = reminderOffsetsMin,
        createdAt = parseInstant(createdAt, "appointment.created_at"),
    )
