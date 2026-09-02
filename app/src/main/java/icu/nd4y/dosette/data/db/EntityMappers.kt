package icu.nd4y.dosette.data.db

import icu.nd4y.dosette.data.db.entity.AppointmentEntity
import icu.nd4y.dosette.data.db.entity.DoseLogEntity
import icu.nd4y.dosette.data.db.entity.MedicationEntity
import icu.nd4y.dosette.data.db.entity.MedicationVariantEntity
import icu.nd4y.dosette.data.db.entity.ProfileEntity
import icu.nd4y.dosette.data.db.entity.ReminderStateEntity
import icu.nd4y.dosette.data.db.entity.ScheduleEntity
import icu.nd4y.dosette.data.db.entity.ScheduleTimeEntity
import icu.nd4y.dosette.data.db.entity.ScheduleWithTimes
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.MedicationVariant
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.domain.model.Profile
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.domain.model.ReminderState
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleTime
import icu.nd4y.dosette.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalTime

internal fun LocalTime.toMinutes(): Int = hour * 60 + minute

private fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)

private fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

private fun Int.toWeekdays(): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(mutableSetOf()) { this and (1 shl (it.value - 1)) != 0 }

fun ProfileEntity.toDomain(): Profile = Profile(id, name, colorSeed, avatarKey, sortOrder, createdAt)

fun Profile.toEntity(): ProfileEntity = ProfileEntity(id, name, colorSeed, avatarKey, sortOrder, createdAt)

fun MedicationEntity.toDomain(): Medication =
    Medication(
        id = id,
        profileId = profileId,
        name = name,
        form = MedicationForm.valueOf(form),
        strengthValue = strengthValue,
        strengthUnit = strengthUnit,
        instructions = instructions,
        colorSeed = colorSeed,
        iconKey = iconKey,
        defaultVariantId = defaultVariantId,
        archivedAt = archivedAt,
        createdAt = createdAt,
    )

fun Medication.toEntity(): MedicationEntity =
    MedicationEntity(
        id = id,
        profileId = profileId,
        name = name,
        form = form.name,
        strengthValue = strengthValue,
        strengthUnit = strengthUnit,
        instructions = instructions,
        colorSeed = colorSeed,
        iconKey = iconKey,
        defaultVariantId = defaultVariantId,
        archivedAt = archivedAt,
        createdAt = createdAt,
    )

fun ScheduleWithTimes.toDomain(): Schedule =
    Schedule(
        id = schedule.id,
        medicationId = schedule.medicationId,
        type = ScheduleType.valueOf(schedule.type),
        startDate = schedule.startDate,
        endDate = schedule.endDate,
        anchorDate = schedule.anchorDate,
        oneOff = schedule.oneOff,
        weekdays = schedule.weekdaysMask.toWeekdays(),
        intervalDays = schedule.intervalDays,
        cycleDaysOn = schedule.cycleDaysOn,
        cycleDaysOff = schedule.cycleDaysOff,
        defaultDoseAmount = schedule.defaultDoseAmount,
        remindersEnabled = schedule.remindersEnabled,
        createdAt = schedule.createdAt,
        times =
            times
                .sortedBy { it.sortIndex }
                .map { ScheduleTime(it.id, it.scheduleId, it.timeMinutes.toLocalTime(), it.doseAmount, it.sortIndex) },
    )

fun Schedule.toEntity(): ScheduleEntity =
    ScheduleEntity(
        id = id,
        medicationId = medicationId,
        type = type.name,
        startDate = startDate,
        endDate = endDate,
        anchorDate = anchorDate,
        oneOff = oneOff,
        weekdaysMask = weekdays.toMask(),
        intervalDays = intervalDays,
        cycleDaysOn = cycleDaysOn,
        cycleDaysOff = cycleDaysOff,
        defaultDoseAmount = defaultDoseAmount,
        remindersEnabled = remindersEnabled,
        createdAt = createdAt,
    )

fun Schedule.timeEntities(): List<ScheduleTimeEntity> =
    times.map { ScheduleTimeEntity(it.id, it.scheduleId, it.time.toMinutes(), it.doseAmount, it.sortIndex) }

fun DoseLogEntity.toDomain(): DoseLog =
    DoseLog(
        id = id,
        profileId = profileId,
        medicationId = medicationId,
        scheduleId = scheduleId,
        kind = DoseKind.valueOf(kind),
        date = date,
        time = timeMinutes?.toLocalTime(),
        scheduledAt = scheduledAt,
        status = DoseStatus.valueOf(status),
        actedAt = actedAt,
        amount = amount,
        variantId = variantId,
        consumedUnits = consumedUnits,
        note = note,
        updatedAt = updatedAt,
    )

fun DoseLog.toEntity(): DoseLogEntity =
    DoseLogEntity(
        id = id,
        profileId = profileId,
        medicationId = medicationId,
        scheduleId = scheduleId,
        kind = kind.name,
        date = date,
        timeMinutes = time?.toMinutes(),
        scheduledAt = scheduledAt,
        status = status.name,
        actedAt = actedAt,
        amount = amount,
        variantId = variantId,
        consumedUnits = consumedUnits,
        note = note,
        updatedAt = updatedAt,
    )

fun MedicationVariantEntity.toDomain(): MedicationVariant =
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
        lastRefillAt = lastRefillAt,
    )

fun MedicationVariant.toEntity(): MedicationVariantEntity =
    MedicationVariantEntity(
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
        lastRefillAt = lastRefillAt,
    )

fun AppointmentEntity.toDomain(): Appointment =
    Appointment(
        id = id,
        profileId = profileId,
        title = title,
        doctorName = doctorName,
        location = location,
        date = date,
        time = timeMinutes.toLocalTime(),
        notes = notes,
        reminderOffsetsMin = reminderOffsetsMin.split(',').filter { it.isNotBlank() }.map(String::toInt),
        createdAt = createdAt,
    )

fun Appointment.toEntity(): AppointmentEntity =
    AppointmentEntity(
        id = id,
        profileId = profileId,
        title = title,
        doctorName = doctorName,
        location = location,
        date = date,
        timeMinutes = time.toMinutes(),
        notes = notes,
        reminderOffsetsMin = reminderOffsetsMin.joinToString(","),
        createdAt = createdAt,
    )

fun ReminderStateEntity.toDomain(): ReminderState =
    ReminderState(
        occurrenceKey = OccurrenceKey.decode(occurrenceKey),
        medicationId = medicationId,
        profileId = profileId,
        scheduledAt = scheduledAt,
        phase = ReminderPhase.valueOf(phase),
        snoozedUntil = snoozedUntil,
        snoozedUntilPlace = snoozedUntilPlace?.let(PlaceId::valueOf),
        graceAnchor = graceAnchor,
        nagCount = nagCount,
        firstNotifiedAt = firstNotifiedAt,
        lastAlertAt = lastAlertAt,
    )

fun ReminderState.toEntity(): ReminderStateEntity =
    ReminderStateEntity(
        occurrenceKey = occurrenceKey.encode(),
        medicationId = medicationId,
        profileId = profileId,
        scheduledAt = scheduledAt,
        phase = phase.name,
        snoozedUntil = snoozedUntil,
        snoozedUntilPlace = snoozedUntilPlace?.name,
        graceAnchor = graceAnchor,
        nagCount = nagCount,
        firstNotifiedAt = firstNotifiedAt,
        lastAlertAt = lastAlertAt,
    )
