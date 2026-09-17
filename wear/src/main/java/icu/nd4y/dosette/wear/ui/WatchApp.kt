package icu.nd4y.dosette.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.curvedText
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import icu.nd4y.dosette.link.WatchActionKind
import icu.nd4y.dosette.wear.R
import icu.nd4y.dosette.wear.theme.WatchTheme
import icu.nd4y.dosette.wear.today.ActionOutcome
import icu.nd4y.dosette.wear.today.DoseScreen
import icu.nd4y.dosette.wear.today.TodayScreen
import icu.nd4y.dosette.wear.today.TodayViewModel
import icu.nd4y.dosette.wear.today.WatchUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data object TodayKey : NavKey

@Serializable
data class DoseKey(
    val doseId: String,
) : NavKey

/** The screens' callbacks, bundled so the stateless root stays testable without a ViewModel. */
data class WatchActions(
    val take: (String) -> Unit = {},
    val skip: (String) -> Unit = {},
    val snooze: (String) -> Unit = {},
    val takePrn: (String) -> Unit = {},
)

@Composable
fun WatchApp(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WatchContent(
        state = state,
        actions =
            WatchActions(
                take = viewModel::take,
                skip = viewModel::skip,
                snooze = viewModel::snooze,
                takePrn = viewModel::takePrn,
            ),
        outcomes = viewModel.outcomes,
    )
}

/**
 * Everything on screen given a state: the list, the dose screen it pushes,
 * and the confirmation that follows each tap. [timeSource] is only ever
 * replaced by tests, so a screenshot does not carry the wall clock.
 */
@Composable
fun WatchContent(
    state: WatchUiState,
    actions: WatchActions,
    outcomes: Flow<ActionOutcome>? = null,
    timeSource: TimeSource? = null,
    dynamicColor: Boolean = true,
) {
    val backStack = rememberNavBackStack(TodayKey)
    var outcome by remember { mutableStateOf<ActionOutcome?>(null) }
    LaunchedEffect(outcomes) {
        outcomes?.collect { outcome = it }
    }
    WatchTheme(dynamicColor = dynamicColor) {
        AppScaffold(
            timeText = { if (timeSource == null) TimeText() else TimeText(timeSource = timeSource) },
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                entryProvider =
                    entryProvider {
                        entry<TodayKey> {
                            TodayScreen(
                                state = state,
                                onOpenDose = { backStack.add(DoseKey(it.id)) },
                                onTakePrn = { actions.takePrn(it.prn.medicationId) },
                            )
                        }
                        entry<DoseKey> { key ->
                            DoseScreen(
                                dose = state.dose(key.doseId),
                                onTake = {
                                    actions.take(key.doseId)
                                    backStack.removeLastOrNull()
                                },
                                onSkip = {
                                    actions.skip(key.doseId)
                                    backStack.removeLastOrNull()
                                },
                                onSnooze = {
                                    actions.snooze(key.doseId)
                                    backStack.removeLastOrNull()
                                },
                            )
                        }
                    },
            )
            OutcomeDialogs(outcome, onDismiss = { outcome = null })
        }
    }
}

@Composable
private fun OutcomeDialogs(
    outcome: ActionOutcome?,
    onDismiss: () -> Unit,
) {
    val sent = outcome as? ActionOutcome.Sent
    val sentText =
        when {
            sent == null -> ""
            !sent.phoneReachable -> stringResource(R.string.confirm_queued)
            else -> stringResource(sent.kind.confirmation())
        }
    SuccessConfirmationDialog(
        visible = sent != null,
        onDismissRequest = onDismiss,
        curvedText = { curvedText(sentText) },
    )
    val failedText = stringResource(R.string.confirm_failed)
    FailureConfirmationDialog(
        visible = outcome is ActionOutcome.Failed,
        onDismissRequest = onDismiss,
        curvedText = { curvedText(failedText) },
    )
}

private fun WatchActionKind.confirmation(): Int =
    when (this) {
        WatchActionKind.TAKE, WatchActionKind.TAKE_PRN -> R.string.confirm_taken
        WatchActionKind.SKIP -> R.string.confirm_skipped
        WatchActionKind.SNOOZE -> R.string.confirm_snoozed
    }
