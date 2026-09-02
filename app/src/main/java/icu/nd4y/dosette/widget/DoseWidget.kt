package icu.nd4y.dosette.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.material3.ColorProviders
import icu.nd4y.dosette.ui.common.withAppLanguage
import icu.nd4y.dosette.ui.theme.DarkColors
import icu.nd4y.dosette.ui.theme.LightColors

class DoseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DoseWidget()
}

/**
 * Home-screen widget: the day ring plus upcoming doses, markable in place.
 * One widget, three layouts — the launcher size bucket picks between the
 * 2x2 next-dose card, the 4x2 next-slot row and the 4x4 day list.
 */
class DoseWidget : GlanceAppWidget() {
    // Taller buckets let a 4x4 (or 4x5) widget list more of the day: the
    // launcher renders the largest bucket that fits, and LargeContent
    // budgets its rows by that bucket's height.
    override val sizeMode: SizeMode =
        SizeMode.Responsive(setOf(COMPACT, MEDIUM, LARGE, TALL, EXTRA_TALL))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val loader = widgetEntryPoint(context).stateLoader()
        // The session outlives a single update, so collect live data:
        // a take from the widget itself must repaint it immediately.
        val initial = loader.load()
        // One flow per session: created inside provideContent it would be
        // re-subscribed on every recomposition.
        val states = loader.observe()
        provideContent {
            val state by states.collectAsState(initial = initial)
            // Below API 33 the per-app language reaches only AppCompat activities.
            val localized = remember(state.language) { context.withAppLanguage(state.language) }
            CompositionLocalProvider(LocalContext provides localized) {
                GlanceTheme(colors = widgetColors()) {
                    WidgetRoot(state)
                }
            }
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 240.dp)
        val TALL = DpSize(250.dp, 330.dp)
        val EXTRA_TALL = DpSize(250.dp, 420.dp)
    }
}

/** Material You on Android 12+, the app's fixed teal scheme below. */
@Composable
private fun widgetColors() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme.colors
    } else {
        ColorProviders(light = LightColors, dark = DarkColors)
    }

@Composable
private fun WidgetRoot(state: WidgetState) {
    val size = LocalSize.current
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .clickableOpenApp(),
    ) {
        when {
            size.width < DoseWidget.MEDIUM.width -> CompactContent(state)
            size.height < DoseWidget.LARGE.height -> MediumContent(state)
            else -> LargeContent(state)
        }
    }
}
