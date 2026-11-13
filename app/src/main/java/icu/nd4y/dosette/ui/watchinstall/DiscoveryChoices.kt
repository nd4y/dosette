package icu.nd4y.dosette.ui.watchinstall

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import icu.nd4y.dosette.R
import icu.nd4y.dosette.watch.install.AdbServiceKind

@Composable
internal fun DiscoveryChoices(
    state: WatchWizardState,
    onAddress: (String, String, String) -> Unit,
) {
    val pairing = state.step == WatchInstallStep.PAIR
    val kind = if (pairing) AdbServiceKind.PAIRING else AdbServiceKind.CONNECTION
    val services = state.discovery.services.filter { it.kind == kind }
    Text(stringResource(R.string.watch_discovery_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.watch_discovery_hint), style = MaterialTheme.typography.bodySmall)
    if (state.discovery.unavailable || services.isEmpty()) {
        val message =
            if (state.discovery.unavailable) {
                R.string.watch_discovery_unavailable
            } else {
                R.string.watch_discovery_empty
            }
        Text(stringResource(message))
    }
    services.forEach { service ->
        TextButton(onClick = {
            onAddress(
                service.endpoint.host,
                if (pairing) service.endpoint.port.toString() else "",
                if (pairing) "" else service.endpoint.port.toString(),
            )
        }) {
            Text(
                stringResource(
                    R.string.watch_discovery_device,
                    service.name,
                    service.endpoint.host,
                    service.endpoint.port,
                ),
            )
        }
    }
}
