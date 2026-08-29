package icu.nd4y.dosette.reminders.places

import icu.nd4y.dosette.domain.model.PlaceConfig
import icu.nd4y.dosette.domain.model.PlaceId

/**
 * Android boundary of the "snooze until a place" feature; faked in tests.
 * Geofences are the passive trigger, [isCurrentlyAt] is the Wi-Fi check the
 * engine runs on every pass as a fallback.
 */
interface PlaceMonitor {
    /** Best-effort: does the current Wi-Fi network match [config]? */
    fun isCurrentlyAt(config: PlaceConfig): Boolean

    /** Keep geofences registered for exactly [places]; empty map clears all. */
    fun syncGeofences(places: Map<PlaceId, PlaceConfig>)
}
