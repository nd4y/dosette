package icu.nd4y.dosette.ui.cabinet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.Cabinet

/** The selection applies to this profile's balances; every cabinet shares the same regimen. */
@Composable
fun CabinetPicker(
    modifier: Modifier = Modifier,
    profileId: String? = null,
    viewModel: CabinetPickerViewModel = hiltViewModel(),
) {
    LaunchedEffect(profileId) { viewModel.bind(profileId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Cabinet?>(null) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    if (state.selected == null) return
    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        stringResource(
                            R.string.cabinets_source,
                            state.selected?.name ?: stringResource(R.string.cabinets_main),
                        ),
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.active.forEach { cabinet ->
                        DropdownMenuItem(
                            text = { Text(cabinet.name ?: stringResource(R.string.cabinets_main)) },
                            onClick = {
                                expanded = false
                                viewModel.select(cabinet.id)
                            },
                        )
                    }
                }
            }
            TextButton(onClick = { manage = true }) { Text(stringResource(R.string.cabinets_manage)) }
        }
    }
    if (manage) {
        AlertDialog(
            onDismissRequest = { manage = false },
            title = { Text(stringResource(R.string.cabinets_title)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.cabinets_explanation))
                    state.cabinets.forEach { cabinet ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                cabinet.name ?: stringResource(R.string.cabinets_main),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row {
                                TextButton(onClick = {
                                    edit = cabinet
                                    name = cabinet.name.orEmpty()
                                }) { Text(stringResource(R.string.cabinets_rename)) }
                                if (cabinet.archivedAt == null) {
                                    TextButton(
                                        onClick = { viewModel.archive(cabinet.id) },
                                    ) { Text(stringResource(R.string.cabinets_archive)) }
                                } else {
                                    TextButton(
                                        onClick = { viewModel.restore(cabinet.id) },
                                    ) { Text(stringResource(R.string.cabinets_restore)) }
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        adding = true
                        name = ""
                    }) { Text(stringResource(R.string.cabinets_add)) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { manage = false },
                ) { Text(stringResource(R.string.cabinets_done)) }
            },
        )
    }
    if (adding || edit != null) {
        CabinetNameDialog(adding, name, onName = { name = it }, onDismiss = {
            adding = false
            edit = null
        }, onSave = {
            viewModel.saveName(edit?.id, name)
            adding = false
            edit = null
        })
    }
    if (error) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(stringResource(R.string.cabinets_error)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissError,
                ) { Text(stringResource(R.string.cabinets_done)) }
            },
        )
    }
}

@Composable
private fun CabinetNameDialog(
    adding: Boolean,
    name: String,
    onName: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (adding) R.string.cabinets_add else R.string.cabinets_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { onName(it.take(60)) },
                label = { Text(stringResource(R.string.cabinets_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = onSave) { Text(stringResource(R.string.cabinets_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cabinets_cancel)) } },
    )
}
