package icu.nd4y.dosette.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * API 31-32 only: the user re-granted the revocable "Alarms & reminders"
 * special access. The chain has been running on inexact fallback alarms
 * (see [AlarmScheduler]) — re-arm it exactly.
 */
@AndroidEntryPoint
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var engine: ReminderEngine

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val result = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                runCatching { engine.reschedule() }
                    .onFailure { Log.e("ExactAlarmPermission", "re-arm failed", it) }
            } finally {
                result.finish()
            }
        }
    }
}
