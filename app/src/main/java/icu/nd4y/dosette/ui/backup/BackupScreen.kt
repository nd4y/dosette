package icu.nd4y.dosette.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.data.backup.BackupPreview
import icu.nd4y.dosette.ui.designsystem.strokeGlyph
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun BackupScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var exportDialogOpen by remember { mutableStateOf(false) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/x-yaml"),
        ) { uri -> uri?.let(viewModel::export) }
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(viewModel::requestImport) }

    BackupContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onExport = { exportDialogOpen = true },
        onImport = { importLauncher.launch(arrayOf("*/*")) },
        onConfirmImport = viewModel::confirmImport,
        onDismissImport = viewModel::dismissImport,
        onSubmitPassword = viewModel::submitImportPassword,
        modifier = modifier,
    )

    if (exportDialogOpen) {
        ExportPasswordDialog(
            onConfirm = { password ->
                exportDialogOpen = false
                viewModel.setExportPassword(password)
                val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val extension = if (viewModel.exportEncrypted) "yaml.enc" else "yaml"
                exportLauncher.launch("dosette-backup-$date.$extension")
            },
            onDismiss = { exportDialogOpen = false },
        )
    }
}

@Composable
private fun ExportPasswordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.backup_password_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.backup_export_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ImportPasswordDialog(
    error: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.backup_enter_password),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true,
                    isError = error,
                    supportingText =
                        if (error) {
                            { Text(stringResource(R.string.backup_password_wrong)) }
                        } else {
                            null
                        },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }) {
                Text(stringResource(R.string.backup_import_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun BackupContent(
    state: BackupUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onDismissImport: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(BackIcon, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.backup_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        BackupCard(
            icon = ExportIcon,
            title = stringResource(R.string.backup_export_title),
            text = stringResource(R.string.backup_export_text),
            actionLabel = stringResource(R.string.backup_export_action),
            enabled = !state.busy,
            onClick = onExport,
        )
        BackupCard(
            icon = ImportIcon,
            title = stringResource(R.string.backup_import_title),
            text = stringResource(R.string.backup_import_text),
            actionLabel = stringResource(R.string.backup_import_action),
            enabled = !state.busy,
            onClick = onImport,
        )

        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.result?.let { result ->
            val (text, isError) =
                when (result) {
                    BackupResult.EXPORTED -> {
                        stringResource(R.string.backup_exported) to false
                    }

                    BackupResult.IMPORTED -> {
                        stringResource(R.string.backup_imported) to false
                    }

                    BackupResult.ERROR -> {
                        stringResource(
                            R.string.backup_error,
                            state.errorDetail.orEmpty(),
                        ) to true
                    }
                }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }

    state.pendingImport?.let { preview ->
        ImportConfirmDialog(
            preview = preview,
            onConfirm = onConfirmImport,
            onDismiss = onDismissImport,
        )
    }

    if (state.passwordNeeded) {
        ImportPasswordDialog(
            error = state.passwordError,
            onSubmit = onSubmitPassword,
            onDismiss = onDismissImport,
        )
    }
}

@Composable
private fun ImportConfirmDialog(
    preview: BackupPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.backup_confirm_text,
                    preview.profiles,
                    preview.medications,
                    preview.doseLogs,
                    preview.appointments,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.backup_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun BackupCard(
    icon: ImageVector,
    title: String,
    text: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClick, enabled = enabled) {
                Text(actionLabel)
            }
        }
    }
}

private val BackIcon: ImageVector by lazy {
    strokeGlyph("Back", strokeWidth = 2.2f) {
        moveTo(19f, 12f)
        lineTo(5f, 12f)
        moveTo(11f, 6f)
        lineToRelative(-6f, 6f)
        lineToRelative(6f, 6f)
    }
}

private val ExportIcon: ImageVector by lazy {
    strokeGlyph("Export", strokeWidth = 2f) {
        moveTo(12f, 4f)
        lineTo(12f, 14f)
        moveTo(8f, 8f)
        lineToRelative(4f, -4f)
        lineToRelative(4f, 4f)
        moveTo(5f, 17f)
        lineTo(5f, 19f)
        lineTo(19f, 19f)
        lineTo(19f, 17f)
    }
}

private val ImportIcon: ImageVector by lazy {
    strokeGlyph("Import", strokeWidth = 2f) {
        moveTo(12f, 4f)
        lineTo(12f, 14f)
        moveTo(8f, 10f)
        lineToRelative(4f, 4f)
        lineToRelative(4f, -4f)
        moveTo(5f, 17f)
        lineTo(5f, 19f)
        lineTo(19f, 19f)
        lineTo(19f, 17f)
    }
}
