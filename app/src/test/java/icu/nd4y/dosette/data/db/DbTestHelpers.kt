package icu.nd4y.dosette.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import icu.nd4y.dosette.data.db.entity.DoseLogEntity
import icu.nd4y.dosette.data.db.entity.MedicationEntity
import icu.nd4y.dosette.data.db.entity.MedicationVariantEntity
import icu.nd4y.dosette.data.db.entity.ProfileEntity
import icu.nd4y.dosette.data.db.entity.ScheduleEntity
import icu.nd4y.dosette.data.db.entity.ScheduleTimeEntity
import java.time.Instant
import java.time.LocalDate

fun inMemoryDb(): AppDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

val testInstant: Instant = Instant.parse("2026-08-29T08:00:00Z")

fun profileEntity(
    id: String = "p1",
    name: String = "Alex",
) = ProfileEntity(id = id, name = name, colorSeed = 0, avatarKey = null, sortOrder = 0, createdAt = testInstant)

fun medicationEntity(
    id: String = "m1",
    profileId: String = "p1",
    name: String = "Metformin",
    archivedAt: Instant? = null,
) = MedicationEntity(
    id = id,
    profileId = profileId,
    name = name,
    form = "TABLET",
    strengthValue = 500.0,
    strengthUnit = "mg",
    instructions = null,
    colorSeed = 2,
    iconKey = "capsule",
    defaultVariantId = null,
    archivedAt = archivedAt,
    createdAt = testInstant,
)

fun scheduleEntity(
    id: String = "s1",
    medicationId: String = "m1",
    startDate: LocalDate = LocalDate.parse("2026-05-01"),
    endDate: LocalDate? = null,
    oneOff: Boolean = false,
    // Versions exist before their slots: the plan-as-of-creation rule drops earlier ones.
    createdAt: Instant = Instant.parse("2026-05-01T00:00:00Z"),
) = ScheduleEntity(
    id = id,
    medicationId = medicationId,
    type = "FIXED_TIMES",
    startDate = startDate,
    endDate = endDate,
    anchorDate = null,
    oneOff = oneOff,
    weekdaysMask = 0,
    intervalDays = null,
    cycleDaysOn = null,
    cycleDaysOff = null,
    defaultDoseAmount = 1.0,
    remindersEnabled = true,
    createdAt = createdAt,
)

fun scheduleTimeEntity(
    id: String = "t1",
    scheduleId: String = "s1",
    timeMinutes: Int = 8 * 60,
    sortIndex: Int = 0,
    doseAmount: Double = 1.0,
) = ScheduleTimeEntity(
    id = id,
    scheduleId = scheduleId,
    timeMinutes = timeMinutes,
    doseAmount = doseAmount,
    sortIndex = sortIndex,
)

fun doseLogEntity(
    id: String = "d1",
    profileId: String = "p1",
    medicationId: String = "m1",
    date: LocalDate = LocalDate.parse("2026-08-29"),
    timeMinutes: Int? = 8 * 60,
    status: String = "TAKEN",
    kind: String = "SCHEDULED",
) = DoseLogEntity(
    id = id,
    profileId = profileId,
    medicationId = medicationId,
    scheduleId = "s1",
    kind = kind,
    date = date,
    timeMinutes = timeMinutes,
    scheduledAt = testInstant,
    status = status,
    actedAt = testInstant,
    amount = 1.0,
    variantId = null,
    consumedUnits = null,
    note = null,
    updatedAt = testInstant,
)

fun variantEntity(
    id: String = "v1",
    medicationId: String = "m1",
    strengthValue: Double? = 500.0,
    currentStock: Double = 10.0,
    trackingEnabled: Boolean = true,
    sortOrder: Int = 0,
) = MedicationVariantEntity(
    id = id,
    medicationId = medicationId,
    label = null,
    strengthValue = strengthValue,
    strengthUnit = "mg",
    sortOrder = sortOrder,
    trackingEnabled = trackingEnabled,
    currentStock = currentStock,
    lowStockThreshold = 5.0,
    defaultRefillAmount = 60.0,
    lastRefillAt = null,
)
