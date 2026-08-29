package icu.nd4y.dosette.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.cabinet.ScheduleBrief
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Observable current locale (java.util flavor) — recomposes on language change. */
@Composable
fun currentLocale(): java.util.Locale =
    java.util.Locale
        .forLanguageTag(
            androidx.compose.ui.text.intl.Locale.current
                .toLanguageTag(),
        )

fun List<LocalTime>.joinTimes(): String = joinToString(", ") { it.format(TimeFormat) }

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
                if (interval == 2) {
                    stringResource(R.string.schedule_every_other_day)
                } else {
                    stringResource(R.string.schedule_every_n_days, interval)
                },
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
