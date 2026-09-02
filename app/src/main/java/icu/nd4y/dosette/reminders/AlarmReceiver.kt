package icu.nd4y.dosette.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.os.UserManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.di.IoDispatcher
import icu.nd4y.dosette.reminders.notifications.Channels
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * The single chained alarm fired. Direct-boot aware, because the trigger
 * [LockedBootReceiver] re-arms may ring before the first unlock.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject
    lateinit var engine: ReminderEngine

    @Inject
    lateinit var notifier: ReminderNotifier

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!UserManagerCompat.isUserUnlocked(context)) {
            // The database is still credential-encrypted: the engine cannot
            // even tell which dose this is. A generic notice has to do; the
            // unlock delivers BOOT_COMPLETED and the reconcile replaces it.
            Channels.ensureCreated(context)
            notifier.postLockedNotice()
            // The engine cannot plan, but the remembered dose times can: one
            // generic notice per dose until the unlock rebuilds the chain.
            alarmScheduler.nextUpcomingAfter(Instant.now().plus(REARM_LEAD))?.let { next ->
                alarmScheduler.scheduleExact(next, alarmScheduler.directBootAlarm()?.alarmClock ?: true)
            }
            return
        }
        val result = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                // An uncaught throw here would kill the process AFTER the
                // alarm was consumed but BEFORE the next one is armed — the
                // whole reminder chain would silently stop.
                runCatching { engine.processDueEvents() }
                    .onFailure { Log.e("AlarmReceiver", "engine pass failed", it) }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val REARM_LEAD: Duration = Duration.ofMinutes(1)
    }
}
