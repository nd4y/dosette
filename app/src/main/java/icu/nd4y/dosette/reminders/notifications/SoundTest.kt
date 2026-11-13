package icu.nd4y.dosette.reminders.notifications

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import icu.nd4y.dosette.MainActivity
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.common.withAppLocale

/** Explicit user test of the real reminder channel. Never records an intake or changes its settings. */
object SoundTest {
    @SuppressLint("MissingPermission")
    fun show(context: Context): Boolean {
        Channels.ensureCreated(context)
        val manager = NotificationManagerCompat.from(context)
        val channel =
            context
                .getSystemService(
                    NotificationManager::class.java,
                )?.getNotificationChannel(Channels.DOSE_ALERTS)
        val enabled = manager.areNotificationsEnabled() && channel != null
        val audible =
            channel?.let { it.importance >= NotificationManager.IMPORTANCE_DEFAULT && it.sound != null } == true
        if (!enabled || !audible) return false
        val localized = context.withAppLocale()
        val open =
            PendingIntent.getActivity(
                context,
                NotificationIds.SOUND_TEST,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.notify(
            NotificationIds.SOUND_TEST,
            NotificationCompat
                .Builder(context, Channels.DOSE_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_pill)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentTitle(localized.getString(R.string.sound_test_title))
                .setContentText(localized.getString(R.string.sound_test_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setTimeoutAfter(10_000)
                .build(),
        )
        return true
    }
}
