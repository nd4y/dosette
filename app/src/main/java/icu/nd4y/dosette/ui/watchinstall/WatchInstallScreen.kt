package icu.nd4y.dosette.ui.watchinstall

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.designsystem.ScreenHeader
import icu.nd4y.dosette.watch.install.InstallFailure

@Composable
fun WatchInstallScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: WatchInstallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.busy) { viewModel.cancel() }
    val view = LocalView.current
    DisposableEffect(state.busy, view) {
        val previous = view.keepScreenOn
        if (state.busy) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    WatchInstallContent(
        state,
        contentPadding,
        onBack,
        viewModel::step,
        viewModel::address,
        viewModel::pair,
        viewModel::inspect,
        viewModel::install,
        viewModel::cancel,
    )
}

@Composable
fun WatchInstallContent(
    state: WatchWizardState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onStep: (WatchInstallStep) -> Unit,
    onAddress: (String, String, String) -> Unit,
    onPair: (String) -> Unit,
    onInspect: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(stringResource(R.string.watch_install_title), if (state.busy) onCancel else onBack)
        Text(
            stringResource(R.string.watch_install_step, state.step.ordinal + 1, WatchInstallStep.entries.size),
            style = MaterialTheme.typography.labelLarge,
        )
        state.error?.let { Text(stringResource(it.message()), color = MaterialTheme.colorScheme.error) }
        if (state.busy) {
            Text(
                stringResource(
                    if (state.step == WatchInstallStep.REVIEW) {
                        R.string.watch_install_working
                    } else {
                        R.string.watch_install_checking
                    },
                ),
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (state.progress > 0) Text(stringResource(R.string.watch_install_progress, state.progress))
            Text(stringResource(R.string.watch_install_keep_open))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        } else {
            when (state.step) {
                WatchInstallStep.PREPARE -> {
                    PrepareWatch { onStep(WatchInstallStep.PAIR) }
                }

                WatchInstallStep.PAIR -> {
                    PairWatch(state, onAddress, onPair) { onStep(WatchInstallStep.CONNECT) }
                }

                WatchInstallStep.CONNECT -> {
                    ConnectWatch(state, onAddress, onInspect) { onStep(WatchInstallStep.PAIR) }
                }

                WatchInstallStep.REVIEW -> {
                    ReviewWatch(state, onInstall) { onStep(WatchInstallStep.CONNECT) }
                }

                WatchInstallStep.DONE -> {
                    Text(
                        stringResource(
                            R.string.watch_install_done,
                            state.plan
                                ?.candidate
                                ?.versionName
                                .orEmpty(),
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(stringResource(R.string.watch_install_finish_hint))
                    Button(onClick = onBack) { Text(stringResource(R.string.watch_install_finish)) }
                }
            }
        }
    }
}

@Composable
private fun PrepareWatch(onContinue: () -> Unit) {
    Text(stringResource(R.string.watch_install_prepare), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.watch_install_prepare_steps))
    Text(stringResource(R.string.watch_install_privacy))
    if (Build.VERSION.SDK_INT < 29) Text(stringResource(R.string.watch_install_phone_old))
    Button(onClick = onContinue, enabled = Build.VERSION.SDK_INT >= 29) {
        Text(stringResource(R.string.watch_install_continue))
    }
}

@Composable
private fun PairWatch(
    state: WatchWizardState,
    onAddress: (String, String, String) -> Unit,
    onPair: (String) -> Unit,
    onSkip: () -> Unit,
) {
    // A pairing code must never enter saved instance state, preferences, backups, or logs.
    var code by remember { mutableStateOf("") }
    Text(stringResource(R.string.watch_install_pair), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.watch_install_pair_hint))
    AddressField(state.host) { onAddress(it, state.pairingPort, state.connectionPort) }
    PortField(state.pairingPort, R.string.watch_install_pair_port) { onAddress(state.host, it, state.connectionPort) }
    OutlinedTextField(
        code,
        { code = it.filter(Char::isDigit).take(6) },
        label = { Text(stringResource(R.string.watch_install_code)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = {
        val value = code
        code = ""
        onPair(value)
    }, enabled = code.length == 6) {
        Text(stringResource(R.string.watch_install_pair_action))
    }
    TextButton(onClick = onSkip) { Text(stringResource(R.string.watch_install_already_paired)) }
}

@Composable
private fun ConnectWatch(
    state: WatchWizardState,
    onAddress: (String, String, String) -> Unit,
    onInspect: () -> Unit,
    onPair: () -> Unit,
) {
    Text(stringResource(R.string.watch_install_connect), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.watch_install_connect_hint))
    AddressField(state.host) { onAddress(it, state.pairingPort, state.connectionPort) }
    PortField(
        state.connectionPort,
        R.string.watch_install_connection_port,
    ) { onAddress(state.host, state.pairingPort, it) }
    Button(onClick = onInspect) { Text(stringResource(R.string.watch_install_check)) }
    TextButton(onClick = onPair) { Text(stringResource(R.string.watch_install_pair_again)) }
}

@Composable
private fun ReviewWatch(
    state: WatchWizardState,
    onInstall: () -> Unit,
    onConnect: () -> Unit,
) {
    val plan = state.plan ?: return
    Text(stringResource(R.string.watch_install_review), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.watch_install_candidate, plan.candidate.versionName))
    Text(
        plan.installed?.let { stringResource(R.string.watch_install_installed, it.versionName) }
            ?: stringResource(R.string.watch_install_not_installed),
    )
    Text(stringResource(R.string.watch_install_signature_ok))
    Text(
        plan.candidate.certificate
            .chunked(2)
            .joinToString(":"),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(stringResource(R.string.watch_install_preserve_data))
    if (plan.upToDate) {
        Text(stringResource(R.string.watch_install_current))
        Button(onClick = onInstall) { Text(stringResource(R.string.watch_install_finish)) }
    } else {
        Button(onClick = onInstall) {
            Text(
                stringResource(
                    if (plan.installed ==
                        null
                    ) {
                        R.string.watch_install_install
                    } else {
                        R.string.watch_install_update
                    },
                ),
            )
        }
    }
    TextButton(onClick = onConnect) { Text(stringResource(R.string.watch_install_reconnect)) }
}

@Composable
private fun AddressField(
    value: String,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        onValue,
        label = { Text(stringResource(R.string.watch_install_address)) },
        placeholder = { Text("192.168.1.20") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PortField(
    value: String,
    label: Int,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        { onValue(it.filter(Char::isDigit).take(5)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun InstallFailure.message(): Int =
    when (this) {
        InstallFailure.ADDRESS -> R.string.watch_install_error_address
        InstallFailure.PAIRING -> R.string.watch_install_error_pairing
        InstallFailure.CONNECTION -> R.string.watch_install_error_connection
        InstallFailure.NOT_WATCH -> R.string.watch_install_error_not_watch
        InstallFailure.SIGNATURE -> R.string.watch_install_error_signature
        InstallFailure.PACKAGE -> R.string.watch_install_error_package
        InstallFailure.DOWNGRADE -> R.string.watch_install_error_downgrade
        InstallFailure.STORAGE -> R.string.watch_install_error_storage
        InstallFailure.INSTALL -> R.string.watch_install_error_install
        InstallFailure.VERIFY -> R.string.watch_install_error_verify
        InstallFailure.INTERRUPTED -> R.string.watch_install_error_interrupted
    }
