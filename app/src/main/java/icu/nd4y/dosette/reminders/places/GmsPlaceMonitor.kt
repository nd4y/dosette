package icu.nd4y.dosette.reminders.places

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import icu.nd4y.dosette.domain.model.PlaceConfig
import icu.nd4y.dosette.domain.model.PlaceId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geofences via Play services (passive, battery-friendly) plus a Wi-Fi SSID
 * check. Everything degrades gracefully: without location permission both
 * signals are simply unavailable and place-snoozes wake only via the
 * 15-minute poll once permission appears.
 */
@Singleton
class GmsPlaceMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PlaceMonitor {
        private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

        private val geofencePendingIntent: PendingIntent by lazy {
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, PlaceReceiver::class.java),
                // MUTABLE: the geofencing service fills in the triggering event.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }

        private fun hasLocationPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        override fun isCurrentlyAt(config: PlaceConfig): Boolean {
            val ssid = config.wifiSsid
            if (ssid == null || !hasLocationPermission()) return false

            // Deprecated but still the only synchronous SSID source; returns
            // <unknown ssid> without location permission, which never matches.
            @Suppress("DEPRECATION")
            val current =
                context
                    .getSystemService(WifiManager::class.java)
                    ?.connectionInfo
                    ?.ssid
                    ?.trim('"')
            return current == ssid
        }

        @SuppressLint("MissingPermission")
        override fun syncGeofences(places: Map<PlaceId, PlaceConfig>) {
            if (!hasLocationPermission()) return
            geofencingClient.removeGeofences(geofencePendingIntent)

            val fences =
                places
                    .filterValues { it.hasGeo }
                    .map { (id, config) ->
                        Geofence
                            .Builder()
                            .setRequestId(id.name)
                            .setCircularRegion(
                                requireNotNull(config.latitude),
                                requireNotNull(config.longitude),
                                config.radiusMeters.toFloat(),
                            ).setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                            .build()
                    }
            if (fences.isEmpty()) return

            val request =
                GeofencingRequest
                    .Builder()
                    // Already inside when snoozing "until home" while at home is
                    // legitimate: fire immediately.
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(fences)
                    .build()
            geofencingClient.addGeofences(request, geofencePendingIntent)
        }
    }
