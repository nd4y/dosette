package icu.nd4y.dosette.domain.model

/** Named locations a reminder can be postponed to. */
enum class PlaceId {
    HOME,
    WORK,
}

/**
 * How a place is recognized. Either signal suffices: the geofence is the
 * passive battery-friendly trigger, the Wi-Fi SSID doubles as confirmation
 * and works indoors where GPS is weak.
 */
@kotlinx.serialization.Serializable
data class PlaceConfig(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val wifiSsid: String? = null,
) {
    val hasGeo: Boolean get() = latitude != null && longitude != null
    val isConfigured: Boolean get() = hasGeo || wifiSsid != null

    companion object {
        const val DEFAULT_RADIUS_METERS = 150
    }
}
