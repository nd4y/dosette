package icu.nd4y.dosette.ui.today

import java.time.LocalDate

/**
 * Flat item list behind the Today timeline: past days, the today block
 * (title, hero ring, slots, as-needed meds), future days. Kept as data
 * so ordering and the landing position are unit-testable.
 */
sealed interface TimelineItem {
    val key: String

    data class DayHeader(
        val date: LocalDate,
    ) : TimelineItem {
        override val key: String get() = "day-$date"
    }

    data object Title : TimelineItem {
        override val key: String get() = "header"
    }

    data object Profiles : TimelineItem {
        override val key: String get() = "profiles"
    }

    data object Hero : TimelineItem {
        override val key: String get() = "hero"
    }

    data class Slot(
        val doses: List<TodayDose>,
    ) : TimelineItem {
        override val key: String
            get() = "slot-${doses.first().date}-${doses.first().slot}-${doses.first().time}"
    }

    data class Dose(
        val dose: TodayDose,
        /** Future doses are a preview: no take/skip affordances. */
        val readOnly: Boolean,
    ) : TimelineItem {
        override val key: String get() = "dose-${dose.key.encode()}"
    }

    data object TodayEmpty : TimelineItem {
        override val key: String get() = "today-empty"
    }

    data object PrnHeader : TimelineItem {
        override val key: String get() = "prn-header"
    }

    data class Prn(
        val med: PrnMed,
    ) : TimelineItem {
        override val key: String get() = "prn-${med.medicationId}"
    }
}

fun timelineItems(state: TodayUiState): List<TimelineItem> =
    buildList {
        val (past, todayAndOn) = state.days.partition { it.date < state.date }
        past.forEach { day -> addDay(day, readOnly = false, withHeader = true) }

        add(TimelineItem.Title)
        if (state.profiles.size > 1) add(TimelineItem.Profiles)
        add(TimelineItem.Hero)
        val today = todayAndOn.firstOrNull { it.date == state.date }
        if (today != null && today.doses.isEmpty()) {
            add(TimelineItem.TodayEmpty)
        } else if (today != null) {
            addDay(today, readOnly = false, withHeader = false)
        }

        if (state.prn.isNotEmpty()) {
            add(TimelineItem.PrnHeader)
            state.prn.forEach { add(TimelineItem.Prn(it)) }
        }

        todayAndOn
            .filter { it.date > state.date }
            .forEach { day -> addDay(day, readOnly = true, withHeader = true) }
    }

private fun MutableList<TimelineItem>.addDay(
    day: TimelineDay,
    readOnly: Boolean,
    withHeader: Boolean,
) {
    if (withHeader) add(TimelineItem.DayHeader(day.date))
    slotSections(day.doses).forEach { doses ->
        add(TimelineItem.Slot(doses))
        doses.forEach { add(TimelineItem.Dose(it, readOnly)) }
    }
}

/**
 * Where the list lands when the screen opens: the today block, or the
 * header of the earliest past day still carrying an unresolved dose.
 */
fun anchorIndex(
    items: List<TimelineItem>,
    state: TodayUiState,
): Int {
    val anchorKey =
        if (state.anchorDate < state.date) {
            TimelineItem.DayHeader(state.anchorDate).key
        } else {
            TimelineItem.Title.key
        }
    return items.indexOfFirst { it.key == anchorKey }.coerceAtLeast(0)
}
