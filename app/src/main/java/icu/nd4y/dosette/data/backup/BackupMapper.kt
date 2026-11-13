package icu.nd4y.dosette.data.backup

import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.domain.backup.BackupSnapshot
import icu.nd4y.dosette.domain.backup.ProfileBackup
import icu.nd4y.dosette.domain.backup.SettingsBackup
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.Cabinet
import icu.nd4y.dosette.domain.model.CabinetStock
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.MedicationVariant
import icu.nd4y.dosette.domain.model.Profile
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleType
import java.time.Instant

/** The import file is unusable: wrong schema, broken values or dangling references. */
class BackupFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Everything the app owns, flattened — the unit both export and import operate on. */
data class BackupData(
    val settings: AppSettings,
    val profiles: List<Profile>,
    val medications: List<Medication>,
    val variants: List<MedicationVariant>,
    val schedules: List<Schedule>,
    val doseLogs: List<DoseLog>,
    val appointments: List<Appointment>,
    val cabinets: List<Cabinet> = emptyList(),
    val stocks: List<CabinetStock> = emptyList(),
    val cabinetSelections: Map<String, String> = emptyMap(),
)

object BackupMapper {
    fun toSnapshot(
        data: BackupData,
        exportedAt: Instant,
    ): BackupSnapshot =
        BackupSnapshot(
            schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
            exportedAt = exportedAt.toString(),
            settings =
                SettingsBackup(
                    activeProfileId = data.settings.activeProfileId,
                    nagIntervalMin = data.settings.nagIntervalMin,
                    nagMaxCount = data.settings.nagMaxCount,
                    snoozeMin = data.settings.snoozeMin,
                    missedGraceMin = data.settings.missedGraceMin,
                    theme = data.settings.theme.name,
                    dynamicColor = data.settings.dynamicColor,
                    language = data.settings.language.name,
                    lowStockNotifyEnabled = data.settings.lowStockNotifyEnabled,
                    alarmClock = data.settings.alarmClock,
                ),
            profiles =
                data.profiles.map { profile ->
                    val meds = data.medications.filter { it.profileId == profile.id }
                    val cabinetIds = data.cabinets.filter { it.profileId == profile.id }.mapTo(mutableSetOf()) { it.id }
                    ProfileBackup(
                        id = profile.id,
                        name = profile.name,
                        colorSeed = profile.colorSeed,
                        avatarKey = profile.avatarKey,
                        sortOrder = profile.sortOrder,
                        createdAt = profile.createdAt.toString(),
                        cabinets = data.cabinets.filter { it.profileId == profile.id }.map { it.toBackup() },
                        activeCabinetId = data.cabinetSelections[profile.id],
                        stocks = data.stocks.filter { it.cabinetId in cabinetIds }.map { it.toBackup() },
                        medications = meds.map { med -> med.toBackup(data) },
                        doseLogs =
                            data.doseLogs
                                .filter { it.profileId == profile.id }
                                .map { it.toBackup() },
                        appointments =
                            data.appointments
                                .filter { it.profileId == profile.id }
                                .map { it.toBackup() },
                    )
                },
        )

    fun fromSnapshot(snapshot: BackupSnapshot): BackupData {
        check(snapshot.schemaVersion >= 1) { "unsupported backup schema ${snapshot.schemaVersion}" }
        if (snapshot.schemaVersion > BackupSnapshot.CURRENT_SCHEMA_VERSION) {
            throw BackupFormatException(
                "Backup schema ${snapshot.schemaVersion} is newer than supported " +
                    "(${BackupSnapshot.CURRENT_SCHEMA_VERSION}); update the app first",
            )
        }
        val profiles = snapshot.profiles.map { it.toDomain() }
        val medications = snapshot.profiles.flatMap { p -> p.medications.map { it.toDomain(p.id) } }
        val variants =
            snapshot.profiles.flatMap { p ->
                p.medications.flatMap { m -> m.variants.map { it.toDomain(m.id) } }
            }
        val schedules =
            snapshot.profiles.flatMap { p ->
                p.medications.flatMap { m -> m.schedules.map { it.toDomain(m.id) } }
            }
        val doseLogs = snapshot.profiles.flatMap { p -> p.doseLogs.map { it.toDomain(p.id) } }
        val appointments = snapshot.profiles.flatMap { p -> p.appointments.map { it.toDomain(p.id) } }
        val data =
            BackupData(
                settings = snapshot.settings.toDomain(),
                profiles = profiles,
                medications = medications,
                variants = variants,
                schedules = schedules,
                doseLogs = doseLogs,
                appointments = appointments,
                cabinets = snapshot.profiles.flatMap { it.cabinetRows() },
                stocks = snapshot.profiles.flatMap { it.stockRows() },
                cabinetSelections = snapshot.profiles.associate { it.id to (it.activeCabinetId ?: "main:${it.id}") },
            )
        validate(data)
        return data
    }

    private fun validate(data: BackupData) {
        validateReferences(data)
        validateValues(data)
        validateInventory(data)
        validateCabinets(data)
        validateDefaultStock(data)
    }

    private fun validateReferences(data: BackupData) {
        val profileIds = data.profiles.mapTo(mutableSetOf()) { it.id }
        val medicationsById = data.medications.associateBy { it.id }
        val medIds = medicationsById.keys
        val variantsByMed = data.variants.groupBy({ it.medicationId }, { it.id })

        // Duplicate ids must fail here with a readable message, not surface
        // as a raw SQLite constraint error from the import transaction.
        check(profileIds.size == data.profiles.size) { "duplicate profile ids" }
        check(medIds.size == data.medications.size) { "duplicate medication ids" }
        check(data.schedules.distinctBy { it.id }.size == data.schedules.size) { "duplicate schedule ids" }
        check(data.variants.distinctBy { it.id }.size == data.variants.size) { "duplicate variant ids" }
        check(data.doseLogs.distinctBy { it.id }.size == data.doseLogs.size) { "duplicate dose log ids" }
        check(data.appointments.distinctBy { it.id }.size == data.appointments.size) { "duplicate appointment ids" }
        val times = data.schedules.flatMap { it.times }
        check(times.distinctBy { it.id }.size == times.size) { "duplicate schedule time ids" }
        val scheduledLogs = data.doseLogs.filter { it.kind == DoseKind.SCHEDULED }
        check(scheduledLogs.distinctBy { Triple(it.medicationId, it.date, it.time) }.size == scheduledLogs.size) {
            "duplicate scheduled dose occurrences"
        }

        data.settings.activeProfileId?.let {
            check(it in profileIds) { "active_profile_id $it points to a missing profile" }
        }
        for (med in data.medications) {
            check(med.nagIntervalMin == null || med.nagIntervalMin in 0..1440) { "Invalid medication repeat interval" }
            // The id opens every reminder-state key ("id|date|time").
            check(med.id.isNotBlank() && '|' !in med.id) {
                "medication id '${med.id}' must be non-empty and must not contain '|'"
            }
            med.defaultVariantId?.let { variantId ->
                check(variantId in variantsByMed[med.id].orEmpty()) {
                    "medication ${med.id}: default_variant_id $variantId is not among its variants"
                }
            }
        }
        for (log in data.doseLogs) {
            check(log.medicationId in medIds) { "dose log ${log.id} references missing medication ${log.medicationId}" }
            check(medicationsById[log.medicationId]?.profileId == log.profileId) {
                "dose log ${log.id} belongs to a different profile than its medication"
            }
            // A scheduled log without a planned time has no occurrence
            // identity and would crash every reminder pass after import.
            if (log.kind == DoseKind.SCHEDULED) {
                check(log.time != null) { "scheduled dose log ${log.id} has no time" }
            }
            // schedule_id and variant_id are history, not references: the app
            // deletes replaced same-day versions and dropped package variants
            // while their logs stay, and an own export must always import.
        }
    }

    /** Values the reminder engine would choke on — a negative grace throws on every pass. */
    private fun validateValues(data: BackupData) {
        for (schedule in data.schedules) {
            schedule.endDate?.let {
                check(!it.isBefore(schedule.startDate)) {
                    "schedule ${schedule.id}: end_date $it is before start_date ${schedule.startDate}"
                }
            }
            when (schedule.type) {
                ScheduleType.EVERY_N_DAYS -> {
                    check(
                        (schedule.intervalDays ?: 0) >= 1,
                    ) { "schedule ${schedule.id}: interval_days must be at least 1" }
                }

                ScheduleType.CYCLE -> {
                    check((schedule.cycleDaysOn ?: 0) >= 1 && (schedule.cycleDaysOff ?: -1) >= 0) {
                        "schedule ${schedule.id}: cycle_days_on must be at least 1 and cycle_days_off at least 0"
                    }
                }

                else -> {
                    Unit
                }
            }
            check(schedule.defaultDoseAmount.isFinite() && schedule.defaultDoseAmount > 0) {
                "schedule ${schedule.id}: default_dose_amount must be positive and finite"
            }
            check(schedule.times.distinctBy { it.time }.size == schedule.times.size) {
                "schedule ${schedule.id}: duplicate dose times"
            }
            for (slot in schedule.times) {
                check(slot.doseAmount.isFinite() && slot.doseAmount > 0) {
                    "schedule ${schedule.id}: dose_amount must be positive and finite"
                }
            }
        }
        val s = data.settings
        check(s.nagIntervalMin >= 0) { "settings: nag_interval_min must not be negative" }
        check(s.nagMaxCount >= 0) { "settings: nag_max_count must not be negative (0 = no cap)" }
        check(s.snoozeMin >= 1 && s.missedGraceMin >= 1) {
            "settings: snooze_min and missed_grace_min must be at least 1"
        }
    }

    private fun validateInventory(data: BackupData) {
        data.medications.forEach { med ->
            med.strengthValue?.let { checkNonnegative(it, "medication ${med.id}: strength_value") }
        }
        data.variants.forEach { variant ->
            checkNonnegative(variant.currentStock, "variant ${variant.id}: current_stock")
            variant.strengthValue?.let { checkNonnegative(it, "variant ${variant.id}: strength_value") }
            variant.lowStockThreshold?.let { checkNonnegative(it, "variant ${variant.id}: low_stock_threshold") }
            variant.defaultRefillAmount?.let { checkNonnegative(it, "variant ${variant.id}: default_refill_amount") }
        }
        data.doseLogs.forEach { log ->
            checkNonnegative(log.amount, "dose log ${log.id}: amount")
            log.consumedUnits?.let { checkNonnegative(it, "dose log ${log.id}: consumed_units") }
        }
    }

    private fun validateCabinets(data: BackupData) {
        val cabinets = data.cabinets.associateBy { it.id }
        val variants = data.variants.associateBy { it.id }
        val meds = data.medications.associateBy { it.id }
        check(cabinets.size == data.cabinets.size) { "duplicate cabinet ids" }
        check(
            data.stocks.distinctBy { it.variantId to it.cabinetId }.size == data.stocks.size,
        ) { "duplicate stock entries" }
        for (profile in data.profiles) {
            check(cabinets["main:${profile.id}"]?.profileId == profile.id) { "missing default cabinet" }
            val selected = cabinets[data.cabinetSelections[profile.id]]
            check(selected?.profileId == profile.id && selected.archivedAt == null) { "invalid selected cabinet" }
        }
        for (cabinet in data.cabinets) {
            check(data.profiles.any { it.id == cabinet.profileId }) { "cabinet belongs to missing profile" }
            check(
                cabinet.id.isNotBlank() &&
                    (cabinet.name == null || (cabinet.name.isNotBlank() && cabinet.name.length <= 60)),
            ) { "invalid cabinet name or id" }
        }
        for (stock in data.stocks) {
            val variant = variants[stock.variantId]
            val cabinet = cabinets[stock.cabinetId]
            check(variant != null && cabinet != null && meds[variant.medicationId]?.profileId == cabinet.profileId) {
                "stock crosses profiles or references missing item"
            }
            checkNonnegative(stock.currentStock, "cabinet stock")
        }
        for (log in data.doseLogs) {
            log.cabinetId?.let { check(cabinets[it]?.profileId == log.profileId) { "dose source crosses profiles" } }
        }
    }

    /** The legacy projection must agree with the authoritative ledger; ambiguous files are rejected. */
    private fun validateDefaultStock(data: BackupData) {
        val medications = data.medications.associateBy { it.id }
        val stock = data.stocks.associateBy { it.variantId to it.cabinetId }
        for (variant in data.variants) {
            val profileId = medications.getValue(variant.medicationId).profileId
            val main = stock[variant.id to "main:$profileId"]
            check(variant.currentStock == (main?.currentStock ?: 0.0) && variant.lastRefillAt == main?.lastRefillAt) {
                "variant ${variant.id}: default stock projection disagrees with cabinet ledger"
            }
        }
    }

    private fun checkNonnegative(
        value: Double,
        field: String,
    ) {
        check(value.isFinite() && value >= 0) { "$field must be finite and nonnegative" }
    }

    private inline fun check(
        condition: Boolean,
        message: () -> String,
    ) {
        if (!condition) throw BackupFormatException(message())
    }
}

/**
 * The GCM tag did not verify: a wrong password or a corrupted file. Kept
 * apart from [BackupFormatException] so the password dialog can react to
 * it instead of reporting a broken backup.
 */
class BackupPasswordException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
