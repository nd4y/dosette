package icu.nd4y.dosette.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Wall-clock or timezone changed: every planned instant is stale. */
@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {
    @Inject
    lateinit var engine: ReminderEngine

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) {
            return
        }
        val result = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                engine.processDueEvents()
            } finally {
                result.finish()
            }
        }
    }
}
