package icu.nd4y.dosette.testing

import icu.nd4y.dosette.domain.model.PlaceConfig
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.reminders.places.PlaceMonitor

/** Answers the Wi-Fi check with [currentlyAt] and remembers the last geofence set. */
class FakePlaceMonitor : PlaceMonitor {
    var currentlyAt = false
    var synced: Map<PlaceId, PlaceConfig> = emptyMap()
        private set

    override fun isCurrentlyAt(config: PlaceConfig): Boolean = currentlyAt

    override fun syncGeofences(places: Map<PlaceId, PlaceConfig>) {
        synced = places
    }
}
