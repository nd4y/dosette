package icu.nd4y.dosette.ui.common

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** The system page where notifications for this app are switched back on. */
fun openNotificationSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
