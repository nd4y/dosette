package icu.nd4y.dosette.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import icu.nd4y.dosette.R

@Composable
internal fun PlaceLocationDisclosure(
    background: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_disclosure_title)) },
        text = {
            Text(
                text =
                    stringResource(
                        if (background) R.string.place_background_disclosure else R.string.place_disclosure,
                    ),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.place_disclosure_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
