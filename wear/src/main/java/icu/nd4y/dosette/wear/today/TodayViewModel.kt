package icu.nd4y.dosette.wear.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.link.WatchAction
import icu.nd4y.dosette.link.WatchActionKind
import icu.nd4y.dosette.link.WatchDose
import icu.nd4y.dosette.link.WatchDoseStatus
import icu.nd4y.dosette.link.WatchPrn
import icu.nd4y.dosette.link.WatchSnapshot
import icu.nd4y.dosette.wear.link.WearLink
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/** A dose row: the phone's word, overlaid with the tap this watch has queued for it. */
data class WatchDoseUi(
    val dose: WatchDose,
    /** A tap not yet acknowledged by the phone. */
    val inFlight: WatchActionKind? = null,
    /** Planned time reached (or a reminder ringing): the row asks for attention. */
    val due: Boolean = false,
) {
    val id: String get() = dose.id
    val time: LocalTime get() = LocalTime.ofSecondOfDay(dose.timeMinutes * SECONDS_PER_MINUTE)
    val actedTime: LocalTime? get() = dose.actedTimeMinutes?.let { LocalTime.ofSecondOfDay(it * SECONDS_PER_MINUTE) }

    /** What the row shows: the phone's status, or the tap's outcome while it is in flight. */
    val status: WatchDoseStatus
        get() =
            when (inFlight) {
                WatchActionKind.TAKE -> WatchDoseStatus.TAKEN
                WatchActionKind.SKIP -> WatchDoseStatus.SKIPPED
                else -> dose.status
            }
}

data class WatchPrnUi(
    val prn: WatchPrn,
    val inFlight: Boolean = false,
)

data class WatchUiState(
    /** The first read of the Data Layer is done — until then nothing is known, not even "no phone". */
    val loaded: Boolean = false,
    /** A phone has published the day at least once. */
    val hasSnapshot: Boolean = false,
    val date: LocalDate? = null,
    /** The phone's picture is of another day (it has not run its midnight pass yet). */
    val stale: Boolean = false,
    val profileName: String? = null,
    val carryover: List<WatchDoseUi> = emptyList(),
    val doses: List<WatchDoseUi> = emptyList(),
    val prn: List<WatchPrnUi> = emptyList(),
    /** null until checked. */
    val phoneReachable: Boolean? = null,
) {
    val taken: Int get() = doses.count { it.status == WatchDoseStatus.TAKEN }
    val planned: Int get() = doses.size
    val isEmpty: Boolean get() = doses.isEmpty() && carryover.isEmpty() && prn.isEmpty()

    fun dose(id: String): WatchDoseUi? = (carryover + doses).firstOrNull { it.id == id }
}

sealed interface ActionOutcome {
    data class Sent(
        val kind: WatchActionKind,
        /** False when the tap is queued for a phone that is out of reach right now. */
        val phoneReachable: Boolean,
    ) : ActionOutcome

    data object Failed : ActionOutcome
}

@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val link: WearLink,
        private val clock: Clock,
    ) : ViewModel() {
        private val reachable = MutableStateFlow<Boolean?>(null)

        val uiState: StateFlow<WatchUiState> =
            combine(link.snapshot, link.pendingActions, reachable) { snapshot, pending, phone ->
                build(snapshot, pending, phone)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WatchUiState())

        // Confirmations show for a moment each; a buffer keeps quick taps from being dropped.
        private val _outcomes = MutableSharedFlow<ActionOutcome>(extraBufferCapacity = 8)

        /** One event per tap, once the Data Layer has accepted (or refused) it. */
        val outcomes: SharedFlow<ActionOutcome> = _outcomes

        init {
            checkPhone()
        }

        fun checkPhone() {
            viewModelScope.launch { reachable.value = link.phoneReachable() }
        }

        fun take(doseId: String) = send(WatchActionKind.TAKE, doseId = doseId)

        fun skip(doseId: String) = send(WatchActionKind.SKIP, doseId = doseId)

        fun snooze(doseId: String) = send(WatchActionKind.SNOOZE, doseId = doseId)

        fun takePrn(medicationId: String) = send(WatchActionKind.TAKE_PRN, medicationId = medicationId)

        private fun send(
            kind: WatchActionKind,
            doseId: String? = null,
            medicationId: String? = null,
        ) {
            viewModelScope.launch {
                val action =
                    WatchAction(
                        id = UUID.randomUUID().toString(),
                        kind = kind,
                        doseId = doseId,
                        medicationId = medicationId,
                        actedAt = clock.millis(),
                    )
                val accepted = link.send(action)
                val phone = if (accepted) link.phoneReachable().also { reachable.value = it } else false
                _outcomes.emit(if (accepted) ActionOutcome.Sent(kind, phone) else ActionOutcome.Failed)
            }
        }

        private fun build(
            snapshot: WatchSnapshot?,
            pending: List<WatchAction>,
            phone: Boolean?,
        ): WatchUiState {
            if (snapshot == null) return WatchUiState(loaded = true, phoneReachable = phone)
            val now = clock.instant().atZone(clock.zone)
            val today = now.toLocalDate()
            val date = runCatching { LocalDate.parse(snapshot.date) }.getOrDefault(today)
            // The latest tap per dose wins, as it will on the phone.
            val inFlight =
                pending
                    .filter { it.doseId != null }
                    .sortedBy { it.actedAt }
                    .associate { it.doseId to it.kind }
            val prnInFlight =
                pending.filter { it.kind == WatchActionKind.TAKE_PRN }.mapTo(
                    HashSet(),
                ) { it.medicationId }
            val rows =
                snapshot.doses.map { dose ->
                    val time = LocalTime.ofSecondOfDay(dose.timeMinutes * SECONDS_PER_MINUTE)
                    WatchDoseUi(
                        dose = dose,
                        inFlight = inFlight[dose.id],
                        due =
                            dose.carryover || dose.reminderActive || date.isBefore(today) ||
                                (date == today && !time.isAfter(now.toLocalTime())),
                    )
                }
            return WatchUiState(
                loaded = true,
                hasSnapshot = true,
                date = date,
                stale = date != today,
                profileName = snapshot.profileName,
                carryover = rows.filter { it.dose.carryover },
                doses = rows.filter { !it.dose.carryover },
                prn = snapshot.prn.map { WatchPrnUi(it, inFlight = it.medicationId in prnInFlight) },
                phoneReachable = phone,
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private const val SECONDS_PER_MINUTE = 60L
