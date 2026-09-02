package icu.nd4y.dosette.domain.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full-backup YAML schema. Everything the app owns lives under one root:
 * settings first, then profiles with their medications, logs and visits
 * nested inside. Dates and times are ISO strings so the file stays
 * hand-readable and diff-friendly.
 */
@Serializable
data class BackupSnapshot(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("exported_at") val exportedAt: String,
    val settings: SettingsBackup,
    val profiles: List<ProfileBackup> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class SettingsBackup(
    @SerialName("active_profile_id") val activeProfileId: String? = null,
    @SerialName("nag_interval_min") val nagIntervalMin: Int,
    @SerialName("nag_max_count") val nagMaxCount: Int,
    @SerialName("snooze_min") val snoozeMin: Int,
    @SerialName("missed_grace_min") val missedGraceMin: Int,
    val theme: String,
    @SerialName("dynamic_color") val dynamicColor: Boolean,
    val language: String,
    @SerialName("low_stock_notify") val lowStockNotifyEnabled: Boolean,
    /** Defaulted: backups written before the setting existed still parse. */
    @SerialName("alarm_clock") val alarmClock: Boolean = true,
)

@Serializable
data class ProfileBackup(
    val id: String,
    val name: String,
    @SerialName("color_seed") val colorSeed: Int,
    @SerialName("avatar_key") val avatarKey: String? = null,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("created_at") val createdAt: String,
    val medications: List<MedicationBackup> = emptyList(),
    @SerialName("dose_logs") val doseLogs: List<DoseLogBackup> = emptyList(),
    val appointments: List<AppointmentBackup> = emptyList(),
)

@Serializable
data class MedicationBackup(
    val id: String,
    val name: String,
    val form: String,
    @SerialName("strength_value") val strengthValue: Double? = null,
    @SerialName("strength_unit") val strengthUnit: String? = null,
    val instructions: String? = null,
    @SerialName("color_seed") val colorSeed: Int,
    @SerialName("icon_key") val iconKey: String,
    @SerialName("default_variant_id") val defaultVariantId: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    val variants: List<VariantBackup> = emptyList(),
    val schedules: List<ScheduleBackup> = emptyList(),
)

@Serializable
data class VariantBackup(
    val id: String,
    val label: String? = null,
    @SerialName("strength_value") val strengthValue: Double? = null,
    @SerialName("strength_unit") val strengthUnit: String? = null,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("tracking_enabled") val trackingEnabled: Boolean,
    @SerialName("current_stock") val currentStock: Double,
    @SerialName("low_stock_threshold") val lowStockThreshold: Double? = null,
    @SerialName("default_refill_amount") val defaultRefillAmount: Double? = null,
    @SerialName("last_refill_at") val lastRefillAt: String? = null,
)

@Serializable
data class ScheduleBackup(
    val id: String,
    val type: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    /** Day names (MONDAY..SUNDAY) for WEEKDAYS schedules. */
    val weekdays: List<String> = emptyList(),
    @SerialName("interval_days") val intervalDays: Int? = null,
    @SerialName("cycle_days_on") val cycleDaysOn: Int? = null,
    @SerialName("cycle_days_off") val cycleDaysOff: Int? = null,
    @SerialName("default_dose_amount") val defaultDoseAmount: Double,
    @SerialName("reminders_enabled") val remindersEnabled: Boolean,
    @SerialName("created_at") val createdAt: String,
    val times: List<ScheduleTimeBackup> = emptyList(),
)

@Serializable
data class ScheduleTimeBackup(
    val id: String,
    /** Wall-clock "HH:mm". */
    val time: String,
    @SerialName("dose_amount") val doseAmount: Double,
    @SerialName("sort_index") val sortIndex: Int,
)

@Serializable
data class DoseLogBackup(
    val id: String,
    @SerialName("medication_id") val medicationId: String,
    @SerialName("schedule_id") val scheduleId: String? = null,
    val kind: String,
    val date: String,
    val time: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    val status: String,
    @SerialName("acted_at") val actedAt: String? = null,
    val amount: Double,
    @SerialName("variant_id") val variantId: String? = null,
    @SerialName("consumed_units") val consumedUnits: Double? = null,
    val note: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class AppointmentBackup(
    val id: String,
    val title: String,
    @SerialName("doctor_name") val doctorName: String? = null,
    val location: String? = null,
    val date: String,
    val time: String,
    val notes: String? = null,
    @SerialName("reminder_offsets_min") val reminderOffsetsMin: List<Int> = emptyList(),
    @SerialName("created_at") val createdAt: String,
)
