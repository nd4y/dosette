package icu.nd4y.dosette.reminders.places

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.di.IoDispatcher
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.reminders.ReminderEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Geofence ENTER fired: wake the reminders snoozed until that place. */
@AndroidEntryPoint
class PlaceReceiver : BroadcastReceiver() {
    @Inject
    lateinit var engine: ReminderEngine

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val event = GeofencingEvent.fromIntent(intent)?.takeIf { !it.hasError() }
        val places =
            event
                ?.triggeringGeofences
                .orEmpty()
                .mapNotNull { fence -> runCatching { PlaceId.valueOf(fence.requestId) }.getOrNull() }
                .distinct()
        if (places.isEmpty()) return

        val result = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                runCatching { places.forEach { engine.onPlaceReached(it) } }
                    .onFailure { Log.e("PlaceReceiver", "engine pass failed", it) }
            } finally {
                result.finish()
            }
        }
    }
}
