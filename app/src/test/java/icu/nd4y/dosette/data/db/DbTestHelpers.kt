package icu.nd4y.dosette.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import icu.nd4y.dosette.data.db.entity.DoseLogEntity
import icu.nd4y.dosette.data.db.entity.InventoryEntity
import icu.nd4y.dosette.data.db.entity.MedicationEntity
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
    archivedAt = archivedAt,
    createdAt = testInstant,
)

fun scheduleEntity(
    id: String = "s1",
    medicationId: String = "m1",
    startDate: LocalDate = LocalDate.parse("2026-05-01"),
    endDate: LocalDate? = null,
) = ScheduleEntity(
    id = id,
    medicationId = medicationId,
    type = "FIXED_TIMES",
    startDate = startDate,
    endDate = endDate,
    weekdaysMask = 0,
    intervalDays = null,
    cycleDaysOn = null,
    cycleDaysOff = null,
    defaultDoseAmount = 1.0,
    remindersEnabled = true,
    createdAt = testInstant,
)

fun scheduleTimeEntity(
    id: String = "t1",
    scheduleId: String = "s1",
    timeMinutes: Int = 8 * 60,
    sortIndex: Int = 0,
) = ScheduleTimeEntity(
    id = id,
    scheduleId = scheduleId,
    timeMinutes = timeMinutes,
    doseAmount = 1.0,
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
    note = null,
    updatedAt = testInstant,
)

fun inventoryEntity(
    medicationId: String = "m1",
    currentStock: Double = 10.0,
    trackingEnabled: Boolean = true,
) = InventoryEntity(
    medicationId = medicationId,
    trackingEnabled = trackingEnabled,
    currentStock = currentStock,
    lowStockThreshold = 5.0,
    defaultRefillAmount = 60.0,
    lastRefillAt = null,
)
