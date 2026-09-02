package icu.nd4y.dosette.reminders.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import icu.nd4y.dosette.MainActivity
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.reminders.NotificationActionReceiver
import icu.nd4y.dosette.ui.common.withAppLocale
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderPayload(
    val key: OccurrenceKey,
    /** "Metformin 500 mg" */
    val title: String,
    /** Units of the dose as display text, e.g. "1" or "2.5"; null = unknown. */
    val amountText: String?,
    /** Free-form intake note, e.g. "with food". */
    val instructions: String?,
    /** Shown only when more than one profile exists. */
    val profileName: String?,
)

/** Side-effect boundary of the reminder engine; faked in tests. */
interface ReminderNotifier {
    fun postReminder(
        payload: ReminderPayload,
        alert: Boolean,
    )

    fun cancelReminder(key: OccurrenceKey)

    fun postMissedNotice(
        key: OccurrenceKey,
        medicationTitle: String,
    )

    fun postLowStock(
        medicationId: String,
        medicationTitle: String,
        unitsLeft: String,
    )

    fun postAppointment(
        appointment: Appointment,
        offsetMin: Int,
    )

    fun cancelAll()

    /** Drops the notices of a visit that was deleted or moved. */
    fun cancelAppointment(
        appointmentId: String,
        offsetsMin: List<Int>,
    )

    /** Generic "time for a medication" for an alarm that rang before the first unlock. */
    fun postLockedNotice()

    fun cancelLockedNotice()
}

@Singleton
class AndroidReminderNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ReminderNotifier {
        private val manager = NotificationManagerCompat.from(context)

        // Texts follow the in-app language, which below API 33 the application
        // context knows nothing about.
        private val localized: Context
            get() = context.withAppLocale()

        // POST_NOTIFICATIONS is requested in onboarding; if the user declined,
        // notifyIfAllowed silently skips and the app shows an in-app banner instead.
        @SuppressLint("MissingPermission")
        private fun notifyIfAllowed(
            id: Int,
            notification: android.app.Notification,
        ) {
            if (manager.areNotificationsEnabled()) {
                manager.notify(id, notification)
            }
        }

        override fun postReminder(
            payload: ReminderPayload,
            alert: Boolean,
        ) {
            val id = NotificationIds.reminder(payload.key)
            val text =
                buildList {
                    add(localized.getString(R.string.notification_dose_text, payload.key.time.format(TIME_FORMAT)))
                    payload.amountText?.let { add(localized.getString(R.string.unit_pieces, it)) }
                    payload.instructions?.let(::add)
                    payload.profileName?.let { add(localized.getString(R.string.notification_profile, it)) }
                }.joinToString(" · ")
            // Always the alerting channel: the silent swipe-repost is muted with
            // setSilent instead of a LOW channel, so it reappears in the SAME
            // spot of the shade — to the user the notification never leaves.
            val notification =
                NotificationCompat
                    .Builder(context, Channels.DOSE_ALERTS)
                    .setSmallIcon(R.drawable.ic_stat_pill)
                    .setContentTitle(payload.title)
                    .setContentText(text)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setColor(context.getColor(R.color.notification_accent))
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(false)
                    .setSilent(!alert)
                    .setContentIntent(contentIntent(id))
                    .setDeleteIntent(actionIntent(payload.key, NotificationActionReceiver.ACTION_DISMISSED, id, 0))
                    .addAction(
                        0,
                        localized.getString(R.string.action_skip),
                        actionIntent(payload.key, NotificationActionReceiver.ACTION_SKIP, id, 2),
                    ).addAction(
                        0,
                        localized.getString(R.string.action_snooze),
                        actionIntent(payload.key, NotificationActionReceiver.ACTION_SNOOZE, id, 3),
                    ).addAction(
                        0,
                        localized.getString(R.string.action_take),
                        actionIntent(payload.key, NotificationActionReceiver.ACTION_TAKE, id, 1),
                    ).build()
            notifyIfAllowed(id, notification)
        }

        override fun cancelReminder(key: OccurrenceKey) {
            manager.cancel(NotificationIds.reminder(key))
        }

        override fun postMissedNotice(
            key: OccurrenceKey,
            medicationTitle: String,
        ) {
            val notification =
                NotificationCompat
                    .Builder(context, Channels.MISC)
                    .setSmallIcon(R.drawable.ic_stat_pill)
                    .setContentTitle(localized.getString(R.string.notification_missed_title, medicationTitle))
                    .setContentText(localized.getString(R.string.notification_missed_text))
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent(NotificationIds.missedNotice(key)))
                    .build()
            notifyIfAllowed(NotificationIds.missedNotice(key), notification)
        }

        override fun postLowStock(
            medicationId: String,
            medicationTitle: String,
            unitsLeft: String,
        ) {
            val notification =
                NotificationCompat
                    .Builder(context, Channels.INVENTORY)
                    .setSmallIcon(R.drawable.ic_stat_pill)
                    .setContentTitle(localized.getString(R.string.notification_low_stock_title, medicationTitle))
                    .setContentText(localized.getString(R.string.notification_low_stock_text, unitsLeft))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent(NotificationIds.lowStock(medicationId)))
                    .build()
            notifyIfAllowed(NotificationIds.lowStock(medicationId), notification)
        }

        override fun postAppointment(
            appointment: Appointment,
            offsetMin: Int,
        ) {
            val id = NotificationIds.appointment(appointment.id, offsetMin)
            val time = appointment.time.format(TIME_FORMAT)
            val text =
                listOfNotNull(
                    localized.getString(R.string.notification_appointment_time, time),
                    appointment.location,
                ).joinToString(" · ")
            val notification =
                NotificationCompat
                    .Builder(context, Channels.APPOINTMENTS)
                    .setSmallIcon(R.drawable.ic_stat_pill)
                    .setContentTitle(appointment.title)
                    .setContentText(text)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent(id))
                    .build()
            notifyIfAllowed(id, notification)
        }

        override fun cancelAll() {
            manager.cancelAll()
        }

        override fun cancelAppointment(
            appointmentId: String,
            offsetsMin: List<Int>,
        ) {
            offsetsMin.forEach { manager.cancel(NotificationIds.appointment(appointmentId, it)) }
        }

        override fun postLockedNotice() {
            // No dose details on purpose: the database cannot be read while
            // the user is locked, so this is all the app knows.
            val notification =
                NotificationCompat
                    .Builder(context, Channels.DOSE_ALERTS)
                    .setSmallIcon(R.drawable.ic_stat_pill)
                    .setContentTitle(localized.getString(R.string.notification_locked_title))
                    .setContentText(localized.getString(R.string.notification_locked_text))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setColor(context.getColor(R.color.notification_accent))
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent(NotificationIds.LOCKED_NOTICE))
                    .build()
            notifyIfAllowed(NotificationIds.LOCKED_NOTICE, notification)
        }

        override fun cancelLockedNotice() {
            manager.cancel(NotificationIds.LOCKED_NOTICE)
        }

        private fun contentIntent(requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun actionIntent(
            key: OccurrenceKey,
            action: String,
            notificationId: Int,
            actionOrdinal: Int,
        ): PendingIntent {
            val intent =
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_KEY, key.encode())
            return PendingIntent.getBroadcast(
                context,
                notificationId * 10 + actionOrdinal,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private companion object {
            val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        }
    }
