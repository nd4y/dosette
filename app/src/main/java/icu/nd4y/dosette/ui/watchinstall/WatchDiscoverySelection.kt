package icu.nd4y.dosette.ui.watchinstall

import icu.nd4y.dosette.watch.install.AdbServiceKind
import icu.nd4y.dosette.watch.install.WatchDiscoveryState

/** Never guess among multiple devices or replace a user-selected host. */
internal fun WatchWizardState.withDiscovery(found: WatchDiscoveryState): WatchWizardState {
    val pairing = step == WatchInstallStep.PAIR
    val kind = if (pairing) AdbServiceKind.PAIRING else AdbServiceKind.CONNECTION
    val candidates = matchingServices(found, kind)
    val candidate = candidates.singleOrNull()
    val endpoint = candidate?.endpoint
    val lost = selectedMissing(found, kind)
    return when {
        !canSelectDiscovery() -> {
            copy(discovery = found)
        }

        endpoint == null && lost -> {
            copy(
                discovery = found,
                selectedService = null,
                pairingPort = if (pairing) "" else pairingPort,
                connectionPort = if (pairing) connectionPort else "",
            )
        }

        endpoint == null || (host.isNotBlank() && host != endpoint.host) -> {
            copy(discovery = found)
        }

        else -> {
            copy(
                discovery = found,
                selectedService = candidate,
                host = endpoint.host,
                pairingPort = if (pairing) endpoint.port.toString() else pairingPort,
                connectionPort = if (pairing) connectionPort else endpoint.port.toString(),
            )
        }
    }
}

private fun WatchWizardState.selectedMissing(
    found: WatchDiscoveryState,
    kind: AdbServiceKind,
): Boolean {
    val selected = selectedService
    return selected != null && selected.kind == kind &&
        found.services.none { it.name == selected.name && it.kind == kind }
}

private fun WatchWizardState.matchingServices(
    found: WatchDiscoveryState,
    kind: AdbServiceKind,
) = found.services.filter { it.kind == kind }.let { matching ->
    if (kind == AdbServiceKind.PAIRING || host.isBlank()) {
        matching
    } else {
        matching.filter { it.endpoint.host == host }
    }
}

private fun WatchWizardState.canSelectDiscovery(): Boolean =
    !busy && (step == WatchInstallStep.PAIR || step == WatchInstallStep.CONNECT)
