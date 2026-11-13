package icu.nd4y.dosette.wear.tile

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** Wakes the tile even when the Wear activity has no active DataClient collector. */
class TileSnapshotListener : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        TileService.getUpdater(this).requestUpdate(DoseTileService::class.java)
    }
}
