package icu.nd4y.dosette.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant

/**
 * Runs between the reboot and the first unlock, before [BootReceiver] can.
 * The database is credential-encrypted there, so the engine cannot plan
 * anything; the last trigger [AlarmScheduler] mirrored into device-protected
 * storage is re-armed as is. Should it ring while still locked,
 * [AlarmReceiver] shows a generic notice; the unlock delivers BOOT_COMPLETED
 * and the reconcile rebuilds the real chain.
 */
class LockedBootReceiver : BroadcastReceiver() {
    /** Hilt access the way the widget callbacks do it; only the scheduler is safe to touch here. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Dependencies {
        fun alarmScheduler(): AlarmScheduler
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val scheduler = EntryPointAccessors.fromApplication(context, Dependencies::class.java).alarmScheduler()
        // Nothing mirrored yet: a fresh install that never armed an alarm.
        val stored = scheduler.directBootAlarm() ?: return
        // A trigger that passed while the phone was off is pushed a minute
        // out so it rings once the boot has settled, not in the middle of it.
        val at = maxOf(stored.at, Instant.now().plus(REARM_LEAD))
        scheduler.scheduleExact(at, stored.alarmClock)
    }

    private companion object {
        val REARM_LEAD: Duration = Duration.ofMinutes(1)
    }
}
