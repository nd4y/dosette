package icu.nd4y.dosette.wear.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
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

/**
 * One dose with its actions. "Take" is a full-width button right under the
 * title, so it is on screen without a scroll on every watch size (an edge
 * button would only grow to full size once the list is scrolled to its end).
 * It is offered for everything but a dose already taken — a missed or skipped
 * one can still be marked taken, as on the phone. Skip and snooze are the
 * compact buttons below, only while the dose is pending; snooze only while
 * its reminder is ringing.
 */
@Composable
fun DoseScreen(
    dose: WatchDoseUi?,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: () -> Unit,
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
            if (dose == null) {
                item(key = "gone") {
                    Text(
                        text = stringResource(R.string.dose_gone),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    )
                }
                return@TransformingLazyColumn
            }
            item(key = "title") {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text(dose.dose.title, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (dose.inFlight == null && dose.status != WatchDoseStatus.TAKEN) {
                item(key = "take") {
                    Button(
                        onClick = onTake,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null) },
                        secondaryLabel = { Text(planLine(dose), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    ) {
                        Text(stringResource(R.string.action_take))
                    }
                }
            } else {
                item(key = "plan") {
                    Text(
                        text = planLine(dose),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, spec)
                                .padding(horizontal = 8.dp),
                    )
                }
            }
            if (dose.inFlight != null || dose.status != WatchDoseStatus.PENDING) {
                item(key = "status") {
                    Text(
                        text = doseSecondary(dose),
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            when (dose.status) {
                                WatchDoseStatus.MISSED -> MaterialTheme.colorScheme.error
                                WatchDoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, spec)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (dose.inFlight == null && dose.status == WatchDoseStatus.PENDING) {
                item(key = "skip") {
                    SecondaryAction(R.drawable.ic_close, stringResource(R.string.action_skip), onSkip, spec)
                }
                if (dose.dose.reminderActive) {
                    item(key = "snooze") {
                        SecondaryAction(R.drawable.ic_snooze, stringResource(R.string.action_snooze), onSnooze, spec)
                    }
                }
            }
        }
    }
}

/** "Planned for 13:00 · 2 pcs" */
@Composable
private fun planLine(dose: WatchDoseUi): String {
    val time = dose.time.format(TIME_FORMAT)
    return dose.dose.amount
        ?.let { stringResource(R.string.planned_at_amount, time, it) }
        ?: stringResource(R.string.planned_at, time)
}

@Composable
private fun TransformingLazyColumnItemScope.SecondaryAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    spec: TransformationSpec,
) {
    CompactButton(
        onClick = onClick,
        modifier = Modifier.transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        colors = ButtonDefaults.filledTonalButtonColors(),
        icon = { Icon(painter = painterResource(icon), contentDescription = null) },
    ) {
        Text(label)
    }
}
