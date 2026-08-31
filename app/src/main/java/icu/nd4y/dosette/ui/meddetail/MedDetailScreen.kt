package icu.nd4y.dosette.ui.meddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.common.TimeFormat
import icu.nd4y.dosette.ui.common.formatAmount
import icu.nd4y.dosette.ui.common.strengthLabel
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.MedIconBox
import icu.nd4y.dosette.ui.designsystem.StockBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedDetailScreen(
    medicationId: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedDetailViewModel =
        hiltViewModel<MedDetailViewModel, MedDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(medicationId) },
        ),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
            Icon(DosetteIcons.Back, contentDescription = stringResource(R.string.action_back))
        }

        val med = details ?: return
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MedIconBox(form = med.medication.form, colorSeed = med.medication.colorSeed, size = 84.dp)
            Text(
                text = med.medication.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            strengthLabel(med.medication.strengthValue, med.medication.strengthUnit)?.let { strength ->
                Text(
                    text = strength,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DetailBlock(title = stringResource(R.string.review_schedule)) {
            med.schedules.filter { it.endDate == null }.forEach { schedule ->
                // Wrap: 4-5 daily slots would otherwise run off the screen.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    schedule.times.forEach { slot ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Text(
                                text = "${slot.time.format(TimeFormat)} × ${formatAmount(slot.doseAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            med.medication.instructions?.let { instructions ->
                Text(
                    text = instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val tracked = med.variants.filter { it.trackingEnabled }
        if (tracked.isNotEmpty()) {
            DetailBlock(title = stringResource(R.string.review_stock)) {
                tracked.forEach { variant ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                variant.strengthValue
                                    ?.let { "${formatAmount(it)} ${variant.strengthUnit.orEmpty()}".trim() }
                                    ?: (variant.label ?: "—"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        StockBadge(
                            text = stringResource(R.string.stock_left, formatAmount(variant.currentStock)),
                            warning = variant.lowStockThreshold?.let { variant.currentStock <= it } == true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border =
            androidx.compose.foundation
                .BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}
