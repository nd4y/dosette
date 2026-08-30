package icu.nd4y.dosette.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import icu.nd4y.dosette.reminders.WidgetRefresher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes fresh data into every placed widget. Called by the reminder
 * engine after each pass and by the PRN action, so the widget follows the
 * exact same triggers as notifications (plus the midnight housekeeping).
 */
@Singleton
class WidgetUpdater
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WidgetRefresher {
        suspend fun updateAll() {
            DoseWidget().updateAll(context)
        }

        override suspend fun refresh() = updateAll()
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface WidgetModule {
    @Binds
    fun widgetRefresher(impl: WidgetUpdater): WidgetRefresher
}
