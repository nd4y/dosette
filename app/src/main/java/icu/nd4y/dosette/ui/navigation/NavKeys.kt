package icu.nd4y.dosette.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object CabinetKey : NavKey

@Serializable
data class MedDetailKey(
    val medicationId: String,
) : NavKey

/** The wizard only creates medications; an edit flow does not exist yet. */
@Serializable
data object MedEditKey : NavKey

@Serializable
data object MoreKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object ProfilesKey : NavKey

@Serializable
data object AppointmentsKey : NavKey

@Serializable
data class AppointmentEditKey(
    /** null = create a new appointment. */
    val appointmentId: String? = null,
) : NavKey

@Serializable
data object StatsKey : NavKey

@Serializable
data object BackupKey : NavKey
