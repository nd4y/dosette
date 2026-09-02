package icu.nd4y.dosette.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.di.IoDispatcher
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Notifications and alarms do not survive a reboot; persisted reminder
 * states do. Re-post and re-arm. BOOT_COMPLETED arrives only after the
 * first unlock — the locked window before it belongs to [LockedBootReceiver].
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var engine: ReminderEngine

    @Inject
    lateinit var notifier: ReminderNotifier

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HANDLED_ACTIONS) return
        val result = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                runCatching {
                    // Posted by an alarm that rang before the unlock; the
                    // reconcile below re-posts the real reminders in its place.
                    notifier.cancelLockedNotice()
                    engine.reconcile()
                }.onFailure { Log.e("BootReceiver", "reconcile failed", it) }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON",
            )
    }
}
