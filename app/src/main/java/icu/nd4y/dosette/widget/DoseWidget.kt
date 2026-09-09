package icu.nd4y.dosette.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
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
 * One widget, three layouts picked by the exact cell size: the 2-cell-wide
 * next-dose card, the wider next-slot row and, from 240dp of height, the
 * day list.
 */
class DoseWidget : GlanceAppWidget() {
    // The exact cell size, not a bucket: the layouts budget their rows by
    // height, and a bucket below the real size hid rows behind "+N more"
    // while the bottom of the widget stayed empty (a 4x3 cell of 322dp was
    // rendered for a 240dp bucket).
    override val sizeMode: SizeMode = SizeMode.Exact

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
        /** Narrower than this (two cells on most launchers): the next-dose card. */
        val MEDIUM_MIN_WIDTH: Dp = 250.dp

        /** Below this height a wide widget shows the next slot; from it, the day list. */
        val LARGE_MIN_HEIGHT: Dp = 240.dp
    }
}

/** The three layouts, chosen by the widget's exact size. */
enum class WidgetLayout { COMPACT, MEDIUM, LARGE }

fun widgetLayoutFor(size: DpSize): WidgetLayout =
    when {
        size.width < DoseWidget.MEDIUM_MIN_WIDTH -> WidgetLayout.COMPACT
        size.height < DoseWidget.LARGE_MIN_HEIGHT -> WidgetLayout.MEDIUM
        else -> WidgetLayout.LARGE
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
        when (widgetLayoutFor(size)) {
            WidgetLayout.COMPACT -> CompactContent(state)
            WidgetLayout.MEDIUM -> MediumContent(state)
            WidgetLayout.LARGE -> LargeContent(state)
        }
    }
}
