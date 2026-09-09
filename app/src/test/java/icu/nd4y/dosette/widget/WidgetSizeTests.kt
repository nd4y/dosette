package icu.nd4y.dosette.widget

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.ui.SHOT_OPTIONS
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.PrnMed
import icu.nd4y.dosette.ui.today.TodayDose
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

private const val RU_PIXEL7 = "ru-rRU-" + RobolectricDeviceQualifiers.Pixel7
private const val SHOTS = "src/test/screenshots"

/**
 * The widget rendered through Glance at the exact sizes a launcher hands
 * out, inflated from the RemoteViews the way the home screen does it. The
 * pictures are the deliverable: a clipped row or a "+N more" next to empty
 * space shows up there, not in the layout budgets' unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RU_PIXEL7)
class WidgetSizeTests {
    private val day: LocalDate = LocalDate.parse("2026-09-09")

    private fun dose(
        time: String,
        name: String,
        strength: String,
        form: MedicationForm,
        colorSeed: Int,
        status: DoseUiStatus = DoseUiStatus.PENDING,
        amount: String = "1",
    ) = TodayDose(
        medicationId = name,
        date = day,
        time = LocalTime.parse(time),
        name = name,
        strengthText = strength,
        amountText = amount,
        instructions = null,
        form = form,
        colorSeed = colorSeed,
        status = status,
        actedTime = if (status == DoseUiStatus.TAKEN) LocalTime.parse(time).plusMinutes(5) else null,
    )

    /** Mid-day: breakfast taken, lunch due in 20 minutes, the evening ahead, one as-needed drug. */
    private val midday =
        WidgetState(
            date = day,
            doses =
                listOf(
                    dose("08:00", "Метформин", "500 мг", MedicationForm.TABLET, 0, DoseUiStatus.TAKEN),
                    dose("08:00", "Лизиноприл", "10 мг", MedicationForm.TABLET, 1, DoseUiStatus.TAKEN),
                    dose("13:00", "Витамин D", "2000 МЕ", MedicationForm.DROPS, 3),
                    dose("13:00", "Омега-3", "1000 мг", MedicationForm.CAPSULE, 5, amount = "2"),
                    dose("20:00", "Аторвастатин", "20 мг", MedicationForm.CAPSULE, 2),
                    dose("22:00", "Мелатонин", "3 мг", MedicationForm.TABLET, 4),
                ),
            prn = listOf(PrnMed("ibu", "Ибупрофен", "400 мг", MedicationForm.TABLET, 6)),
            minutesToNext = 20,
        )

    /** Every dose of the day acted on — the ring-only picture. */
    private val allDone =
        midday.copy(
            doses = midday.doses.map { it.copy(status = DoseUiStatus.TAKEN, actedTime = it.time) },
            minutesToNext = null,
        )

    /** Glance widget over a fixed state — the real one loads its state through Hilt. */
    private class FixtureWidget(
        private val state: WidgetState,
    ) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Exact

        override suspend fun provideGlance(
            context: Context,
            id: GlanceId,
        ) = provideContent {
            GlanceTheme(colors = widgetColors()) {
                WidgetRoot(state)
            }
        }
    }

    /** Composes, inflates and lays the widget out at [size], returning the view tree. */
    @OptIn(ExperimentalGlanceApi::class)
    private fun render(
        state: WidgetState,
        size: DpSize,
    ): View {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remoteViews = runBlocking { FixtureWidget(state).compose(context, size = size) }
        val density = context.resources.displayMetrics.density
        val widthPx = (size.width.value * density).roundToInt()
        val heightPx = (size.height.value * density).roundToInt()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host, ViewGroup.LayoutParams(widthPx, heightPx))
        val widget = remoteViews.apply(activity, host)
        host.addView(widget, FrameLayout.LayoutParams(widthPx, heightPx))
        host.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, widthPx, heightPx)
        return host
    }

    private fun shoot(
        name: String,
        state: WidgetState,
        size: DpSize,
    ): List<String> {
        val view = render(state, size)
        view.captureRoboImage("$SHOTS/widget_$name.png", roborazziOptions = SHOT_OPTIONS)
        return texts(view)
    }

    /** Every visible text in the tree, top to bottom. */
    private fun texts(view: View): List<String> =
        when (view) {
            is TextView -> listOf(view.text.toString()).filter { it.isNotBlank() }
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else -> emptyList()
        }

    // Pixel Launcher, 5x6 grid on a 6.3" phone: 65x100 dp cells, 15 / 11 dp gutters.
    @Test
    fun `two cells wide is the next-dose card`() {
        val texts = shoot("2x2", midday, DpSize(146.dp, 211.dp))

        assertThat(texts).containsAtLeast("13:00", "Витамин D", "Принял")
        assertThat(texts).doesNotContain("Омега-3")
    }

    @Test
    fun `a taller two-cell card keeps the same picture`() {
        val texts = shoot("2x3", midday, DpSize(146.dp, 322.dp))

        assertThat(texts).containsAtLeast("13:00", "Витамин D", "Принял")
    }

    @Test
    fun `three cells wide is still the card`() {
        val texts = shoot("3x2", midday, DpSize(226.dp, 211.dp))

        assertThat(texts).containsAtLeast("13:00", "Витамин D")
    }

    @Test
    fun `four by two lists the next slot in full`() {
        val texts = shoot("4x2", midday, DpSize(307.dp, 211.dp))

        assertThat(texts).containsAtLeast("Витамин D", "Омега-3")
        assertThat(texts.none { it.startsWith("+") }).isTrue()
    }

    @Test
    fun `five by two is the same row, wider`() {
        val texts = shoot("5x2", midday, DpSize(388.dp, 211.dp))

        assertThat(texts).containsAtLeast("Витамин D", "Омега-3")
    }

    @Test
    fun `four by three shows the day with the oldest slot folded`() {
        val texts = shoot("4x3", midday, DpSize(307.dp, 322.dp))

        assertThat(texts).containsAtLeast("Сегодня", "Витамин D", "Омега-3", "Аторвастатин")
        // The taken breakfast rows fold into their header: no names, marks only.
        assertThat(texts).doesNotContain("Метформин")
        // The last evening dose does not fit and is counted, not lost.
        assertThat(texts.any { it.startsWith("+") }).isTrue()
    }

    @Test
    fun `four by four lists the whole day and the as-needed row`() {
        val texts = shoot("4x4", midday, DpSize(307.dp, 434.dp))

        assertThat(
            texts,
        ).containsAtLeast("Метформин", "Лизиноприл", "Витамин D", "Омега-3", "Аторвастатин", "Мелатонин")
        assertThat(texts.any { it.startsWith("Ибупрофен") }).isTrue()
        assertThat(texts.none { it.startsWith("+") }).isTrue()
    }

    @Test
    fun `five by four is the same list, wider`() {
        val texts = shoot("5x4", midday, DpSize(388.dp, 434.dp))

        assertThat(texts).containsAtLeast("Метформин", "Мелатонин")
    }

    // The provider minimums: what a dense launcher hands a fresh 2x2 / 4x2.
    @Test
    fun `the nominal minimum sizes still fit their content`() {
        assertThat(shoot("2x2_min", midday, DpSize(110.dp, 110.dp))).containsAtLeast("13:00", "Принял")
        assertThat(shoot("4x2_min", midday, DpSize(250.dp, 110.dp))).contains("Витамин D")
    }

    // Landscape cells are wide and short.
    @Test
    fun `landscape cells pick the row or the list by height alone`() {
        assertThat(shoot("land_4x2", midday, DpSize(400.dp, 140.dp))).contains("Витамин D")
        assertThat(shoot("land_4x3", midday, DpSize(400.dp, 200.dp))).contains("Омега-3")
        assertThat(shoot("land_4x4", midday, DpSize(400.dp, 270.dp))).contains("Сегодня")
    }

    @Test
    fun `a finished day shows the ring and a line in every size`() {
        listOf("2x2" to DpSize(146.dp, 211.dp), "4x2" to DpSize(307.dp, 211.dp), "4x4" to DpSize(307.dp, 434.dp))
            .forEach { (name, size) ->
                val texts = shoot("done_$name", allDone, size)
                assertThat(texts).contains("6/6")
            }
    }
}
