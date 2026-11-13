package icu.nd4y.dosette.reminders.notifications

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import icu.nd4y.dosette.R

object Channels {
    /** Every dose reminder; the silent swipe-repost mutes via setSilent instead of a channel. */
    const val DOSE_ALERTS = "ch_dose_alerts"

    const val APPOINTMENTS = "ch_appointments"
    const val INVENTORY = "ch_inventory"
    const val MISC = "ch_misc"

    /** Retired LOW channel the swipe-repost used before setSilent; removed from existing installs. */
    private const val LEGACY_DOSE_SILENT = "ch_dose_silent"

    fun ensureCreated(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.deleteNotificationChannel(LEGACY_DOSE_SILENT)
        manager.createNotificationChannelsCompat(
            listOf(
                manager.getNotificationChannelCompat(DOSE_ALERTS) ?: NotificationChannelCompat
                    .Builder(DOSE_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_dose_alerts))
                    .setSound(
                        Uri.parse("android.resource://${context.packageName}/raw/dose_chime"),
                        AudioAttributes
                            .Builder()
                            .setUsage(
                                AudioAttributes.USAGE_NOTIFICATION,
                            ).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    ).build(),
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
