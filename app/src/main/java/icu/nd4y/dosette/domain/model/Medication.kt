package icu.nd4y.dosette.domain.model

import java.time.Instant

data class Medication(
    val id: String,
    val profileId: String,
    val name: String,
    val form: MedicationForm,
    val strengthValue: Double?,
    val strengthUnit: String?,
    val instructions: String?,
    val colorSeed: Int,
    val iconKey: String,
    /** Variant consumed by default when the dose is taken. */
    val defaultVariantId: String?,
    val archivedAt: Instant?,
    val createdAt: Instant,
    /** null follows global reminders; 0 disables repeats for this medication. */
    val nagIntervalMin: Int? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}

enum class MedicationForm {
    TABLET,
    CAPSULE,
    INJECTION,
    DROPS,
    LIQUID,
    INHALER,
    OINTMENT,
    SPRAY,
    OTHER,
}
