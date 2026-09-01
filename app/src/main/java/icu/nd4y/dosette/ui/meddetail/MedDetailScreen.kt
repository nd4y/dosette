package icu.nd4y.dosette.ui.meddetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.domain.model.MedicationVariant
import icu.nd4y.dosette.domain.stats.AdherenceCalculator.DayStatus
import icu.nd4y.dosette.ui.common.TimeFormat
import icu.nd4y.dosette.ui.common.formatAmount
import icu.nd4y.dosette.ui.common.strengthLabel
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.MedIconBox
import icu.nd4y.dosette.ui.designsystem.StockBadge
import icu.nd4y.dosette.ui.designsystem.strokeGlyph

@Composable
fun MedDetailScreen(
    medicationId: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedDetailViewModel =
        hiltViewModel<MedDetailViewModel, MedDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(medicationId) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    MedDetailContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onEdit = onEdit,
        onArchive = viewModel::archive,
        onUnarchive = viewModel::unarchive,
        onDelete = viewModel::delete,
        onRefill = viewModel::refill,
        onSetStock = viewModel::setStock,
        modifier = modifier,
    )
}

/** Stateless body — rendered directly by screenshot tests. */
@Composable
fun MedDetailContent(
    state: MedDetailUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onRefill: (String, Double) -> Unit,
    onSetStock: (String, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var refillFor by remember { mutableStateOf<MedicationVariant?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        val med = state.details
        DetailTopBar(
            archived = med?.medication?.isArchived == true,
            enabled = med != null,
            onBack = onBack,
            onEdit = onEdit,
            onArchive = onArchive,
            onUnarchive = onUnarchive,
            onDeleteRequest = { confirmDelete = true },
        )
        if (med == null) return

        DetailHero(state)
        ScheduleBlock(med)
        StockBlock(med.variants.filter { it.trackingEnabled }, onRefillRequest = { refillFor = it })
        AdherenceBlock(state)
    }

    refillFor?.let { variant ->
        RefillDialog(
            variant = variant,
            onRefill = onRefill,
            onSetStock = onSetStock,
            onDismiss = { refillFor = null },
        )
    }
    if (confirmDelete) {
        DeleteDialog(
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailTopBar(
    archived: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
            Icon(DosetteIcons.Back, contentDescription = stringResource(R.string.action_back))
        }
        Spacer(modifier = Modifier.weight(1f))
        if (!enabled) return@Row
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = EditIcon,
                contentDescription = stringResource(R.string.action_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = MenuDotsIcon,
                    contentDescription = stringResource(R.string.detail_more_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (archived) R.string.action_unarchive else R.string.action_archive,
                            ),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        if (archived) onUnarchive() else onArchive()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDeleteRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailHero(state: MedDetailUiState) {
    val med = state.details ?: return
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
        if (med.medication.isArchived) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.med_archived_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleBlock(med: MedicationDetails) {
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
}

@Composable
private fun StockBlock(
    tracked: List<MedicationVariant>,
    onRefillRequest: (MedicationVariant) -> Unit,
) {
    if (tracked.isEmpty()) return
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
                TextButton(onClick = { onRefillRequest(variant) }) {
                    Text(stringResource(R.string.stock_refill_action))
                }
            }
        }
    }
}

@Composable
private fun AdherenceBlock(state: MedDetailUiState) {
    if (state.days.isEmpty()) return
    DetailBlock(title = stringResource(R.string.detail_last30)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            state.days.forEach { day ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(26.dp)
                            .background(dayColor(day.status), RoundedCornerShape(3.dp)),
                )
            }
        }
        state.adherencePercent?.let { percent ->
            Text(
                text = stringResource(R.string.detail_taken_percent, percent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun dayColor(status: DayStatus): Color =
    when (status) {
        DayStatus.COMPLETE -> MaterialTheme.colorScheme.primary
        DayStatus.PARTIAL -> PartialColor
        DayStatus.ALL_MISSED -> MaterialTheme.colorScheme.error
        DayStatus.NONE -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

@Composable
private fun RefillDialog(
    variant: MedicationVariant,
    onRefill: (String, Double) -> Unit,
    onSetStock: (String, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by rememberSaveable {
        mutableStateOf(variant.defaultRefillAmount?.let(::formatAmount).orEmpty())
    }
    val amount = amountText.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.refill_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.stock_left, formatAmount(variant.currentStock)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.refill_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    amount?.let { onRefill(variant.id, it) }
                    onDismiss()
                },
                enabled = amount != null && amount > 0,
            ) { Text(stringResource(R.string.refill_add)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                TextButton(
                    onClick = {
                        amount?.let { onSetStock(variant.id, it) }
                        onDismiss()
                    },
                    enabled = amount != null && amount >= 0,
                ) { Text(stringResource(R.string.refill_set_exact)) }
            }
        },
    )
}

@Composable
private fun DeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_med_title)) },
        text = { Text(stringResource(R.string.delete_med_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DetailBlock(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

/** Same partial-day accent as the calendar grid. */
private val PartialColor = Color(0xFFE8A33D)

private val EditIcon: ImageVector by lazy {
    strokeGlyph("Edit") {
        moveTo(17f, 3.5f)
        lineTo(20.5f, 7f)
        lineTo(8f, 19.5f)
        lineTo(3.5f, 20.5f)
        lineTo(4.5f, 16f)
        close()
    }
}

private val MenuDotsIcon: ImageVector by lazy {
    strokeGlyph("MenuDots") {
        listOf(5.5f, 12f, 18.5f).forEach { cy ->
            moveTo(12f, cy - 0.9f)
            arcToRelative(0.9f, 0.9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 1.8f)
            arcToRelative(0.9f, 0.9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -1.8f)
        }
    }
}
