package icu.nd4y.dosette.wear.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import icu.nd4y.dosette.link.WatchDoseStatus
import icu.nd4y.dosette.wear.R
import java.time.format.DateTimeFormatter

internal val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** The day's list: what the phone's Today tab and widget show, minus the calendar. */
@Composable
fun TodayScreen(
    state: WatchUiState,
    onOpenDose: (WatchDoseUi) -> Unit,
    onTakePrn: (WatchPrnUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { Header(state, spec) }
            if (state.planned > 0) {
                item(key = "progress") { ProgressLine(state.taken, state.planned, spec) }
            }
            when {
                !state.loaded -> {
                    Unit
                }

                !state.hasSnapshot -> {
                    item(key = "no-phone") { Hint(stringResource(R.string.no_phone_data), spec) }
                }

                state.isEmpty -> {
                    item(key = "empty") { Hint(stringResource(R.string.empty_today), spec) }
                }

                state.phoneReachable == false -> {
                    item(key = "unreachable") { Hint(stringResource(R.string.phone_unreachable), spec) }
                }
            }
            if (state.carryover.isNotEmpty()) {
                item(key = "yesterday") { SubHeader(stringResource(R.string.header_yesterday), spec) }
                items(state.carryover, key = { "c:" + it.id }) { dose ->
                    DoseRow(dose, onClick = { onOpenDose(dose) }, spec = spec)
                }
            }
            items(state.doses, key = { it.id }) { dose ->
                DoseRow(dose, onClick = { onOpenDose(dose) }, spec = spec)
            }
            if (state.prn.isNotEmpty()) {
                item(key = "prn") { SubHeader(stringResource(R.string.header_prn), spec) }
                items(state.prn, key = { "prn:" + it.prn.medicationId }) { prn ->
                    PrnRow(prn, onClick = { onTakePrn(prn) }, spec = spec)
                }
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.Header(
    state: WatchUiState,
    spec: TransformationSpec,
) {
    ListHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(
            text =
                state.profileName?.let { stringResource(R.string.today_title_profile, it) }
                    ?: stringResource(R.string.today_title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransformingLazyColumnItemScope.ProgressLine(
    taken: Int,
    planned: Int,
    spec: TransformationSpec,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .transformedHeight(this, spec)
                .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            progress = { taken.toFloat() / planned },
            modifier = Modifier.size(18.dp),
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.today_progress, taken, planned),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransformingLazyColumnItemScope.SubHeader(
    text: String,
    spec: TransformationSpec,
) {
    ListHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TransformingLazyColumnItemScope.Hint(
    text: String,
    spec: TransformationSpec,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .transformedHeight(this, spec)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun TransformingLazyColumnItemScope.DoseRow(
    dose: WatchDoseUi,
    onClick: () -> Unit,
    spec: TransformationSpec,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        colors = doseRowColors(dose),
        icon = { StatusIcon(dose) },
        secondaryLabel = {
            Text(doseSecondary(dose), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    ) {
        Text(dose.dose.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun doseRowColors(dose: WatchDoseUi): ButtonColors =
    when {
        dose.inFlight == null && dose.status == WatchDoseStatus.PENDING && dose.due -> {
            ButtonDefaults.buttonColors()
        }

        dose.inFlight == null && dose.status == WatchDoseStatus.PENDING -> {
            ButtonDefaults.filledTonalButtonColors()
        }

        else -> {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconColor = statusTint(dose.status),
            )
        }
    }

@Composable
private fun statusTint(status: WatchDoseStatus): Color =
    when (status) {
        WatchDoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
        WatchDoseStatus.MISSED -> MaterialTheme.colorScheme.error
        WatchDoseStatus.SKIPPED, WatchDoseStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun StatusIcon(dose: WatchDoseUi) {
    val icon =
        when {
            dose.inFlight != null -> R.drawable.ic_sync
            dose.status == WatchDoseStatus.TAKEN -> R.drawable.ic_check
            dose.status == WatchDoseStatus.SKIPPED -> R.drawable.ic_close
            dose.status == WatchDoseStatus.MISSED -> R.drawable.ic_missed
            else -> R.drawable.ic_pill
        }
    Icon(painter = painterResource(icon), contentDescription = null)
}

/** Second line of a dose row. */
@Composable
internal fun doseSecondary(dose: WatchDoseUi): String {
    val time = dose.time.format(TIME_FORMAT)
    return when {
        dose.inFlight != null -> {
            stringResource(R.string.sending)
        }

        dose.status == WatchDoseStatus.TAKEN -> {
            dose.actedTime?.let { stringResource(R.string.status_taken_at, it.format(TIME_FORMAT)) }
                ?: stringResource(R.string.status_taken)
        }

        dose.status == WatchDoseStatus.SKIPPED -> {
            "${stringResource(R.string.status_skipped)} · $time"
        }

        dose.status == WatchDoseStatus.MISSED -> {
            "${stringResource(R.string.status_missed)} · $time"
        }

        else -> {
            listOfNotNull(time, dose.dose.amount?.let { stringResource(R.string.dose_amount, it) })
                .joinToString(" · ")
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.PrnRow(
    prn: WatchPrnUi,
    onClick: () -> Unit,
    spec: TransformationSpec,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = !prn.inFlight,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        icon = {
            Icon(
                painter = painterResource(if (prn.inFlight) R.drawable.ic_sync else R.drawable.ic_add),
                contentDescription = null,
            )
        },
        secondaryLabel = {
            Text(
                text = if (prn.inFlight) stringResource(R.string.sending) else stringResource(R.string.header_prn),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(prn.prn.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
