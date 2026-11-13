package icu.nd4y.dosette.ui.common

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import icu.nd4y.dosette.R
import icu.nd4y.dosette.reminders.notifications.Channels
import icu.nd4y.dosette.reminders.notifications.SoundTest

/** Shows the dose-alert channel sound and lets the user test it or open the system settings. */
@Composable
fun ReminderSoundSetup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var channelSound by remember { mutableStateOf<Uri?>(null) }
    var channelImportance by remember { mutableStateOf(NotificationManager.IMPORTANCE_NONE) }
    var testFailed by remember { mutableStateOf(false) }

    // The user changes notification settings outside the app; refresh when we are resumed again.
    LifecycleResumeEffect(Unit) {
        Channels.ensureCreated(context)
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channel =
            context
                .getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(Channels.DOSE_ALERTS)
        channelSound = channel?.sound
        channelImportance = channel?.importance ?: NotificationManager.IMPORTANCE_NONE
        onPauseOrDispose {}
    }

    val soundTitle =
        remember(context, channelSound) {
            channelSound?.let { uri ->
                runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
            }
        }
    val statusText =
        when {
            !notificationsEnabled || channelImportance == NotificationManager.IMPORTANCE_NONE -> {
                stringResource(R.string.sound_setup_status_notifications_disabled)
            }

            channelImportance < NotificationManager.IMPORTANCE_DEFAULT || channelSound == null -> {
                stringResource(R.string.sound_setup_status_silent)
            }

            soundTitle != null -> {
                stringResource(R.string.sound_setup_status_sound, soundTitle)
            }

            else -> {
                stringResource(R.string.sound_setup_status_sound_unknown)
            }
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.sound_setup_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.sound_setup_dnd_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { openDoseAlertsChannelSettings(context) }) {
            Text(stringResource(R.string.sound_setup_open_settings))
        }
        TextButton(
            onClick = {
                testFailed = !runCatching { SoundTest.show(context) }.getOrDefault(false)
            },
        ) {
            Text(stringResource(R.string.sound_setup_test))
        }
        if (testFailed) {
            Text(
                text = stringResource(R.string.sound_setup_test_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Opens the dose-alert channel; some OEM builds lack it, so fall back to the app notification page. */
private fun openDoseAlertsChannelSettings(context: Context) {
    val channelIntent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Channels.DOSE_ALERTS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(channelIntent) }
        .onFailure {
            val appIntent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(appIntent) }
        }
}
