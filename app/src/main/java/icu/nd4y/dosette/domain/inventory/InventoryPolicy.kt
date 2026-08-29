package icu.nd4y.dosette.domain.inventory

import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleType
import kotlin.math.floor

object InventoryPolicy {
    /**
     * Low-stock notifications fire only on the downward crossing of the
     * threshold — not on every take below it.
     */
    fun crossedLowThreshold(
        before: Double,
        after: Double,
        threshold: Double?,
    ): Boolean {
        if (threshold == null) return false
        return before > threshold && after <= threshold
    }

    /** Average consumption per day implied by a schedule; 0 for PRN. */
    fun dailyConsumption(schedule: Schedule): Double {
        val perActiveDay = schedule.times.sumOf { it.doseAmount }
        return when (schedule.type) {
            ScheduleType.FIXED_TIMES -> {
                perActiveDay
            }

            ScheduleType.WEEKDAYS -> {
                perActiveDay * schedule.weekdays.size / DAYS_PER_WEEK
            }

            ScheduleType.EVERY_N_DAYS -> {
                val interval = schedule.intervalDays
                if (interval == null || interval < 1) 0.0 else perActiveDay / interval
            }

            ScheduleType.CYCLE -> {
                cycleConsumption(perActiveDay, schedule.cycleDaysOn, schedule.cycleDaysOff)
            }

            ScheduleType.AS_NEEDED -> {
                0.0
            }
        }
    }

    fun dailyConsumption(schedules: List<Schedule>): Double = schedules.sumOf { dailyConsumption(it) }

    private fun cycleConsumption(
        perActiveDay: Double,
        on: Int?,
        off: Int?,
    ): Double =
        if (on == null || off == null) {
            0.0
        } else if (on < 1 || off < 0) {
            0.0
        } else {
            perActiveDay * on / (on + off)
        }

    /** Whole days the stock lasts at the given consumption; null when consumption is zero. */
    fun daysOfSupply(
        stock: Double,
        dailyConsumption: Double,
    ): Int? {
        require(stock >= 0) { "negative stock" }
        if (dailyConsumption <= 0.0) return null
        return floor(stock / dailyConsumption).toInt()
    }

    private const val DAYS_PER_WEEK = 7.0
}
