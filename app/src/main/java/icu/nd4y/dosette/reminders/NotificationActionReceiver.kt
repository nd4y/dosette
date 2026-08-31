package icu.nd4y.dosette.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.di.IoDispatcher
import icu.nd4y.dosette.domain.model.OccurrenceKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var engine: ReminderEngine

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val key =
            intent
                .getStringExtra(EXTRA_OCCURRENCE_KEY)
                ?.let { encoded -> runCatching { OccurrenceKey.decode(encoded) }.getOrNull() }
        val action = intent.action
        if (key == null || action == null) return

        val result = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                runCatching {
                    when (action) {
                        ACTION_TAKE -> engine.onUserAction(key, UserDoseAction.TAKE)
                        ACTION_SKIP -> engine.onUserAction(key, UserDoseAction.SKIP)
                        ACTION_SNOOZE -> engine.onUserAction(key, UserDoseAction.SNOOZE)
                        ACTION_DISMISSED -> engine.onDismissed(key)
                    }
                }.onFailure { Log.e("NotificationAction", "engine pass failed", it) }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_TAKE = "icu.nd4y.dosette.action.TAKE"
        const val ACTION_SKIP = "icu.nd4y.dosette.action.SKIP"
        const val ACTION_SNOOZE = "icu.nd4y.dosette.action.SNOOZE"
        const val ACTION_DISMISSED = "icu.nd4y.dosette.action.DISMISSED"
        const val EXTRA_OCCURRENCE_KEY = "occurrence_key"
    }
}
