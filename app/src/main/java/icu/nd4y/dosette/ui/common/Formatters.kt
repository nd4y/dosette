package icu.nd4y.dosette.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.cabinet.ScheduleBrief
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 1.0 -> "1", 0.5 -> "0.5" — dose amounts without a trailing ".0". */
fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** "150 mg" from strength value + unit; null when there is no strength. */
fun strengthLabel(
    value: Double?,
    unit: String?,
): String? = value?.let { "${formatAmount(it)} ${unit.orEmpty()}".trim() }

/** Observable current locale (java.util flavor) — recomposes on language change. */
@Composable
fun currentLocale(): java.util.Locale =
    java.util.Locale
        .forLanguageTag(
            androidx.compose.ui.text.intl.Locale.current
                .toLanguageTag(),
        )

fun List<LocalTime>.joinTimes(): String = joinToString(", ") { it.format(TimeFormat) }

/** "Every day" / "Every other day" / "Every N days" — 1 and 2 read as words. */
@Composable
fun everyNDaysText(interval: Int): String =
    when (interval) {
        1 -> stringResource(R.string.schedule_every_day)
        2 -> stringResource(R.string.schedule_every_other_day)
        else -> stringResource(R.string.schedule_every_n_days, interval)
    }

@Composable
fun ScheduleBrief.asText(): String =
    when (this) {
        is ScheduleBrief.FixedTimes -> {
            listOf(
                pluralStringResource(R.plurals.times_per_day, times.size, times.size),
                times.joinTimes(),
            ).joinToString(" · ")
        }

        is ScheduleBrief.Weekdays -> {
            listOf(
                pluralStringResource(R.plurals.days_per_week, days, days),
                times.joinTimes(),
            ).joinToString(" · ")
        }

        is ScheduleBrief.EveryNDays -> {
            listOf(
                everyNDaysText(interval),
                times.joinTimes(),
            ).joinToString(" · ")
        }

        is ScheduleBrief.Cycle -> {
            stringResource(R.string.schedule_cycle_summary, daysOn, daysOff)
        }

        ScheduleBrief.AsNeeded -> {
            stringResource(R.string.schedule_as_needed)
        }

        ScheduleBrief.None -> {
            ""
        }
    }
