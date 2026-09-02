package icu.nd4y.dosette.domain

import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleTime
import icu.nd4y.dosette.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

fun schedule(
    id: String = "s1",
    medicationId: String = "m1",
    type: ScheduleType = ScheduleType.FIXED_TIMES,
    startDate: LocalDate = LocalDate.parse("2026-08-01"),
    endDate: LocalDate? = null,
    anchorDate: LocalDate? = null,
    oneOff: Boolean = false,
    createdAt: Instant = Instant.parse("2026-08-01T00:00:00Z"),
    weekdays: Set<DayOfWeek> = emptySet(),
    intervalDays: Int? = null,
    cycleDaysOn: Int? = null,
    cycleDaysOff: Int? = null,
    times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    amount: Double = 1.0,
): Schedule =
    Schedule(
        id = id,
        medicationId = medicationId,
        type = type,
        startDate = startDate,
        endDate = endDate,
        anchorDate = anchorDate,
        oneOff = oneOff,
        weekdays = weekdays,
        intervalDays = intervalDays,
        cycleDaysOn = cycleDaysOn,
        cycleDaysOff = cycleDaysOff,
        defaultDoseAmount = amount,
        remindersEnabled = true,
        createdAt = createdAt,
        times =
            times.mapIndexed { index, time ->
                ScheduleTime(id = "$id-t$index", scheduleId = id, time = time, doseAmount = amount, sortIndex = index)
            },
    )
