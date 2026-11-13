package icu.nd4y.dosette.reminders.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import icu.nd4y.dosette.MainActivity
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.reminders.NotificationActionReceiver
import java.time.format.DateTimeFormatter

/** Reconciles the complete active set, including after process death or an upgrade. */
internal class GroupedReminders(
    private val context: Context,
    private val localized: Context,
    private val manager: NotificationManagerCompat,
    private val single: (ReminderPayload, Boolean) -> Unit,
) {
    @SuppressLint("MissingPermission")
    fun sync(
        payloads: List<ReminderPayload>,
        alertKeys: Set<OccurrenceKey>,
    ) {
        val groups = payloads.distinctBy { it.key }.groupBy { it.key.date to it.key.time }
        val desired = groups.values.mapTo(mutableSetOf()) { id(it) }
        // Also retire the previous version's per-medicine notifications. Other
        // channels (appointments, stock, missed) and the locked notice are separate.
        manager.activeNotifications
            .filter {
                it.notification.channelId == Channels.DOSE_ALERTS &&
                    it.id != NotificationIds.LOCKED_NOTICE && it.id !in desired
            }.forEach { manager.cancel(it.tag, it.id) }
        groups.values.forEach { doses ->
            val alert = doses.any { it.key in alertKeys }
            if (doses.size == 1) {
                single(doses.single(), alert)
            } else if (manager.areNotificationsEnabled()) {
                manager.notify(id(doses), notification(doses, alert))
            }
        }
    }

    private fun id(doses: List<ReminderPayload>): Int =
        if (doses.size == 1) {
            NotificationIds.reminder(doses.single().key)
        } else {
            "dose-slot|${doses.first().key.date}|${doses.first().key.time}".hashCode()
        }

    private fun notification(
        doses: List<ReminderPayload>,
        alert: Boolean,
    ): android.app.Notification {
        val id = id(doses)
        val open =
            PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val dismiss =
            PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(NotificationActionReceiver.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val lines =
            doses.map { dose ->
                listOfNotNull(
                    dose.title,
                    dose.amountText?.let {
                        localized.getString(R.string.unit_pieces, it)
                    },
                    dose.instructions,
                    dose.profileName,
                ).joinToString(" · ")
            }
        val style = NotificationCompat.InboxStyle()
        lines.forEach(style::addLine)
        return NotificationCompat
            .Builder(context, Channels.DOSE_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(
                localized.getString(
                    R.string.notification_slot_title,
                    doses
                        .first()
                        .key.time
                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                    doses.size,
                ),
            ).setContentText(doses.joinToString(", ") { it.title })
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(context.getColor(R.color.notification_accent))
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setSilent(!alert)
            .setContentIntent(open)
            .setDeleteIntent(dismiss)
            // No "take all": each intake remains an explicit choice in the app.
            .addAction(R.drawable.ic_stat_pill, localized.getString(R.string.notification_open_doses), open)
            .build()
    }
}
