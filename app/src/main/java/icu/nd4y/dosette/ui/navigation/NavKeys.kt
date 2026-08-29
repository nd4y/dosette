package icu.nd4y.dosette.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object CabinetKey : NavKey

@Serializable
data class MedDetailKey(
    val medicationId: String,
) : NavKey

@Serializable
data class MedEditKey(
    /** null = create a new medication. */
    val medicationId: String? = null,
) : NavKey

@Serializable
data object MoreKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object ProfilesKey : NavKey
