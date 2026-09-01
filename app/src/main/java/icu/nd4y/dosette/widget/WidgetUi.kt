package icu.nd4y.dosette.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import icu.nd4y.dosette.MainActivity
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.ui.common.TimeFormat
import icu.nd4y.dosette.ui.designsystem.MedPalette
import icu.nd4y.dosette.ui.today.DaySlot
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.PrnMed
import icu.nd4y.dosette.ui.today.TodayDose
import icu.nd4y.dosette.ui.today.slotSections
import java.time.format.DateTimeFormatter

private const val MAX_LARGE_ROWS = 5
private const val MAX_MEDIUM_ROWS = 2
private const val MINUTES_PER_HOUR = 60

internal fun GlanceModifier.clickableOpenApp(): GlanceModifier = clickable(actionStartActivity<MainActivity>())

@Composable
internal fun CompactContent(state: WidgetState) {
    val next = state.nextSlotDoses.firstOrNull()
    if (next == null) {
        AllDoneContent(state, ringSize = 56.dp, ringFont = 15.sp)
        return
    }
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayRing(state, sizeDp = 44.dp, fontSize = 12.sp)
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(
                    text = next.time.format(TimeFormat),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Text(
                    text = nextDoseLabel(state, next),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = next.name,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
        Text(
            text = doseSubtitle(next),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(16.dp)
                    .clickable(takeAction(next)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_check),
                contentDescription = null,
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = context.getString(R.string.action_take),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
    }
}

@Composable
internal fun MediumContent(state: WidgetState) {
    val slotDoses = state.nextSlotDoses
    if (slotDoses.isEmpty()) {
        AllDoneContent(state, ringSize = 64.dp, ringFont = 16.sp)
        return
    }
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayRing(state, sizeDp = 64.dp, fontSize = 16.sp)
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val first = slotDoses.first()
                val slotTitle =
                    if (first.date < state.date) {
                        context.getString(R.string.day_yesterday)
                    } else {
                        slotLabel(first.slot)
                    }
                Text(
                    text = "$slotTitle · ${first.time.format(TimeFormat)}",
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = nextDoseLabel(state, first),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
            slotDoses.take(MAX_MEDIUM_ROWS).forEach { dose ->
                Spacer(GlanceModifier.height(5.dp))
                PendingRow(dose, compactButton = true)
            }
            val hidden = slotDoses.size - MAX_MEDIUM_ROWS
            if (hidden > 0) {
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = context.getString(R.string.widget_more_doses, hidden),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                )
            }
        }
    }
}

@Composable
internal fun LargeContent(state: WidgetState) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val raw = state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    // Russian weekday names are lowercase mid-sentence; English stays as is.
    val dateText = if (locale.language == "ru") raw.replaceFirstChar { it.lowercase(locale) } else raw

    Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = context.getString(R.string.tab_today),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Text(
                    text = dateText,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                )
            }
            DayRing(state, sizeDp = 44.dp, fontSize = 12.sp)
        }

        var remaining = MAX_LARGE_ROWS
        if (state.carryover.isNotEmpty()) {
            // A dose snoozed across midnight outranks everything below.
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    text = context.getString(R.string.day_yesterday),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
            state.carryover.forEach { dose ->
                if (remaining <= 0) return@forEach
                remaining--
                Spacer(GlanceModifier.height(4.dp))
                PendingRow(dose, compactButton = false)
            }
        }
        slotSections(state.doses).forEach { doses ->
            if (remaining <= 0) return@forEach
            SlotHeader(doses.first().slot, doses)
            doses.forEach { dose ->
                if (remaining <= 0) return@forEach
                remaining--
                Spacer(GlanceModifier.height(4.dp))
                if (dose.status == DoseUiStatus.PENDING) {
                    PendingRow(dose, compactButton = false)
                } else {
                    ActedRow(dose)
                }
            }
        }

        val hidden = state.carryover.size + state.doses.size - (MAX_LARGE_ROWS - remaining)
        if (hidden > 0) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = context.getString(R.string.widget_more_doses, hidden),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
        } else if (state.prn.isNotEmpty() && remaining > 0) {
            Spacer(GlanceModifier.height(6.dp))
            PrnRow(state.prn.first())
        }
    }
}

@Composable
private fun AllDoneContent(
    state: WidgetState,
    ringSize: Dp,
    ringFont: TextUnit,
) {
    val context = LocalContext.current
    val empty = state.planned == 0
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayRing(state, sizeDp = ringSize, fontSize = ringFont)
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text =
                context.getString(
                    if (empty) R.string.today_empty_title else R.string.widget_all_done,
                ),
            maxLines = 2,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

@Composable
private fun SlotHeader(
    slot: DaySlot,
    doses: List<TodayDose>,
) {
    val context = LocalContext.current
    // Only genuinely taken doses earn the label; a missed slot stays silent.
    val allTaken = doses.all { it.status == DoseUiStatus.TAKEN }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${slotLabel(slot)} · ${doses.minOf { it.time }.format(TimeFormat)}",
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
        Spacer(GlanceModifier.defaultWeight())
        if (allTaken) {
            Text(
                text = context.getString(R.string.slot_all_done),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 10.sp),
            )
        }
    }
}

/** Pending dose: name + dose and the take action, sized for its widget. */
@Composable
private fun PendingRow(
    dose: TodayDose,
    compactButton: Boolean,
) {
    val context = LocalContext.current
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(14.dp)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedChip(dose.colorSeed, dose.form, sizeDp = 28.dp, corner = 10.dp)
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = dose.name,
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
            Text(
                text = doseSubtitle(dose),
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 10.sp,
                    ),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        if (compactButton) {
            Box(
                modifier =
                    GlanceModifier
                        .size(30.dp)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(15.dp)
                        .clickable(takeAction(dose)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_check),
                    contentDescription = context.getString(R.string.action_take),
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                )
            }
        } else {
            Box(
                modifier =
                    GlanceModifier
                        .height(30.dp)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(15.dp)
                        .clickable(takeAction(dose))
                        .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = context.getString(R.string.action_take),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ActedRow(dose: TodayDose) {
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(12.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedChip(dose.colorSeed, dose.form, sizeDp = 24.dp, corner = 8.dp)
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = dose.name,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            modifier = GlanceModifier.defaultWeight(),
        )
        StatusCircle(dose.status)
    }
}

@Composable
private fun StatusCircle(status: DoseUiStatus) {
    val (background, tint, icon) =
        when (status) {
            DoseUiStatus.MISSED -> {
                Triple(
                    GlanceTheme.colors.errorContainer,
                    GlanceTheme.colors.onErrorContainer,
                    R.drawable.ic_widget_cross,
                )
            }

            DoseUiStatus.SKIPPED -> {
                Triple(
                    GlanceTheme.colors.surfaceVariant,
                    GlanceTheme.colors.onSurfaceVariant,
                    R.drawable.ic_widget_skip,
                )
            }

            else -> {
                Triple(
                    GlanceTheme.colors.primary,
                    GlanceTheme.colors.onPrimary,
                    R.drawable.ic_widget_check,
                )
            }
        }
    Box(
        modifier = GlanceModifier.size(20.dp).background(background).cornerRadius(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(11.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
private fun PrnRow(prn: PrnMed) {
    val context = LocalContext.current
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(12.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedChip(prn.colorSeed, prn.form, sizeDp = 24.dp, corner = 8.dp)
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = "${prn.name} · ${context.getString(R.string.schedule_as_needed)}",
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Box(
            modifier =
                GlanceModifier
                    .size(26.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(9.dp)
                    .clickable(
                        actionRunCallback<TakePrnAction>(
                            actionParametersOf(prnMedicationParam to prn.medicationId),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_plus),
                contentDescription = context.getString(R.string.prn_take),
                modifier = GlanceModifier.size(13.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            )
        }
    }
}

@Composable
private fun DayRing(
    state: WidgetState,
    sizeDp: Dp,
    fontSize: TextUnit,
) {
    val context = LocalContext.current
    val px =
        (sizeDp.value * context.resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(MIN_RING_PX)
    Box(modifier = GlanceModifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(RingBitmap.render(context, px, state.taken, state.planned)),
            contentDescription = null,
            modifier = GlanceModifier.size(sizeDp),
        )
        Text(
            text = "${state.taken}/${state.planned}",
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}

@Composable
private fun MedChip(
    colorSeed: Int,
    form: MedicationForm,
    sizeDp: Dp,
    corner: Dp,
) {
    val light = MedPalette.resolve(colorSeed, darkTheme = false)
    val dark = MedPalette.resolve(colorSeed, darkTheme = true)
    Box(
        modifier =
            GlanceModifier
                .size(sizeDp)
                .background(ColorProvider(day = light.container, night = dark.container))
                .cornerRadius(corner),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(formIcon(form)),
            contentDescription = null,
            modifier = GlanceModifier.size((sizeDp.value * CHIP_ICON_FRACTION).dp),
            colorFilter =
                ColorFilter.tint(ColorProvider(day = light.onContainer, night = dark.onContainer)),
        )
    }
}

/** The same stroke glyphs the app draws in MedFormIcons, as resources. */
private fun formIcon(form: MedicationForm): Int =
    when (form) {
        MedicationForm.TABLET -> R.drawable.ic_widget_form_tablet
        MedicationForm.CAPSULE -> R.drawable.ic_widget_form_capsule
        MedicationForm.INJECTION -> R.drawable.ic_widget_form_injection
        MedicationForm.DROPS -> R.drawable.ic_widget_form_drops
        MedicationForm.LIQUID -> R.drawable.ic_widget_form_liquid
        MedicationForm.INHALER -> R.drawable.ic_widget_form_inhaler
        MedicationForm.OINTMENT -> R.drawable.ic_widget_form_ointment
        MedicationForm.SPRAY -> R.drawable.ic_widget_form_spray
        MedicationForm.OTHER -> R.drawable.ic_widget_form_other
    }

private fun takeAction(dose: TodayDose) =
    actionRunCallback<TakeDoseAction>(actionParametersOf(doseKeyParam to dose.key.encode()))

@Composable
private fun doseSubtitle(dose: TodayDose): String {
    val context = LocalContext.current
    return listOfNotNull(
        dose.strengthText,
        context.getString(R.string.unit_pieces, dose.amountText),
    ).joinToString(" · ")
}

@Composable
private fun slotLabel(slot: DaySlot): String {
    val context = LocalContext.current
    return context.getString(
        when (slot) {
            DaySlot.MORNING -> R.string.slot_morning
            DaySlot.AFTERNOON -> R.string.slot_afternoon
            DaySlot.EVENING -> R.string.slot_evening
            DaySlot.NIGHT -> R.string.slot_night
        },
    )
}

@Composable
private fun nextDoseLabel(
    state: WidgetState,
    next: TodayDose,
): String {
    val context = LocalContext.current
    val minutes = state.minutesToNext
    return when {
        next.date < state.date -> context.getString(R.string.widget_due_now)
        minutes == null -> ""
        minutes <= 0 -> context.getString(R.string.widget_due_now)
        minutes < MINUTES_PER_HOUR -> context.getString(R.string.widget_in_minutes, minutes)
        else -> context.getString(R.string.widget_in_hours, minutes / MINUTES_PER_HOUR)
    }
}

private const val MIN_RING_PX = 48
private const val CHIP_ICON_FRACTION = 0.55
