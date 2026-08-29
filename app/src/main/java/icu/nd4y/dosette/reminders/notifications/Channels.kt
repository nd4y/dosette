package icu.nd4y.dosette.reminders.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import icu.nd4y.dosette.R

object Channels {
    /** Initial alert and every audible nag re-alert. */
    const val DOSE_ALERTS = "ch_dose_alerts"

    /** Silent re-post after the user swipes the ongoing reminder away. */
    const val DOSE_SILENT = "ch_dose_silent"

    const val APPOINTMENTS = "ch_appointments"
    const val INVENTORY = "ch_inventory"
    const val MISC = "ch_misc"

    fun ensureCreated(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannelsCompat(
            listOf(
                channel(context, DOSE_ALERTS, R.string.channel_dose_alerts, NotificationManagerCompat.IMPORTANCE_HIGH),
                channel(context, DOSE_SILENT, R.string.channel_dose_silent, NotificationManagerCompat.IMPORTANCE_LOW),
                channel(
                    context,
                    APPOINTMENTS,
                    R.string.channel_appointments,
                    NotificationManagerCompat.IMPORTANCE_HIGH,
                ),
                channel(context, INVENTORY, R.string.channel_inventory, NotificationManagerCompat.IMPORTANCE_DEFAULT),
                channel(context, MISC, R.string.channel_misc, NotificationManagerCompat.IMPORTANCE_LOW),
            ),
        )
    }

    private fun channel(
        context: Context,
        id: String,
        nameRes: Int,
        importance: Int,
    ): NotificationChannelCompat =
        NotificationChannelCompat
            .Builder(id, importance)
            .setName(context.getString(nameRes))
            .build()
}
