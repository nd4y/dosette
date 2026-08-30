package icu.nd4y.dosette.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import icu.nd4y.dosette.data.repository.PrnRecorder
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.UserDoseAction

/** Hilt access for Glance code, which cannot be constructor-injected. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun stateLoader(): WidgetStateLoader

    fun engine(): ReminderEngine

    fun prnRecorder(): PrnRecorder

    fun updater(): WidgetUpdater
}

internal fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

internal val doseKeyParam = ActionParameters.Key<String>("occurrenceKey")
internal val prnMedicationParam = ActionParameters.Key<String>("prnMedicationId")

/** «Принял» on a widget row — same engine path as the notification button. */
internal class TakeDoseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val encoded = parameters[doseKeyParam] ?: return
        // The engine refreshes the widget at the end of its pass.
        widgetEntryPoint(context).engine().onUserAction(OccurrenceKey.decode(encoded), UserDoseAction.TAKE)
    }
}

/** «+» on the as-needed row — records the intake without opening the app. */
internal class TakePrnAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val medicationId = parameters[prnMedicationParam] ?: return
        val entryPoint = widgetEntryPoint(context)
        entryPoint.prnRecorder().record(medicationId)
        entryPoint.updater().updateAll()
    }
}
