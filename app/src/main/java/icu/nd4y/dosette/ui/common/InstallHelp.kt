package icu.nd4y.dosette.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import icu.nd4y.dosette.R

private const val INSTALL_GUIDE = "https://github.com/nd4y/dosette/blob/main/docs/install.md"
private const val RELEASES = "https://github.com/nd4y/dosette/releases/latest"

@Composable
fun InstallHelp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.install_help_share)
    val shareText = stringResource(R.string.install_help_share_text, RELEASES, INSTALL_GUIDE)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.install_help_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.install_help_text), style = MaterialTheme.typography.bodySmall)
        TextButton(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES))) } },
        ) {
            Text(stringResource(R.string.install_help_latest))
        }
        TextButton(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_GUIDE))) } },
        ) {
            Text(stringResource(R.string.install_help_guide))
        }
        TextButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
            runCatching { context.startActivity(Intent.createChooser(send, shareTitle)) }
        }) { Text(shareTitle) }
    }
}
