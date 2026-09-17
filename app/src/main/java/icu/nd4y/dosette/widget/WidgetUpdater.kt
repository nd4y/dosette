package icu.nd4y.dosette.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes fresh data into every placed widget. Reached through the mirror
 * refresher, which the reminder engine calls after each pass and the PRN
 * action after each intake, so the widget follows the exact same triggers
 * as notifications (plus the midnight housekeeping).
 */
@Singleton
class WidgetUpdater
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        suspend fun updateAll() {
            DoseWidget().updateAll(context)
        }
    }
