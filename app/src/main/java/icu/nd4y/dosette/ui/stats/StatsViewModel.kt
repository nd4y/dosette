package icu.nd4y.dosette.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.stats.AdherenceCalculator
import icu.nd4y.dosette.domain.stats.StreakCalculator
import icu.nd4y.dosette.ui.common.dayTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class MedStat(
    val medicationId: String,
    val name: String,
    val colorSeed: Int,
    val taken: Int,
    val missed: Int,
) {
    val percent: Int? get() = AdherenceCalculator.percent(taken, missed)
}

data class StatsUiState(
    val loading: Boolean = true,
    /** Adherence over the last [StatsViewModel.WINDOW_DAYS] days; null = nothing finalized yet. */
    val percent: Int? = null,
    val taken: Int = 0,
    val missed: Int = 0,
    val skipped: Int = 0,
    /** Consecutive fully-adhered days ending today. */
    val streakDays: Int = 0,
    val meds: List<MedStat> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        medicationRepository: MedicationRepository,
        doseLogRepository: DoseLogRepository,
        settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<StatsUiState> =
            combine(
                settingsRepository.settings.map { it.activeProfileId }.distinctUntilChanged(),
                // The 30-day window slides at midnight, not on data changes.
                dayTicker(clock),
            ) { profileId, today -> profileId to today }
                .flatMapLatest { (profileId, today) ->
                    if (profileId == null) {
                        flowOf(StatsUiState(loading = false))
                    } else {
                        combine(
                            medicationRepository.observeByProfile(profileId),
                            doseLogRepository.observeRange(
                                profileId,
                                today.minusDays(STREAK_LOOKBACK_DAYS - 1L),
                                today,
                            ),
                        ) { meds, logs ->
                            buildState(
                                today = today,
                                scheduled = logs.filter { it.kind == DoseKind.SCHEDULED },
                                names = meds.associate { it.medication.id to it.medication.name },
                                seeds = meds.associate { it.medication.id to it.medication.colorSeed },
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

        private fun buildState(
            today: LocalDate,
            scheduled: List<DoseLog>,
            names: Map<String, String>,
            seeds: Map<String, Int>,
        ): StatsUiState {
            val windowStart = today.minusDays(WINDOW_DAYS - 1L)
            val window = scheduled.filter { it.date >= windowStart }

            val dayStatuses =
                scheduled
                    .groupBy { it.date }
                    .mapValues { (_, dayLogs) ->
                        AdherenceCalculator.dayStatus(
                            taken = dayLogs.count { it.status == DoseStatus.TAKEN },
                            skipped = dayLogs.count { it.status == DoseStatus.SKIPPED },
                            missed = dayLogs.count { it.status == DoseStatus.MISSED },
                        )
                    }

            val meds =
                window
                    .groupBy { it.medicationId }
                    .mapNotNull { (medicationId, medLogs) ->
                        val name = names[medicationId] ?: return@mapNotNull null
                        MedStat(
                            medicationId = medicationId,
                            name = name,
                            colorSeed = seeds[medicationId] ?: 0,
                            taken = medLogs.count { it.status == DoseStatus.TAKEN },
                            missed = medLogs.count { it.status == DoseStatus.MISSED },
                        )
                    }.sortedWith(compareByDescending<MedStat> { it.percent ?: -1 }.thenBy { it.name })

            return StatsUiState(
                loading = false,
                percent =
                    AdherenceCalculator.percent(
                        taken = window.count { it.status == DoseStatus.TAKEN },
                        missed = window.count { it.status == DoseStatus.MISSED },
                    ),
                taken = window.count { it.status == DoseStatus.TAKEN },
                missed = window.count { it.status == DoseStatus.MISSED },
                skipped = window.count { it.status == DoseStatus.SKIPPED },
                streakDays = StreakCalculator.currentStreak(dayStatuses, today),
                meds = meds,
            )
        }

        companion object {
            const val WINDOW_DAYS = 30
            const val STREAK_LOOKBACK_DAYS = 90L
        }
    }
