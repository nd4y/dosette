package icu.nd4y.dosette.data.backup

import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.domain.backup.BackupSnapshot
import icu.nd4y.dosette.domain.backup.ProfileBackup
import icu.nd4y.dosette.domain.backup.SettingsBackup
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.MedicationVariant
import icu.nd4y.dosette.domain.model.Profile
import icu.nd4y.dosette.domain.model.Schedule
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
                ),
            profiles =
                data.profiles.map { profile ->
                    val meds = data.medications.filter { it.profileId == profile.id }
                    ProfileBackup(
                        id = profile.id,
                        name = profile.name,
                        colorSeed = profile.colorSeed,
                        avatarKey = profile.avatarKey,
                        sortOrder = profile.sortOrder,
                        createdAt = profile.createdAt.toString(),
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
            )
        validate(data)
        return data
    }

    private fun validate(data: BackupData) {
        val profileIds = data.profiles.mapTo(mutableSetOf()) { it.id }
        val medIds = data.medications.mapTo(mutableSetOf()) { it.id }
        val scheduleIds = data.schedules.mapTo(mutableSetOf()) { it.id }
        val variantsByMed = data.variants.groupBy({ it.medicationId }, { it.id })

        check(profileIds.size == data.profiles.size) { "duplicate profile ids" }
        check(medIds.size == data.medications.size) { "duplicate medication ids" }

        data.settings.activeProfileId?.let {
            check(it in profileIds) { "active_profile_id $it points to a missing profile" }
        }
        for (med in data.medications) {
            med.defaultVariantId?.let { variantId ->
                check(variantId in variantsByMed[med.id].orEmpty()) {
                    "medication ${med.id}: default_variant_id $variantId is not among its variants"
                }
            }
        }
        for (log in data.doseLogs) {
            check(log.medicationId in medIds) { "dose log ${log.id} references missing medication ${log.medicationId}" }
            log.scheduleId?.let {
                check(it in scheduleIds) { "dose log ${log.id} references missing schedule $it" }
            }
            log.variantId?.let {
                check(it in variantsByMed[log.medicationId].orEmpty()) {
                    "dose log ${log.id} references missing variant $it"
                }
            }
        }
    }

    private inline fun check(
        condition: Boolean,
        message: () -> String,
    ) {
        if (!condition) throw BackupFormatException(message())
    }
}
