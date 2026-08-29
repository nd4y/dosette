package icu.nd4y.dosette.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import icu.nd4y.dosette.MainActivity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exactly one system alarm exists at any moment (fixed request code +
 * FLAG_UPDATE_CURRENT). setAlarmClock is used deliberately: it is fully
 * Doze-exempt and not subject to the once-per-9-minutes while-idle
 * throttle that would silently stretch short nag intervals. The cost is
 * an alarm icon in the status bar while a reminder is pending.
 */
@Singleton
class AlarmScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun scheduleExact(at: Instant) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val triggerAtMillis = maxOf(at.toEpochMilli(), System.currentTimeMillis() + MIN_LEAD_MILLIS)
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent()),
                alarmIntent(),
            )
        }

        fun cancel() {
            context.getSystemService(AlarmManager::class.java).cancel(alarmIntent())
        }

        private fun alarmIntent(): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun showIntent(): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_CODE_SHOW,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private companion object {
            const val REQUEST_CODE_ALARM = 1001
            const val REQUEST_CODE_SHOW = 1002
            const val MIN_LEAD_MILLIS = 1000L
        }
    }
