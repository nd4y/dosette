package icu.nd4y.dosette.reminders

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
 * Notifications and alarms do not survive a reboot; persisted reminder
 * states do. Re-post and re-arm.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var engine: ReminderEngine

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
                runCatching { engine.reconcile() }
                    .onFailure { Log.e("BootReceiver", "reconcile failed", it) }
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
