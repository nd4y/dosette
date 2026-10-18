package icu.nd4y.dosette.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import icu.nd4y.dosette.domain.model.PlaceId

internal fun requestBackgroundLocationAccess(
    context: Context,
    onLegacyRequest: () -> Unit,
    onFailure: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android 11+ grants all-the-time access in Settings, not a runtime dialog.
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }.onFailure { onFailure() }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        onLegacyRequest()
    }
}

internal fun requestPlaceAccess(
    context: Context,
    request: Pair<PlaceId, PlaceAction>,
    onGranted: (Pair<PlaceId, PlaceAction>) -> Unit,
    onRequest: (Pair<PlaceId, PlaceAction>) -> Unit,
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        onGranted(request)
    } else {
        onRequest(request)
    }
}

internal fun capturePlace(
    context: Context,
    viewModel: SettingsViewModel,
    request: Pair<PlaceId, PlaceAction>,
    onLocationSaved: () -> Unit,
    onFailure: () -> Unit,
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val (id, action) = request
    when (action) {
        PlaceAction.USE_LOCATION -> {
            LocationServices
                .getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        onFailure()
                    } else {
                        viewModel.setPlaceFromLocation(id, location.latitude, location.longitude)
                        onLocationSaved()
                    }
                }.addOnFailureListener { onFailure() }
        }

        PlaceAction.USE_WIFI -> {
            viewModel.bindCurrentWifi(id)
        }
    }
}
